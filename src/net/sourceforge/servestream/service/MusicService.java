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

import java.util.ArrayList;

import net.sourceforge.servestream.utils.PlaylistHandler;
import net.sourceforge.servestream.utils.PreferenceConstants;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class MusicService extends Service {
	public final static String TAG = "ServeStream.MusicService";

	public static final int ERROR = -2147483648;
	public static final int STARTED_PLAYING = 100;
	public static final int FINISHED = 200;
	public static final int OPENING_MEDIA = 300;
	
    private ArrayList<String> mediaFiles = null;
	private int mediaFilesIndex = 0;
	
    private MediaPlayer mediaPlayer = null;
    
    private Handler handler;
    
	private SharedPreferences preferences = null;
	
	private boolean isOpeningMedia = false;
	
	public Handler disconnectHandler = null;
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {

		preferences = PreferenceManager.getDefaultSharedPreferences(this);
    	
        // Display a notification about us starting.  We put an icon in the status bar.
		ConnectionNotifier.getInstance().showRunningNotification(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {  	
    	
		Log.v(TAG, "onDestroy called");
    	
        // Cancel the persistent notification.
		ConnectionNotifier.getInstance().hideRunningNotification(this);
		
		releaseMediaPlayer();
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder binder = new MusicBinder();
    
    @Override
    public IBinder onBind(Intent intent) {
    	
		Log.v(TAG, "onBind called");
    	
		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, MusicService.class));
    	
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
		
		Log.v(TAG, "onUnbind called");
    	
		if (!mediaPlayer.isPlaying()) {
			stopSelf();
			
			if (disconnectHandler != null)
				Message.obtain(disconnectHandler, -1, "").sendToTarget();
		}
    	
		return true;
    }
    
    private void disconnect() {
		
    	ConnectionNotifier.getInstance().hideRunningNotification(this);
		
    	stopSelf();
    	
		if (disconnectHandler != null)
			Message.obtain(disconnectHandler, -1, "").sendToTarget();
    }
    
    public boolean isOpeningMedia() {
    	return isOpeningMedia;
    }
    
    public boolean isPlaying() {
        return mediaPlayer.isPlaying();	
    }
    
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
    
    public MediaPlayer getMediaPlayer() {
    	return this.mediaPlayer;
    }
    
    public void setMediaPlayer(MediaPlayer mediaPlayer) {
    	this.mediaPlayer = mediaPlayer;
    	this.mediaPlayer.setOnCompletionListener(m_onCompletionListener);
        this.mediaPlayer.setOnErrorListener(new OnErrorListener() {
			public boolean onError(MediaPlayer arg0, int arg1, int arg2) {
			    handler.sendMessage(Message.obtain(handler, ERROR));
				return true;
			}

        });
    }
    
    public void startMediaPlayer() {
        startMedia(0);
    }
    
    public void nextMediaFile() {
    	
    	if (mediaFilesIndex == (mediaFiles.size() - 1)) {
    		startMedia(0);
    	} else {
            startMedia(mediaFilesIndex + 1);
    	}
    }
    
    public void pauseMedia() {
    	mediaPlayer.pause();
    }
    
    public void resumeMedia() {
    	mediaPlayer.start();
    }
    
    public void previousMediaFile() {
    	if (mediaFilesIndex == 0) {
    		startMedia((mediaFiles.size() - 1));
    	} else {
            startMedia((mediaFilesIndex - 1));
    	}
    }
    
    public void seekTo(int position) {
    	mediaPlayer.seekTo(position);
    }
    
    public void setHandler(Handler handler) {
    	this.handler = handler;
    }
    
    public void seekBackward() {
    	
    	int seekPosition = mediaPlayer.getCurrentPosition() - 15000;
    	
    	if (seekPosition >= 0) {
    		mediaPlayer.seekTo(seekPosition);
    	} else {
    		mediaPlayer.seekTo(0);    		
    	}
    }

    public void seekForward() {
    	
    	int seekPosition = mediaPlayer.getCurrentPosition() + 15000;
    	
    	if (seekPosition < mediaPlayer.getDuration()) {
    		mediaPlayer.seekTo(seekPosition);
    	} else {
    		mediaPlayer.seekTo(mediaPlayer.getDuration());    		
    	}
    }
    
    private void startMedia(int index) {
    	mediaFilesIndex = index;
    	
    	try {
    		isOpeningMedia = true;
	        mediaPlayer.reset();
	        mediaPlayer.setDataSource(mediaFiles.get(mediaFilesIndex));
	        mediaPlayer.prepare();
		    mediaPlayer.start();
		    isOpeningMedia = false;
		    handler.sendMessage(Message.obtain(handler, OPENING_MEDIA));
		    handler.sendMessage(Message.obtain(handler, STARTED_PLAYING, mediaFiles.get(mediaFilesIndex)));
    	} catch (Exception ex) {
		    handler.sendMessage(Message.obtain(handler, ERROR));
    		ex.printStackTrace();
    	}
    }
    
    public void stopMedia() {
    	mediaPlayer.stop();
    	mediaPlayer.reset();
    }
    
    private OnCompletionListener m_onCompletionListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mp) {

			// if repeat preference is set to one, play the media file again
			if (preferences.getString(PreferenceConstants.REPEAT, "Off").equals("One")) {
				startMedia(mediaFilesIndex);
				return;
			}
			
			mediaFilesIndex++;
			
			if (mediaFilesIndex == mediaFiles.size()) {
				if (preferences.getString(PreferenceConstants.REPEAT, "Off").equals("All")) {
					startMedia(0);
					return;
				}
				disconnect();
			} else {
                startMedia(mediaFilesIndex);
			}
		}
    };
    
    public boolean queueNewMedia(String requestedStream) {
    
        if (isPlaylist(requestedStream)) {
            PlaylistHandler playlistHandler = new PlaylistHandler(requestedStream);
            playlistHandler.buildPlaylist();
            mediaFiles = playlistHandler.getPlayListFiles();
        } else {
        	mediaFiles = new ArrayList<String>();
        	mediaFiles.add(requestedStream);
        }
    	
    	return true;
    }
    
    /**
     * Checks if a file is a playlist file
     * 
     * @param mediaFileName The file to check
     * @return boolean True if the file is a playlist, false otherwise
     */
    private boolean isPlaylist(String mediaFileName) {
    	if (mediaFileName.length() > 4) {
    	    if (mediaFileName.substring(mediaFileName.length() - 4, mediaFileName.length()).equalsIgnoreCase(".m3u")) {
    	    	return true;
    	    }
    	}
    	
    	return false;
    }
}
