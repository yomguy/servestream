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
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.service.MediaService;
import net.sourceforge.servestream.utils.MediaFile;
import net.sourceforge.servestream.utils.MusicUtils;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Display;
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
    
	public static final boolean VISIBLE = true;
	public static final boolean NOT_VISIBLE = false;
	
	private int displayWidth = 0;
	private int displayHeight = 0;
    
	private Stream requestedStream = null;
	
    private MediaPlayer mediaPlayer;

	private Animation media_controls_fade_in, media_controls_fade_out;
	private RelativeLayout mediaControls;
    
    private SurfaceView preview = null;
    private SurfaceHolder holder;
    
    private Button shuffleButton, repeatButton = null;
    private Button playPauseButton = null;
    
    private boolean userIsSeeking = false;
    
    private SeekBar seekBar;
    private TextView positionText, durationText;
    private TextView trackNumber, trackText;
    
    private Toast toast;
    
    private Handler handler = new Handler();
    
    private ProgressDialog dialog = null;
    
	private MediaService boundService;
	
	private ServiceConnection connection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        boundService = ((MediaService.MediaBinder)service).getService();
	        
			// let media service know about our event handling services
			boundService.disconnectHandler = disconnectHandler;
	        
	        boundService.mediaPlayerHandler = mediaPlayerHandler;
	        
        	if (boundService.getMediaPlayer() == null) {
                mediaPlayer = new MediaPlayer();
                boundService.setMediaPlayer(mediaPlayer);
        	} else {
        		mediaPlayer = boundService.getMediaPlayer();
        	}
        	
        	Log.v(TAG, "Bind Complete");
		    
        	if (preview == null) {
		        makeSurface();
			    Log.v(TAG, "Surface Made");
		    }
        	
        	setShuffleButtonImage();
        	setRepeatButtonImage();
        	setPlayPauseButtonImage();
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
		        case MediaService.SHOW_MEDIA_CONTROLS:
			        showMediaInfoAndControls();
			        break;
		        case MediaService.ERROR:
					new AlertDialog.Builder(StreamMediaActivity.this)
					.setTitle(R.string.cannot_play_media_title)
					.setMessage(R.string.cannot_play_media_message)
					.setPositiveButton(R.string.cannot_play_media_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
						}).create().show();
					break;
		        case MediaService.PREPARE_MEDIA_INFO:
		        	updateMediaInfo();
		        	break;
		        case MediaService.START_SEEK_BAR:
                    startSeekBar();
	                break;
		        case MediaService.START_DIALOG:
		        	try {
		        		dialog = ProgressDialog.show(StreamMediaActivity.this, "", 
		        				"Opening file...", true);
		        	} catch (Exception ex) {
		        	    ex.printStackTrace();	
		        	}
		        	break;
		        case MediaService.STOP_DIALOG:
		        	if (dialog != null)
                        dismissDialog();
		        	break;
			}   	
		}
	};
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.acc_streammedia);
        
        Log.v(TAG, "onCreate called");
		
		// get the phone's display width and height
		getDisplayMeasurements();

		// obtain the requested stream
		if (getIntent().getData() == null) {
			requestedStream = null;
		} else {
			try {
				requestedStream = new Stream(getIntent().getData().toString());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
		trackNumber = (TextView) findViewById(R.id.track_number_text);
		trackText = (TextView) findViewById(R.id.track_url_text);
		
	    positionText = (TextView) findViewById(R.id.position_text);
	    durationText = (TextView) findViewById(R.id.duration_text);
	    
        seekBar = (SeekBar) findViewById(R.id.seek_bar);
        seekBar.setVisibility(SeekBar.VISIBLE);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
		    	//positionText.setText(getFormattedTime(progress, getTimeFormat()));
				updateProgress(progress);
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

		mediaControls = (RelativeLayout) findViewById(R.id.media_controls);
		mediaControls.setVisibility(View.GONE);
        
		shuffleButton = (Button) findViewById(R.id.shuffle_button);
		repeatButton = (Button) findViewById(R.id.repeat_button);
		
		shuffleButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				toggleShuffle();
			}
			
		});
		
		repeatButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				cycleRepeat();
			}
			
		});
		
		playPauseButton = (Button) findViewById(R.id.play_pause_button);
		playPauseButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				if (boundService != null) {
					if (boundService.isPlaying()) {
						boundService.pauseMedia();
					} else {
						boundService.resumeMedia();
					}
					setPlayPauseButtonImage();
				}
			}
			
		});
        
		Button previousButton = (Button) findViewById(R.id.previous_button);
		previousButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.previousMediaFile();
				
				playPauseButton.setBackgroundResource(R.drawable.pause_button);
				
			    mediaControls.startAnimation(media_controls_fade_out);
				mediaControls.setVisibility(View.GONE);
			}
			
		});

		Button seekBackwardButton = (Button) findViewById(R.id.seek_backward_button);
		seekBackwardButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.seekBackward();
			}
			
		});
		
		Button seekForwardButton = (Button) findViewById(R.id.seek_forward_button);
		seekForwardButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.seekForward();
			}
			
		});
		
		Button nextButton = (Button) findViewById(R.id.next_button);
		nextButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {
				boundService.nextMediaFile();
				
				playPauseButton.setBackgroundResource(R.drawable.pause_button);

			    mediaControls.startAnimation(media_controls_fade_out);
				mediaControls.setVisibility(View.GONE);
			}
			
		});
    }
    
	@Override
	public void onStart() {
		super.onStart();
		
		Log.v(TAG, "onStart called");
		
		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, MediaService.class), connection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		Log.v(TAG, "onPause called");
		
    	if (dialog != null)
            dismissDialog();
		
		boundService.setStreamActivityState(NOT_VISIBLE);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		Log.v(TAG, "onStop called");
		
        //unbindService(connection);
	}
	
    @Override
    public void onResume() {
        super.onResume();
        
        Log.v(TAG, "onResume called");
        
        //updateTrackInfo();
        setPlayPauseButtonImage();
    }
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
        Log.v(TAG,"onDestroy called");
        
        // Detach our existing connection.
        unbindService(connection);
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		Log.d(TAG, "onNewIntent called");
		
		if (intent.getData() == null) {
			Log.e(TAG, "Got null intent data in onNewIntent()");
			requestedStream = null;
		} else {
			try {
				requestedStream = new Stream(intent.getData().toString());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		
    	// let media service know the activity is visible
		boundService.setStreamActivityState(VISIBLE);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    
	    if (keyCode == KeyEvent.KEYCODE_SEARCH && !mediaControls.isShown()) {
	    	mediaControls.setVisibility(View.VISIBLE);
	        return true;
	    }
	    
	    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && !mediaControls.isShown()) {
	    	mediaControls.setVisibility(View.VISIBLE);
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
		
		boolean controlsAreVisible = false;
		
		if (controlsAreVisible = mediaControls.isShown()) {
	    	mediaControls.setVisibility(View.GONE);
		}
		
		// get new window size on orientation change
		getDisplayMeasurements();
		
	    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	    } else {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	    }
	    
	    if (holder != null)
	    	holder.setFixedSize(displayWidth, displayHeight);
	    
	    if (controlsAreVisible)
	    	mediaControls.setVisibility(View.VISIBLE);
	}

    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        Log.d(TAG, "surfaceChanged called");
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");

    	// let media service know the activity is visible
		boundService.setStreamActivityState(VISIBLE);
        
        if (boundService == null) {
        	Log.v(TAG, "Service is null");
        }
        
    	// if the requested stream is null the intent used to launch
    	// StreamMediaActivity did not supply a new URL to stream
        if (requestedStream != null && !requestedStream.equals(boundService.getCurrentStream())) {
            try {
                boundService.setDisplay(holder);
                holder.setFixedSize(displayWidth, displayHeight);
                
                if (!boundService.queueNewMedia(requestedStream)) {
                	errorOpeningMediaMessage();
                	return;
                }
                
                if (boundService.getNumOfQueuedFiles() == 0) {
                	handleInvalidPlaylist();
                    return;
                }
                	
                boundService.startMediaPlayer();
                boundService.setCurrentStream(requestedStream);
            } catch (Exception ex) {
                Log.e(TAG, "error: " + ex.getMessage());
            }
        } else {
        	/*if (boundService.isPlayingVideo()) {
            	makeSurface();
                mediaPlayer.setDisplay(holder);
                holder.setFixedSize(displayWidth, displayHeight);
                boundService.resetSurfaceView();
        	} */
        	updateMediaInfo();
        	startSeekBar();
    		mediaControls.setVisibility(View.VISIBLE);
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
    
    private void updateMediaInfo() {
    	MediaFile mediaFile = boundService.getCurrentMediaInfo();
    	trackNumber.setText(String.valueOf(mediaFile.getTrackNumber()) + " / " + String.valueOf(boundService.getNumOfQueuedFiles()));
    	
    	if (mediaFile.getTitle() != null)
    	    trackText.setText(mediaFile.getTitle());
    	else
    		trackText.setText(mediaFile.getDecodedURL());
    }
    
    private void startSeekBar() {
    	long duration = boundService.getDuration();
    	
    	refreshNow();
    	durationText.setText(MusicUtils.makeTimeString(this, duration / 1000));
    	
        new Thread(this).start();
    }
    
    private void showMediaInfoAndControls() {
		mediaControls.startAnimation(media_controls_fade_in);
		mediaControls.setVisibility(View.VISIBLE);
    }
    
    private void handleInvalidPlaylist() {

    	if (dialog != null)
    		dismissDialog();

		new AlertDialog.Builder(StreamMediaActivity.this)
		.setTitle(R.string.invalid_playlist_title)
		.setMessage(R.string.invalid_playlist_message)
		.setPositiveButton(R.string.invalid_playlist_pos, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
			}).create().show();
    }
    
	private void dismissDialog() {
		handler.post(new Runnable() {
			public void run() {
                dialog.dismiss();
			}
		});
	}
    
    public void run() {
    	
    	long currentPosition = boundService.getPosition();
        long duration = boundService.getDuration();
        
        seekBar.setProgress((int)currentPosition);
        seekBar.setMax((int)duration);
        
        while (mediaPlayer != null && currentPosition < duration && !boundService.isOpeningMedia()) {
            try {
                Thread.sleep(1000);
                currentPosition = boundService.getPosition();
            } catch (Exception ex) {
                return;
            }            
            
            //Log.v(TAG, "Still seeking");
            
            if (!userIsSeeking) {
                seekBar.setProgress((int)currentPosition);
            }
        }
    }
    
    private void makeSurface() {
        preview = (SurfaceView) findViewById(R.id.surface_view);
        preview.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View arg0, MotionEvent arg1) {
				if (mediaControls.isShown()) {
				    mediaControls.startAnimation(media_controls_fade_out);
				    mediaControls.setVisibility(View.GONE);
				} else {
				    mediaControls.startAnimation(media_controls_fade_in);
				    mediaControls.setVisibility(View.VISIBLE);
				}
				return false;
			}
		});
        
        holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    /**
     * 
     * 
     * @param resid
     */
    private void showToast(int resid) {
        if (toast == null) {
            toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        toast.setText(resid);
        toast.show();
    }
    
    private void toggleShuffle() {
        if (boundService == null) {
            return;
        }
        
        int shuffle = boundService.getShuffleMode();
        if (shuffle == MediaService.SHUFFLE_NONE) {
            boundService.setShuffleMode(MediaService.SHUFFLE_ON);
            if (boundService.getRepeatMode() == MediaService.REPEAT_CURRENT) {
                boundService.setRepeatMode(MediaService.REPEAT_ALL);
                setRepeatButtonImage();
            }
            showToast(R.string.shuffle_on_notif);
        } else if (shuffle == MediaService.SHUFFLE_ON) {
            boundService.setShuffleMode(MediaService.SHUFFLE_NONE);
            showToast(R.string.shuffle_off_notif);
        } else {
            Log.e(TAG, "Invalid shuffle mode: " + shuffle);
        }
        setShuffleButtonImage();
    }
    
    private void cycleRepeat() {
        if (boundService == null) {
            return;
        }
        
        int mode = boundService.getRepeatMode();
        if (mode == MediaService.REPEAT_NONE) {
           boundService.setRepeatMode(MediaService.REPEAT_ALL);
           showToast(R.string.repeat_all_notif);
        } else if (mode == MediaService.REPEAT_ALL) {
            boundService.setRepeatMode(MediaService.REPEAT_CURRENT);
            if (boundService.getShuffleMode() != MediaService.SHUFFLE_NONE) {
                boundService.setShuffleMode(MediaService.SHUFFLE_NONE);
                setShuffleButtonImage();
            }
            showToast(R.string.repeat_current_notif);
        } else {
            boundService.setRepeatMode(MediaService.REPEAT_NONE);
            showToast(R.string.repeat_off_notif);
        }
        setRepeatButtonImage();
    }
    
    private void setShuffleButtonImage() {
        if (boundService == null) return;
            
        switch (boundService.getShuffleMode()) {
            case MediaService.SHUFFLE_NONE:
                shuffleButton.setBackgroundResource(R.drawable.shuffle_disabled_button);
                break;
            default:
                shuffleButton.setBackgroundResource(R.drawable.shuffle_button);
                break;
        }
    }
    
    private void setRepeatButtonImage() {
        if (boundService == null) return;

        switch (boundService.getRepeatMode()) {
            case MediaService.REPEAT_ALL:
                repeatButton.setBackgroundResource(R.drawable.repeat_all_button);
                break;
            case MediaService.REPEAT_CURRENT:
                repeatButton.setBackgroundResource(R.drawable.repeat_one_button);
                break;
            default:
                repeatButton.setBackgroundResource(R.drawable.repeat_disabled_button);
                break;
        }
    }
    
    private void setPlayPauseButtonImage() {
        if (boundService != null && boundService.isPlaying()) {
			playPauseButton.setBackgroundResource(R.drawable.pause_button);
		} else {
			playPauseButton.setBackgroundResource(R.drawable.play_button);
		}
    }

    private long refreshNow() {
    	
        if(boundService == null)
            return 500;
        //TODO fix this added line
    	long duration = boundService.getDuration();
        //long pos = mPosOverride < 0 ? mediaPlayer.position() : mPosOverride;
		long pos = boundService.getPosition();
		long remaining = 1000 - (pos % 1000);
		if ((pos >= 0) && (duration > 0)) {
			positionText.setText(MusicUtils.makeTimeString(this, pos / 1000));
		    
		    if (boundService.isPlaying()) {
		    	positionText.setVisibility(View.VISIBLE);
		    } else {
		        // blink the counter
		        int vis = positionText.getVisibility();
		        positionText.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
		        remaining = 500;
		    }

		    //mProgress.setProgress((int) (1000 * pos / mDuration));
		} else {
			positionText.setText("--:--");
		    //mProgress.setProgress(1000);
		}
		// return the number of milliseconds until the next full second, so
		// the counter can be updated at just the right time
		return remaining;
    }
    
    private void updateProgress(int progress) {
    	positionText.setText(MusicUtils.makeTimeString(this, Long.valueOf(progress) / 1000));
    }
    
    private void errorOpeningMediaMessage() {
		new AlertDialog.Builder(StreamMediaActivity.this)
		.setMessage("Sorry, the following URL cannot be opened")
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				finish();
			}
			}).create().show();
    }
}
