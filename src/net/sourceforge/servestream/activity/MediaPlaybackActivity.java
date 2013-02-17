/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.CoverView;
import net.sourceforge.servestream.utils.CoverView.CoverViewListener;
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
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MediaPlaybackActivity extends Activity implements MusicUtils.Defs,
    OnSharedPreferenceChangeListener,
    CoverViewListener
{
    private static final String TAG = MediaPlaybackActivity.class.getName();

    private static final int MAX_SLEEP_TIMER_MINUTES = 120;
    
    private int mParentActivityState = VISIBLE;
    private static int VISIBLE = 1;
    private static int GONE = 2;
    
    private final static int PREPARING_MEDIA = 1;
    private final static int DIALOG_SLEEP_TIMER = 2;
    
    private SharedPreferences mPreferences;
    
    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaPlaybackService mService = null;
    private ImageButton mCloseButton = null;
    private ImageButton mPlayQueue = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private Worker mAlbumArtWorker;
    private AlbumArtHandler mAlbumArtHandler;
    private Toast mToast;
    private ServiceToken mToken;

    private TextView mTrackNumber;
    
    public MediaPlaybackActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mAlbumArtWorker = new Worker("album art worker");
        mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_media_player);

        mCurrentTime = (TextView) findViewById(R.id.position_text);
        mTotalTime = (TextView) findViewById(R.id.duration_text);
        mProgress = (ProgressBar) findViewById(R.id.seek_bar);
        mAlbum = (CoverView) findViewById(R.id.album_art);
        mAlbum.setup(mAlbumArtWorker.getLooper(), this);
        mTrackName = (TextView) findViewById(R.id.trackname);
        mTrackName.setSelected(true);
        mArtistAndAlbumName = (TextView) findViewById(R.id.artist_and_album);
        mArtistAndAlbumName.setSelected(true);
        mTrackNumber = (TextView) findViewById(R.id.track_number_text);

        mCloseButton = (ImageButton) findViewById(R.id.close);
        mCloseButton.setOnClickListener(mCloseListener);
        mPlayQueue = (ImageButton) findViewById(R.id.play_queue);
        mPlayQueue.setOnClickListener(mPlayQueueListener);
        
        mPrevButton = (RepeatingImageButton) findViewById(R.id.previous_button);
        mPrevButton.setOnClickListener(mPrevListener);
        mPrevButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (ImageButton) findViewById(R.id.play_pause_button);
        mPauseButton.setOnClickListener(mPauseListener);
        mNextButton = (RepeatingImageButton) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(mNextListener);
        mNextButton.setRepeatListener(mFfwdListener, 260);
        seekmethod = 1;

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);
        
        mShuffleButton = (ImageButton) findViewById(R.id.shuffle_button);
        mShuffleButton.setOnClickListener(mShuffleListener);        
        mRepeatButton = (ImageButton) findViewById(R.id.repeat_button);
        mRepeatButton.setOnClickListener(mRepeatListener);
        
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);
    }
    
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
  	    if (key.equals(PreferenceConstants.WAKELOCK)) {
			if (sharedPreferences.getBoolean(PreferenceConstants.WAKELOCK, true)) {
		    	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  	        } else {
  	      		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  	        }
  	    }
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

    private View.OnClickListener mCloseListener = new View.OnClickListener() {
        public void onClick(View v) {
        	finish();
        }
    };
    
    private View.OnClickListener mPlayQueueListener = new View.OnClickListener() {
        public void onClick(View v) {
        	startActivity(new Intent(MediaPlaybackActivity.this, NowPlayingActivity.class));
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
                mService.prev();
            } catch (RemoteException ex) {
            }
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                mService.next();
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
        paused = false;
        
		if (mPreferences.getBoolean(PreferenceConstants.WAKELOCK, true)) {
	    	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
        
        mToken = MusicUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
            mHandler.sendEmptyMessage(QUIT);
        }
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.ART_CHANGED);
        f.addAction(MediaPlaybackService.START_DIALOG);
        f.addAction(MediaPlaybackService.STOP_DIALOG);
        registerReceiver(mStatusListener, new IntentFilter(f));
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mParentActivityState = VISIBLE;
        updateTrackInfo();
        setPauseButtonImage();
    }

    @Override
    public void onPause() {
    	super.onPause();
    	mParentActivityState = GONE;
    	removeDialog(PREPARING_MEDIA);
    }

    @Override
    public void onStop() {
        paused = true;
        mHandler.removeMessages(REFRESH);
        unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }
    
    @Override
    public void onDestroy() {
        mAlbumArtWorker.quit();
        super.onDestroy();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case (R.id.menu_item_stop):
    			doStop();
    			return true;
        	case (R.id.menu_item_sleep_timer):
        		showDialog(DIALOG_SLEEP_TIMER);
        		return true;
        	case (R.id.menu_item_settings):
        		startActivity(new Intent(MediaPlaybackActivity.this, SettingsActivity.class));
        		return true;
        	 case EFFECTS_PANEL: {
                try {
                	Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
					i.putExtra("android.media.extra.AUDIO_SESSION", mService.getAudioSessionId());
	                startActivityForResult(i, EFFECTS_PANEL);
	                return true;
				} catch (RemoteException e) {
					e.printStackTrace();
				}
             }
        }
         	
        return super.onOptionsItemSelected(item);
    }
    
    protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    ProgressDialog progressDialog = null;
	    switch(id) {
	    	case PREPARING_MEDIA:
	    		progressDialog = new ProgressDialog(MediaPlaybackActivity.this);
	    		progressDialog.setMessage(getString(R.string.opening_url_message));
	    		progressDialog.setCancelable(true);
	    		return progressDialog;
	    	case DIALOG_SLEEP_TIMER:
	    		dialog = null;
	    		
	    		if (mService == null) {
	    			break;
	    		}
	    	
	    		int sleepTimerMode = -1;
	    	
	    		try {
	    			sleepTimerMode = mService.getSleepTimerMode();
	    		} catch (RemoteException e) {
	    			break;
	    		}
			
            	LayoutInflater factory = LayoutInflater.from(this);
            	final View sleepTimerView = factory.inflate(R.layout.alert_dialog_sleep_timer, null);
            	final TextView sleepTimerText = (TextView) sleepTimerView.findViewById(R.id.sleep_timer_text);
            	final SeekBar seekbar = (SeekBar) sleepTimerView.findViewById(R.id.seekbar);
            	seekbar.setMax(MAX_SLEEP_TIMER_MINUTES);
            	sleepTimerText.setText(makeTimeString(sleepTimerMode));
            	seekbar.setProgress(sleepTimerMode);
            	seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

            		@Override
            		public void onProgressChanged(SeekBar seekBar, int progress,
            				boolean fromUser) {
            			sleepTimerText.setText(makeTimeString(progress));
            		}

            		@Override
            		public void onStartTrackingTouch(SeekBar seekBar) {
            			
            		}

            		@Override
            		public void onStopTrackingTouch(SeekBar seekBar) {
            			
            		}
            	
            	});
            	return new AlertDialog.Builder(MediaPlaybackActivity.this)
                	.setTitle(R.string.menu_sleep_timer)
                	.setView(sleepTimerView)
                	.setCancelable(true)
                	.setPositiveButton(R.string.set_alarm, new DialogInterface.OnClickListener() {
                		public void onClick(DialogInterface dialog, int whichButton) {
                			setSleepTimer(seekbar.getProgress());
                			dialog.dismiss();
                		}
                	})
                	.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                		public void onClick(DialogInterface dialog, int whichButton) {
                			removeDialog(DIALOG_SLEEP_TIMER);
                		}
                	})
                	.create();
	    	default:
	    		dialog = null;
	    }
	    return dialog;
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_media_playback, menu);
        
        // Don't offer the audio effects display when running on an OS
        // before API level 9 because it relies on the getAudioSessionId method,
        // which isn't available until after API 8
        if (Build.VERSION.SDK_INT >= 9 && MusicUtils.getCurrentAudioId() >= 0) {
            Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
            if (getPackageManager().resolveActivity(i, 0) != null) {
                menu.add(0, EFFECTS_PANEL, 0, R.string.list_menu_effects).setIcon(R.drawable.ic_menu_eq);
            }
        }
        
        return true;
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
    
    private void doStop() {
    	try {
    		if(mService != null) {
    			mService.stop();
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
            	mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                if (mService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
                	mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
                showToast(R.string.shuffle_on_notif);
            } else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL) {
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
    
    private void setSleepTimer(int pos) {
        if (mService == null) {
            return;
        }    	
    	try {
			MusicUtils.sService.setSleepTimerMode(pos);
			if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
				showToast(R.string.sleep_timer_off_notif);
			} else {
			    showToast(getString(R.string.sleep_timer_on_notif) + " " + makeTimeString(pos));
			}
		} catch (RemoteException e) {
		}
    }
    
    private String makeTimeString(int pos) {
    	String minuteText;
    	
    	if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
	    	minuteText = getResources().getString(R.string.minute_picker_cancel);
	    } else if (pos == 1) {
	    	minuteText = getResources().getString(R.string.minute);
	    } else if (pos % 60 == 0 && pos > 60) {
	    	minuteText = getResources().getString(R.string.hours, String.valueOf(pos / 60));
	    } else if (pos % 60 == 0) {
	    	minuteText = getResources().getString(R.string.hour);
	    } else {
	    	minuteText = getResources().getString(R.string.minutes, String.valueOf(pos));
	    }
    	
    	return minuteText;
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, null, Toast.LENGTH_SHORT);
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

        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    private ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
                mService = IMediaPlaybackService.Stub.asInterface(obj);
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
                    mRepeatButton.setImageResource(R.drawable.btn_player_repeat_checked);
                    break;
                case MediaPlaybackService.REPEAT_CURRENT:
                    mRepeatButton.setImageResource(R.drawable.btn_player_repeat_one_checked);
                    break;
                default:
                    mRepeatButton.setImageResource(R.drawable.btn_player_repeat_normal);
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
                    mShuffleButton.setImageResource(R.drawable.btn_player_shuffle_normal);
                    break;
                default:
                    mShuffleButton.setImageResource(R.drawable.btn_player_shuffle_checked);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setImageResource(R.drawable.btn_player_pause);
            } else {
                mPauseButton.setImageResource(R.drawable.btn_player_play);
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setSeekControls() {
    	if (mService == null) {
    		return;
    	}
    	
    	try {
			if (mService.duration() > 0) {
				mProgress.setEnabled(true);
            	mPrevButton.setRepeatListener(mRewListener, 260);
            	mNextButton.setRepeatListener(mFfwdListener, 260);
			} else {
				mProgress.setEnabled(false);
    			mPrevButton.setRepeatListener(null, -1);
    			mNextButton.setRepeatListener(null, -1);
			}
		} catch (RemoteException e) {
		}	
    }
    
    private CoverView mAlbum;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistAndAlbumName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private static final int GET_ALBUM_ART = 3;
    private static final int REFRESH_ALBUM_ART = 4;

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
                	mProgress.setSecondaryProgress((int) (mService.getPercentDownloaded() * 1000));
        			mPrevButton.setRepeatListener(null, -1);
        			mNextButton.setRepeatListener(null, -1);
        			mProgress.setEnabled(false);
        		} else {
        			mDuration = mService.getCompleteFileDuration();
                	mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
                	mPrevButton.setRepeatListener(mRewListener, 260);
                	mNextButton.setRepeatListener(mFfwdListener, 260);
                	mProgress.setEnabled(true);
        		}
        	}
        	
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            long remaining = 1000 - (pos % 1000);
            if ((pos >= 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
                
                if (mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    remaining = 500;
                }

                if (mDuration > 0) {
                	mProgress.setProgress((int) (1000 * pos / mDuration));
                } else {
                	mProgress.setProgress(1000);
                }
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
                            .setPositiveButton(android.R.string.ok,
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
                setSeekControls();
                setPauseButtonImage();
                queueNextRefresh(1);
            } else if (action.equals(MediaPlaybackService.ART_CHANGED)) {
                try {
                	mAlbumArtHandler.removeMessages(REFRESH_ALBUM_ART);
					mAlbumArtHandler.obtainMessage(REFRESH_ALBUM_ART, new IdWrapper(mService.getTrackId())).sendToTarget();
				} catch (RemoteException e) {
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
            }
        }
    };
    
    private static class IdWrapper {
        public long id;
        IdWrapper(long id) {
            this.id = id;
        }
    }
    
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
            
            String trackName = mService.getTrackName();
            if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
            	trackName = mService.getMediaUri();
            }
            
            mTrackName.setText(trackName);
            
            String artistName = mService.getArtistName();
            String albumName = mService.getAlbumName();
            String artistAndAlbumName = null;
            
            if ((artistName == null || artistName.equals(Media.UNKNOWN_STRING)) &&
            		albumName == null || albumName.equals(Media.UNKNOWN_STRING)) {
            	artistAndAlbumName = "";
            } else {
            	artistAndAlbumName = mService.getArtistName() + " - " + mService.getAlbumName();
            }
            
            mArtistAndAlbumName.setText(artistAndAlbumName);
            
            mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
            mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new IdWrapper(mService.getTrackId())).sendToTarget();
            
            if (mService.isStreaming()) {
            	mDuration = mService.duration();
            	mProgress.setSecondaryProgress(0);
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
    
    public class AlbumArtHandler extends Handler {
        private long mId = -1;
        
        public AlbumArtHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg)
        {
            long id = ((IdWrapper) msg.obj).id;
            
            if (((msg.what == GET_ALBUM_ART && mId != id)
            		|| msg.what == REFRESH_ALBUM_ART)
            		&& id >= 0) {
            	if (mAlbum.generateBitmap(id)) {
            		mId = id;
            	}
            }
        }
    }
    
    private static class Worker implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;
        
        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         * @param name A name for the new thread
         */
        Worker(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
        
        public Looper getLooper() {
            return mLooper;
        }
        
        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLock.notifyAll();
            }
            Looper.loop();
        }
        
        public void quit() {
            mLooper.quit();
        }
    }

	@Override
	public void onCoverViewInitialized() {
        if (mService == null) {
            return;
        }
        
        try {
            mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
			mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new IdWrapper(mService.getTrackId())).sendToTarget();
		} catch (RemoteException e) {
		}
	}
}
