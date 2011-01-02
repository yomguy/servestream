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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
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
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

public class StreamMediaActivity extends Activity implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "ServeStream.StreamMediaActivity";
    
	private static final int MEDIA_CONTROLS_DISPLAY_TIME = 2000;
    
	private int displayWidth = 0;
	private int displayHeight = 0;
    
	private String requestedStream = "";
	
    private MediaPlayer mediaPlayer;

	private Animation media_controls_fade_in, media_controls_fade_out;
	private RelativeLayout mediaControllerGroup;
    
    private SurfaceView preview = null;
    private SurfaceHolder holder;
    
    private boolean userIsSeeking = false;
    
    private SeekBar seekBar;
    private TextView positionText;
    private TextView durationText;
    
    private int timeFormat;
    
    private Handler handler = new Handler();
    
    private ProgressDialog dialog = null;
    
	private MusicService boundService;

	private ServiceConnection connection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        boundService = ((MusicService.MusicBinder)service).getService();
	        
			// let manager know about our event handling services
			boundService.disconnectHandler = disconnectHandler;
	        
	        boundService.mediaPlayerHandler = mediaPlayerHandler;
	        
        	if (boundService.getMediaPlayer() == null) {
                mediaPlayer = new MediaPlayer();
            	mediaPlayer.setOnPreparedListener(new OnPreparedListener() {

    				public void onPrepared(MediaPlayer mp) {
    					//handler.post(new Runnable() {
    					//public void run() {
                        //    dialog.dismiss();
    					//}
    					//});
    				}
            	});
                boundService.setMediaPlayer(mediaPlayer);
        	} else {
        		mediaPlayer = boundService.getMediaPlayer();
        	}
        	
        	Log.v(TAG, "Bind Complete");
		    
        	if (preview == null) {
		        makeSurface();
			    Log.v(TAG, "Surface Made");
		    }
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        boundService = null;
	    }
	};
	
	protected Handler disconnectHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "MediaService sending disconnect signal to parentHandler");

			finish();
		}
	};
	
	protected Handler mediaPlayerHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			switch (msg.what) {
		        case MusicService.NOW_PLAYING_MESSAGE:
			        Toast t = Toast.makeText(StreamMediaActivity.this, "Now Playing: " + msg.obj, Toast.LENGTH_LONG);
			        t.setGravity(Gravity.LEFT | Gravity.CENTER, 0, 0);
			        t.show();
			        break;
		        case MusicService.ERROR:
					new AlertDialog.Builder(StreamMediaActivity.this)
					.setTitle(R.string.cannot_play_media_title)
					.setMessage(R.string.cannot_play_media_message)
					.setPositiveButton(R.string.cannot_play_media_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
						}).create().show();
					break;
		        case MusicService.START_SEEK_BAR:
                    startSeekBar();
	                break;
		        case MusicService.START_DIALOG:
		    		dialog = ProgressDialog.show(StreamMediaActivity.this, "", 
		                    "Opening file...", true);
		        	break;
		        case MusicService.STOP_DIALOG:
					handler.post(new Runnable() {
						public void run() {
	                        dialog.dismiss();
						}
						});
		        	break;
			}   	
		}
	};
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.acc_streammedia);
        
        Log.v(TAG, "onCreate called");
        
		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_play))); 
		
		// get the phone's display width and height
		getDisplayMeasurements();

		// obtain the requested stream
		if (getIntent().getExtras() != null) {
			requestedStream = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
		} else {
			requestedStream = null;
		}
		
	    positionText = (TextView) findViewById(R.id.position_text);
	    durationText = (TextView) findViewById(R.id.duration_text);
	    
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        seekBar.setVisibility(SeekBar.VISIBLE);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
		    	positionText.setText(getFormattedTime(progress, getTimeFormat()));
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
                userIsSeeking = true;
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
				boundService.seekTo(seekBar.getProgress());
			}
        	
        });
	    
		// preload animation for media controller
		media_controls_fade_in = AnimationUtils.loadAnimation(this, R.anim.media_controls_fade_in);
		media_controls_fade_out = AnimationUtils.loadAnimation(this, R.anim.media_controls_fade_out);
        
		mediaControllerGroup = (RelativeLayout) findViewById(R.id.media_controller_group);
		mediaControllerGroup.setVisibility(View.GONE);
        
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
				
			    //mediaControllerGroup.startAnimation(media_controls_fade_out);
				//mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
        
		Button previousButton = (Button) findViewById(R.id.previous_button);
		previousButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.previousMediaFile();
				
				playPauseButton.setBackgroundResource(R.drawable.pause_button);
				
			    mediaControllerGroup.startAnimation(media_controls_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});

		Button seekBackwardButton = (Button) findViewById(R.id.seek_backward_button);
		seekBackwardButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.seekBackward();
				
			    //mediaControllerGroup.startAnimation(media_controls_fade_out);
				//mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
		
		Button seekForwardButton = (Button) findViewById(R.id.seek_forward_button);
		seekForwardButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.seekForward();
				
			    //mediaControllerGroup.startAnimation(media_controls_fade_out);
				//mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
		
		Button nextButton = (Button) findViewById(R.id.next_button);
		nextButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.nextMediaFile();
				
				playPauseButton.setBackgroundResource(R.drawable.pause_button);
				
			    mediaControllerGroup.startAnimation(media_controls_fade_out);
				mediaControllerGroup.setVisibility(View.GONE);
			}
			
		});
    }
    
	@Override
	public void onStart() {
		super.onStart();
		
		Log.v(TAG, "onStart called");
		
		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
        Log.v(TAG,"onDestroy called");
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
        unbindService(connection);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		Log.d(TAG, "onNewIntent called");
		
		// obtain the requested stream
		if (getIntent().getExtras() != null) {
			requestedStream = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
		} else {
			requestedStream = null;
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ( keyCode == KeyEvent.KEYCODE_MENU && mediaControllerGroup.isShown()) {
	    	mediaControllerGroup.setVisibility(View.GONE);
	        return true;
	    }
	    return super.onKeyDown(keyCode, event);
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
        
        if (boundService == null) {
        	Log.v(TAG, "Service is null");
        }
        
    	// if the requested stream is null the intent used to launch
    	// StreamMediaActivity did not supply a new URL to stream
        if (requestedStream != null && !requestedStream.equals(boundService.getnowPlayingPlaylist())) {
            try {
                mediaPlayer.setDisplay(holder);
                holder.setFixedSize(displayWidth, displayHeight);
                boundService.stopMedia();
                boundService.queueNewMedia(requestedStream);
                boundService.startMediaPlayer();
                boundService.setNowPlayingPlaylist(requestedStream);
            } catch (Exception e) {
                Log.e(TAG, "error: " + e.getMessage(), e);
            }
        } else {
        	if (boundService.isPlayingVideo()) {
            	makeSurface();
                mediaPlayer.setDisplay(holder);
                holder.setFixedSize(displayWidth, displayHeight);
                boundService.resetSurfaceView();
        	}        	
        	startSeekBar();	
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
    
    private void startSeekBar() {
    	setTimeFormat(mediaPlayer.getDuration());
    	durationText.setText(getFormattedTime(mediaPlayer.getDuration()));
    	positionText.setText(getFormattedTime(mediaPlayer.getCurrentPosition(),getTimeFormat()));
        new Thread(this).start();
    }
    
    public void run() {
    	
        int currentPosition = 0;
        int duration = mediaPlayer.getDuration();
        
        seekBar.setProgress(0);
        seekBar.setMax(duration);
        
        while(mediaPlayer != null && currentPosition < duration && !boundService.isOpeningMedia()){
            try {
                Thread.sleep(1000);
                currentPosition = mediaPlayer.getCurrentPosition();
            } catch (Exception ex) {
                return;
            }            
            
            if (!userIsSeeking) {
                seekBar.setProgress(currentPosition);
            }
        }
    }
    
    private void makeSurface() {
        preview = (SurfaceView) findViewById(R.id.surface_view);
        preview.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View arg0, MotionEvent arg1) {
				if (mediaControllerGroup.isShown()) {
				    mediaControllerGroup.startAnimation(media_controls_fade_out);
				    mediaControllerGroup.setVisibility(View.GONE);
				} else {
					mediaControllerGroup.startAnimation(media_controls_fade_in);
					mediaControllerGroup.setVisibility(View.VISIBLE);
					
					/*handler.postDelayed(new Runnable() {
						public void run() {
							if (mediaControllerGroup.getVisibility() == View.GONE)
								return;

							mediaControllerGroup.startAnimation(media_controls_fade_out);
							mediaControllerGroup.setVisibility(View.GONE);
						}
					}, MEDIA_CONTROLS_DISPLAY_TIME);*/
				}
				return false;
			}
		});
        
        holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    public String getFormattedTime(long time) {
    	long elapsedTime = time;
    	String formattedTime = "";
    	
    	if (time == 0) {
    		return "00:00";
    	} else {
    	    String format = String.format("%%0%dd", 2);
    	    elapsedTime = elapsedTime / 1000;
    	    String seconds = String.format(format, elapsedTime % 60);
    	    String minutes = String.format(format, (elapsedTime % 3600) / 60);
    	    String hours = String.format(format, elapsedTime / 3600);
        
            if (!hours.equals("00")) {
                formattedTime = hours + ":" + minutes + ":" + seconds;
            } else if (!minutes.equals("00")) {
        	    formattedTime = minutes + ":" + seconds;
            } else {
        	    formattedTime = seconds;
            }
    	}
            
        return formattedTime;  
    }
    
    public String getFormattedTime(long time, int timeFormat) {
    	long elapsedTime = time;
    	String formattedTime = "";
    	
    	if (time == 0) {
    		return "00:00";
    	} else {
    	    String format = String.format("%%0%dd", 2);
    	    elapsedTime = elapsedTime / 1000;
    	    String seconds = String.format(format, elapsedTime % 60);
    	    String minutes = String.format(format, (elapsedTime % 3600) / 60);
    	    String hours = String.format(format, elapsedTime / 3600);
        
            if (timeFormat == 1) {
                formattedTime = hours + ":" + minutes + ":" + seconds;
            } else if (timeFormat == 2) {
        	    formattedTime = minutes + ":" + seconds;
            } else if (timeFormat == 3) {
        	    formattedTime = seconds;
            }
    	}
        
        return formattedTime;
    }
    
    public int getTimeFormat() {
    	return this.timeFormat;
    }
    
    public void setTimeFormat(long time) {
    	long elapsedTime = time;
    	
    	if (time == 0) {
    	    this.timeFormat = 2;	
    	} else {
    	    String format = String.format("%%0%dd", 2);
    	    elapsedTime = elapsedTime / 1000;
    	    String minutes = String.format(format, (elapsedTime % 3600) / 60);
    	    String hours = String.format(format, elapsedTime / 3600);
        
            if (!hours.equals("00")) {
        	    this.timeFormat = 1;
            } else if (!minutes.equals("00")) {
        	    this.timeFormat = 2;
            } else {
        	    this.timeFormat = 3;
            }
        }
    }
}
