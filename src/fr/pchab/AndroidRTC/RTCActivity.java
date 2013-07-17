package fr.pchab.AndroidRTC;

import android.app.Activity;
import android.os.Bundle;

import com.codebutler.android_websockets.SocketIOClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.webrtc.*;

import java.net.URI;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

public class RTCActivity extends Activity {
    private String host = "http://54.214.218.3:3000/";
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();
    private PeerConnectionFactory factory;
    private Map<String, Peer> peers = new HashMap<String, Peer>();
    private MediaConstraints pcConstraints;
    private MediaStream lMS;
    private SocketIOClient client = new SocketIOClient(URI.create(host), new SocketIOClient.Handler() {
        @Override
        public void onConnect() {
        }

        @Override
        public void on(String event, JSONArray arguments) {
            try {
                JSONObject json = arguments.getJSONObject(0);
                String from = json.getString("from");

                // if peer is unknown, add him
                if(!peers.containsKey(from)) {
                    addPeer(from);
                }

                peers.get(from).getMessageHandler().handle(json);
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

    // Command pattern
    public interface Command{
        void execute(JSONObject payload) throws JSONException;
    }

    private class Peer implements SdpObserver, PeerConnection.Observer{
        private PeerConnection pc;
        private String id;
        private MessageHandler messageHandler;

        public class MessageHandler {
            private Map<String, Command> commandMap;

            public MessageHandler() {
                this.commandMap = new HashMap<String, Command>();
                commandMap.put("offer", new SetRemoteSDPCommand());
                commandMap.put("answer", new SetRemoteSDPCommand());
                commandMap.put("stop", new StopCommand());
                commandMap.put("close", new CloseCommand());
                commandMap.put("candidate", new AddIceCandidateCommand());
            }

            public void handle(JSONObject json) throws JSONException {
                String type = json.getString("type");
                JSONObject payload = json.getJSONObject("payload");
                commandMap.get(type).execute(payload);
            }
        }

        public MessageHandler getMessageHandler() {
            return messageHandler;
        }

        public class SetRemoteSDPCommand implements Command{
            public void execute(JSONObject payload) throws JSONException {
                SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
                );
                pc.setRemoteDescription(Peer.this, sdp);
            }
        }

        public class StopCommand implements Command {
            public void execute(JSONObject payload) throws JSONException {
                sendMessage("closed", payload);
            }
        }

        public class CloseCommand implements Command{
            public void execute(JSONObject payload) {
                removePeer(id);
            }
        }

        public class AddIceCandidateCommand implements Command{
            public void execute(JSONObject payload) throws JSONException {
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

        private void sendMessage(String type, JSONObject payload) throws JSONException {
            JSONObject message = new JSONObject();
            message.put("to", id);
            message.put("type", type);
            message.put("payload", payload);
            client.emit("message", new JSONArray().put(message));
        }

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject payload = new JSONObject();
                        payload.put("type", sdp.type.canonicalForm());
                        payload.put("sdp", sdp.description);
                        sendMessage("answer", payload);
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
            if(signalingState == PeerConnection.SignalingState.CLOSED) {
                removePeer(id);
            }
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}

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
                        sendMessage("candidate", payload);
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
            this.messageHandler = new MessageHandler();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

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

        VideoCapturer capturer = getVideoCapturer();
        VideoSource videoSource = factory.createVideoSource(capturer, videoConstraints);
        lMS = factory.createLocalMediaStream("ARDAMS");
        VideoTrack videoTrack = factory.createVideoTrack("ARDAMSv0", videoSource);
        lMS.addTrack(videoTrack);
        lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));

        JSONArray arguments = new JSONArray();
        arguments.put("android_test");
        try {
            client.emit("readyToStream", arguments);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Cycle through likely device names for the camera and return the first
    // capturer that works, or crash if none do.
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
        peers.get(id).pc.close();
        peers.get(id).pc.dispose();
        peers.remove(id);
    }
}
