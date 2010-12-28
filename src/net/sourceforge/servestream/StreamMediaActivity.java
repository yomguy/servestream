/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2010 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.service.MusicService;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public class StreamMediaActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "ServeStream.StreamMediaActivity";
    
	private static final int MEDIA_CONTROLS_DISPLAY_TIME = 2000;
    
	private int displayWidth = 0;
	private int displayHeight = 0;
    
	private String requestedStream = "";
	private String currentlyPlayingStream = "";
	
    private MediaPlayer mediaPlayer;

	private Animation controller_fade_in, controller_fade_out;
	private RelativeLayout mediaControllerGroup;
    
    private SurfaceView preview;
    private SurfaceHolder holder;

    private ProgressDialog dialog = null;
    
	private Handler handler = new Handler();
    
	private MusicService boundService;

	private ServiceConnection connection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        boundService = ((MusicService.MusicBinder)service).getService();
	        boundService.setNowPlayingHandler(nowPlayingHandler);
	        //boundService.setProgressDialog(dialog);
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        boundService = null;
	    }
	};
    
	protected Handler nowPlayingHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
	        Toast.makeText(StreamMediaActivity.this, "Now Playing: " + msg.obj, Toast.LENGTH_LONG).show();
		}
	};
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.acc_streammedia);
        
		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_play))); 
		
		// get the phone's display width and height
		getDisplayMeasurements();
		
		requestedStream = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
        
		//preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		// preload animation for media controller display
		controller_fade_in = AnimationUtils.loadAnimation(this, R.anim.controller_fade_in);
		controller_fade_out = AnimationUtils.loadAnimation(this, R.anim.controller_fade_out);
        
		mediaControllerGroup = (RelativeLayout) findViewById(R.id.media_controller_group);
		mediaControllerGroup.setVisibility(View.GONE);
        
        preview = (SurfaceView) findViewById(R.id.surface_view);
        preview.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View arg0, MotionEvent arg1) {
				if (mediaControllerGroup.isShown()) {
				    mediaControllerGroup.startAnimation(controller_fade_out);
				    mediaControllerGroup.setVisibility(View.GONE);
				} else {
					mediaControllerGroup.startAnimation(controller_fade_in);
					mediaControllerGroup.setVisibility(View.VISIBLE);
					
					handler.postDelayed(new Runnable() {
						public void run() {
							if (mediaControllerGroup.getVisibility() == View.GONE)
								return;

							mediaControllerGroup.startAnimation(controller_fade_out);
							mediaControllerGroup.setVisibility(View.GONE);
						}
					}, MEDIA_CONTROLS_DISPLAY_TIME);
				}
				return false;
			}
		});
        
        holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setDisplay(holder);
        holder.setFixedSize(displayWidth, displayHeight);
        
		Button previousButton = (Button) findViewById(R.id.previous_button);
		previousButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.previousMediaFile();
				
			    mediaControllerGroup.startAnimation(controller_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});

		Button seekBackwardButton = (Button) findViewById(R.id.seek_backward_button);
		seekBackwardButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.seekBackward();
				
			    mediaControllerGroup.startAnimation(controller_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
		
		final Button playPauseButton = (Button) findViewById(R.id.play_pause_button);
		playPauseButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if (boundService.isPlaying()) {
					boundService.pauseMedia();
					playPauseButton.setBackgroundResource(R.drawable.play_button);
				} else {
					boundService.resumeMedia();
					playPauseButton.setBackgroundResource(R.drawable.pause_button);
				}
				
			    mediaControllerGroup.startAnimation(controller_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
		
		Button seekForwardButton = (Button) findViewById(R.id.seek_forward_button);
		seekForwardButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.seekForward();
				
			    mediaControllerGroup.startAnimation(controller_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
		
		Button nextButton = (Button) findViewById(R.id.next_button);
		nextButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.nextMediaFile();
				
			    mediaControllerGroup.startAnimation(controller_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
    }
    
	@Override
	public void onStart() {
		super.onStart();
		
		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
        unbindService(connection);
	}
    
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		requestedStream = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuItem settings = menu.add(R.string.list_menu_settings);
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(StreamMediaActivity.this, SettingsActivity.class));

		MenuItem help = menu.add(R.string.title_help);
		help.setIcon(android.R.drawable.ic_menu_help);
		help.setIntent(new Intent(StreamMediaActivity.this, HelpActivity.class));

		return true;
	}
    
    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		if (mediaControllerGroup.isShown()) {
			mediaControllerGroup.setVisibility(View.GONE);
		}
		
		// get new window size on orientation change
		getDisplayMeasurements();
		
	    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        holder.setFixedSize(displayWidth, displayHeight);
	    } else {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        holder.setFixedSize(displayWidth, displayHeight);
	    }
	}

    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "surfaceChanged called");
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");

        if (!requestedStream.equals(currentlyPlayingStream)) {
            try {
            	
            	if (boundService.getMediaPlayer() == null) {
                    boundService.setMediaPlayer(mediaPlayer);
            	} else {
                    boundService.stopMedia();
            	}
            	
                boundService.queueNewMedia(requestedStream);
                boundService.startMediaPlayer();
            } catch (Exception e) {
                Log.e(TAG, "error: " + e.getMessage(), e);
            }
            currentlyPlayingStream = requestedStream;
        }
    }
    
    /**
     * Retrieve the phone display width and height
     */
    private void getDisplayMeasurements() {
        Display display = getWindowManager().getDefaultDisplay();
        displayWidth = display.getWidth();
        displayHeight = display.getHeight();
    }
}
