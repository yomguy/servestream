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
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.Random;
import java.util.Vector;

import net.sourceforge.servestream.StreamMediaActivity;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.player.MultiPlayer;
import net.sourceforge.servestream.utils.M3UPlaylistParser;
import net.sourceforge.servestream.utils.MediaFile;
import net.sourceforge.servestream.utils.PLSPlaylistParser;
import net.sourceforge.servestream.widget.ServeStreamAppWidgetOneProvider;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaService extends Service {
	private static final String TAG = "ServeStream.MediaService";
	
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

    public static final String PLAYSTATE_CHANGED = "net.sourceforge.servestream.playstatechanged";
    public static final String META_CHANGED = "net.sourceforge.servestream.metachanged";
    public static final String START_DIALOG = "net.sourceforge.servestream.startdialog";
    public static final String STOP_DIALOG = "net.sourceforge.servestream.stopdialog";
    public static final String QUEUE_CHANGED = "net.sourceforge.servestream.queuechanged";
    public static final String PLAYER_CLOSED = "net.sourceforge.servestream.playerclosed";
    
    public static final String SERVICECMD = "net.sourceforge.servestream.mediaservicecommand";
    public static final String CMDNAME = "command";

    public static final String TOGGLEPAUSE_ACTION = "net.sourceforge.servestream.mediaservicecommand.togglepause";
    public static final String PAUSE_ACTION = "net.sourceforge.servestream.mediaservicecommand.pause";
    public static final String NEXT_ACTION = "net.sourceforge.servestream.mediaservicecommand.next";

    public static final int TRACK_ENDED = 1;
    public static final int RELEASE_WAKELOCK = 2;
    public static final int SERVER_DIED = 3;
    public static final int PLAYER_PREPARED = 5;
    private static final int MAX_HISTORY_SIZE = 100;
    
    protected StreamDatabase mStreamdb = null;
    
    private MultiPlayer mPlayer;
    private int mParentActivityState = StreamMediaActivity.VISIBLE;
    private String mFileToPlay;
    private String mPlayListToPlay;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private long [] mPlayList = null;
    private MediaFile [] mPlayListFiles = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private int mPlayPos = -1;
    private final Shuffler mRand = new Shuffler();
    //private int mOpenFailedCounter = 0;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mPausedDuringPhoneCall = false;
    
    private ServeStreamAppWidgetOneProvider mAppWidgetProvider = ServeStreamAppWidgetOneProvider.getInstance();
    
    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 60000;
    
    private Handler mMediaplayerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.v(TAG, "mMediaplayerHandler.handleMessage " + msg.what);
            switch (msg.what) {
                case SERVER_DIED:
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
                case PLAYER_PREPARED:
                    Intent i = new Intent(STOP_DIALOG);
                    sendBroadcast(i);
                    play();
                    notifyChange(META_CHANGED);
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
        
        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);

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
        
        // release all MediaPlayer resources
        mPlayer.release();
        mPlayer = null;
        
        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);

		notifyChange(PLAYER_CLOSED);
        
        unregisterReceiver(mIntentReceiver);
        
        // Cancel the persistent notification.
		ConnectionNotifier.getInstance().hideRunningNotification(this);
        
        super.onDestroy();
    }

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
        
        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        return START_STICKY;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        /*mServiceInUse = false;

        if (isPlaying()) {
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
        stopSelf(mServiceStartId);*/
    	
		Log.v(TAG, "onUnbind called");
		
		if (!isPlaying() || playingVideo()) {
			stopSelf();
		}
    	
        return true;
    }
    
    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mServiceInUse
                    || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
            stopSelf(mServiceStartId);
        }
    };

    public MultiPlayer getMediaPlayer() {
    	return mPlayer;
    }
    
    public void setParentActivityState(int state) {
    	mParentActivityState = state;
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

            Intent i = new Intent(START_DIALOG);
            sendBroadcast(i);
            
            mFileToPlay = path;
            Log.v(TAG, "opening" + mPlayListFiles[mPlayPos].getURL().toString());
            mPlayer.setDataSource(mPlayListFiles[mPlayPos].getURL());
            if (! mPlayer.isInitialized()) {
                stop(true);
//                if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
//                    // beware: this ends up being recursive because next() calls open() again.
//                    next(false);
//                }
//                if (! mPlayer.isInitialized() && mOpenFailedCounter != 0) {
//                    // need to make sure we only shows this once
//                    mOpenFailedCounter = 0;
//                    if (!mQuietMode) {
//                        Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
//                    }
//                    Log.d(TAG, "Failed to open file for playback");
//                }
//            } else {
//                mOpenFailedCounter = 0;
            }
            
            //i = new Intent(STOP_DIALOG);
            //sendStickyBroadcast(i);
        }
    }

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {

    	if (mPlayer.isInitialized()) {

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
            gotoIdleState();
        } else {
            stopForeground(false);
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
        }
        
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
                mPlayer.pause();
                gotoIdleState();
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
        public long [] getQueue() {
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
        public boolean loadQueue(String filename) {
        	return mService.get().loadQueue(filename);
        }
        public MultiPlayer getMediaPlayer() {
        	return mService.get().getMediaPlayer();
        }
        public void setParentActivityState(int state) {
        	mService.get().setParentActivityState(state);
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
}