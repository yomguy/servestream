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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;

import net.sourceforge.servestream.utils.PlaylistHandler;
import net.sourceforge.servestream.utils.PreferenceConstants;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.SurfaceHolder;

public class MusicService extends Service {
	public final static String TAG = "ServeStream.MusicService";

	public static final int ERROR = -2147483648;
	public static final int NOW_PLAYING_MESSAGE = 100;
	public static final int START_SEEK_BAR = 300;
	public static final int START_DIALOG = 400;
	public static final int STOP_DIALOG = 500;
	
	private final int SEEK_INCREMENT = 15000;
	
    private ArrayList<String> mediaFiles = null;
	private int mediaFilesIndex = 0;
	
	private String nowPlayingPlaylist = "";
	private String currentlyPlayingTrack = "";
	
    private MediaPlayer mediaPlayer = null;
    
	private SharedPreferences preferences = null;
	
	private boolean isOpeningMedia = false;
	
    public Handler mediaPlayerHandler;
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
    	
		if (!mediaPlayer.isPlaying() || playingVideo()) {
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
    
    public void setDisplay(SurfaceHolder holder) {
        mediaPlayer.setDisplay(holder);
    }
    
    public void setNowPlayingPlaylist(String nowPlayingPlaylist) {
        this.nowPlayingPlaylist = nowPlayingPlaylist;
    }
    
    public String getnowPlayingPlaylist() {
    	return this.nowPlayingPlaylist;
    }
    
    public String getDecodedNowPlayingTrack(){
		String decodedURL = null;
    	
    	try {
			decodedURL = URLDecoder.decode(this.currentlyPlayingTrack, "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			decodedURL = "";
		}
		
		return decodedURL;
    }
    
    public MediaPlayer getMediaPlayer() {
    	return this.mediaPlayer;
    }
    
    public void setMediaPlayer(MediaPlayer mediaPlayer) {
    	this.mediaPlayer = mediaPlayer;
    	this.mediaPlayer.setOnPreparedListener(m_onPreparedListener);
    	this.mediaPlayer.setOnCompletionListener(m_onCompletionListener);
        this.mediaPlayer.setOnErrorListener(new OnErrorListener() {
			public boolean onError(MediaPlayer arg0, int arg1, int arg2) {
			    mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, ERROR));
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
    	
    	ConnectionNotifier.getInstance().hideRunningNotification(this);
    	
    	mediaPlayer.pause();
    }
    
    public void resumeMedia() {
    	
		ConnectionNotifier.getInstance().showRunningNotification(this);
    	
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
    
    public void seekBackward() {
    	
    	int seekPosition = mediaPlayer.getCurrentPosition() - SEEK_INCREMENT;
    	
    	if (seekPosition >= 0) {
    		mediaPlayer.seekTo(seekPosition);
    	} else {
    		mediaPlayer.seekTo(0);    		
    	}
    }

    public void seekForward() {
    	
    	int seekPosition = mediaPlayer.getCurrentPosition() + SEEK_INCREMENT;
    	
    	if (seekPosition < mediaPlayer.getDuration()) {
    		mediaPlayer.seekTo(seekPosition);
    	} else {
    		mediaPlayer.seekTo(mediaPlayer.getDuration());    		
    	}
    }
    
    private void startMedia(int index) {
    	
        // Display a notification about us starting.  We put an icon in the status bar.
		//ConnectionNotifier.getInstance().showRunningNotification(this);
		
		// send a message to start the "Opening file..." dialog
		mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, START_DIALOG));
		
    	mediaFilesIndex = index;
    	
    	// set the flag to true which kills the seek bar thread
    	isOpeningMedia = true;
    	
    	if (mediaPlayer.isPlaying())
    		mediaPlayer.stop();
    	
	    mediaPlayer.reset();
	        
	    try {
	        mediaPlayer.setDataSource(mediaFiles.get(mediaFilesIndex));
	        mediaPlayer.prepareAsync();
    	} catch (Exception ex) {
    		ex.printStackTrace();
    	}
    }
    
    /*public void stopMedia() {
    	mediaPlayer.stop();
    	mediaPlayer.reset();
    }*/

    private OnPreparedListener m_onPreparedListener = new OnPreparedListener() {

		public void onPrepared(MediaPlayer mp) {
			
			// start playing the media file
			mediaPlayer.start();
			
			// show the notification icon
			ConnectionNotifier.getInstance().showRunningNotification(MusicService.this);
			
			// we are finished opening the media, so set the flag to false
		    isOpeningMedia = false;
		    
		    // send a message to start the seek bar 
		    mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, START_SEEK_BAR));
			
			// send a message to stop the "Opening file..." dialog
    		mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, STOP_DIALOG));
			
		    // update the currently playing track
		    currentlyPlayingTrack = mediaFiles.get(mediaFilesIndex);

		    // send a message to show a now playing message
		    mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, NOW_PLAYING_MESSAGE, getDecodedNowPlayingTrack()));
		}
    };
    
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
    
    public void resetSurfaceView() {
    	int position = 0;
    	
        try {
        	position = mediaPlayer.getCurrentPosition();
    	    mediaPlayer.stop();
            mediaPlayer.reset();
			mediaPlayer.setDataSource(mediaFiles.get(mediaFilesIndex));
	        mediaPlayer.prepareAsync();
		    mediaPlayer.start();
		    mediaPlayer.seekTo(position);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    }
    
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
    
    /**
     * Checks if the currently playing media file is a video file
     * 
     * @return boolean True if the currently playing file is a video, false otherwise
     */
    private boolean playingVideo() {
    	String fileExtention = null;
    	
    	try {
    	    if (this.currentlyPlayingTrack.length() > 4) {
    		    fileExtention = this.currentlyPlayingTrack.substring(this.currentlyPlayingTrack.length() - 4, this.currentlyPlayingTrack.length());
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
}
