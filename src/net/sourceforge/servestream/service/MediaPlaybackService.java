/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
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

package net.sourceforge.servestream.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.Vector;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MediaPlayerActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.bitmap.DatabaseImageResizer;
import net.sourceforge.servestream.bitmap.ImageCache;
import net.sourceforge.servestream.media.Metadata;
import net.sourceforge.servestream.media.MetadataRetrieverTask;
import net.sourceforge.servestream.media.MultiPlayer;
import net.sourceforge.servestream.media.MetadataRetrieverListener;
import net.sourceforge.servestream.media.ShoutCastRetrieverTask;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.receiver.ConnectivityReceiver;
import net.sourceforge.servestream.receiver.MediaButtonIntentReceiver;
import net.sourceforge.servestream.service.RemoteControlClientCompat.MetadataEditorCompat;
import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.DetermineActionTask;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.preference.PreferenceConstants;
import net.sourceforge.servestream.utils.Utils;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service implements 
		OnSharedPreferenceChangeListener,
		MetadataRetrieverListener,
		DetermineActionTask.MusicRetrieverPreparedListener {

    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 2;
    
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_ON = 1;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static final String PLAYSTATE_CHANGED = "net.sourceforge.servestream.playstatechanged";
    public static final String META_CHANGED = "net.sourceforge.servestream.metachanged";
    public static final String META_RETRIEVED = "net.sourceforge.servestream.meta_retrieved";
    public static final String ART_CHANGED = "net.sourceforge.servestream.artchanged";
    public static final String QUEUE_CHANGED = "net.sourceforge.servestream.queuechanged";
    private static final String AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    private static final String AVRCP_META_CHANGED = "com.android.music.metachanged";
    
    public static final String PLAYBACK_STARTED = "net.sourceforge.servestream.playbackstarted";
    public static final String PLAYBACK_COMPLETE = "net.sourceforge.servestream.playbackcomplete";
    public static final String START_DIALOG = "net.sourceforge.servestream.startdialog";
    public static final String STOP_DIALOG = "net.sourceforge.servestream.stopdialog";
    public static final String PLAYER_CLOSED = "net.sourceforge.servestream.playerclosed";
    
    public static final String SERVICECMD = "net.sourceforge.servestream.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String CMDNOTIF = "buttonId";

    public static final String TOGGLEPAUSE_ACTION = "net.sourceforge.servestream.musicservicecommand.togglepause";
    public static final String PAUSE_ACTION = "net.sourceforge.servestream.musicservicecommand.pause";
    public static final String PREVIOUS_ACTION = "net.sourceforge.servestream.musicservicecommand.previous";
    public static final String NEXT_ACTION = "net.sourceforge.servestream.musicservicecommand.next";

    public static final String BLUETOOTH_DEVICE_PAIRED = "net.sourceforge.servestream.musicservicecommand.bluetooth_device_paired";
    
    private static final String NOTIFICATION_IMAGE_CACHE_DIR = "notification_album_art";
    private static final String LOCK_SCREEN_IMAGE_CACHE_DIR = "lock_screen_album_art";
    
    public static final int SLEEP_TIMER_OFF = 0;
    
    public static final int TRACK_ENDED = 1;
    private static final int RELEASE_WAKELOCK = 2;
    public static final int SERVER_DIED = 3;
    private static final int FOCUSCHANGE = 4;
    private static final int FADEDOWN = 5;
    private static final int FADEUP = 6;
    private static final int TRACK_WENT_TO_NEXT = 7;
    public static final int PREPARED = 8;
    public static final int ERROR = 9;
    public static final int INFO = 10;
    private static final int MAX_HISTORY_SIZE = 100;
    
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mMediaMountedCount = 0;
    private long [] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private Cursor mCursor;
    private int mPlayPos = -1;
    private int mNextPlayPos = -1;
    private static final String LOGTAG = "MediaPlaybackService";
    private final Shuffler mRand = new Shuffler();
    private int mOpenFailedCounter = 0;
    String[] mCursorCols = new String[] {
            Media.MediaColumns._ID,             // index must match IDCOLIDX below
            Media.MediaColumns.URI,
            Media.MediaColumns.TITLE,
            Media.MediaColumns.ALBUM,
            Media.MediaColumns.ARTIST,
            Media.MediaColumns.DURATION
    };
    private final static int IDCOLIDX = 0;
    private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private AudioManager mAudioManager;
    private boolean mQueueIsSaveable = true;
    // used to track what type of audio focus loss caused the playback to pause
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean mPausedByConnectivityReceiver = false;

    private SharedPreferences mPreferences;
    // We use this to distinguish between different cards when saving/restoring playlists.
    // This will have to change if we want to support multiple simultaneous cards.
    private int mCardId;    

    private AppWidgetOneProvider mAppWidgetProvider = AppWidgetOneProvider.getInstance();
    
    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 60000;

    // our RemoteControlClient object, which will use remote control APIs available in
    // SDK level >= 14, if they're available.
    private RemoteControlClientCompat mRemoteControlClientCompat;
    
    // The component name of MusicIntentReceiver, for use with media button and remote control APIs
    private ComponentName mMediaButtonReceiverComponent;

    private ConnectivityReceiver mConnectivityManager;
    private boolean mRetrieveShoutCastMetadata = false;
    private ShoutCastRetrieverTask mShoutCastRetrieverTask;
    private MetadataRetrieverTask mMetadataRetrieverTask;
    
    private DatabaseImageResizer mNotificationImageFetcher;
    private DatabaseImageResizer mLockScreenImageFetcher;
    
    @SuppressLint("HandlerLeak")
    private Handler mMediaplayerHandler = new Handler() {
        float mCurrentVolume = 1.0f;
        @Override
        public void handleMessage(Message msg) {
            MusicUtils.debugLog("mMediaplayerHandler.handleMessage " + msg.what);
            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case SERVER_DIED:
                    if (mIsSupposedToBePlaying) {
                        gotoNext(true);
                    } else {
                        // the server died when we were idle, so just
                        // reopen the same song (it will start again
                        // from the beginning though when the user
                        // restarts)
                        openCurrentAndNext();
                    }
                    break;
                case TRACK_WENT_TO_NEXT:
                    mPlayPos = mNextPlayPos;
                    if (mCursor != null) {
                        mCursor.close();
                        mCursor = null;
                    }
                    mCursor = getCursorForId(mPlayList[mPlayPos]);
                    notifyChange(META_CHANGED);
                    updateNotification(false);
                    setNextTrack();
                    break;
                case TRACK_ENDED:
                    if (mRepeatMode == REPEAT_CURRENT) {
                        seek(0);
                        play();
                    } else {
                        gotoNext(false);
                    }
                    break;
                case PREPARED:
                    Intent i = new Intent("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION");
                    i.putExtra("android.media.extra.AUDIO_SESSION", getAudioSessionId());
                    i.putExtra("android.media.extra.PACKAGE_NAME", getPackageName());
                    sendBroadcast(i);
                	removeStickyBroadcast(new Intent(START_DIALOG));
                    sendBroadcast(new Intent(STOP_DIALOG));
                    play();
                    notifyChange(META_CHANGED);
                    notifyChange(PLAYBACK_STARTED);
                    
            		if (mRetrieveShoutCastMetadata) {
            			if (mShoutCastRetrieverTask != null) {
            				mShoutCastRetrieverTask.stop();
            				mShoutCastRetrieverTask = null;
            			}
            			
            			mShoutCastRetrieverTask = new ShoutCastRetrieverTask(MediaPlaybackService.this, mPlayList[mPlayPos]);
            			mShoutCastRetrieverTask.start();
            		}
            		break;
                case ERROR:
                	int what = msg.arg1;
            		if (what == SERVER_DIED) {
            			if (mIsSupposedToBePlaying) {
            				gotoNext(true);
                        } else {
                        	// the server died when we were idle, so just
                            // reopen the same song (it will start again
                            // from the beginning though when the user
                            // restarts)
                            openCurrentAndNext();
                        }
            		} else {
            			handleError();
            		}
            		break;
                case INFO:
                   	notifyChange(META_CHANGED);
                	break;
                case RELEASE_WAKELOCK:
                    mWakeLock.release();
                    break;

                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with the code that
                    // handles fade-in
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = false;
                            }
                            pause(true);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            mMediaplayerHandler.removeMessages(FADEUP);
                            mMediaplayerHandler.sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = true;
                            }
                            pause(true);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            if(!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                mPlayer.setVolume(mCurrentVolume);
                                play(); // also queues a fade-in
                            } else {
                                mMediaplayerHandler.removeMessages(FADEDOWN);
                                mMediaplayerHandler.sendEmptyMessage(FADEUP);
                            }
                            break;
                        default:
                            Log.e(LOGTAG, "Unknown audio focus change code");
                    }
                    break;

                default:
                    break;
            }
        }
    };
    
    @SuppressLint("HandlerLeak")
    private Handler mInitialMediaplayerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            MusicUtils.debugLog("mInitialMediaplayerHandler.handleMessage " + msg.what);
        	mPlayer.setHandler(mMediaplayerHandler);
            switch (msg.what) {
                case PREPARED:
                    Intent i = new Intent("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION");
                    i.putExtra("android.media.extra.AUDIO_SESSION", getAudioSessionId());
                    i.putExtra("android.media.extra.PACKAGE_NAME", getPackageName());
                    sendBroadcast(i);
            		break;
                case ERROR:
            		break;

                default:
                    break;
            }
        }
    };
    
	/* (non-Javadoc)
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
  	    if (key.equals(PreferenceConstants.WIFI_LOCK)) {
			if (sharedPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true)) {
				final boolean lockingWifi = mPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true);
				mConnectivityManager.setWantWifiLock(lockingWifi);
  	        }
  	    } else if (key.equals(PreferenceConstants.RETRIEVE_SHOUTCAST_METADATA)) {
  	    	mRetrieveShoutCastMetadata = sharedPreferences.getBoolean(PreferenceConstants.RETRIEVE_SHOUTCAST_METADATA, false);
  	    	
  	    	if (mShoutCastRetrieverTask != null) {
  	    		mShoutCastRetrieverTask.stop();
  	    		mShoutCastRetrieverTask = null;
  	    	}
  	    	if (mRetrieveShoutCastMetadata &&
  	    			mPlayList != null && mPlayList.length > 0) {
  	    		mShoutCastRetrieverTask = new ShoutCastRetrieverTask(this, mPlayList[mPlayPos]);
  	    		mShoutCastRetrieverTask.start();
  	    	}
  	    } else if (key.equals(PreferenceConstants.RETRIEVE_METADATA)) {
  	    	if (mMetadataRetrieverTask != null &&
  	    			mMetadataRetrieverTask.getStatus() != AsyncTask.Status.FINISHED) {
  	    		mMetadataRetrieverTask.cancel();
  	    		mMetadataRetrieverTask = null;
  	    	}
  	    	if (sharedPreferences.getBoolean(PreferenceConstants.RETRIEVE_METADATA, false) &&
  	    			mPlayList != null && mPlayList.length > 0) {
  	    		mMetadataRetrieverTask = new MetadataRetrieverTask(this, mPlayList);
  	    		mMetadataRetrieverTask.execute();
  	    	}
  	    }
  	}
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                prev();
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
            	if (intent.getBooleanExtra("from_connectivity_receiver", false) && !mPausedByConnectivityReceiver) {
            		return;
            	}
            	
                if (isPlaying()) {
                    pause(true);
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
            	boolean wasPlaying = mIsSupposedToBePlaying;
            	
                pause(true);
                mPausedByTransientLossOfFocus = false;
                
                if (wasPlaying != mIsSupposedToBePlaying) {
                	mPausedByConnectivityReceiver = intent.getBooleanExtra("from_connectivity_receiver", false);
                }
            } else if (CMDPLAY.equals(cmd)) {
                play();
            } else if (CMDSTOP.equals(cmd)) {
                pause(true);
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (AppWidgetOneProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
                // Someone asked us to refresh a set of specific widgets, probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                //TODO Fix this!
                mAppWidgetProvider.performUpdate(MediaPlaybackService.this, appWidgetIds, "");
            }
        }
    };

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    public MediaPlaybackService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        int imageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_notification_size);
        
        ImageCache.ImageCacheParams cacheParams =
    			new ImageCache.ImageCacheParams(this, NOTIFICATION_IMAGE_CACHE_DIR);

    	cacheParams.setMemCacheSizePercent(0.25f); // Set memory cache to 25% of app memory
        
    	mNotificationImageFetcher = new DatabaseImageResizer(this, imageThumbSize);
    	mNotificationImageFetcher.addImageCache(cacheParams);
        
        cacheParams =
    			new ImageCache.ImageCacheParams(this, LOCK_SCREEN_IMAGE_CACHE_DIR);

    	cacheParams.setMemCacheSizePercent(0.25f); // Set memory cache to 25% of app memory
    	
    	mLockScreenImageFetcher = new DatabaseImageResizer(this, 600);
    	mLockScreenImageFetcher.addImageCache(cacheParams);
    	
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiverComponent = new ComponentName(this, MediaButtonIntentReceiver.class);
        mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiverComponent);
        
        // Use the remote control APIs (if available) to set the playback state
        if (mRemoteControlClientCompat == null) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            intent.setComponent(mMediaButtonReceiverComponent);
            mRemoteControlClientCompat = new RemoteControlClientCompat(
                    PendingIntent.getBroadcast(this /*context*/,
                            0 /*requestCode, ignored*/, intent /*intent*/, 0 /*flags*/));
            RemoteControlHelper.registerRemoteControlClient(mAudioManager,
                    mRemoteControlClientCompat);
        }
        
        mRemoteControlClientCompat.setTransportControlFlags(
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_PREVIOUS |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_PLAY |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_PAUSE |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_NEXT |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_STOP);
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        
		final boolean lockingWifi = mPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true);
		mConnectivityManager = new ConnectivityReceiver(this, lockingWifi);
		
		mRetrieveShoutCastMetadata = mPreferences.getBoolean(PreferenceConstants.RETRIEVE_SHOUTCAST_METADATA, false);
		
        mMediaButtonReceiverComponent = new ComponentName(this, MediaButtonIntentReceiver.class);
		
        mCardId = MusicUtils.getCardId(this);
        
        registerExternalStorageListener();

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer(this);
        mPlayer.setHandler(mMediaplayerHandler);

        reloadQueue();
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);
        
        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }

    @Override
    public void onDestroy() {
        // Check that we're not being destroyed while something is still playing.
        if (isPlaying()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }
        
        mAppWidgetProvider.notifyChange(this, PLAYER_CLOSED);
        
        mConnectivityManager.cleanup();
        
		if (mMetadataRetrieverTask != null &&
	    		mMetadataRetrieverTask.getStatus() != AsyncTask.Status.FINISHED) {
	    	mMetadataRetrieverTask.cancel();
	    	mMetadataRetrieverTask = null;
	    }
        
        // release all MediaPlayer resources, including the native player and wakelocks
        Intent i = new Intent("android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION");
        i.putExtra("android.media.extra.AUDIO_SESSION", getAudioSessionId());
        i.putExtra("android.media.extra.PACKAGE_NAME", getPackageName());
        sendBroadcast(i);
        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        RemoteControlHelper.unregisterRemoteControlClient(mAudioManager,
                mRemoteControlClientCompat);
        
        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }

        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
    	Utils.deleteAllFiles();
        
        mNotificationImageFetcher.closeCache();
        mLockScreenImageFetcher.closeCache();
        
        super.onDestroy();
    }
    
    private final char hexdigits [] = new char [] {
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'
    };

    private void saveQueue(boolean full) {
        if (!mQueueIsSaveable) {
            return;
        }

        Editor ed = mPreferences.edit();
        //long start = System.currentTimeMillis();
        if (full) {
            StringBuilder q = new StringBuilder();
            
            // The current playlist is saved as a list of "reverse hexadecimal"
            // numbers, which we can generate faster than normal decimal or
            // hexadecimal numbers, which in turn allows us to save the playlist
            // more often without worrying too much about performance.
            // (saving the full state takes about 40 ms under no-load conditions
            // on the phone)
            int len = mPlayListLen;
            for (int i = 0; i < len; i++) {
                long n = mPlayList[i];
                if (n < 0) {
                    continue;
                } else if (n == 0) {
                    q.append("0;");
                } else {
                    while (n != 0) {
                        int digit = (int)(n & 0xf);
                        n >>>= 4;
                        q.append(hexdigits[digit]);
                    }
                    q.append(";");
                }
            }
            //Log.i("@@@@ service", "created queue string in " + (System.currentTimeMillis() - start) + " ms");
            ed.putString("queue", q.toString());
            ed.putInt("cardid", mCardId);
            if (mShuffleMode != SHUFFLE_NONE) {
                // In shuffle mode we need to save the history too
                len = mHistory.size();
                q.setLength(0);
                for (int i = 0; i < len; i++) {
                    int n = mHistory.get(i);
                    if (n == 0) {
                        q.append("0;");
                    } else {
                        while (n != 0) {
                            int digit = (n & 0xf);
                            n >>>= 4;
                            q.append(hexdigits[digit]);
                        }
                        q.append(";");
                    }
                }
                ed.putString("history", q.toString());
            }
        }
        ed.putInt("curpos", mPlayPos);
        if (mPlayer.isInitialized()) {
            ed.putLong("seekpos", mPlayer.position());
        }
        ed.putInt("repeatmode", mRepeatMode);
        ed.putInt("shufflemode", mShuffleMode);
        ed.commit();

        //Log.i("@@@@ service", "saved state in " + (System.currentTimeMillis() - start) + " ms");
    }

    private void reloadQueue() {
        String q = null;
        
        boolean newstyle = false;
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            newstyle = true;
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
            q = mPreferences.getString("queue", "");
        }
        int qlen = q != null ? q.length() : 0;
        if (qlen > 1) {
            //Log.i("@@@@ service", "loaded queue: " + q);
            int plen = 0;
            int n = 0;
            int shift = 0;
            for (int i = 0; i < qlen; i++) {
                char c = q.charAt(i);
                if (c == ';') {
                    ensurePlayListCapacity(plen + 1);
                    mPlayList[plen] = n;
                    plen++;
                    n = 0;
                    shift = 0;
                } else {
                    if (c >= '0' && c <= '9') {
                        n += ((c - '0') << shift);
                    } else if (c >= 'a' && c <= 'f') {
                        n += ((10 + c - 'a') << shift);
                    } else {
                        // bogus playlist data
                        plen = 0;
                        break;
                    }
                    shift += 4;
                }
            }
            mPlayListLen = plen;

            int pos = mPreferences.getInt("curpos", 0);
            if (pos < 0 || pos >= mPlayListLen) {
                // The saved playlist is bogus, discard it
                mPlayListLen = 0;
                return;
            }
            mPlayPos = pos;
            
            // When reloadQueue is called in response to a card-insertion,
            // we might not be able to query the media provider right away.
            // To deal with this, try querying for the current file, and if
            // that fails, wait a while and try again. If that too fails,
            // assume there is a problem and don't restore the state.
            Cursor crsr = MusicUtils.query(this,
            			Media.MediaColumns.CONTENT_URI,
                        new String [] {"_id"}, "_id=" + mPlayList[mPlayPos] , null, null);
            if (crsr == null || crsr.getCount() == 0) {
                // wait a bit and try again
                SystemClock.sleep(3000);
                crsr = getContentResolver().query(
                		Media.MediaColumns.CONTENT_URI,
                        mCursorCols, "_id=" + mPlayList[mPlayPos] , null, null);
            }
            if (crsr != null) {
                crsr.close();
            }

            // Make sure we don't auto-skip to the next song, since that
            // also starts playback. What could happen in that case is:
            // - music is paused
            // - go to UMS and delete some files, including the currently playing one
            // - come back from UMS
            // (time passes)
            // - music app is killed for some reason (out of memory)
            // - music service is restarted, service restores state, doesn't find
            //   the "current" file, goes to the next and: playback starts on its
            //   own, potentially at some random inconvenient time.
            mOpenFailedCounter = 20;
            mQuietMode = true;
            //openCurrentAndNext();
            mQuietMode = false;
            /*if (!mPlayer.isInitialized()) {
                // couldn't restore the saved state
                mPlayListLen = 0;
                return;
            }*/
            
            long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);
            Log.d(LOGTAG, "restored queue, currently at position "
                    + position() + "/" + duration()
                    + " (requested " + seekpos + ")");
            
            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_ON) {
                shufmode = SHUFFLE_NONE;
            }
            if (shufmode != SHUFFLE_NONE) {
                // in shuffle mode we need to restore the history too
                q = mPreferences.getString("history", "");
                qlen = q != null ? q.length() : 0;
                if (qlen > 1) {
                    plen = 0;
                    n = 0;
                    shift = 0;
                    mHistory.clear();
                    for (int i = 0; i < qlen; i++) {
                        char c = q.charAt(i);
                        if (c == ';') {
                            if (n >= mPlayListLen) {
                                // bogus history data
                                mHistory.clear();
                                break;
                            }
                            mHistory.add(n);
                            n = 0;
                            shift = 0;
                        } else {
                            if (c >= '0' && c <= '9') {
                                n += ((c - '0') << shift);
                            } else if (c >= 'a' && c <= 'f') {
                                n += ((10 + c - 'a') << shift);
                            } else {
                                // bogus history data
                                mHistory.clear();
                                break;
                            }
                            shift += 4;
                        }
                    }
                }
            }
            /*if (shufmode == SHUFFLE_AUTO) {
                if (! makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }*/
            mShuffleMode = shufmode;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        if (intent != null) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");

            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                boolean remove_status_icon =  (intent.getIntExtra(CMDNOTIF, 0) != 2);
                if (isPlaying()) {
                    pause(remove_status_icon);
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
                
                if (!remove_status_icon) {
                	updateNotification(true);
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause(true);
                mPausedByTransientLossOfFocus = false;
            } else if (CMDPLAY.equals(cmd)) {
                play();
            } else if (CMDSTOP.equals(cmd)) {
                pause(true);
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (BLUETOOTH_DEVICE_PAIRED.equals(action)) {
        		Uri uri = TransportFactory.getUri(intent.getStringExtra("uri"));

        		if (uri != null) {
        			UriBean uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
        			
        			AbsTransport transport = TransportFactory.getTransport(uriBean.getProtocol());
        			transport.setUri(uriBean);
        	   
        			new DetermineActionTask(this, uriBean, this).execute();
        		}
            }
        }
        
        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        return START_STICKY;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        mServiceInUse = false;

        // Take a snapshot of the current playlist
        saveQueue(true);
        
        if (isPlaying() || mPausedByTransientLossOfFocus) {
            // something is currently playing, or will be playing once 
            // an in-progress action requesting audio focus ends, so don't stop the service now.
            return true;
        }
        
        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        // Also delay stopping the service if we're transitioning between tracks.
        if (mPlayListLen > 0  || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }
        
        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }
    
    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mPausedByTransientLossOfFocus || mServiceInUse
                    || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    };

    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     *
     * @param storagePath path to mount point for the removed media
     */
    public void closeExternalStorageFiles(String storagePath) {
        // stop playback and clean up if the SD card is going to be unmounted.
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        saveQueue(true);
                        mQueueIsSaveable = false;
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mMediaMountedCount++;
                        mCardId = MusicUtils.getCardId(MediaPlaybackService.this);
                        reloadQueue();
                        mQueueIsSaveable = true;
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    /**
     * Notify the change-receivers that something has changed.
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Integer: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "track" - String: the name of the track
     * The intent has an action that is one of
     * "com.android.music.metachanged"
     * "com.android.music.queuechanged",
     * "com.android.music.playbackcomplete"
     * "com.android.music.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
    private void notifyChange(String what) {

        Intent i = new Intent(what);
        i.putExtra("id", Long.valueOf(getAudioId()));
        i.putExtra("artist", getArtistName());
        i.putExtra("album",getAlbumName());
        i.putExtra("track", getTrackName());
        i.putExtra("playing", isPlaying());
        sendStickyBroadcast(i);
        
        bluetoothNotifyChange(what);

        if (what.equals(PLAYSTATE_CHANGED)) {
            mRemoteControlClientCompat.setPlaybackState(isPlaying() ?
            		RemoteControlClientCompat.PLAYSTATE_PLAYING : RemoteControlClientCompat.PLAYSTATE_PAUSED);
            
            if (isPlaying() && mRetrieveShoutCastMetadata) {
    			mShoutCastRetrieverTask = new ShoutCastRetrieverTask(MediaPlaybackService.this, mPlayList[mPlayPos]);
    			mShoutCastRetrieverTask.start();
            } else {
                if (mShoutCastRetrieverTask != null) {
                	mShoutCastRetrieverTask.stop();
                	mShoutCastRetrieverTask = null;
                }
            }
        } else if (what.equals(META_CHANGED)) {
            // Update the remote controls
            MetadataEditorCompat metadataEditor = mRemoteControlClientCompat.editMetadata(true);
            metadataEditor.putString(2, getArtistName());
            metadataEditor.putString(1, getAlbumName());
            metadataEditor.putString(7, getTrackName());
            metadataEditor.putLong(9, getDuration());
            if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
            	metadataEditor.putBitmap(100, getAlbumArt(false));
            }
            metadataEditor.apply();
        }

        if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
        } else {
            saveQueue(false);
        }

        // Share this notification directly with our widgets
        mAppWidgetProvider.notifyChange(this, what);
    }

    private void bluetoothNotifyChange(String what) {
    	if (!mPreferences.getBoolean(PreferenceConstants.SEND_BLUETOOTH_METADATA, true)) {
    		return;
    	}
    	
    	Intent i = null;
    	
        if (what.equals(PLAYSTATE_CHANGED)) {
        	i = new Intent(AVRCP_PLAYSTATE_CHANGED);
        } else if (what.equals(META_CHANGED)) {
        	i = new Intent(AVRCP_META_CHANGED);
        } else {
        	return;
        }
        
        i.putExtra("id", Long.valueOf(getAudioId()));
        i.putExtra("artist", getArtistName());
        i.putExtra("album", getAlbumName());
        i.putExtra("track", getTrackName());
        i.putExtra("playing", isPlaying());        
        i.putExtra("ListSize", getQueue());
		i.putExtra("duration", duration());
		i.putExtra("position", position());
        sendBroadcast(i);
    }
    
    private void ensurePlayListCapacity(int size) {
        if (mPlayList == null || size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            long [] newlist = new long[size * 2];
            int len = mPlayList != null ? mPlayList.length : mPlayListLen;
            for (int i = 0; i < len; i++) {
                newlist[i] = mPlayList[i];
            }
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
    }
    
    // insert the list of songs at the specified position in the playlist
    private void addToPlayList(long [] list, int position) {
        int addlen = list.length;
        if (position < 0) { // overwrite
            mPlayListLen = 0;
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + addlen);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }
        
        // move part of list after insertion point
        int tailsize = mPlayListLen - position;
        for (int i = tailsize ; i > 0 ; i--) {
            mPlayList[position + i] = mPlayList[position + i - addlen]; 
        }
        
        // copy list into playlist
        for (int i = 0; i < addlen; i++) {
            mPlayList[position + i] = list[i];
        }
        mPlayListLen += addlen;
        if (mPlayListLen == 0) {
            mCursor.close();
            mCursor = null;
            notifyChange(META_CHANGED);
        } else {
        	long [] newlist = list;
    		if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_METADATA, false)) {
    			if (mMetadataRetrieverTask != null &&
      	    			mMetadataRetrieverTask.getStatus() != AsyncTask.Status.FINISHED) {
      	    		mMetadataRetrieverTask.cancel();
      	    		mMetadataRetrieverTask = null;
      	    		newlist = mPlayList;
      	    	}
      	    	if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_METADATA, true)) {
      	    		mMetadataRetrieverTask = new MetadataRetrieverTask(this, newlist);
      	    		mMetadataRetrieverTask.execute();
      	    	}
        	}
        }
    }
    
    /**
     * Appends a list of tracks to the current playlist.
     * If nothing is playing currently, playback will be started at
     * the first track.
     * If the action is NOW, playback will switch to the first of
     * the new tracks immediately.
     * @param list The list of tracks to append.
     * @param action NOW, NEXT or LAST
     */
    public void enqueue(long [] list, int action) {
        synchronized(this) {
            if (action == NEXT && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                notifyChange(QUEUE_CHANGED);
            } else {
                // action == LAST || action == NOW || mPlayPos + 1 == mPlayListLen
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.length;
                    openCurrentAndNext();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrentAndNext();
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Replaces the current playlist with a new list,
     * and prepares for starting playback at the specified
     * position in the list, or a random position if the
     * specified position is 0.
     * @param list The new list of tracks.
     */
    public void open(long [] list, int position) {
        synchronized (this) {
            long oldId = getAudioId();
            int listlength = list.length;
            boolean newlist = true;
            if (mPlayListLen == listlength) {
                // possible fast path: list might be the same
                newlist = false;
                for (int i = 0; i < listlength; i++) {
                    if (list[i] != mPlayList[i]) {
                        newlist = true;
                        break;
                    }
                }
            }
            if (newlist) {
                addToPlayList(list, -1);
                notifyChange(QUEUE_CHANGED);
            }
            int oldpos = mPlayPos;
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = mRand.nextInt(mPlayListLen);
            }
            mHistory.clear();

            openCurrentAndNext();
            if (oldId != getAudioId()) {
                notifyChange(META_CHANGED);
            }
        }
    }
    
    /**
     * Moves the item at index1 to index2.
     * @param index1
     * @param index2
     */
    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= mPlayListLen) {
                index1 = mPlayListLen - 1;
            }
            if (index2 >= mPlayListLen) {
                index2 = mPlayListLen - 1;
            }
            if (index1 < index2) {
                long tmp = mPlayList[index1];
                for (int i = index1; i < index2; i++) {
                    mPlayList[i] = mPlayList[i+1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                        mPlayPos--;
                }
            } else if (index2 < index1) {
                long tmp = mPlayList[index1];
                for (int i = index1; i > index2; i--) {
                    mPlayList[i] = mPlayList[i-1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                        mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Returns the current play list
     * @return An array of integers containing the IDs of the tracks in the play list
     */
    public long [] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            long [] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
    }

    private Cursor getCursorForId(long lid) {
        String id = String.valueOf(lid);

        Cursor c = getContentResolver().query(
                Media.MediaColumns.CONTENT_URI,
                mCursorCols, "_id=" + id , null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    private void openCurrentAndNext() {
        synchronized (this) {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }

            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            // TODO is there a better place for this?
            //MusicUtils.clearAlbumArtCache();
            
            mCursor = getCursorForId(mPlayList[mPlayPos]);
            /*while(true) {
                if (mCursor != null && mCursor.getCount() != 0 &&
                		open(mCursor.getString(mCursor.getColumnIndex(Media.MediaColumns.URI)))) {
                    break;
                }
                // if we get here then opening the file failed. We can close the cursor now, because
                // we're either going to create a new one next, or stop trying
                if (mCursor != null) {
                    mCursor.close();
                    mCursor = null;
                }
                if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
                    int pos = getNextPosition(false);
                    if (pos < 0) {
                        gotoIdleState();
                        if (mIsSupposedToBePlaying) {
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                        }
                        return;
                    }
                    mPlayPos = pos;
                    stop(false);
                    mPlayPos = pos;
                    mCursor = getCursorForId(mPlayList[mPlayPos]);
                } else {
                    mOpenFailedCounter = 0;
                    if (!mQuietMode) {
                        Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                    }
                    Log.d(LOGTAG, "Failed to open file for playback");
                    gotoIdleState();
                    if (mIsSupposedToBePlaying) {
                        mIsSupposedToBePlaying = false;
                        notifyChange(PLAYSTATE_CHANGED);
                    }
                    return;
                }
            }

            setNextTrack();
        }*/
        
            if (mCursor != null && mCursor.getCount() != 0) {
            	open(mCursor.getString(mCursor.getColumnIndex(Media.MediaColumns.URI)));
            	setNextTrack();
            }
        }
    }

    private void setNextTrack() {
        mNextPlayPos = getNextPosition(false);
        if (mNextPlayPos >= 0) {
            Cursor cursor = getCursorForId(mPlayList[mNextPlayPos]);
            mPlayer.setNextDataSource(cursor.getString(cursor.getColumnIndex(Media.MediaColumns.URI)));
            cursor.close();
        }
    }

    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     */
    public void open(String path) {
        synchronized (this) {
            if (path == null) {
                return;
            }
            
            // if mCursor is null, try to associate path with a database cursor
            if (mCursor == null) {

                ContentResolver resolver = getContentResolver();
                Uri uri;
                String where;
                String selectionArgs[];
                if (path.startsWith("content://media/")) {
                    uri = Uri.parse(path);
                    where = null;
                    selectionArgs = null;
                } else {
                   uri = MediaStore.Audio.Media.getContentUriForPath(path);
                   where = MediaStore.Audio.Media.DATA + "=?";
                   selectionArgs = new String[] { path };
                }
                
                try {
                    mCursor = resolver.query(uri, mCursorCols, where, selectionArgs, null);
                    if  (mCursor != null) {
                        if (mCursor.getCount() == 0) {
                            mCursor.close();
                            mCursor = null;
                        } else {
                            mCursor.moveToNext();
                            ensurePlayListCapacity(1);
                            mPlayListLen = 1;
                            mPlayList[0] = mCursor.getLong(IDCOLIDX);
                            mPlayPos = 0;
                        }
                    }
                } catch (UnsupportedOperationException ex) {
                }
            }
            
            mFileToPlay = path;
            
            Log.i(LOGTAG, "Opening: " + mFileToPlay);
            
            sendStickyBroadcast(new Intent(START_DIALOG));
            
            boolean isLocalFile = mPreferences.getBoolean(PreferenceConstants.PROGRESSIVE_DOWNLOAD, false);
            boolean useFFmpegPlayer = mPreferences.getBoolean(PreferenceConstants.USE_FFMPEG_PLAYER, false);   	
            if (isLocalFile) {
            	mPlayer.setDataSource(this, mPlayList[mPlayPos]);
            } else {
            	mPlayer.setDataSource(mFileToPlay, useFFmpegPlayer);
            }
        }
    }

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        MediaButtonHelper.registerMediaButtonEventReceiverCompat(
                mAudioManager, mMediaButtonReceiverComponent);

        if (mPlayer.isInitialized()) {
            // if we are at the end of the song, go to the next song first
            /*long duration = mPlayer.duration();
            if (mRepeatMode != REPEAT_CURRENT && duration > 2000 &&
                mPlayer.position() >= duration - 2000) {
                gotoNext(true);
            }*/

            mPlayer.start();
            // make sure we fade in, in case a previous fadein was stopped because
            // of another focus loss
            mMediaplayerHandler.removeMessages(FADEDOWN);
            mMediaplayerHandler.sendEmptyMessage(FADEUP);

            updateNotification(false);
            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }

        } else {
        	openCurrentAndNext();
        }
    }

    private void updateNotification(boolean updateNotification) {
        String contentText;
    	
    	String trackName = getTrackName();
    	if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
    		trackName = getMediaUri();
    	}
    	
        String artist = getArtistName();
    	if (artist == null || artist.equals(Media.UNKNOWN_STRING)) {
    		artist = getString(R.string.unknown_artist_name);
    	}
        
    	String album = getAlbumName();
        if (album == null || album.equals(Media.UNKNOWN_STRING)) {
            contentText = getString(R.string.notification_alt_info, artist);
        } else {
        	contentText = getString(R.string.notification_artist_album, artist, album);
        }
        
      	TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
    	stackBuilder.addNextIntentWithParentStack(new Intent(this, MediaPlayerActivity.class)
        	.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    	PendingIntent contentIntent = stackBuilder.getPendingIntent((int) System.currentTimeMillis(), 0);
        
        NotificationCompat.Builder status = new NotificationCompat.Builder(this)
        		.setContentTitle(trackName)
        		.setContentText(contentText)
                .setContentIntent(contentIntent)
                .setWhen(0);
		
        int trackId = getTrackId();
	    if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false) && trackId != -1) {
	    	Bitmap bitmap = getAlbumArt(true);
	    	if (bitmap != null) {
	        	status.setLargeIcon(bitmap);
	    	}
	    }
	    
    	status.setSmallIcon(R.drawable.notification_icon);
        
	    Notification notification = status.build();
	    
	    // If the user has a phone running Android 4.0+ show an expanded notification
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
	    	notification = buildExpandedView(notification, updateNotification);
	    }
	    
		if (!updateNotification) {
    		startForeground(PLAYBACKSERVICE_STATUS, notification);
    	} else {
    		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    		notificationManager.notify(PLAYBACKSERVICE_STATUS, notification);
    	}
    }
    
    @SuppressLint("NewApi")
	private Notification buildExpandedView(Notification notification, boolean updateNotification) {
    	RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_small);
    	RemoteViews expandedContentView = new RemoteViews(getPackageName(), R.layout.notification_expanded);
    	setupContentView(contentView, updateNotification);
    	setupExpandedContentView(expandedContentView, updateNotification);
    	
        notification.contentView = contentView;
        notification.bigContentView = expandedContentView;
        return notification;
    }
    
    private void setupContentView(RemoteViews rv, boolean updateNotification) {
    	String contentText;
     	
     	String trackName = getTrackName();
     	if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
     		trackName = getMediaUri();
     	}
     	
        String artist = getArtistName();
     	if (artist == null || artist.equals(Media.UNKNOWN_STRING)) {
     		artist = getString(R.string.unknown_artist_name);
     	}
         
     	String album = getAlbumName();
        if (album == null || album.equals(Media.UNKNOWN_STRING)) {
        	contentText = getString(R.string.notification_alt_info, artist);
        } else {
        	contentText = getString(R.string.notification_artist_album, artist, album);
        }
        
        if (updateNotification) {
        	if (isPlaying()) {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        	} else {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_play);
        	}
        } else {
        	rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        }
    	
        int trackId = getTrackId();
	    if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false) && trackId != -1) {
	    	Bitmap bitmap = getAlbumArt(true);
	    	if (bitmap != null) {
	    		rv.setImageViewBitmap(R.id.coverart, bitmap);
	    	}
	    }
        
    	// set the text for the notifications
    	rv.setTextViewText(R.id.title, trackName);
    	rv.setTextViewText(R.id.subtitle, contentText);

    	rv.setOnClickPendingIntent(R.id.play_pause, createPendingIntent(2, CMDTOGGLEPAUSE));
    	rv.setOnClickPendingIntent(R.id.next, createPendingIntent(3, CMDNEXT));
    	rv.setOnClickPendingIntent(R.id.close, createPendingIntent(4, CMDSTOP));
    }
    
    private void setupExpandedContentView(RemoteViews rv, boolean updateNotification) {
    	String trackName = getTrackName();
    	if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
    			trackName = getMediaUri();
    	}
    	
        String artist = getArtistName();
    	if (artist == null || artist.equals(Media.UNKNOWN_STRING)) {
    		artist = getString(R.string.unknown_artist_name);
    	}
        
    	String album = getAlbumName();
        
        if (updateNotification) {
        	if (isPlaying()) {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        	} else {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_play);
        	}
        } else {
        	rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        }
    	
        int trackId = getTrackId();
	    if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false) && trackId != -1) {
	    	Bitmap bitmap = getAlbumArt(true);
	    	if (bitmap != null) {
	    		rv.setImageViewBitmap(R.id.coverart, bitmap);
	    	}
	    }
        
    	// set the text for the notifications
    	rv.setTextViewText(R.id.firstLine, trackName);
    	rv.setTextViewText(R.id.secondLine, album);
    	rv.setTextViewText(R.id.thirdLine, artist);

    	rv.setOnClickPendingIntent(R.id.prev, createPendingIntent(1, CMDPREVIOUS));
    	rv.setOnClickPendingIntent(R.id.play_pause, createPendingIntent(2, CMDTOGGLEPAUSE));
    	rv.setOnClickPendingIntent(R.id.next, createPendingIntent(3, CMDNEXT));
    	rv.setOnClickPendingIntent(R.id.close, createPendingIntent(4, CMDSTOP));
    }
    
    private PendingIntent createPendingIntent(int requestCode, String command) {
        Intent intent = new Intent(this, MediaPlaybackService.class);
        intent.setAction(MediaPlaybackService.SERVICECMD);
        intent.putExtra(MediaPlaybackService.CMDNOTIF, requestCode);
        intent.putExtra(MediaPlaybackService.CMDNAME, command);
		return PendingIntent.getService(this, requestCode, intent, 0);
    }
    
    private void stop(boolean remove_status_icon) {
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        if (remove_status_icon) {
            gotoIdleState();
        } else {
            stopForeground(false);
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
        }
    }

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Pauses playback (call play() to resume)
     */
    public void pause(boolean remove_status_icon) {
        synchronized(this) {
            mMediaplayerHandler.removeMessages(FADEUP);
            if (isPlaying()) {
                mPlayer.pause();
                if (remove_status_icon) {
                    gotoIdleState();
                } else {
                    stopForeground(false);
                }
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            }
            mPausedByConnectivityReceiver = false;
        }
    }

    /** Returns whether something is currently playing
     *
     * @return true if something is playing (or will be playing shortly, in case
     * we're currently transitioning between tracks), false if not.
     */
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    /*
      Desired behavior for prev/next/shuffle:

      - NEXT will move to the next track in the list when not shuffling, and to
        a track randomly picked from the not-yet-played tracks when shuffling.
        If all tracks have already been played, pick from the full set, but
        avoid picking the previously played track if possible.
      - when shuffling, PREV will go to the previously played track. Hitting PREV
        again will go to the track played before that, etc. When the start of the
        history has been reached, PREV is a no-op.
        When not shuffling, PREV will go to the sequentially previous track (the
        difference with the shuffle-case is mainly that when not shuffling, the
        user can back up to tracks that are not in the history).

        Example:
        When playing an album with 10 tracks from the start, and enabling shuffle
        while playing track 5, the remaining tracks (6-10) will be shuffled, e.g.
        the final play order might be 1-2-3-4-5-8-10-6-9-7.
        When hitting 'prev' 8 times while playing track 7 in this example, the
        user will go to tracks 9-6-10-8-5-4-3-2. If the user then hits 'next',
        a random track will be picked again. If at any time user disables shuffling
        the next/previous track will be picked in sequential order again.
     */

    public void prev() {
        synchronized (this) {
            if (mPlayListLen <= 0) {
                Log.d(LOGTAG, "No play queue");
                return;
            }
        	
            if (mShuffleMode == SHUFFLE_ON) {
                // go to previously-played track and remove it from the history
                int histsize = mHistory.size();
                if (histsize == 0) {
                    // prev is a no-op
                    return;
                }
                Integer pos = mHistory.remove(histsize - 1);
                mPlayPos = pos.intValue();
            } else {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = mPlayListLen - 1;
                }
            }
            stop(false);
            openCurrentAndNext();
            notifyChange(META_CHANGED);
        }
    }

    /**
     * Get the next position to play. Note that this may actually modify mPlayPos
     * if playback is in SHUFFLE_AUTO mode and the shuffle list window needed to
     * be adjusted. Either way, the return value is the next value that should be
     * assigned to mPlayPos;
     */
    private int getNextPosition(boolean force) {
        if (mRepeatMode == REPEAT_CURRENT) {
            if (mPlayPos < 0) return 0;
            return mPlayPos;
        } else if (mShuffleMode == SHUFFLE_ON) {
            // Pick random next track from the not-yet-played ones
            // TODO: make it work right after adding/removing items in the queue.

            // Store the current file in the history, but keep the history at a
            // reasonable size
            if (mPlayPos >= 0) {
                mHistory.add(mPlayPos);
            }
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.removeElementAt(0);
            }

            int numTracks = mPlayListLen;
            int[] tracks = new int[numTracks];
            for (int i=0;i < numTracks; i++) {
                tracks[i] = i;
            }

            int numHistory = mHistory.size();
            int numUnplayed = numTracks;
            for (int i=0;i < numHistory; i++) {
                int idx = mHistory.get(i).intValue();
                if (idx < numTracks && tracks[idx] >= 0) {
                    numUnplayed--;
                    tracks[idx] = -1;
                }
            }

            // 'numUnplayed' now indicates how many tracks have not yet
            // been played, and 'tracks' contains the indices of those
            // tracks.
            if (numUnplayed <=0) {
                // everything's already been played
                if (mRepeatMode == REPEAT_ALL || force) {
                    //pick from full set
                    numUnplayed = numTracks;
                    for (int i=0;i < numTracks; i++) {
                        tracks[i] = i;
                    }
                } else {
                    // all done
                    return -1;
                }
            }
            int skip = mRand.nextInt(numUnplayed);
            int cnt = -1;
            while (true) {
                while (tracks[++cnt] < 0)
                    ;
                skip--;
                if (skip < 0) {
                    break;
                }
            }
            return cnt;
        } else {
            if (mPlayPos >= mPlayListLen - 1) {
                // we're at the end of the list
                if (mRepeatMode == REPEAT_NONE && !force) {
                    // all done
                    return -1;
                } else if (mRepeatMode == REPEAT_ALL || force) {
                    return 0;
                }
                return -1;
            } else {
                return mPlayPos + 1;
            }
        }
    }

    public void gotoNext(boolean force) {
        synchronized (this) {
            if (mPlayListLen <= 0) {
                Log.d(LOGTAG, "No play queue");
                return;
            }

            int pos = getNextPosition(force);
            if (pos < 0) {
                gotoIdleState();
                if (mIsSupposedToBePlaying) {
                    mIsSupposedToBePlaying = false;
                    notifyChange(PLAYSTATE_CHANGED);
                }
                return;
            }
            mPlayPos = pos;
            stop(false);
            mPlayPos = pos;
            openCurrentAndNext();
            notifyChange(META_CHANGED);
        }
    }
    
    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        stopForeground(true);
    }
    
    // A simple variation of Random that makes sure that the
    // value it returns is not equal to the value it returned
    // previously, unless the interval is 1.
    private static class Shuffler {
        private int mPrevious;
        private Random mRandom = new Random();
        public int nextInt(int interval) {
            int ret;
            do {
                ret = mRandom.nextInt(interval);
            } while (ret == mPrevious && interval > 1);
            mPrevious = ret;
            return ret;
        }
    };

    /**
     * Removes the range of tracks specified from the play list. If a file within the range is
     * the file currently being played, playback will move to the next file after the
     * range. 
     * @param first The first file to be removed
     * @param last The last file to be removed
     * @return the number of tracks deleted
     */
    public int removeTracks(int first, int last) {
        int numremoved = removeTracksInternal(first, last);
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }
    
    private int removeTracksInternal(int first, int last) {
        synchronized (this) {
            if (last < first) return 0;
            if (first < 0) first = 0;
            if (last >= mPlayListLen) last = mPlayListLen - 1;

            boolean gotonext = false;
            if (first <= mPlayPos && mPlayPos <= last) {
                mPlayPos = first;
                gotonext = true;
            } else if (mPlayPos > last) {
                mPlayPos -= (last - first + 1);
            }
            int num = mPlayListLen - last - 1;
            for (int i = 0; i < num; i++) {
                mPlayList[first + i] = mPlayList[last + 1 + i];
            }
            mPlayListLen -= last - first + 1;
            
            if (gotonext) {
                if (mPlayListLen == 0) {
                    stop(true);
                    mPlayPos = -1;
                    if (mCursor != null) {
                        mCursor.close();
                        mCursor = null;
                    }
                } else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    boolean wasPlaying = isPlaying();
                    stop(false);
                    openCurrentAndNext();
                    if (wasPlaying) {
                        play();
                    }
                }
                notifyChange(META_CHANGED);
            }
            return last - first + 1;
        }
    }
    
    /**
     * Removes all instances of the track with the given id
     * from the playlist.
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
    public int removeTrack(long id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < mPlayListLen; i++) {
                if (mPlayList[i] == id) {
                    numremoved += removeTracksInternal(i, i);
                    i--;
                }
            }
        }
        if (numremoved > 0) {
            notifyChange(QUEUE_CHANGED);
        }
        return numremoved;
    }
    
    public void setShuffleMode(int shufflemode) {
        synchronized(this) {
            if (mShuffleMode == shufflemode && mPlayListLen > 0) {
                return;
            }
            mShuffleMode = shufflemode;
            saveQueue(false);
        }
    }
    public int getShuffleMode() {
        return mShuffleMode;
    }
    
    public void setRepeatMode(int repeatmode) {
        synchronized(this) {
            mRepeatMode = repeatmode;
            setNextTrack();
            saveQueue(false);
        }
    }
    public int getRepeatMode() {
        return mRepeatMode;
    }

    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    /**
     * Returns the path of the currently playing file, or null if
     * no file is currently playing.
     */
    public String getPath() {
        return mFileToPlay;
    }
    
    /**
     * Returns the rowid of the currently playing file, or -1 if
     * no file is currently playing.
     */
    public long getAudioId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
    }
    
    /**
     * Returns the position in the queue 
     * @return the position in the queue
     */
    public int getQueuePosition() {
        synchronized(this) {
            return mPlayPos;
        }
    }
    
    /**
     * Starts playing the track at the given position in the queue.
     * @param pos The position in the queue of the track that will be played.
     */
    public void setQueuePosition(int pos) {
        synchronized(this) {
            stop(false);
            mPlayPos = pos;
            openCurrentAndNext();
            notifyChange(META_CHANGED);
        }
    }

    public String getArtistName() {
        synchronized(this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
        }
    }
    
    public long getArtistId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID));
        }
    }

    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM));
        }
    }

    public long getAlbumId() {
        synchronized (this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
        }
    }

    public String getTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
        }
    }

    public int getDuration() {
    	synchronized(this) {
    		if (mCursor == null) {
    		    return -1;
    		}
    		
    		return mCursor.getInt(mCursor.getColumnIndexOrThrow(Media.MediaColumns.DURATION));
    	}
    }
    
    public Bitmap getAlbumArt(boolean small) {
    	synchronized(this) {
    		if (mCursor == null) {
    		    return null;
    		}
    		
    		if (small) {
    			return mNotificationImageFetcher.loadImage(getTrackId());
    		} else {
    			return mLockScreenImageFetcher.loadImage(getTrackId());
    		}
    	}
    }
    
    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the current playback position in milliseconds
     */
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    public long seek(long pos) {
        if (mPlayer.isInitialized()) {
            if (pos < 0) pos = 0;
            if (pos > mPlayer.duration()) pos = mPlayer.duration();
            return mPlayer.seek(pos);
        }
        return -1;
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId: the audio session ID.
     */
    public void setAudioSessionId(int sessionId) {
        synchronized (this) {
            mPlayer.setAudioSessionId(sessionId);
        }
    }

    /**
     * Returns the audio session ID.
     */
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /*
     * By making this a static class with a WeakReference to the Service, we
     * ensure that the Service can be GCd even when the system process still
     * has a remote reference to the stub.
     */
    static class ServiceStub extends IMediaPlaybackService.Stub {
        WeakReference<MediaPlaybackService> mService;
        
        ServiceStub(MediaPlaybackService service) {
            mService = new WeakReference<MediaPlaybackService>(service);
        }

        public void openFile(String path)
        {
            mService.get().open(path);
        }
        public void open(long [] list, int position) {
            mService.get().open(list, position);
        }
        public int getQueuePosition() {
            return mService.get().getQueuePosition();
        }
        public void setQueuePosition(int index) {
            mService.get().setQueuePosition(index);
        }
        public boolean isPlaying() {
            return mService.get().isPlaying();
        }
        public void stop() {
            mService.get().stop();
        }
        public void pause() {
            mService.get().pause(true);
        }
        public void play() {
            mService.get().play();
        }
        public void prev() {
            mService.get().prev();
        }
        public void next() {
            mService.get().gotoNext(true);
        }
        public String getTrackName() {
            return mService.get().getTrackName();
        }
        public String getAlbumName() {
            return mService.get().getAlbumName();
        }
        public long getAlbumId() {
            return mService.get().getAlbumId();
        }
        public String getArtistName() {
            return mService.get().getArtistName();
        }
        public long getArtistId() {
            return mService.get().getArtistId();
        }
        public void enqueue(long [] list , int action) {
            mService.get().enqueue(list, action);
        }
        public long [] getQueue() {
            return mService.get().getQueue();
        }
        public void moveQueueItem(int from, int to) {
            mService.get().moveQueueItem(from, to);
        }
        public String getPath() {
            return mService.get().getPath();
        }
        public long getAudioId() {
            return mService.get().getAudioId();
        }
        public long position() {
            return mService.get().position();
        }
        public long duration() {
            return mService.get().duration();
        }
        public long seek(long pos) {
            return mService.get().seek(pos);
        }
        public void setShuffleMode(int shufflemode) {
            mService.get().setShuffleMode(shufflemode);
        }
        public int getShuffleMode() {
            return mService.get().getShuffleMode();
        }
        public int removeTracks(int first, int last) {
            return mService.get().removeTracks(first, last);
        }
        public int removeTrack(long id) {
            return mService.get().removeTrack(id);
        }
        public void setRepeatMode(int repeatmode) {
            mService.get().setRepeatMode(repeatmode);
        }
        public int getRepeatMode() {
            return mService.get().getRepeatMode();
        }
        public int getAudioSessionId() {
            return mService.get().getAudioSessionId();
        }
        
        // new
        public String getTrackNumber() {
        	return mService.get().getTrackNumber();
        }
        public int getTrackId() {
        	return mService.get().getTrackId();
        }
        public String getMediaUri() {
        	return mService.get().getMediaUri();
        }
        public void setSleepTimerMode(int sleepmode) {
        	mService.get().setSleepTimerMode(sleepmode);
        }
        public int getSleepTimerMode() {
        	return mService.get().getSleepTimerMode();
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("" + mPlayListLen + " items in queue, currently at index " + mPlayPos);
        writer.println("Currently loaded:");
        writer.println(getArtistName());
        writer.println(getAlbumName());
        writer.println(getTrackName());
        writer.println(getPath());
        writer.println("playing: " + mIsSupposedToBePlaying);
        // TODO fix this!
        //writer.println("actual: " + mPlayer.mCurrentMediaPlayer.isPlaying());
        writer.println("shuffle mode: " + mShuffleMode);
    }

    private final IBinder mBinder = new ServiceStub(this);
    
    public String getTrackNumber() {
    	synchronized (this) {
    		return ((mPlayPos + 1) + " / " + mPlayListLen);
    	}
    }
    
    public int getTrackId() {
    	synchronized(this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(Media.MediaColumns._ID));
        }
    }
    
    public String getMediaUri() {
        synchronized(this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(Media.MediaColumns.URI));
        }
    }
    
    private int mSleepTimerMode = SLEEP_TIMER_OFF;
    
    private Handler mSleepTimerHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		Log.v(LOGTAG, "mSleepTimerHandler called");
    		stop();
    	}
    };
    
    public void setSleepTimerMode(int minutes) {
    	synchronized(this) {
    		mSleepTimerHandler.removeCallbacksAndMessages(null);
    	
    		if (minutes != SLEEP_TIMER_OFF) {
    			Message msg = mSleepTimerHandler.obtainMessage();
    			mSleepTimerHandler.sendMessageDelayed(msg, minutes * 60000);
    		}
    		
    		mSleepTimerMode = minutes;
    	}
    }
    
    public int getSleepTimerMode() {
    	return mSleepTimerMode;
    }
    
    @Override
	public synchronized void onMetadataParsed(long id, Metadata metadata) {
    	if (updateMetadata(id, metadata) > 0) {
    		notifyChange(META_RETRIEVED);
    	}
    	
		if (mPlayList == null || id != mPlayList[mPlayPos]) {
			return;
		}
		
    	Cursor cursor;
    	Cursor tempCursor;
    	
    	if (mCursor == null) {
    		return;
    	}
    	
        cursor = getContentResolver().query(
                Media.MediaColumns.CONTENT_URI,
                mCursorCols, "_id=" + id , null, null);
        
        if (cursor != null) {
        	cursor.moveToFirst();
        	tempCursor = mCursor;
        	tempCursor.close();
        	mCursor = cursor;
        	
            notifyChange(META_CHANGED);
            notifyChange(ART_CHANGED);
            //MusicUtils.clearNotificationArtCache();
            updateNotification(true);
        }
    }
    
    private int updateMetadata(long id, Metadata metadata) {
		int rows = 0;
		
		// Form an array specifying which columns to return. 
		ContentValues values = new ContentValues();
		values.put(Media.MediaColumns.TITLE, validateAttribute(metadata.getString(Metadata.METADATA_KEY_TITLE)));
		values.put(Media.MediaColumns.ALBUM, validateAttribute(metadata.getString(Metadata.METADATA_KEY_ALBUM)));
		values.put(Media.MediaColumns.ARTIST, validateAttribute(metadata.getString(Metadata.METADATA_KEY_ARTIST)));
		values.put(Media.MediaColumns.DURATION, convertToInteger(metadata.getString(Metadata.METADATA_KEY_DURATION)));
		
		if (metadata.getByteArray(Metadata.METADATA_KEY_ARTWORK) != null) {
			values.put(Media.MediaColumns.ARTWORK, metadata.getByteArray(Metadata.METADATA_KEY_ARTWORK));
		}
		
		// Get the base URI for the Media Files table in the Media content provider.
        Uri mediaFile = ContentUris.withAppendedId(Media.MediaColumns.CONTENT_URI, id);
		
		// Execute the update.
		rows = getContentResolver().update(mediaFile, 
				values, 
				null,
				null);
	
		// return the number of rows updated.
		return rows;
	}
    
    private String validateAttribute(String attribute) {
		if (attribute == null) {
			return Media.UNKNOWN_STRING;
		}
		
		return attribute.trim();
	}
	
	private int convertToInteger(String attribute) {
		int integerAttribute = Media.UNKNOWN_INTEGER;
		
		String validatedAttribute = validateAttribute(attribute);
		
		if (!validatedAttribute.equals(Media.UNKNOWN_STRING)) {
			try {
				integerAttribute = Integer.valueOf(validatedAttribute);
			} catch(NumberFormatException e) {
				// there was a problem converting the string
			}
		}
		
		return integerAttribute;
	}
    
    private Handler mDelayedPlaybackHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            gotoNext(false);
            notifyChange(META_CHANGED);
        }
    };
	
	private void handleError() {
    	if (!mPlayer.isInitialized()) {
            Intent i = new Intent(STOP_DIALOG);
            sendBroadcast(i);
            
            stop(true);
            mOpenFailedCounter++;
            
            if (mPlayListLen > 1) {
            	if (mOpenFailedCounter == mPlayListLen) {
            		mOpenFailedCounter = 0;
            	} else {
            		mDelayedPlaybackHandler.sendEmptyMessageDelayed(0, 2500);
            	}
            }
            
            if (!mQuietMode) {
            	Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
            }
            
            Log.d(LOGTAG, "Failed to open file for playback");
        } else {
        	mOpenFailedCounter = 0;
        }
    }

	@Override
	public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
		if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
			open(list, 0);
		}
	}
}
