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
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class MusicService extends Service {
	public final static String TAG = "ServeStream.MusicService";
	
    private ArrayList<String> mediaFiles = null;
	private int mediaFilesIndex = 0;
	
    private MediaPlayer mediaPlayer = null;
    
    private Handler nowPlayingHandler;
    
	private SharedPreferences preferences = null;
    //private ProgressDialog dialog = null;
    
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
    	
        Log.i("MusicService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {  	
    	
        // Cancel the persistent notification.
		ConnectionNotifier.getInstance().hideRunningNotification(this);
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder binder = new MusicBinder();
    
    @Override
    public IBinder onBind(Intent intent) {
    	
		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, MusicService.class));
    	
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
		
		if (!mediaPlayer.isPlaying()) {
			releaseMediaPlayer();
			stopSelf();
		}
    	
		return true;
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
    
    /*public void setProgressDialog(ProgressDialog dialog) {
    	this.dialog = dialog;
    }*/
    
    public void setNowPlayingHandler(Handler handler) {
    	this.nowPlayingHandler = handler;
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
	        mediaPlayer.reset();
	        mediaPlayer.setDataSource(mediaFiles.get(mediaFilesIndex));
	        mediaPlayer.prepare();
		    mediaPlayer.start();
		    nowPlayingHandler.sendMessage(Message.obtain(nowPlayingHandler, 1, mediaFiles.get(mediaFilesIndex)));
		    Log.v(TAG, "Starting media file");
		    //dialog.dismiss();
    	} catch (Exception ex) {
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
				releaseMediaPlayer();
				stopSelf();
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