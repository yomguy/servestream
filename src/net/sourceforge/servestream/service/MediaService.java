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

package net.sourceforge.servestream.service;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import java.util.Vector;

import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.metadata.SHOUTcastMetadata;
import net.sourceforge.servestream.player.MultiPlayer;
import net.sourceforge.servestream.utils.ASXPlaylistParser;
import net.sourceforge.servestream.utils.M3UPlaylistParser;
import net.sourceforge.servestream.utils.MediaFile;
import net.sourceforge.servestream.utils.PLSPlaylistParser;
import net.sourceforge.servestream.widget.ServeStreamAppWidgetOneProvider;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaService extends Service {
	private static final String TAG = MediaService.class.getName();
	
    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 1;
    
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_ON = 1;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static final int SLEEP_TIMER_OFF = 0;
    public static final int SLEEP_TIMER_TEN_MIN = 1;
    public static final int SLEEP_TIMER_TWENTY_MIN = 2;
    public static final int SLEEP_TIMER_THIRTY_MIN = 3;
    public static final int SLEEP_TIMER_FOURTY_MIN = 4;
    public static final int SLEEP_TIMER_FIFTY_MIN = 5;
    public static final int SLEEP_TIMER_SIXTY_MIN = 6;
    
    public static final String PLAYSTATE_CHANGED = "net.sourceforge.servestream.playstatechanged";
    public static final String META_CHANGED = "net.sourceforge.servestream.metachanged";
    public static final String SHOUTCAST_META_CHANGED = "net.sourceforge.servestream.shoutcastmetachanged";
    public static final String START_DIALOG = "net.sourceforge.servestream.startdialog";
    public static final String STOP_DIALOG = "net.sourceforge.servestream.stopdialog";
    public static final String QUEUE_CHANGED = "net.sourceforge.servestream.queuechanged";
    public static final String PLAYER_CLOSED = "net.sourceforge.servestream.playerclosed";
    public static final String ERROR_MESSAGE = "net.sourceforge.servestream.errormessage";
    public static final String CLOSE_PLAYER = "net.sourceforge.servestream.closeplayer";
    
    public static final String SERVICECMD = "net.sourceforge.servestream.mediaservicecommand";
    public static final String CMDNAME = "command";

    public static final String TOGGLEPAUSE_ACTION = "net.sourceforge.servestream.mediaservicecommand.togglepause";
    public static final String PAUSE_ACTION = "net.sourceforge.servestream.mediaservicecommand.pause";
    public static final String NEXT_ACTION = "net.sourceforge.servestream.mediaservicecommand.next";

    public static final int TRACK_ENDED = 1;
    public static final int RELEASE_WAKELOCK = 2;
    public static final int SERVER_DIED = 3;
    public static final int PLAYER_PREPARED = 5;
    public static final int PLAYER_ERROR = 6;
    private static final int MAX_HISTORY_SIZE = 100;
    
    private static final int SHOUTCAST_METADATA_REFRESH = 1;
    
    protected StreamDatabase mStreamdb = null;
    
    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private String mPlayListToPlay;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mSleepTimerMode = SLEEP_TIMER_OFF;
    private long [] mPlayList = null;
    private MediaFile [] mPlayListFiles = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private int mPlayPos = -1;
    private final Shuffler mRand = new Shuffler();
    private int mOpenFailedCounter = 0;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private boolean mPausedDuringPhoneCall = false;
    private SHOUTcastMetadata mSHOUTcastMetadata = null;
    
    private ServeStreamAppWidgetOneProvider mAppWidgetProvider = ServeStreamAppWidgetOneProvider.getInstance();
    
    private Handler mMediaplayerHandler = new Handler() {
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
                    if (mRepeatMode == REPEAT_CURRENT) {
                        seek(0);
                        play();
                    } else {
                        next(false);
                    }
                    break;
                case RELEASE_WAKELOCK:
                    mWakeLock.release();
                    break;
                case PLAYER_PREPARED:
                    Intent i = new Intent(STOP_DIALOG);
                    sendBroadcast(i);
                    if (handleError()) {
                    play();
                    notifyChange(META_CHANGED);
                    }
                	break;
                case PLAYER_ERROR:
                	i = new Intent(ERROR_MESSAGE);
                	sendBroadcast(i);
                	break;
                default:
                    break;
            }
        }
    };

    private final Handler mSHOUTcastMetadataHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOUTCAST_METADATA_REFRESH:
                	mSHOUTcastMetadata.refreshMetadata();
                	
					Log.v(TAG, mSHOUTcastMetadata.getTitle());
					Log.v(TAG, mSHOUTcastMetadata.getArtist());

		    		MediaFile mediaFile = mPlayListFiles[mPlayPos];
		    		mediaFile.setTitle(mSHOUTcastMetadata.getArtist() + " - " + mSHOUTcastMetadata.getTitle());
					
		    		notifyChange(SHOUTCAST_META_CHANGED);
		    		
			        msg = mSHOUTcastMetadataHandler.obtainMessage(SHOUTCAST_METADATA_REFRESH);
			        mSHOUTcastMetadataHandler.removeMessages(SHOUTCAST_METADATA_REFRESH);
			        mSHOUTcastMetadataHandler.sendMessageDelayed(msg, 4000);
                    break;
                default:
                    break;
            }
        }
    };
    
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            Log.v(TAG, "mIntentReceiver.onReceive " + action + " / " + cmd);
            if (NEXT_ACTION.equals(action)) {
                next(true);
            } else if (TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (PAUSE_ACTION.equals(action)) {
                pause();
            } else if (ServeStreamAppWidgetOneProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
                // Someone asked us to refresh a set of specific widgets, probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                mAppWidgetProvider.performUpdate(MediaService.this, appWidgetIds, "");
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
    
    /**
     * Default constructor
     */
    public MediaService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "onCreate called");
        
        mStreamdb = new StreamDatabase(this);
        
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(mPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
        
        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer();
        mPlayer.setHandler(mMediaplayerHandler);
        
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
        
        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);

        commandFilter = new IntentFilter();
        commandFilter.addAction(Intent.ACTION_DOCK_EVENT);
        registerReceiver(mDockReceiver,commandFilter);
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
        
        // release all MediaPlayer resources
        mPlayer.release();
        mPlayer = null;
        
        // make sure there aren't any other messages coming
        mSleepTimerHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);
        //mSHOUTcastMetadataHandler.removeMessages(SHOUTCAST_METADATA_REFRESH);

		notifyChange(PLAYER_CLOSED);
        
        unregisterReceiver(mIntentReceiver);
        
        // Cancel the persistent notification.
		ConnectionNotifier.getInstance().hideRunningNotification(this);
        
		mWakeLock.release();
        super.onDestroy();
    }

//    private void saveQueue(boolean full) {
//
//        Editor ed = mPreferences.edit();
//        if (full) {
//            StringBuilder q = new StringBuilder();
//            
//            // The current playlist is save
//            int len = mPlayListLen;
//            for (int i = 0; i < len; i++) {
//            	MediaFile mediaFile = mPlayListFiles[i];
//            	q.append(MusicUtils.mediaFileToXML(mediaFile));
//            	//q.append("\n");
//            }
//            //Log.i("@@@@ service", "created queue string in " + (System.currentTimeMillis() - start) + " ms");
//            ed.putString("queue", q.toString());
//            Log.v(TAG, "Saved: " + q.toString());
//            ed.putInt("cardid", mCardId);
//            if (mShuffleMode != SHUFFLE_NONE) {
//                // In shuffle mode we need to save the history too
//                len = mHistory.size();
//                q.setLength(0);
//                for (int i = 0; i < len; i++) {
//                    int n = mHistory.get(i);
//                    if (n == 0) {
//                        q.append("0;");
//                    } else {
//                        while (n != 0) {
//                            int digit = (n & 0xf);
//                            n >>>= 4;
//                            q.append(digit);
//                        }
//                        q.append(";");
//                    }
//                }
//                ed.putString("history", q.toString());
//            }
//        }
//        ed.putInt("curpos", mPlayPos);
//        if (mPlayer.isInitialized()) {
//            ed.putLong("seekpos", mPlayer.position());
//        }
//        ed.putInt("repeatmode", mRepeatMode);
//        ed.putInt("shufflemode", mShuffleMode);
//        ed.commit();
//        //SharedPreferencesCompat.apply(ed);
//
//        //Log.i("@@@@ service", "saved state in " + (System.currentTimeMillis() - start) + " ms");
//    }
    
//    private void reloadQueue() {
//    	ArrayList<MediaFile> mediaFiles = new ArrayList<MediaFile>();
//    	
//        String q = null;
//        
//        int id = mCardId;
//        if (mPreferences.contains("cardid")) {
//            id = mPreferences.getInt("cardid", ~mCardId);
//        }
//        if (id == mCardId) {
//            // Only restore the saved playlist if the card is still
//            // the same one as when the playlist was saved
//            q = mPreferences.getString("queue", "");
//        }
//        int qlen = q != null ? q.length() : 0;
//        if (qlen > 1) {
//            //Log.i("@@@@ service", "loaded queue: " + q);
//            int plen = 0;
//            String file = "";
//            for (int i = 0; i < qlen; i++) {
//                char c = q.charAt(i);
//                if (c == '\n') {
//                	MediaFile mediaFile = MusicUtils.XMLToMediaFile(file);
//                	mediaFiles.add(mediaFile);
//                    plen++;
//                    file = "";
//                } else {
//                	file = file + c;
//                }
//            }
//            mPlayListLen = plen;
//
//            int pos = mPreferences.getInt("curpos", 0);
//            //if (pos < 0 || pos >= mPlayListLen) {
//                // The saved playlist is bogus, discard it
//                //mPlayListLen = 0;
//                //return;
//            //}
//            mPlayPos = pos;
//
//            // Make sure we don't auto-skip to the next song, since that
//            // also starts playback. What could happen in that case is:
//            // - music is paused
//            // - go to UMS and delete some files, including the currently playing one
//            // - come back from UMS
//            // (time passes)
//            // - music app is killed for some reason (out of memory)
//            // - music service is restarted, service restores state, doesn't find
//            //   the "current" file, goes to the next and: playback starts on its
//            //   own, potentially at some random inconvenient time.
//            //mOpenFailedCounter = 20;
//            //mQuietMode = true;
//            //openCurrent();
//            //mQuietMode = false;
//            //if (!mPlayer.isInitialized()) {
//                // couldn't restore the saved state
//            //    mPlayListLen = 0;
//            //    return;
//            //}
//            
//            long seekpos = mPreferences.getLong("seekpos", 0);
//            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);
//            Log.d(TAG, "restored queue, currently at position "
//                    + position() + "/" + duration()
//                    + " (requested " + seekpos + ")");
//            
//            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
//            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
//                repmode = REPEAT_NONE;
//            }
//            mRepeatMode = repmode;
//
//            mPlayList = new long[mPlayListLen];
//            mPlayListFiles = new MediaFile[mPlayListLen];
//            
//            for (int i = 0; i < mPlayListLen; i++) {
//            	mPlayList[i] = i;
//			    mPlayListFiles[i] = mediaFiles.get(i);
//            }
//            
//            mFileToPlay = mPlayListFiles[mPlayPos].getURL();
//            
//            //int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
//            //if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
//            //    shufmode = SHUFFLE_NONE;
//            //}
//            //if (shufmode != SHUFFLE_NONE) {
//                // in shuffle mode we need to restore the history too
//            //    q = mPreferences.getString("history", "");
//            //    qlen = q != null ? q.length() : 0;
//            //    if (qlen > 1) {
//            //        plen = 0;
//            //        n = 0;
//            //        shift = 0;
//            //        mHistory.clear();
//            //        for (int i = 0; i < qlen; i++) {
//            //            char c = q.charAt(i);
//            //            if (c == ';') {
//            //                if (n >= mPlayListLen) {
//            //                    // bogus history data
//            //                    mHistory.clear();
//            //                    break;
//            //                }
//            //                mHistory.add(n);
//            //                n = 0;
//            //                shift = 0;
//            //            } else {
//            //                if (c >= '0' && c <= '9') {
//            //                    n += ((c - '0') << shift);
//            //                } else if (c >= 'a' && c <= 'f') {
//            //                    n += ((10 + c - 'a') << shift);
//            //                } else {
//            //                    // bogus history data
//            //                    mHistory.clear();
//            //                    break;
//            //                }
//            //                shift += 4;
//            //            }
//            //        }
//            //    }
//            //}
//            //if (shufmode == SHUFFLE_AUTO) {
//            //    if (! makeAutoShuffleList()) {
//            //        shufmode = SHUFFLE_NONE;
//            //    }
//            //}
//            //mShuffleMode = shufmode;
//        }
//    }
    
    private boolean loadQueue(String filename) {
        Log.v(TAG, "Loading Queue");        
        
        boolean retVal = true;
    	Stream stream;
    	Stream foundStream = null;
    	
    	try {
			stream = new Stream(filename);
    	
			//TODO add null check
    		
			if (isM3UPlaylist(stream.getURL().getPath())) {
				M3UPlaylistParser playlistParser = new M3UPlaylistParser(stream.getURL());
				playlistParser.retrieveM3UFiles();
				mPlayListFiles = playlistParser.getPlaylistFiles();
				mPlayListLen = playlistParser.getNumberOfFiles();
			} else if (isPLSPlaylist(stream.getURL().getPath())) {
				PLSPlaylistParser playlistParser = new PLSPlaylistParser(stream.getURL());
				playlistParser.retrievePLSFiles();
				mPlayListFiles = playlistParser.getPlaylistFiles();
				mPlayListLen = playlistParser.getNumberOfFiles();
			} else if (isASXPlaylist(stream.getURL().getPath())) {
				ASXPlaylistParser playlistParser = new ASXPlaylistParser(stream.getURL());
				playlistParser.retrieveASXFiles();
				mPlayListFiles = playlistParser.getPlaylistFiles();
				mPlayListLen = playlistParser.getNumberOfFiles();
			} else {
				mPlayListFiles = new MediaFile[1];         
				MediaFile mediaFile = new MediaFile();
				mediaFile.setURL(stream.getURL().toString());
				mediaFile.setTrackNumber(1);
				mPlayListFiles[0] = mediaFile;
				mPlayListLen = 1;
			}
        
		    mPlayPos = 0;
		    mPlayListToPlay = filename;
			mPlayList = new long[mPlayListLen];
			int len = mPlayListLen;
			
			for (int i = 0; i < len; i++) {
				mPlayList[i] = i;
			}
        
		    foundStream = mStreamdb.findStream(stream);
		    
		    if (foundStream != null) {
			    mStreamdb.touchHost(foundStream);
		    }
		    
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
			retVal = false;
		}
		
		return retVal;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
    	
		Log.v(TAG, "onBind called");
    	
		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, MediaService.class));
    	
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mServiceInUse = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;

        if (intent != null) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            Log.v(TAG, "onStartCommand " + action + " / " + cmd);

            if (NEXT_ACTION.equals(action)) {
                next(true);
            } else if (TOGGLEPAUSE_ACTION.equals(action)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (PAUSE_ACTION.equals(action)) {
                pause();
            }
        }
        
        return START_STICKY;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
    	
		Log.v(TAG, "onUnbind called");
    	
        mServiceInUse = false;
		
		if (!isPlaying() || playingVideo()) {
			stopSelf(mServiceStartId);
		}
    	
		Log.v(TAG, "onUnbind succedded");
		
        return true;
    }

    private Handler mSleepTimerHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		Log.v(TAG, "mSleepTimerHandler called");
    		pause();
    		notifyChange(CLOSE_PLAYER);
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
        
    	// send notification to StreamMediaActivity
        Intent i = new Intent(what);
        sendBroadcast(i);
        
        // Share this notification directly with our widgets
        mAppWidgetProvider.notifyChange(this, what);
    }

    /**
     * Returns the current play list
     * 
     * @return An array of MediaFile objects containing the tracks in the play list
     */
    public MediaFile [] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            MediaFile [] list = new MediaFile [len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayListFiles[i];
            }
            return list;
        }
    }
    
    /**
     * Returns the number of files in the current playlist
     * 
     * @return An integer 
     */
    public int getPlayListLength() {
    	return mPlayListLen;
    }

    private void openCurrent() {
        synchronized (this) {

            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            int id = (int) (mPlayList[mPlayPos]);
            
            MediaFile mediaFile = mPlayListFiles[id];
            open(mediaFile.getURL().toString());
        }
    }

    public void queueFirstFile() {
    	openCurrent();
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

            mWakeLock.acquire();
            
            Intent i = new Intent(START_DIALOG);
            sendBroadcast(i);
            
            mFileToPlay = path;
            
            /*try {
				mSHOUTcastMetadata = new SHOUTcastMetadata(new URL(mPlayListFiles[mPlayPos].getURL()));
	            if (mSHOUTcastMetadata.containsMetadata())
	                mSHOUTcastMetadataHandler.sendEmptyMessage(SHOUTCAST_METADATA_REFRESH);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}*/
            
            Log.v(TAG, "opening" + mPlayListFiles[mPlayPos].getURL().toString());
            mPlayer.setDataSource(mPlayListFiles[mPlayPos].getURL());
        }
    }

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {

    	if (mPlayer.isInitialized()) {

    		if (!mWakeLock.isHeld())
    			mWakeLock.acquire();
    		
            mPlayer.start();

            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }
            
            ConnectionNotifier.getInstance().showRunningNotification(this);
        }
    }
    
    private void stop(boolean remove_status_icon) {
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;

        if (remove_status_icon) {

        } else {
            stopForeground(false);
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
        }
        
        if (remove_status_icon)
            ConnectionNotifier.getInstance().hideRunningNotification(this);
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
            	mWakeLock.release();
                mPlayer.pause();
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
                ConnectionNotifier.getInstance().hideRunningNotification(this);
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
                    	notifyChange(CLOSE_PLAYER);
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
                        mIsSupposedToBePlaying = false;
                        notifyChange(CLOSE_PLAYER);
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
    
    public void setShuffleMode(int shufflemode) {
        synchronized(this) {
            if (mShuffleMode == shufflemode && mPlayListLen > 0) {
                return;
            }
            mShuffleMode = shufflemode;
        }
    }
    public int getShuffleMode() {
        return mShuffleMode;
    }
    
    public void setRepeatMode(int repeatmode) {
        synchronized(this) {
            mRepeatMode = repeatmode;
        }
    }
    public int getRepeatMode() {
        return mRepeatMode;
    }
    
    public void setSleepTimerMode(int sleepmode) {
    	synchronized(this) {
    		mSleepTimerMode = sleepmode;
    		
    		mSleepTimerHandler.removeCallbacksAndMessages(null);
    		Message msg = mSleepTimerHandler.obtainMessage();
    	
    		switch (mSleepTimerMode) {
    			case SLEEP_TIMER_TEN_MIN:
    				mSleepTimerHandler.sendMessageDelayed(msg, 600000);
    				break;
    			case SLEEP_TIMER_TWENTY_MIN:
    				mSleepTimerHandler.sendMessageDelayed(msg, 1200000);
    				break;
    			case SLEEP_TIMER_THIRTY_MIN:
    				mSleepTimerHandler.sendMessageDelayed(msg, 1800000);
    				break;
    			case SLEEP_TIMER_FOURTY_MIN:
    				mSleepTimerHandler.sendMessageDelayed(msg, 2400000);
    				break;
    			case SLEEP_TIMER_FIFTY_MIN:
    				mSleepTimerHandler.sendMessageDelayed(msg, 3000000);
    				break;
    			case SLEEP_TIMER_SIXTY_MIN:
    				mSleepTimerHandler.sendMessageDelayed(msg, 3600000);
    				break;
    			default:
    				Log.v(TAG, "Invalid sleep mode selected");
    				break;
    		}
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
     * Returns the path of the currently playlist file, or null if
     * no playlist is currently playing.
     */
    public String getPlayListPath() {
    	return mPlayListToPlay;
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
    		
    		MediaFile mediaFile = mPlayListFiles[mPlayPos];
    		
    		return (mediaFile.getTrackNumber() + " / " + mPlayListLen);
    	}
    }
    
    public String getTrackName() {
        synchronized (this) {
        	
        	MediaFile mediaFile = mPlayListFiles[mPlayPos];
        	
            return mediaFile.getTitle();
        }
    }
    
    public String getMediaURL() {
        synchronized (this) {
        	
        	MediaFile mediaFile = mPlayListFiles[mPlayPos];
        	
            return mediaFile.getDecodedURL();
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
    
    /*
     * By making this a static class with a WeakReference to the Service, we
     * ensure that the Service can be GCd even when the system process still
     * has a remote reference to the stub.
     */
    static class ServiceStub extends IMediaService.Stub {
        WeakReference<MediaService> mService;
        
        ServiceStub(MediaService service) {
            mService = new WeakReference<MediaService>(service);
        }

        public void openFile(String path)
        {
            mService.get().open(path);
        }
        public void queueFirstFile() {
            mService.get().queueFirstFile();
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
        public String getTrackNumber() {
        	return mService.get().getTrackNumber();
        }
        public String getTrackName() {
        	return mService.get().getTrackName();
        }
        public String getMediaURL() {
        	return mService.get().getMediaURL();
        }
        public MediaFile [] getQueue() {
            return mService.get().getQueue();
        }
        public int getPlayListLength() {
        	return mService.get().getPlayListLength();
        }
        public String getPath() {
            return mService.get().getPath();
        }
        public String getPlayListPath() {
        	return mService.get().getPlayListPath();
        }
        public long getMediaId() {
            return mService.get().getMediaId();
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
        public void setRepeatMode(int repeatmode) {
            mService.get().setRepeatMode(repeatmode);
        }
        public int getRepeatMode() {
            return mService.get().getRepeatMode();
        }
        public void setSleepTimerMode(int sleepmode) {
        	mService.get().setSleepTimerMode(sleepmode);
        }
        public int getSleepTimerMode() {
        	return mService.get().getSleepTimerMode();
        }
        public boolean loadQueue(String filename) {
        	return mService.get().loadQueue(filename);
        }
        public MultiPlayer getMediaPlayer() {
        	return mService.get().getMediaPlayer();
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
    
    private boolean isM3UPlaylist(String path) {
    	int index = 0;
    	
    	if (path == null)
    	    return false;
    	
        index = path.lastIndexOf(".");
    		
    	if (index == -1)
    		return false;
    	
    	if ((path.length() - index) != 4)
    		return false;
    		
    	if (path.substring(index, path.length()).equalsIgnoreCase(".m3u"))
    	    return true;		

    	return false;
    }
    
    private boolean isPLSPlaylist(String path) {
    	int index = 0;
    	
    	if (path == null)
    	    return false;
    	
        index = path.lastIndexOf(".");
    		
    	if (index == -1)    	
        	return false;
    	
    	if ((path.length() - index) != 4)
    		return false;
    		
    	if (path.substring(index, path.length()).equalsIgnoreCase(".pls"))
    	    return true;		

    	return false;
    }

    private boolean isASXPlaylist(String path) {
    	int index = 0;
    	
    	if (path == null)
    	    return false;
    	
        index = path.lastIndexOf(".");
    		
    	if (index == -1)    	
        	return false;
    	
    	if ((path.length() - index) != 4)
    		return false;
    		
    	if (path.substring(index, path.length()).equalsIgnoreCase(".asx"))
    	    return true;		

    	return false;
    }
    
    private boolean handleError() {
    	if (!mPlayer.isInitialized()) {
    		stop(true);
            if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
            	// beware: this ends up being recursive because next() calls open() again.
                next(false);
            }
            if (!mPlayer.isInitialized() && mOpenFailedCounter != 0) {
                // need to make sure we only shows this once
                mOpenFailedCounter = 0;
                //if (!mQuietMode) {
                //    Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                //}
                Log.d(TAG, "Failed to open file for playback");
                mMediaplayerHandler.sendEmptyMessage(MediaService.PLAYER_ERROR);
                return false;
            }
        } else {
            mOpenFailedCounter = 0;
        }
    	
        return true;
    }
}