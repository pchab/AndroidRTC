package fr.pchab.AndroidRTC;

import android.app.Activity;
import android.os.Bundle;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.codebutler.android_websockets.SocketIOClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;

import java.net.URI;
import java.util.LinkedList;
import java.util.HashMap;

public class RTCActivity extends Activity {
    private static final String HOST = "http://54.214.218.3:3000/";
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<String, Peer>();
    private MediaConstraints pcConstraints;
    private MediaStream lMS;
    private EditText name;
    private TextView link;
    private final SocketIOClient client = new SocketIOClient(URI.create(HOST), new SocketIOClient.Handler() {
        @Override
        public void onConnect() {
        }

        @Override
        public void on(final String event, final JSONArray arguments) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if(event.equals("id")) link.setText(HOST + arguments.getString(0));
                        JSONObject json = arguments.getJSONObject(0);
                        messageHandler.handle(json);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
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
    private final MessageHandler messageHandler = new MessageHandler();

    public interface Command{
        void execute(String peerId, JSONObject payload) throws JSONException;
    }

    public class SetRemoteSDPCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.pc.setRemoteDescription(peer, sdp);
        }
    }

    public class AddIceCandidateCommand implements Command{
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

    public class MessageHandler {
        private HashMap<String, Command> commandMap;

        public MessageHandler() {
            this.commandMap = new HashMap<String, Command>();
            commandMap.put("offer", new SetRemoteSDPCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        public void handle(JSONObject json) throws JSONException {
            String from = json.getString("from");
            String type = json.getString("type");
            JSONObject payload = json.getJSONObject("payload");

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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject payload = new JSONObject();
                        payload.put("type", sdp.type.canonicalForm());
                        payload.put("sdp", sdp.description);
                        sendMessage(id, "answer", payload);
                        pc.setLocalDescription(Peer.this, sdp);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public void onSetSuccess() {
            runOnUiThread(new Runnable() {
                public void run() {
                    pc.createAnswer(Peer.this, pcConstraints);
                }
            });
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
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            runOnUiThread(new Runnable() {
                public void run() {
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
            });
        }

        @Override
        public void onError() {}

        @Override
        public void onAddStream(MediaStream mediaStream) {}

        @Override
        public void onRemoveStream(MediaStream mediaStream) {}

        public Peer(String id) {
            this.pc = factory.createPeerConnection(iceServers, pcConstraints, this);
            this.id = id;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        name = (EditText) findViewById(R.id.name);
        link = (TextView) findViewById(R.id.link);

        iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        // factory cannot be initialized before AndroidGlobals
        PeerConnectionFactory.initializeAndroidGlobals(this);
        factory = new PeerConnectionFactory();

        client.connect();

        pcConstraints = new MediaConstraints();
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        MediaConstraints videoConstraints = new MediaConstraints();
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", "240"));
        videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", "320"));

        VideoSource videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
        lMS = factory.createLocalMediaStream("ARDAMS");
        lMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));
    }

    /*
    Cycle through likely device names for the camera and return the first
    capturer that works, or crash if none do.
    */
    private VideoCapturer getVideoCapturer() {
        String[] cameraFacing = { "back" , "front" };
        int[] cameraIndex = { 0, 1 };
        int[] cameraOrientation = { 0, 90, 180, 270 };
        for (String facing : cameraFacing) {
            for (int index : cameraIndex) {
                for (int orientation : cameraOrientation) {
                    String name = "Camera " + index + ", Facing " + facing +
                            ", Orientation " + orientation;
                    VideoCapturer capturer = VideoCapturer.create(name);
                    if (capturer != null) {
                        return capturer;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to open capturer");
    }

    public void addPeer(String id) {
        Peer peer = new Peer(id);
        peer.pc.addStream(lMS, new MediaConstraints());
        peers.put(id, peer);
    }

    public void removePeer(String id) {
        Peer peer = peers.get(id);
        peer.pc.close();
        peer.pc.dispose();
        peers.remove(peer.id);
    }

    // button onClick method
    public void stream(View view) {
        JSONArray arguments = new JSONArray();
        arguments.put(name.getText());
        try {
            client.emit("readyToStream", arguments);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
