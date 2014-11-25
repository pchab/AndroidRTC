package deepak.rathi;

/**
 * Copyright Deepak Rathi (https://github.com/deepak-rathi)
 * 
 * STEPS FOLLOWED TO CREATE AUDIO CALL
 * - In WebRtcClient :
create a new method that does the same thing as setCamera but without video
:

  public void setAudio(){
    lMS = factory.createLocalMediaStream("ARDAMS");
    lMS.addTrack(factory.createAudioTrack("ARDAMSa0"));

    mListener.onLocalStream(lMS);
  }

- In RTCactivity :
in startCam() call setAudio() instead of setCamera(...)

At this point, you should still have audio working and a green (or black)
screen.
You can then :
- Remove the videoStreamsView and videoRenderer from RTCactivity
- Change the PeerConnection Constraints "OfferToReceiveVideo" to false
 */

import fr.pchab.AndroidRTC.R;
import fr.pchab.AndroidRTC.VideoStreamsView;
import fr.pchab.AndroidRTC.WebRtcClient;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Window;
import android.widget.Toast;
import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
//import org.webrtc.VideoRenderer;

import java.util.List;

public class RTCAudioActivity extends Activity implements WebRtcClient.RTCListener{
	private final static int AUDIO_CALL_SENT = 666;
	  private VideoStreamsView vsv;
	  private WebRtcClient client;
	  private String mSocketAddress;
	  private String callerId;


	  @Override
	  public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    requestWindowFeature(Window.FEATURE_NO_TITLE);
	    mSocketAddress = "http://" + getResources().getString(R.string.host);
	    mSocketAddress += (":"+getResources().getString(R.string.port)+"/");

	    PeerConnectionFactory.initializeAndroidGlobals(this);

	    // Camera display view
	    Point displaySize = new Point();
	    getWindowManager().getDefaultDisplay().getSize(displaySize);
	    vsv = new VideoStreamsView(this, displaySize);
	    client = new WebRtcClient(this, mSocketAddress);

	    final Intent intent = getIntent();
	    final String action = intent.getAction();

	    if (Intent.ACTION_VIEW.equals(action)) {
	      final List<String> segments = intent.getData().getPathSegments();
	      callerId = segments.get(0);
	      //callerId = "R9lEjFqo4sb1ZbeM9r0i"; static room id for testing
	    }
	  }

	  public void onConfigurationChanged(Configuration newConfig)
	  {
	    super.onConfigurationChanged(newConfig);
	    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	  }

	  @Override
	  public void onPause() {
	    super.onPause();
	    vsv.onPause();
	  }

	  @Override
	  public void onResume() {
	    super.onResume();
	    vsv.onResume();
	  }

	  @Override
	  public void onCallReady(String callId) {
		 //callerId = "R9lEjFqo4sb1ZbeM9r0i"; a static room id for testing
	    if(callerId != null) {
	      try {
	        answer(callerId);
	      } catch (JSONException e) {
	        e.printStackTrace();
	      }
	    } else {
	      call(callId);
	    }
	  }

	  public void answer(String callerId) throws JSONException {
	    client.sendMessage(callerId, "init", null);
	    startCam();
	  }

	  public void call(String callId) {
	    Intent msg = new Intent(Intent.ACTION_SEND);
	    msg.putExtra(Intent.EXTRA_TEXT, mSocketAddress + callId);
	    msg.setType("text/plain");
	    startActivityForResult(Intent.createChooser(msg, "Call someone :"), AUDIO_CALL_SENT);
	  }

	  @Override
	  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if(requestCode == AUDIO_CALL_SENT) {
	      startCam();
	    }
	  }

	  public void startCam() {
	    setContentView(vsv);
	    // Audio call settings
	    
	    client.setAudio();
	    client.start("android_test", true);
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
	   
	  }

	  @Override
	  public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
	   
	  }

	  @Override
	  public void onRemoveRemoteStream(MediaStream remoteStream, int endPoint) {
	   
	  }

}
