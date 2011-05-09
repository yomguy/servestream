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
/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream;

import net.sourceforge.servestream.button.RepeatingImageButton;
import net.sourceforge.servestream.player.MultiPlayer;
import net.sourceforge.servestream.service.IMediaService;
import net.sourceforge.servestream.service.MediaService;
import net.sourceforge.servestream.utils.MusicUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;


public class StreamMediaActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "ServeStream.StreamMediaActivity";
    
    private int mParentActivityState = StreamMediaActivity.VISIBLE;
    public static int VISIBLE = 1;
    public static int GONE = 2;
    
    private ProgressDialog mDialog = null;
    
	private Animation media_controls_fade_in, media_controls_fade_out;
	private RelativeLayout mMediaControls;
    
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaService mMediaService = null;
    private Button mPrevButton;
    private RepeatingImageButton mSeekBackwardButton;
    private Button mPauseButton;
    private RepeatingImageButton mSeekForwardButton;
    private Button mNextButton;
    private Button mRepeatButton;
    private Button mShuffleButton;
    private Toast mToast;

    private TextView mTrackNumber;
    private TextView mTrackName;
    
    private SurfaceView preview = null;
    private SurfaceHolder holder;
    
	private int mDisplayWidth = 0;
	private int mDisplayHeight = 0;
    
	//private Stream mRequestedStream = null;
    private String mRequestedStream = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        Log.v(TAG, "onCreate called");
        
		// get the phone's display width and height
		getDisplayMeasurements();
        
		// determine fullscreen mode
		setFullscreenMode(getResources().getConfiguration().orientation);
		
        setContentView(R.layout.acc_streammedia);

        mCurrentTime = (TextView) findViewById(R.id.position_text);
        mTotalTime = (TextView) findViewById(R.id.duration_text);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mTrackName = (TextView) findViewById(R.id.track_url_text);
        mPrevButton = (Button) findViewById(R.id.previous_button);
        mPrevButton.setOnClickListener(mPrevListener); 
        mSeekBackwardButton = (RepeatingImageButton) findViewById(R.id.seek_backward_button);
        mSeekBackwardButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (Button) findViewById(R.id.play_pause_button);
        mPauseButton.setOnClickListener(mPauseListener);
        mSeekForwardButton = (RepeatingImageButton) findViewById(R.id.seek_forward_button);
        mSeekForwardButton.setRepeatListener(mFfwdListener, 260);
        mNextButton = (Button) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(mNextListener);
        
        mShuffleButton = (Button) findViewById(R.id.shuffle_button);
        mShuffleButton.setOnClickListener(mShuffleListener);
        mRepeatButton = (Button) findViewById(R.id.repeat_button);
        mRepeatButton.setOnClickListener(mRepeatListener);
        
	    mProgress = (ProgressBar) findViewById(R.id.seek_bar);
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);
        
	    mTrackNumber = (TextView) findViewById(R.id.track_number_text);
	    mTrackName = (TextView) findViewById(R.id.track_url_text);
        
		// preload animation for media controller
		media_controls_fade_in = AnimationUtils.loadAnimation(this, R.anim.media_controls_fade_in);
		media_controls_fade_out = AnimationUtils.loadAnimation(this, R.anim.media_controls_fade_out);

		mMediaControls = (RelativeLayout) findViewById(R.id.media_controls);
		mMediaControls.setVisibility(View.GONE);
		
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mMediaService == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    mMediaService.seek(mPosOverride);
                } catch (RemoteException ex) {
                }

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            mPosOverride = -1;
            mFromTouch = false;
        }
    };
    
    private View.OnClickListener mShuffleListener = new View.OnClickListener() {
        public void onClick(View v) {
            toggleShuffle();
        }
    };

    private View.OnClickListener mRepeatListener = new View.OnClickListener() {
        public void onClick(View v) {
            cycleRepeat();
        }
    };

    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };

    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mMediaService == null) return;
            try {
			    mMediaControls.startAnimation(media_controls_fade_out);
				mMediaControls.setVisibility(View.GONE);
            	mMediaService.prev();
            } catch (RemoteException ex) {
            }
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mMediaService == null) return;
            try {
			    mMediaControls.startAnimation(media_controls_fade_out);
				mMediaControls.setVisibility(View.GONE);
                mMediaService.next();
            } catch (RemoteException ex) {
            }
        }
    };

    private RepeatingImageButton.RepeatListener mRewListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanBackward(repcnt, howlong);
        }
    };
    
    private RepeatingImageButton.RepeatListener mFfwdListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanForward(repcnt, howlong);
        }
    };
    
    @Override
    public void onStart() {
        super.onStart();
        
		Log.v(TAG, "onStart called");
        
        paused = false;
        
		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, MediaService.class), connection, Context.BIND_AUTO_CREATE);
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaService.PLAYSTATE_CHANGED);
        f.addAction(MediaService.META_CHANGED);
        f.addAction(MediaService.START_DIALOG);
        f.addAction(MediaService.STOP_DIALOG);
        f.addAction(MediaService.ERROR_MESSAGE);
        registerReceiver(mStatusListener, new IntentFilter(f));
        
        f = new IntentFilter();
        f.addAction(MediaService.CLOSE_PLAYER);
        registerReceiver(mDisconnectListener, new IntentFilter(f));
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        Log.v(TAG, "onResume called");
        
        mParentActivityState = VISIBLE;
        
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	
    	Log.v(TAG, "onPause called");
    	
    	mParentActivityState = GONE;
    	
    	if (mDialog != null)
            dismissDialog();

    }
    
    @Override
    public void onStop() {
    	
    	Log.v(TAG, "onStop called");
    	
        paused = true;
        
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        super.onStop();
    }
    
	@Override
	public void onDestroy() {
		super.onDestroy();
		
        Log.v(TAG,"onDestroy called");
        
        unregisterReceiver(mDisconnectListener);
        
        // Detach our existing connection.
        unbindService(connection);
        mMediaService = null;
	}
    
    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        
		Log.d(TAG, "onNewIntent called");
		
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

		MenuItem sleepTimer = menu.add(0, -2, 0, R.string.menu_sleep_timer);
		sleepTimer.setIcon(R.drawable.ic_menu_sleep_timer);
		
		MenuItem nowPlaying = menu.add(R.string.title_now_playing);
		nowPlaying.setIcon(R.drawable.ic_menu_now_playing);
		nowPlaying.setIntent(new Intent(StreamMediaActivity.this, NowPlayingActivity.class));
		
		return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	try {
    		switch(item.getItemId()) {
        		case (-2): {
        			final String [] sleepTimerModes = getSleepTimerModes();
        			int sleepTimerMode = 0;
					sleepTimerMode = mMediaService.getSleepTimerMode();
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle(R.string.menu_sleep_timer);
					builder.setSingleChoiceItems(sleepTimerModes, sleepTimerMode, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							try {
								mMediaService.setSleepTimerMode(item);
								if (item == MediaService.SLEEP_TIMER_OFF) {
									showToast(R.string.sleep_timer_off_notif);
								} else {
								    showToast(getString(R.string.sleep_timer_on_notif) + " " + sleepTimerModes[mMediaService.getSleepTimerMode()]);
								}
								dialog.dismiss();
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						}
					}).show();
					return true;
				}
         	}
         	
		} catch (RemoteException ex) {
			ex.printStackTrace();
		}
         	
		return false;
    }       

    
    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		boolean controlsAreVisible = false;
		
		if (controlsAreVisible = mMediaControls.isShown()) {
			mMediaControls.setVisibility(View.GONE);
		}
		
		// get new window size on orientation change
		getDisplayMeasurements();
		
		setFullscreenMode(newConfig.orientation);
	    
	    if (holder != null)
	    	holder.setFixedSize(mDisplayWidth, mDisplayHeight);
	    
	    if (controlsAreVisible)
	    	mMediaControls.setVisibility(View.VISIBLE);
	}

    private void setFullscreenMode(int orientation) {
    	
	    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
	    } else {
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN, WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	    }
    }
    
    /**
     * Retrieve the phone display width and height
     */
    private void getDisplayMeasurements() {
        Display display = getWindowManager().getDefaultDisplay();
        mDisplayWidth = display.getWidth();
        mDisplayHeight = display.getHeight();
    }
    
    private void makeSurface() {
        preview = (SurfaceView) findViewById(R.id.surface_view);
        preview.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View arg0, MotionEvent arg1) {
				if (mMediaControls.isShown()) {
					mMediaControls.startAnimation(media_controls_fade_out);
					mMediaControls.setVisibility(View.GONE);
				} else {
					mMediaControls.startAnimation(media_controls_fade_in);
					mMediaControls.setVisibility(View.VISIBLE);
				}
				return false;
			}
		});
        
        holder = preview.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
    
    public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
        //Log.d(TAG, "surfaceChanged called");
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        //Log.d(TAG, "surfaceDestroyed called");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated called");
        
		// obtain the requested stream
		if (getIntent().getData() == null) {
			mRequestedStream = null;
		} else {
			try {
				mRequestedStream = getIntent().getData().toString();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
        
    	// if the requested stream is null the intent used to launch
    	// StreamMediaActivity did not supply a new URL to stream
        try {
			if ((mRequestedStream != null && mMediaService.getPlayListPath() == null) || 
					(mRequestedStream != null && !mRequestedStream.equals(mMediaService.getPlayListPath()))) {
				
				Log.v(TAG, mRequestedStream);
				if (mMediaService.getPlayListPath() != null)
				Log.v(TAG, mMediaService.getPlayListPath());
				
			    try {
			    	MultiPlayer mp = mMediaService.getMediaPlayer();
			    	mp.setDisplay(holder);
			        holder.setFixedSize(mDisplayWidth, mDisplayHeight);
			        
			        startPlayback();
			        
			        setRepeatButtonImage();
			        setShuffleButtonImage();
			        setPauseButtonImage();
			        showToast(R.string.media_controls_notif);
			        
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
				updateTrackInfo();
                setRepeatButtonImage();
                setShuffleButtonImage();
                setPauseButtonImage();
                queueNextRefresh(1);
				mMediaControls.setVisibility(View.VISIBLE);
			}
		} catch (RemoteException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
    	
    }

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    
	    if (keyCode == KeyEvent.KEYCODE_SEARCH && !mMediaControls.isShown()) {
	    	mMediaControls.setVisibility(View.VISIBLE);
	        return true;
	    }
	    
	    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && !mMediaControls.isShown()) {
	    	mMediaControls.setVisibility(View.VISIBLE);
	        return true;
	    }
	    
	    return super.onKeyDown(keyCode, event);
	}
    
    private void scanBackward(int repcnt, long delta) {
        if(mMediaService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mMediaService.position();
                mLastSeekEventTime = 0;
            } else {
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mMediaService.prev();
                    long duration = mMediaService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mMediaService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(mMediaService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mMediaService.position();
                mLastSeekEventTime = 0;
            } else {
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mMediaService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mMediaService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mMediaService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void doPauseResume() {
        try {
            if(mMediaService != null) {
                if (mMediaService.isPlaying()) {
                    mMediaService.pause();
                } else {
                    mMediaService.play();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void toggleShuffle() {
        if (mMediaService == null) {
            return;
        }
        try {
            int shuffle = mMediaService.getShuffleMode();
            if (shuffle == MediaService.SHUFFLE_NONE) {
            	mMediaService.setShuffleMode(MediaService.SHUFFLE_ON);
                if (mMediaService.getRepeatMode() == MediaService.REPEAT_CURRENT) {
                	mMediaService.setRepeatMode(MediaService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
                showToast(R.string.shuffle_on_notif);
            } else if (shuffle == MediaService.SHUFFLE_ON) {
            	mMediaService.setShuffleMode(MediaService.SHUFFLE_NONE);
                showToast(R.string.shuffle_off_notif);
            } else {
                Log.e(TAG, "Invalid shuffle mode: " + shuffle);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
        }
    }
    
    private void cycleRepeat() {
        if (mMediaService == null) {
            return;
        }
        try {
            int mode = mMediaService.getRepeatMode();
            if (mode == MediaService.REPEAT_NONE) {
                mMediaService.setRepeatMode(MediaService.REPEAT_ALL);
                showToast(R.string.repeat_all_notif);
            } else if (mode == MediaService.REPEAT_ALL) {
                mMediaService.setRepeatMode(MediaService.REPEAT_CURRENT);
                if (mMediaService.getShuffleMode() != MediaService.SHUFFLE_NONE) {
                    mMediaService.setShuffleMode(MediaService.SHUFFLE_NONE);
                    setShuffleButtonImage();
                }
                showToast(R.string.repeat_current_notif);
            } else {
                mMediaService.setRepeatMode(MediaService.REPEAT_NONE);
                showToast(R.string.repeat_off_notif);
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
        }
        
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }
    
    private void showToast(String message) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(message);
        mToast.show();
    }

    private void startPlayback() {

        String filename = "";
    	
        if(mMediaService == null)
            return;
        
        filename = mRequestedStream;
            
        	try {
				
                if (!mMediaService.loadQueue(filename)) {
                	errorOpeningMediaMessage();
                	return;
                }
                
                if (mMediaService.getPlayListLength() == 0) {
                	handleInvalidPlaylist();
                    return;
                }
				
                mMediaService.stop();
                mMediaService.queueFirstFile();
                setIntent(new Intent());
            } catch (Exception ex) {
                Log.v(TAG, "couldn't start playback: " + ex);
            }
    }

    private ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
    	        // This is called when the connection with the service has been
    	        // established, giving us the service object we can use to
    	        // interact with the service.  Because we have bound to a explicit
    	        // service that we know is running in our own process, we can
    	        // cast its IBinder to a concrete class and directly access it.
                mMediaService = IMediaService.Stub.asInterface(obj);
                
            	Log.v(TAG, "Bind Complete");
            	
            	if (preview == null) {
    		        makeSurface();
    			    Log.v(TAG, "Surface Made");
    		    }
            }
            public void onServiceDisconnected(ComponentName classname) {
    	        // This is called when the connection with the service has been
    	        // unexpectedly disconnected -- that is, its process crashed.
    	        // Because it is running in our same process, we should never
    	        // see this happen.
                mMediaService = null;
            }
    };

    private void setRepeatButtonImage() {
        if (mMediaService == null) return;
        try {
            switch (mMediaService.getRepeatMode()) {
                case MediaService.REPEAT_ALL:
                    mRepeatButton.setBackgroundResource(R.drawable.repeat_all_button);
                    break;
                case MediaService.REPEAT_CURRENT:
                    mRepeatButton.setBackgroundResource(R.drawable.repeat_one_button);
                    break;
                default:
                    mRepeatButton.setBackgroundResource(R.drawable.repeat_disabled_button);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setShuffleButtonImage() {
        if (mMediaService == null) return;
        try {
            switch (mMediaService.getShuffleMode()) {
                case MediaService.SHUFFLE_NONE:
                    mShuffleButton.setBackgroundResource(R.drawable.shuffle_disabled_button);
                    break;
                default:
                    mShuffleButton.setBackgroundResource(R.drawable.shuffle_button);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setPauseButtonImage() {
        try {
            if (mMediaService != null && mMediaService.isPlaying()) {
                mPauseButton.setBackgroundResource(R.drawable.pause_button);
            } else {
                mPauseButton.setBackgroundResource(R.drawable.play_button);
            }
        } catch (RemoteException ex) {
        }
    }
    
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private boolean paused;

    private static final int REFRESH = 1;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if(mMediaService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mMediaService.position() : mPosOverride;
            long remaining = 1000 - (pos % 1000);
            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
                
                if (mMediaService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    remaining = 500;
                }

                mProgress.setProgress((int) (1000 * pos / mDuration));
            } else {
                mCurrentTime.setText("--:--");
                mProgress.setProgress(1000);
            }
            // return the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            return remaining;
        } catch (RemoteException ex) {
        }
        return 500;
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MediaService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();
                queueNextRefresh(1);
        		mMediaControls.startAnimation(media_controls_fade_in);
        		mMediaControls.setVisibility(View.VISIBLE);
            } else if (action.equals(MediaService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            } else if (action.equals(MediaService.START_DIALOG)) {
	        	try {
	        		if (mParentActivityState == VISIBLE) {
	                	Log.v(TAG, "STARTING DIALOG!");
	        			mDialog = ProgressDialog.show(StreamMediaActivity.this, "", 
	        					"Opening file...", true);
	        		}
	        	} catch (Exception ex) {
	        	    ex.printStackTrace();	
	        	}
            } else if (action.equals(MediaService.STOP_DIALOG)) {
            	if (mParentActivityState == VISIBLE) {
            		dismissDialog();
            	}
            } else if (action.equals(MediaService.ERROR_MESSAGE)) {
				new AlertDialog.Builder(StreamMediaActivity.this)
				.setTitle(R.string.cannot_play_media_title)
				.setMessage(R.string.cannot_play_media_message)
				.setPositiveButton(R.string.cannot_play_media_pos, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
					}).create().show();
            }
        }
    };
    
    private BroadcastReceiver mDisconnectListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MediaService.CLOSE_PLAYER)) {
            	finish();
            }
        }
    };
    
    private void updateTrackInfo() {
        if (mMediaService == null) {
            return;
        }
        try {
        	//TODO ?
            String path = mMediaService.getPath();
            if (path == null) {
                //finish();
                return;
            }
            
            mTrackNumber.setText(mMediaService.getTrackNumber());
            
            if (mMediaService.getTrackName() == null) {
            	mTrackName.setText(mMediaService.getMediaURL());
            } else {
                mTrackName.setText(mMediaService.getTrackName());
            }
    		
            mDuration = mMediaService.duration();
            mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
        } catch (RemoteException ex) {
            //finish();
        }
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
    
    private void handleInvalidPlaylist() {

    	if (mDialog != null)
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
		mHandler.post(new Runnable() {
			public void run() {
				if (mDialog != null)
				    mDialog.dismiss();
			}
		});
	}
	
    public String [] getSleepTimerModes() {
    	String [] sleepModes = new String[7];
    	
    	sleepModes[0] = getString(R.string.list_sleep_timer_off);
        sleepModes[1] = getString(R.string.list_sleep_timer_ten_min);
        sleepModes[2] = getString(R.string.list_sleep_timer_twenty_min);
        sleepModes[3] = getString(R.string.list_sleep_timer_thirty_min);
        sleepModes[4] = getString(R.string.list_sleep_timer_fourty_min);
        sleepModes[5] = getString(R.string.list_sleep_timer_fifty_min);
        sleepModes[6] = getString(R.string.list_sleep_timer_sixty_min);
    	
    	return sleepModes;
    }
}
