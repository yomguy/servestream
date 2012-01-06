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

package net.sourceforge.servestream.activity;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.button.RepeatingImageButton;
import net.sourceforge.servestream.player.MultiPlayer;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.PreferenceConstants;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MediaPlaybackActivity extends Activity implements SurfaceHolder.Callback, MusicUtils.Defs,
    OnSharedPreferenceChangeListener
{
    private static final String TAG = MediaPlaybackActivity.class.getName();

    private int mParentActivityState = VISIBLE;
    private static int VISIBLE = 1;
    private static int GONE = 2;
    
    private final static int PREPARING_MEDIA = 1;
    
    private SharedPreferences mPreferences;
    private WakeLock mWakeLock;
    
	private Animation media_controls_fade_in, media_controls_fade_out;
	private RelativeLayout mMediaControls;
    
    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaPlaybackService mService = null;
    private Button mPrevButton;
    private RepeatingImageButton mSeekBackwardButton;
    private Button mPauseButton;
    private Button mNextButton;
    private RepeatingImageButton mSeekForwardButton;
    private Button mRepeatButton;
    private Button mShuffleButton;
    private Toast mToast;
    private ServiceToken mToken;

    private TextView mTrackNumber;
    
    private SurfaceView preview = null;
    private SurfaceHolder holder;
    
	private int mDisplayWidth = 0;
	private int mDisplayHeight = 0;
    
    public MediaPlaybackActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        if (Build.VERSION.SDK_INT < 11) {
        	requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        
		// get the phone's display width and height
		getDisplayMeasurements();
        
		// determine fullscreen mode
		setFullscreenMode(getResources().getConfiguration().orientation);
		
        setContentView(R.layout.acc_mediaplayer);

        mCurrentTime = (TextView) findViewById(R.id.position_text);
        mTotalTime = (TextView) findViewById(R.id.duration_text);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
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
        
        seekmethod = 1;

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);
        
	    mTrackNumber = (TextView) findViewById(R.id.track_number_text);
	    mTrackName = (TextView) findViewById(R.id.trackname);
	    mArtistName = (TextView) findViewById(R.id.artistname);
	    mAlbumName = (TextView) findViewById(R.id.albumname);
        
        mProgress = (ProgressBar) findViewById(R.id.seek_bar);
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

		// preload animation for media controller
		media_controls_fade_in = AnimationUtils.loadAnimation(this, R.anim.media_controls_fade_in);
		media_controls_fade_out = AnimationUtils.loadAnimation(this, R.anim.media_controls_fade_out);

		mMediaControls = (RelativeLayout) findViewById(R.id.media_controls);
		mMediaControls.setVisibility(View.GONE);
    }
    
	/* (non-Javadoc)
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
  	    if (key.equals(PreferenceConstants.WAKELOCK)) {
			if (sharedPreferences.getBoolean(PreferenceConstants.WAKELOCK, true)) {
		    	mWakeLock.acquire();
  	        } else {
  	            mWakeLock.release();
  	        }
  	    }
  	}
    
    int mInitialX = -1;
    int mLastX = -1;
    int mTextWidth = 0;
    int mViewWidth = 0;
    boolean mDraggingLabel = false;
    
    TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.albumname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        return null;
    }
    
    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    mService.seek(mPosOverride);
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
            if (mService == null) return;
            try {
    			mMediaControls.startAnimation(media_controls_fade_out);
    			mMediaControls.setVisibility(View.GONE);
                mService.prev();
            } catch (RemoteException ex) {
            }
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
			    mMediaControls.startAnimation(media_controls_fade_out);
				mMediaControls.setVisibility(View.GONE);
                mService.next();
            } catch (RemoteException ex) {
            }
        }
    };
    
    @Override
    public void onStart() {
        super.onStart();
        
		if (mPreferences.getBoolean(PreferenceConstants.WAKELOCK, true))
			mWakeLock.acquire();
        
        paused = false;
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.START_DIALOG);
        f.addAction(MediaPlaybackService.STOP_DIALOG);
        f.addAction(MediaPlaybackService.ERROR_MESSAGE);
        
        registerReceiver(mStatusListener, new IntentFilter(f));
        
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
        
    	if (preview == null) {
	        makeSurface();
		    Log.v(TAG, "Surface Made");
	    }
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        Log.v(TAG, "onResume called");
        
        mParentActivityState = VISIBLE;
        
        //updateTrackInfo();
        //setPauseButtonImage();
    }

    @Override
    public void onPause() {
    	super.onPause();
    	
    	Log.v(TAG, "onPause called");
    	
		mWakeLock.release();
    	
    	mParentActivityState = GONE;
    	
    	removeDialog(PREPARING_MEDIA);
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	
    	Log.v(TAG, "onStop called");
    	
        paused = true;
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	try {
    		switch(item.getItemId()) {
    		    case (R.id.menu_item_shuffle):
    		    	toggleShuffle();
    		    	break;
    		    case (R.id.menu_item_repeat):
    		    	cycleRepeat();
    		        break;
        		case (R.id.menu_item_now_playing):
        			startActivity(new Intent(MediaPlaybackActivity.this, NowPlayingActivity.class));
        			break;
        		case (R.id.menu_item_sleep_timer): {
        			final String [] sleepTimerModes = getSleepTimerModes();
        			int sleepTimerMode = 0;
					sleepTimerMode = mService.getSleepTimerMode();
					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle(R.string.menu_sleep_timer);
					builder.setSingleChoiceItems(sleepTimerModes, sleepTimerMode, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							try {
								mService.setSleepTimerMode(item);
								if (item == MediaPlaybackService.SLEEP_TIMER_OFF) {
									showToast(R.string.sleep_timer_off_notif);
								} else {
								    showToast(getString(R.string.sleep_timer_on_notif) + " " + sleepTimerModes[mService.getSleepTimerMode()]);
								}
								dialog.dismiss();
							} catch (RemoteException e) {
								e.printStackTrace();
							}
						}
					}).show();
					return true;
				}
        		case (R.id.menu_item_settings):
        			startActivity(new Intent(MediaPlaybackActivity.this, SettingsActivity.class));
        			break;
         	}
         	
		} catch (RemoteException ex) {
			ex.printStackTrace();
		}
         	
		return false;
    }
    
    protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    ProgressDialog progressDialog = null;
	    switch(id) {
	    case PREPARING_MEDIA:
        	progressDialog = new ProgressDialog(MediaPlaybackActivity.this);
        	progressDialog.setMessage("Opening file...");
        	progressDialog.setCancelable(true);
	    	return progressDialog;
	    default:
	        dialog = null;
	    }
	    return dialog;
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stream_media_menu, menu);
        return true;
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
    
    private final int keyboard[][] = {
        {
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
        },
        {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_DEL,
        },
        {
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_ENTER
        }

    };

    private int lastX;
    private int lastY;

    private boolean seekMethod1(int keyCode)
    {
        if (mService == null) return false;
        for(int x=0;x<10;x++) {
            for(int y=0;y<3;y++) {
                if(keyboard[y][x] == keyCode) {
                    int dir = 0;
                    // top row
                    if(x == lastX && y == lastY) dir = 0;
                    else if (y == 0 && lastY == 0 && x > lastX) dir = 1;
                    else if (y == 0 && lastY == 0 && x < lastX) dir = -1;
                    // bottom row
                    else if (y == 2 && lastY == 2 && x > lastX) dir = -1;
                    else if (y == 2 && lastY == 2 && x < lastX) dir = 1;
                    // moving up
                    else if (y < lastY && x <= 4) dir = 1; 
                    else if (y < lastY && x >= 5) dir = -1; 
                    // moving down
                    else if (y > lastY && x <= 4) dir = -1; 
                    else if (y > lastY && x >= 5) dir = 1; 
                    lastX = x;
                    lastY = y;
                    try {
                        mService.seek(mService.position() + dir * 5);
                    } catch (RemoteException ex) {
                    }
                    refreshNow();
                    return true;
                }
            }
        }
        lastX = -1;
        lastY = -1;
        return false;
    }

    private boolean seekMethod2(int keyCode)
    {
        if (mService == null) return false;
        for(int i=0;i<10;i++) {
            if(keyboard[0][i] == keyCode) {
                int seekpercentage = 100*i/10;
                try {
                    mService.seek(mService.duration() * seekpercentage / 100);
                } catch (RemoteException ex) {
                }
                refreshNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        try {
            switch(keyCode)
            {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            if (mStartSeekPos < 1000) {
                                mService.prev();
                            } else {
                                mService.seek(0);
                            }
                        } else {
                            scanBackward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            mService.next();
                        } else {
                            scanForward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
            }
        } catch (RemoteException ex) {
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean useDpadMusicControl() {
        if (mDeviceHasDpad && (mPrevButton.isFocused() ||
                mNextButton.isFocused() ||
                mPauseButton.isFocused())) {
            return true;
        }
        return false;
    }

    private void makeSurface() {
        preview = (SurfaceView) findViewById(R.id.surface_view);
        preview.setOnLongClickListener(new OnLongClickListener() {

			public boolean onLongClick(View arg0) {
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
    
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
		
	}

	public void surfaceCreated(SurfaceHolder arg0) {
        Log.d(TAG, "surfaceCreated called");
        
		// connect with manager service to find all bridges
		// when connected it will insert all views
        mToken = MusicUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
            mHandler.sendEmptyMessage(QUIT);
        }		
	}

	public void surfaceDestroyed(SurfaceHolder arg0) {

	}
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        int repcnt = event.getRepeatCount();

        if((seekmethod==0)?seekMethod1(keyCode):seekMethod2(keyCode))
            return true;

        switch(keyCode)
        {
            case KeyEvent.KEYCODE_SLASH:
                seekmethod = 1 - seekmethod;
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mPrevButton.hasFocus()) {
                    mPrevButton.requestFocus();
                }
                scanBackward(repcnt, event.getEventTime() - event.getDownTime());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mNextButton.hasFocus()) {
                    mNextButton.requestFocus();
                }
                scanForward(repcnt, event.getEventTime() - event.getDownTime());
                return true;

            case KeyEvent.KEYCODE_S:
                toggleShuffle();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SPACE:
                doPauseResume();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void scanBackward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
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
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
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
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
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
            if(mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void toggleShuffle() {
        if (mService == null) {
            return;
        }
        try {
            int shuffle = mService.getShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
            	mService.setShuffleMode(MediaPlaybackService.SHUFFLE_ON);
                if (mService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
                	mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
                showToast(R.string.shuffle_on_notif);
            } else if (shuffle == MediaPlaybackService.SHUFFLE_ON) {
            	mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                showToast(R.string.shuffle_off_notif);
            } else {
                Log.e(TAG, "Invalid shuffle mode: " + shuffle);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
        }
    }
    
    private void cycleRepeat() {
        if (mService == null) {
            return;
        }
        try {
            int mode = mService.getRepeatMode();
            if (mode == MediaPlaybackService.REPEAT_NONE) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                showToast(R.string.repeat_all_notif);
            } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
                if (mService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    setShuffleButtonImage();
                }
                showToast(R.string.repeat_current_notif);
            } else {
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
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

        if(mService == null)
            return;

        Intent intent = getIntent();
        String action = intent.getAction();
        
        if (action != null && action.equals(MediaPlaybackService.PREPARE_VIDEO)) {
        	try {
				mService.setDataSource(false);
			} catch (RemoteException e) {
				e.printStackTrace();
				Log.d("MediaPlaybackActivity", "couldn't start playback: " + e);
			}
			
			setIntent(new Intent());
        }

        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    private ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
                mService = IMediaPlaybackService.Stub.asInterface(obj);
                
		    	MultiPlayer mp = null;
				try {
					mp = mService.getMediaPlayer();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
		    	mp.setDisplay(holder);
		        holder.setFixedSize(mDisplayWidth, mDisplayHeight);
                
                startPlayback();
                try {
                    // Assume something is playing when the service says it is,
                    // but also if the audio ID is valid but the service is paused.
                    if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                            mService.getPath() != null) {
                        // something is playing now, we're done
                        mRepeatButton.setVisibility(View.VISIBLE);
                        mShuffleButton.setVisibility(View.VISIBLE);
                        setRepeatButtonImage();
                        setShuffleButtonImage();
                        setPauseButtonImage();
                        return;
                    }
                } catch (RemoteException ex) {
                }
                // Service is dead or not playing anything. Return to the previous
                // activity.
                finish();
            }
            public void onServiceDisconnected(ComponentName classname) {
                mService = null;
            }
    };
    
    private void setRepeatButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getRepeatMode()) {
                case MediaPlaybackService.REPEAT_ALL:
                    mRepeatButton.setBackgroundResource(R.drawable.repeat_all_button);
                    break;
                case MediaPlaybackService.REPEAT_CURRENT:
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
        if (mService == null) return;
        try {
            switch (mService.getShuffleMode()) {
                case MediaPlaybackService.SHUFFLE_NONE:
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
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setBackgroundResource(R.drawable.pause_button);
            } else {
                mPauseButton.setBackgroundResource(R.drawable.play_button);
            }
        } catch (RemoteException ex) {
        }
    }
    
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mAlbumName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if(mService == null)
            return 500;
        try {
        	if (!mService.isStreaming()) {
        		if (!mService.isCompleteFileAvailable()) {
        			mSeekBackwardButton.setEnabled(false);
        			mSeekForwardButton.setEnabled(false);
        			mProgress.setEnabled(false);
        		} else {
        			mDuration = mService.getCompleteFileDuration();
                	mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
                	mSeekBackwardButton.setEnabled(true);
                	mSeekForwardButton.setEnabled(true);
                	mProgress.setEnabled(true);
        		}
        	}
        	
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            long remaining = 1000 - (pos % 1000);
            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
                
                if (mService.isPlaying()) {
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
                    
                case QUIT:
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    new AlertDialog.Builder(MediaPlaybackActivity.this)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(R.string.service_start_error_button,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            finish();
                                        }
                                    })
                            .setCancelable(false)
                            .show();
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
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();
                queueNextRefresh(1);
        		
                if (mMediaControls.getVisibility() != View.VISIBLE) {
                	mMediaControls.startAnimation(media_controls_fade_in);
                	mMediaControls.setVisibility(View.VISIBLE);
                }
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            } else if (action.equals(MediaPlaybackService.START_DIALOG)) {
	        	try {
	        		if (mParentActivityState == VISIBLE) {
	        			showDialog(PREPARING_MEDIA);
	        		}
	        	} catch (Exception ex) {
	        	    ex.printStackTrace();	
	        	}
            } else if (action.equals(MediaPlaybackService.STOP_DIALOG)) {
            	if (mParentActivityState == VISIBLE) {
            		try {
            			removeDialog(PREPARING_MEDIA);
    	        	} catch (Exception ex) {
    	        	    ex.printStackTrace();	
    	        	}
            	}
            } else if (action.equals(MediaPlaybackService.ERROR_MESSAGE)) {
				new AlertDialog.Builder(MediaPlaybackActivity.this)
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
    
    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                finish();
                return;
            }
            
            mTrackNumber.setText(mService.getTrackNumber());
            
            ((View) mArtistName.getParent()).setVisibility(View.VISIBLE);
            ((View) mAlbumName.getParent()).setVisibility(View.VISIBLE);
                
            String trackName = mService.getTrackName();
            if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
            	trackName = mService.getMediaUri();
            }
                
            mTrackName.setText(trackName);
            mArtistName.setText(mService.getArtistName());
            mAlbumName.setText(mService.getAlbumName());
                
            if (mService.isStreaming()) {
            	mDuration = mService.duration();
            } else {
            	if (mService.isCompleteFileAvailable()) {
            		mDuration = mService.getCompleteFileDuration();
            	} else {
            		mDuration = 0;
            	}
            }
            mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
        } catch (RemoteException ex) {
            finish();
        }
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