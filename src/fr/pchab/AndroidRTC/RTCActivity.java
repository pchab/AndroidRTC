package fr.pchab.AndroidRTC;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.*;
import org.appspot.apprtc.VideoStreamsView;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRenderer;

public class RTCActivity extends Activity implements RTCClient.RTCListener{
    private static final String HOST = "http://54.214.218.3:3000/";
    private VideoStreamsView vsv;
    private RTCClient client;
    private EditText nameView;
    private CheckBox privacySetting;
    private TextView linkView;
    private ToggleButton cameraFacingView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        nameView = (EditText) findViewById(R.id.name);
        privacySetting = (CheckBox) findViewById(R.id.privacy);
        linkView = (TextView) findViewById(R.id.link);
        cameraFacingView = (ToggleButton) findViewById(R.id.cameraFacing);

        PeerConnectionFactory.initializeAndroidGlobals(this);

        // Camera display view
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        vsv = new VideoStreamsView(this, displaySize);

        client = new RTCClient(this, HOST);
    }

    // button action
    public void stream(View view) {
        setContentView(vsv);

        // Settings
        client.setName(String.valueOf(nameView.getText()));
        client.setPrivacy(privacySetting.isChecked());
        String cameraFacing = cameraFacingView.isChecked() ? "front" : "back";
        client.setCamera(cameraFacing, "320", "240");

        client.start();

        Intent msg = new Intent(Intent.ACTION_SEND);
        msg.putExtra(Intent.EXTRA_TEXT, linkView.getText());
        msg.setType("text/plain");
        startActivity(Intent.createChooser(msg, "Call someone :"));
    }

    @Override
    public void onCallReady(final String callId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                linkView.setText(HOST + callId);
            }
        });
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(new VideoCallbacks(vsv, VideoStreamsView.Endpoint.LOCAL)));
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(new VideoCallbacks(vsv, VideoStreamsView.Endpoint.REMOTE)));
    }

    @Override
    public void onRemoveRemoteStream(MediaStream remoteStream) {
        remoteStream.videoTracks.get(0).dispose();
    }

    // Implementation detail: bridge the VideoRenderer.Callbacks interface to the
    // VideoStreamsView implementation.
    private class VideoCallbacks implements VideoRenderer.Callbacks {
        private final VideoStreamsView view;
        private final VideoStreamsView.Endpoint stream;

        public VideoCallbacks(VideoStreamsView view, VideoStreamsView.Endpoint stream) {
            this.view = view;
            this.stream = stream;
        }

        @Override
        public void setSize(final int width, final int height) {
            view.queueEvent(new Runnable() {
                public void run() {
                    view.setSize(stream, width, height);
                }
            });
        }

        @Override
        public void renderFrame(VideoRenderer.I420Frame frame) {
            view.queueFrame(stream, frame);
        }
    }
}
