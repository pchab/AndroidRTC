package fr.pchab.AndroidRTC;

import com.codebutler.android_websockets.SocketIOClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.*;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;

public class RTCClient {
    private String name;
    private boolean privacy;
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<String, Peer>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream lMS;
    private String cameraFacing = "back";
    private RTCListener mListener;
    private SocketIOClient client;
    private final MessageHandler messageHandler = new MessageHandler();

    public interface RTCListener{
        void onCallReady(String callId);

        void onStatusChanged(String newStatus);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream);

        void onRemoveRemoteStream(MediaStream remoteStream);
    }

    private interface Command{
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    private class CreateOfferCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Peer peer = peers.get(peerId);
            peer.pc.createOffer(peer, pcConstraints);
        }
    }

    private class SetRemoteSDPCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
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

    private void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("message", new JSONArray().put(message));
    }

    private class MessageHandler {
        private HashMap<String, Command> commandMap;

        public MessageHandler() {
            this.commandMap = new HashMap<String, Command>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new SetRemoteSDPCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        public void handle(JSONObject json) throws JSONException {
            String from = json.getString("from");
            String type = json.getString("type");
            JSONObject payload = null;
            if(!type.equals("init")) {
                payload = json.getJSONObject("payload");
            }

            // if peer is unknown, add him
            if(!peers.containsKey(from)) {
                addPeer(from);
            }

            commandMap.get(type).execute(from, payload);
        }
    }

    private class Peer implements SdpObserver, PeerConnection.Observer{
        private PeerConnection pc;
        private String id;

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
        public void onSetSuccess() {
        }

        @Override
        public void onCreateFailure(String s) {}

        @Override
        public void onSetFailure(String s) {}

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        }

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
        public void onError() {}

        @Override
        public void onAddStream(MediaStream mediaStream) {
            mListener.onAddRemoteStream(mediaStream);
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            mListener.onRemoveRemoteStream(mediaStream);
        }

        public Peer(String id) {
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
            mListener.onStatusChanged("CONNECTING");
        }
    }

    public RTCClient(RTCListener listener, String host) {
        mListener = listener;
        factory = new PeerConnectionFactory();

        client = new SocketIOClient(URI.create(host), new SocketIOClient.Handler() {
            @Override
            public void onConnect() {
            }

            @Override
            public void on(String event, JSONArray arguments) {
                try {
                    if(event.equals("id")) {
                        mListener.onCallReady(arguments.getString(0));
                    } else {
                        JSONObject json = arguments.getJSONObject(0);
                        messageHandler.handle(json);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onJSON(JSONObject json) {
            }

            @Override
            public void onMessage(String message) {
            }

            @Override
            public void onDisconnect(int code, String reason) {
            }

            @Override
            public void onError(Exception error) {
            }

            @Override
            public void onConnectToEndpoint(String endpoint) {
            }
        });
        client.connect();

        iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    }

    public void setName(String name){
        this.name = name;
    }

    public void setPrivacy(boolean privacy){
        this.privacy = privacy;
    }

    public void setCamera(String cameraFacing, String height, String width){
        this.cameraFacing = cameraFacing;
        MediaConstraints videoConstraints = new MediaConstraints();
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", height));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", width));

        VideoSource videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
        lMS = factory.createLocalMediaStream("ARDAMS");
        VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
        lMS.addTrack(videoTrack);
        lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));
    }

    public void start(){
        try {
            JSONObject message = new JSONObject();
            message.put("name", name);
            message.put("privacy", privacy);
            client.emit("readyToStream", new JSONArray().put(message));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /*
    Cycle through likely device names for the camera and return the first
    capturer that works, or crash if none do.
    */
    private VideoCapturer getVideoCapturer() {
        int[] cameraIndex = { 0, 1 };
        int[] cameraOrientation = { 0, 90, 180, 270 };
        for (int index : cameraIndex) {
            for (int orientation : cameraOrientation) {
                String name = "Camera " + index + ", Facing " + cameraFacing +
                        ", Orientation " + orientation;
                VideoCapturer capturer = VideoCapturer.create(name);
                if (capturer != null) {
                    return capturer;
                }
            }
        }
        throw new RuntimeException("Failed to open capturer");
    }

    private void addPeer(String id) {
        Peer peer = new Peer(id);
        peer.pc.addStream(lMS, new MediaConstraints());
        mListener.onLocalStream(lMS);
        peers.put(id, peer);
    }

    private void removePeer(String id) {
        Peer peer = peers.get(id);
        peer.pc.close();
        peer.pc.dispose();
        peers.remove(peer.id);
    }
}
