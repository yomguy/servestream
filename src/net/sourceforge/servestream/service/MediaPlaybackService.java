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

package net.sourceforge.servestream.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.Vector;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MediaPlaybackActivity;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.metadata.SHOUTcastMetadata;
import net.sourceforge.servestream.player.MultiPlayer;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.utils.MetadataRetriever;
import net.sourceforge.servestream.utils.PreferenceConstants;
import net.sourceforge.servestream.utils.Utils;
import net.sourceforge.servestream.widget.ServeStreamAppWidgetOneProvider;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service implements OnSharedPreferenceChangeListener {
	private static final String TAG = MediaPlaybackService.class.getName();
	
	private static final int AUDIOFOCUS_GAIN = 1;
	private static final int AUDIOFOCUS_LOSS = -1;
	private static final int AUDIOFOCUS_LOSS_TRANSIENT = -2;
	
    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 100;
    
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_ON = 1;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static final int SLEEP_TIMER_OFF = 0;
    
    public static final String PLAYSTATE_CHANGED = "net.sourceforge.servestream.playstatechanged";
    public static final String META_CHANGED = "net.sourceforge.servestream.metachanged";
    public static final String QUEUE_CHANGED = "net.sourceforge.servestream.queuechanged";
    
    public static final String PLAYBACK_STARTED = "net.sourceforge.servestream.playbackstarted";
    public static final String PLAYBACK_COMPLETE = "net.sourceforge.servestream.playbackcomplete";
    public static final String START_DIALOG = "net.sourceforge.servestream.startdialog";
    public static final String STOP_DIALOG = "net.sourceforge.servestream.stopdialog";
    public static final String PLAYER_CLOSED = "net.sourceforge.servestream.playerclosed";
    public static final String PREPARE_VIDEO = "net.sourceforge.servestream.preparevideo";    
    
    public static final String SERVICECMD = "net.sourceforge.servestream.MediaPlaybackServicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";

    public static final String TOGGLEPAUSE_ACTION = "net.sourceforge.servestream.MediaPlaybackServicecommand.togglepause";
    public static final String PAUSE_ACTION = "net.sourceforge.servestream.MediaPlaybackServicecommand.pause";
    public static final String NEXT_ACTION = "net.sourceforge.servestream.MediaPlaybackServicecommand.next";

    public static final int TRACK_ENDED = 1;
    public static final int SERVER_DIED = 2;
    private static final int FOCUSCHANGE = 3;
    public static final int PLAYER_PREPARED = 6;
    public static final int PLAYER_ERROR = 7;
    private static final int MAX_HISTORY_SIZE = 100;
    
    protected StreamDatabase mStreamdb = null;
    
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mSleepTimerMode = SLEEP_TIMER_OFF;
    private long [] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private Cursor mCursor;
    private int mPlayPos = -1;
    private final Shuffler mRand = new Shuffler();
    private int mOpenFailedCounter = 0;
    String[] mCursorCols = new String[] {
            Media.MediaColumns._ID,             // index must match IDCOLIDX below
            Media.MediaColumns.URI,
            Media.MediaColumns.TITLE,
            Media.MediaColumns.ALBUM,
            Media.MediaColumns.ARTIST,
            Media.MediaColumns.DURATION,
            Media.MediaColumns.TRACK,
            Media.MediaColumns.YEAR
    };
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private AudioManager mAudioManager;
    // used to track what type of audio focus loss caused the playback to pause
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean mPausedByConnectivityReceiver = false;
    private boolean mPausedDuringPhoneCall = false;
    
    private SharedPreferences mPreferences;
    private ConnectivityReceiver mConnectivityManager;
    private SHOUTcastMetadata mSHOUTcastMetadata;
    private DownloadManager mDownloadManager;
    private SimpleLastfmScrobblerManager mSimpleLastfmScrobblerManager;
    private boolean mIsStreaming = true;
    
    private ServeStreamAppWidgetOneProvider mAppWidgetProvider = ServeStreamAppWidgetOneProvider.getInstance();
    
    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 300000;
    
    private Handler mMediaplayerHandler = new Handler() {
        float mCurrentVolume = 1.0f;
        @Override
        public void handleMessage(Message msg) {
            Log.v(TAG, "mMediaplayerHandler.handleMessage " + msg.what);
            switch (msg.what) {
                case SERVER_DIED:
                	Log.v(TAG, "server died!");
                    if (mIsSupposedToBePlaying) {
                        next(true);
                    } else {
                        // the server died when we were idle, so just
                        // reopen the same song (it will start again
                        // from the beginning though when the user
                        // restarts)
                        openCurrent();
                    }
                    break;
                case TRACK_ENDED:
                	notifyChange(PLAYBACK_COMPLETE);
                	
                    if (mRepeatMode == REPEAT_CURRENT) {
                        seek(0);
                        play();
                    } else {
                        next(false);
                    }
                    break;
                case PLAYER_PREPARED:
                    Intent i = new Intent(STOP_DIALOG);
                    sendBroadcast(i);
                    play();
                    notifyChange(META_CHANGED);
                    notifyChange(PLAYBACK_STARTED);
                	break;
                case PLAYER_ERROR:
                	handleError();
                	break;
                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with the code that
                    // handles fade-in
                    switch (msg.arg1) {
                        case AUDIOFOCUS_LOSS:
                            Log.v(TAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = false;
                                mPausedByConnectivityReceiver = false;
                            }
                            pause();
                            break;
                        case AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.v(TAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = true;
                                mPausedByConnectivityReceiver = false;
                            }
                            pause();
                            break;
                        case AUDIOFOCUS_GAIN:
                            Log.v(TAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            if(!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                mPausedByConnectivityReceiver = false;
                                mCurrentVolume = 0f;
                                mPlayer.setVolume(mCurrentVolume);
                                play(); // also queues a fade-in
                            }
                            break;
                        default:
                            Log.e(TAG, "Unknown audio focus change code");
                    }
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
			final boolean retrieveSHOUTcastMetadata = mPreferences.getBoolean(PreferenceConstants.RETRIEVE_SHOUTCAST_METADATA, false);
			mSHOUTcastMetadata.setShouldRetrieveMetadata(retrieveSHOUTcastMetadata);
  	    } else if (key.equals(PreferenceConstants.SEND_SCROBBLER_INFO)) {
			final boolean sendShoutcastInfo = mPreferences.getBoolean(PreferenceConstants.SEND_SCROBBLER_INFO, false);
			mSimpleLastfmScrobblerManager.setShouldSendScrobblerInfo(sendShoutcastInfo);
  	    }
  	}
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            Log.v(TAG, "mIntentReceiver.onReceive " + action + " / " + cmd);
            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                next(true);
            } else if (CMDPREVIOUS.equals(cmd)) {
                prev();
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                    mPausedByConnectivityReceiver = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                mPausedByConnectivityReceiver = false;
            } else if (CMDSTOP.equals(cmd)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                mPausedByConnectivityReceiver = false;
                seek(0);
            } else if (ServeStreamAppWidgetOneProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
                // Someone asked us to refresh a set of specific widgets, probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetProvider.performUpdate(MediaPlaybackService.this, appWidgetIds, "");
            }
        }
    };

    private BroadcastReceiver mDockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            Log.v(TAG, "mDockReceiver.onReceive " + action + " / " + cmd);
        	if(intent.getExtras().containsKey(Intent.EXTRA_DOCK_STATE)){
                int dockState = intent.getExtras().getInt(Intent.EXTRA_DOCK_STATE, 1);
                if(dockState == Intent.EXTRA_DOCK_STATE_UNDOCKED){
                    pause();
                }
            }
        }
    };
    
    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "onCreate called");
        
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        
		final boolean lockingWifi = mPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true);
		mConnectivityManager = new ConnectivityReceiver(this, lockingWifi);
		final boolean retrieveSHOUTcastMetadata = mPreferences.getBoolean(PreferenceConstants.RETRIEVE_SHOUTCAST_METADATA, false);
		mSHOUTcastMetadata = new SHOUTcastMetadata(this, retrieveSHOUTcastMetadata);
		mDownloadManager = new DownloadManager(this);
		final boolean sendScrobblerInfo = mPreferences.getBoolean(PreferenceConstants.SEND_SCROBBLER_INFO, false);
		mSimpleLastfmScrobblerManager = new SimpleLastfmScrobblerManager(this, sendScrobblerInfo);
		
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
        
        mStreamdb = new StreamDatabase(this);
        
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        
        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer();
        mPlayer.setHandler(mMediaplayerHandler);
        
        reloadSettings();
        
        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);

        commandFilter = new IntentFilter();
        commandFilter.addAction(Intent.ACTION_DOCK_EVENT);
        registerReceiver(mDockReceiver,commandFilter);
        
        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }

    @Override
    public void onDestroy() {
    	Log.v(TAG, "onDestroy called");
    	
		if(mStreamdb != null) {
			mStreamdb.close();
			mStreamdb = null;
		}
    	
        // Check that we're not being destroyed while something is still playing.
        if (isPlaying()) {
            Log.e(TAG, "Service being destroyed while still playing.");
        }
        
        notifyChange(PLAYER_CLOSED);
        
        // release all MediaPlayer resources
        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);
        mSleepTimerHandler.removeCallbacksAndMessages(null);

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        
        unregisterReceiver(mIntentReceiver);
        unregisterReceiver(mDockReceiver);
        
        mConnectivityManager.cleanup();
        mSHOUTcastMetadata.cleanup();
    	mDownloadManager.cancelDownload();
    	Utils.deleteAllFiles();
    	mSimpleLastfmScrobblerManager.cleanup();
        
		stopForeground(true);
		
        super.onDestroy();
    }

    private void saveSettings() {
        Editor ed = mPreferences.edit();
        ed.putInt("repeatmode", mRepeatMode);
        ed.putInt("shufflemode", mShuffleMode);
        ed.commit();
    }
    
    private void reloadSettings() {
    	int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
        if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
        	repmode = REPEAT_NONE;
        }
        mRepeatMode = repmode;

        int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
        if (shufmode != SHUFFLE_ON) {
        	shufmode = SHUFFLE_NONE;
        }
        mShuffleMode = shufmode;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
		Log.v(TAG, "onBind called");
    	
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
    	Log.v(TAG, "onRebind called");
    	
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
            Log.v(TAG, "onStartCommand " + action + " / " + cmd);

            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                next(true);
            } else if (CMDPREVIOUS.equals(cmd)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                    mPausedByTransientLossOfFocus = false;
                    mPausedByConnectivityReceiver = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                mPausedByConnectivityReceiver = false;
            } else if (CMDSTOP.equals(cmd)) {
                pause();
                mPausedByTransientLossOfFocus = false;
                mPausedByConnectivityReceiver = false;
                seek(0);
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
    	Log.v(TAG, "onUnbind called");
    	
        mServiceInUse = false;
		
        // Take a snapshot of the current settings
        saveSettings();
        //saveQueue(true);

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
    	Log.v(TAG, "onUnbind succedded");
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
            stopSelf(mServiceStartId);
        }
    };
    
    private Handler mSleepTimerHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		Log.v(TAG, "mSleepTimerHandler called");
    		pause();
    		//TODO: is this enough?
    	}
    };
    
    public MultiPlayer getMediaPlayer() {
    	return mPlayer;
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
        i.putExtra("duration", Long.valueOf(duration()));
        i.putExtra("playing", isPlaying());
        sendBroadcast(i);
        
        // Share this notification directly with our widgets
        mAppWidgetProvider.notifyChange(this, what);
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
    		if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_METADATA, false)) {
    			int playPos = 0;
    			
    			if (mPlayPos > 0) {
    				playPos = mPlayPos;
    			}
    			
    			MetadataRetriever.retrieve(MediaPlaybackService.this, list, playPos);
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
                    openCurrent();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrent();
                play();
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
            if (position >= 0) {
                mPlayPos = position;
            } else {
                mPlayPos = mRand.nextInt(mPlayListLen);
            }
            mHistory.clear();

            openCurrent();
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

    private void openCurrent() {
        synchronized (this) {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }

            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            String id = String.valueOf(mPlayList[mPlayPos]);
            
            mCursor = getContentResolver().query(
                    Media.MediaColumns.CONTENT_URI,
                    mCursorCols, "_id=" + id , null, null);
            if (mCursor != null) {
            	mCursor.moveToFirst();
                open(Media.MediaColumns.CONTENT_URI + "/" + id);
            }
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
            
            //TODO: add back in cursor code?
            
            Intent i = new Intent(START_DIALOG);
            sendBroadcast(i);
            
            int uriColumn = mCursor.getColumnIndex(Media.MediaColumns.URI);
            mFileToPlay = mCursor.getString(uriColumn);
            
            Log.v(TAG, "opening: " + mFileToPlay);
            
    		mDownloadManager.cancelDownload();
            
            if (playingVideo()) {
            	mIsStreaming = true;
                Intent intent = new Intent(this, MediaPlaybackActivity.class);
                intent.setAction(PREPARE_VIDEO);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else { 
            	if (mPreferences.getBoolean(PreferenceConstants.PROGRESSIVE_DOWNLOAD, false)) {
            		mIsStreaming = false;
            		mDownloadManager.download(mPlayList[mPlayPos]);
            	} else {
            		mIsStreaming = true;
            		mPlayer.setDataSource(mFileToPlay, false);
            	}
            }
        }
    }

    private void setDataSource(boolean progressiveDownload) {    	
    	mPlayer.setDataSource(mFileToPlay, false);
    }
    
    /**
     * Starts playback of a previously opened file.
     */
    public void play() {
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(this.getPackageName(),
                MediaButtonIntentReceiver.class.getName()));
    	
    	if (mPlayer.isInitialized()) {
    		
            mPlayer.start();

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
                album = getString(R.string.unknown_album_name);
            }
            
    		Notification status = new Notification(
    				R.drawable.notification_icon, null,
    				System.currentTimeMillis());
            status.flags |= Notification.FLAG_ONGOING_EVENT;
            PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
                    new Intent(this, MediaPlaybackActivity.class), 0);
    		status.setLatestEventInfo(getApplicationContext(), trackName,
    				getString(R.string.notification_artist_album, artist, album), contentIntent);
            startForeground(PLAYBACKSERVICE_STATUS, status);
            
            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }
        }
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
    public void pause() {
        synchronized(this) {
            if (isPlaying()) {        		
                mPlayer.pause();
                gotoIdleState();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
                stopForeground(true);
            }
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
            openCurrent();
        }
    }

    public void next(boolean force) {
        synchronized (this) {
            if (mPlayListLen <= 0) {
                Log.d(TAG, "No media in playlist queue");
                return;
            }
            if (mShuffleMode == SHUFFLE_ON) {
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
                        gotoIdleState();
                        if (mIsSupposedToBePlaying) {
                            mIsSupposedToBePlaying = false;
                            notifyChange(PLAYSTATE_CHANGED);
                        }
                        return;
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
                mPlayPos = cnt;
            } else {
                if (mPlayPos >= mPlayListLen - 1) {
                    // we're at the end of the list
                    if (mRepeatMode == REPEAT_NONE && !force) {
                        // all done
                    	 gotoIdleState();
                         mIsSupposedToBePlaying = false;
                         notifyChange(PLAYSTATE_CHANGED);
                         return;
                    } else if (mRepeatMode == REPEAT_ALL || force) {
                        mPlayPos = 0;
                    }
                } else {
                    mPlayPos++;
                }
            }

            stop(false);
            openCurrent();
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
                    openCurrent();
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
            saveSettings();
        }
    }
    public int getShuffleMode() {
        return mShuffleMode;
    }
    
    public void setRepeatMode(int repeatmode) {
        synchronized(this) {
            mRepeatMode = repeatmode;
            saveSettings();
        }
    }
    public int getRepeatMode() {
        return mRepeatMode;
    }
    
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
    public long getMediaId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
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
            openCurrent();
            notifyChange(META_CHANGED);
        }
    }

    public String getTrackNumber() {
    	synchronized (this) {
    		return ((mPlayPos + 1) + " / " + mPlayListLen);
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
    
    public String getTrackName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(Media.MediaColumns.TITLE));
        }
    }
    
    public String getAlbumName() {
        synchronized (this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(Media.MediaColumns.ALBUM));
        }
    }
    
    public String getArtistName() {
        synchronized(this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(Media.MediaColumns.ARTIST));
        }
    }
    
    public boolean isStreaming() {
        synchronized (this) {    	
        	return mIsStreaming;
        }
    }
    
    public boolean isCompleteFileAvailable() {
        synchronized (this) {    	
            return mDownloadManager.isCompleteFileAvailable();
        }
    }
    
    public long getCompleteFileDuration() {
        synchronized (this) {    	
            return mDownloadManager.getLength();
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
            
            //if (!mPlayListFiles[mPlayPos].isStreaming()) {
        	//	if (pos > mPlayListFiles[mPlayPos].getLength()) pos = mPlayListFiles[mPlayPos].getLength();
        	//} else {
            	if (pos > mPlayer.duration()) pos = mPlayer.duration();
        	//}
            
            return mPlayer.seek(pos);
        }
        return -1;
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
        public void setDataSource(boolean progressiveDownload) {
        	mService.get().setDataSource(progressiveDownload);
        }
        public int getQueuePosition() {
            return mService.get().getQueuePosition();
        }
        public void setQueuePosition(int index) {
            mService.get().setQueuePosition(index);
        }
        public String getTrackNumber() {
        	return mService.get().getTrackNumber();
        }
        public boolean isPlaying() {
            return mService.get().isPlaying();
        }
        public void stop() {
            mService.get().stop();
        }
        public void pause() {
            mService.get().pause();
        }
        public void play() {
            mService.get().play();
        }
        public void prev() {
            mService.get().prev();
        }
        public void next() {
            mService.get().next(true);
        }
        public String getMediaUri() {
        	return mService.get().getMediaUri();
        }
        public String getTrackName() {
            return mService.get().getTrackName();
        }
        public String getAlbumName() {
            return mService.get().getAlbumName();
        }
        public String getArtistName() {
            return mService.get().getArtistName();
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
		public void setSleepTimerMode(int sleepmode) throws RemoteException {
			mService.get().setSleepTimerMode(sleepmode);
		}
		public int getSleepTimerMode() throws RemoteException {
			return mService.get().getSleepTimerMode();
		}
		public MultiPlayer getMediaPlayer() throws RemoteException {
			return mService.get().getMediaPlayer();
		}
		public boolean isStreaming() throws RemoteException {
			return mService.get().isStreaming();
		}
		public boolean isCompleteFileAvailable() throws RemoteException {
			return mService.get().isCompleteFileAvailable();
		}
		public long getCompleteFileDuration() throws RemoteException {
			return mService.get().getCompleteFileDuration();
		}
    }
    
    private final IBinder mBinder = new ServiceStub(this);
    
    private PhoneStateListener mPhoneListener = new PhoneStateListener() {
    	
    	public void onCallStateChanged(int state, String incomingNumber) {
    		try {
    			switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (isPlaying()) {
                    	pause();
                    	mPausedDuringPhoneCall = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (isPlaying()) {
                    	pause();
                    	mPausedDuringPhoneCall = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                	if (mPausedDuringPhoneCall) {
                		play();
                        mPausedDuringPhoneCall = false;
                	}
                	break;
                default:
                    Log.d(TAG, "Invalid phone state: " + state);
    			}
    		} catch (Exception ex) {
    			ex.printStackTrace();
    		}
    	}
    };
    
	/**
	 * Called when connectivity to the network is lost and it doesn't appear
	 * we'll be getting a different connection any time soon.
	 */
	public void onConnectivityLost() {
		if (isPlaying()) {
			mPausedByConnectivityReceiver = true;
			pause();
        }
	}

	/**
	 * Called when connectivity to the network is restored.
	 */
	public void onConnectivityRestored() {
		if (mPausedByConnectivityReceiver) {
			mPausedByConnectivityReceiver = false;
			play();
		}
	}
    
    /**
     * Checks if the currently playing media file is a video file
     * 
     * @return boolean True if the currently playing file is a video, false otherwise
     */
    private boolean playingVideo() {
    	String fileExtention = null;
    	
    	try {
    	    if (this.mFileToPlay.length() > 4) {
    		    fileExtention = this.mFileToPlay.substring(this.mFileToPlay.length() - 4, this.mFileToPlay.length());
    	        if (fileExtention.equalsIgnoreCase(".3gp") || fileExtention.equalsIgnoreCase(".mp4")) {
    	    	    return true;
    	        }
    	    }
    	} catch (Exception ex) {
    		ex.printStackTrace();
    		return false;
    	}
    	
    	return false;
    }
    
    public void updateMetadata() {
    	Cursor cursor;
    	Cursor tempCursor;
    	
    	if (mCursor == null) {
    		return;
    	}
    	
    	long id = mPlayList[mPlayPos];
    	
        cursor = getContentResolver().query(
                Media.MediaColumns.CONTENT_URI,
                mCursorCols, "_id=" + id , null, null);
        
        if (cursor != null) {
        	cursor.moveToFirst();
        	tempCursor = mCursor;
        	tempCursor.close();
        	mCursor = cursor;
        	
            Intent i = new Intent(PLAYSTATE_CHANGED);
            i.putExtra("id", Long.valueOf(getAudioId()));
            i.putExtra("artist", getArtistName());
            i.putExtra("album",getAlbumName());
            i.putExtra("track", getTrackName());
            i.putExtra("duration", Long.valueOf(duration()));
            i.putExtra("playing", isPlaying());
        	mSimpleLastfmScrobblerManager.scrobble(i);
        	
            notifyChange(META_CHANGED);
        }
    }
    
    private void handleError() {
    	if (! mPlayer.isInitialized()) {
            Intent i = new Intent(STOP_DIALOG);
            sendBroadcast(i);
            
            stop(true);
            if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
                // beware: this ends up being recursive because next() calls open() again.
                next(false);
            }
            if (! mPlayer.isInitialized() && mOpenFailedCounter != 0) {
                // need to make sure we only shows this once
                mOpenFailedCounter = 0;
                if (!mQuietMode) {
                    Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                }
                Log.d(TAG, "Failed to open file for playback");
            }
        } else {
            mOpenFailedCounter = 0;
        }
    }
}