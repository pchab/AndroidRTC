package fr.pchab.AndroidRTC;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;
import org.webrtc.*;

class WebRtcClient {
  private final static int MAX_PEER = 2;
  private boolean[] endPoints = new boolean[MAX_PEER];
  private PeerConnectionFactory factory;
  private HashMap<String, Peer> peers = new HashMap<>();
  private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
  private MediaConstraints pcConstraints = new MediaConstraints();
  private MediaStream localMS;
  private VideoSource videoSource;
  private RTCListener mListener;
  private Socket client;
  private final static String TAG = WebRtcClient.class.getCanonicalName();

  public interface RTCListener{
    void onCallReady(String callId);

    void onStatusChanged(String newStatus);

    void onLocalStream(MediaStream localStream);

    void onAddRemoteStream(MediaStream remoteStream, int endPoint);

    void onRemoveRemoteStream(MediaStream remoteStream, int endPoint);
  }

  private interface Command{
    void execute(String peerId, JSONObject payload) throws JSONException;
  }

  private class CreateOfferCommand implements Command{
    public void execute(String peerId, JSONObject payload) throws JSONException {
    	Log.d(TAG,"CreateOfferCommand");
      Peer peer = peers.get(peerId);
      peer.pc.createOffer(peer, pcConstraints);
    }
  }

  private class CreateAnswerCommand implements Command{
    public void execute(String peerId, JSONObject payload) throws JSONException {
    	Log.d(TAG,"CreateAnswerCommand");
      Peer peer = peers.get(peerId);
      SessionDescription sdp = new SessionDescription(
                                                      SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                                                      payload.getString("sdp")
                                                      );
      peer.pc.setRemoteDescription(peer, sdp);
      peer.pc.createAnswer(peer, pcConstraints);
    }
  }

  private class SetRemoteSDPCommand implements Command{
    public void execute(String peerId, JSONObject payload) throws JSONException {
    	Log.d(TAG,"SetRemoteSDPCommand");
      Peer peer = peers.get(peerId);
      SessionDescription sdp = new SessionDescription(
                                                      SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                                                      payload.getString("sdp")
                                                      );
      peer.pc.setRemoteDescription(peer, sdp);
    }
  }

  private class AddIceCandidateCommand implements Command{
    public void execute(String peerId, JSONObject payload) throws JSONException {
    	Log.d(TAG,"AddIceCandidateCommand");
      PeerConnection pc = peers.get(peerId).pc;
      if (pc.getRemoteDescription() != null) {
        IceCandidate candidate = new IceCandidate(
                                                  payload.getString("id"),
                                                  payload.getInt("label"),
                                                  payload.getString("candidate")
                                                  );
        pc.addIceCandidate(candidate);
      }
    }
  }

  public void sendMessage(String to, String type, JSONObject payload) throws JSONException {
    JSONObject message = new JSONObject();
    message.put("to", to);
    message.put("type", type);
    message.put("payload", payload);
    client.emit("message", message);
  }

  private class MessageHandler {
    private HashMap<String, Command> commandMap;

    private MessageHandler() {
      this.commandMap = new HashMap<>();
      commandMap.put("init", new CreateOfferCommand());
      commandMap.put("offer", new CreateAnswerCommand());
      commandMap.put("answer", new SetRemoteSDPCommand());
      commandMap.put("candidate", new AddIceCandidateCommand());
    }

    public Emitter.Listener onMessage = new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        JSONObject data = (JSONObject) args[0];
        try {
          String from = data.getString("from");
          String type = data.getString("type");
          JSONObject payload = null;
          if(!type.equals("init")) {
            payload = data.getJSONObject("payload");
          }
          // if peer is unknown, try to add him
          if(!peers.containsKey(from)) {
            // if MAX_PEER is reach, ignore the call
            int endPoint = findEndPoint();
            if(endPoint != MAX_PEER) {
              Peer peer = addPeer(from, endPoint);
              peer.pc.addStream(localMS);
              commandMap.get(type).execute(from, payload);
            }
          } else {
            commandMap.get(type).execute(from, payload);
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    };

    public Emitter.Listener onId = new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        String id = (String) args[0];
        mListener.onCallReady(id);
      }
    };
  }

  private class Peer implements SdpObserver, PeerConnection.Observer{
    private PeerConnection pc;
    private String id;
    private int endPoint;

    @Override
    public void onCreateSuccess(final SessionDescription sdp) {
      try {
        JSONObject payload = new JSONObject();
        payload.put("type", sdp.type.canonicalForm());
        payload.put("sdp", sdp.description);
        sendMessage(id, sdp.type.canonicalForm(), payload);
        pc.setLocalDescription(Peer.this, sdp);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onSetSuccess() {}

    @Override
    public void onCreateFailure(String s) {}

    @Override
    public void onSetFailure(String s) {}

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
      if(iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
        removePeer(id);
        mListener.onStatusChanged("DISCONNECTED");
      }
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
      try {
        JSONObject payload = new JSONObject();
        payload.put("label", candidate.sdpMLineIndex);
        payload.put("id", candidate.sdpMid);
        payload.put("candidate", candidate.sdp);
        sendMessage(id, "candidate", payload);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
    	Log.d(TAG,"onAddStream "+mediaStream.label());
      // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
      mListener.onAddRemoteStream(mediaStream, endPoint+1);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
      mListener.onRemoveRemoteStream(mediaStream, endPoint);

      removePeer(id);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {}

    @Override
    public void onRenegotiationNeeded() {

    }

    public Peer(String id, int endPoint) {
    	Log.d(TAG,"new Peer: "+id + " " + endPoint);
      this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
      this.id = id;
      this.endPoint = endPoint;

      pc.addStream(localMS); //, new MediaConstraints()

      mListener.onStatusChanged("CONNECTING");
    }
  }

  public WebRtcClient(RTCListener listener, String host) {
    mListener = listener;
    factory = new PeerConnectionFactory();
    MessageHandler messageHandler = new MessageHandler();

    try {
      client = IO.socket(host);
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    client.on("id", messageHandler.onId);
    client.on("message", messageHandler.onMessage);
    client.connect();

    iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
    iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

    pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
    pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
  }

  public void setCamera(String height, String width){
    MediaConstraints videoConstraints = new MediaConstraints();
    videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", height));
    videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", width));

    videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
    AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
    localMS = factory.createLocalMediaStream("ARDAMS");
    localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
    localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

    mListener.onLocalStream(localMS);
  }

  public void stopVideoSource() {
    if(videoSource != null) {
      videoSource.stop();
    }
  }

  public void restartVideoSource() {
    if(videoSource != null) {
      videoSource.restart();
    }
  }

  private int findEndPoint() {
    for(int i = 0; i < MAX_PEER; i++) {
      if(!endPoints[i]) return i;
    }
    return MAX_PEER;
  }

  public void start(String name){
    try {
      JSONObject message = new JSONObject();
      message.put("name", name);
      client.emit("readyToStream", message);
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  private VideoCapturer getVideoCapturer() {
    String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
    return VideoCapturerAndroid.create(frontCameraDeviceName);
  }

  private Peer addPeer(String id, int endPoint) {
    Peer peer = new Peer(id, endPoint);
    peers.put(id, peer);

    endPoints[endPoint] = true;
    return peer;
  }

  private void removePeer(String id) {
    Peer peer = peers.get(id);
    peer.pc.close();
    peer.pc.dispose();
    peers.remove(peer.id);

    endPoints[peer.endPoint] = false;
  }
}
