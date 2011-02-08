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

import java.net.URL;
import java.util.ArrayList;

import net.sourceforge.servestream.StreamMediaActivity;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.utils.M3UPlaylistParser;
import net.sourceforge.servestream.utils.MediaFile;
import net.sourceforge.servestream.utils.PLSPlaylistParser;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.SurfaceHolder;

public class MediaService extends Service {
	public final static String TAG = "ServeStream.MediaService";

	public static final int ERROR = -2147483648;
	public static final int PREPARE_MEDIA_INFO = 100;
	public static final int SHOW_MEDIA_CONTROLS = 200;
	public static final int START_SEEK_BAR = 300;
	public static final int START_DIALOG = 400;
	public static final int STOP_DIALOG = 500;
	
	private String shuffleState;
	public static final String SHUFFLE_OFF = "off";
	public static final String SHUFFLE_ON = "on";
	
	private String repeatState;
	public static final String REPEAT_OFF = "off";
	public static final String REPEAT_ONE = "one";
	public static final String REPEAT_ALL = "all";
	
	private int mediaPlayerState = -1;
	
	private boolean streamActivityState;
	
	private final int SEEK_INCREMENT = 15000;
	
    private ArrayList<MediaFile> mediaFiles = null;
	private int mediaFilesIndex = 0;
	
	private Stream currentStream = null;
	private String currentlyPlayingTrack = "";
	
    private MediaPlayer mediaPlayer = null;
	
	private boolean isOpeningMedia = false;

	protected StreamDatabase streamdb = null;
	
    public Handler mediaPlayerHandler;
	public Handler disconnectHandler = null;
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class MediaBinder extends Binder {
        public MediaService getService() {
            return MediaService.this;
        }
    }

    @Override
    public void onCreate() {

    	Log.v(TAG, "onCreate called");
		
		TelephonyManager tm = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		tm.listen(m_phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
		
		streamdb = new StreamDatabase(this);
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
    	
		if(streamdb != null) {
			streamdb.close();
			streamdb = null;
		}
		
        // Cancel the persistent notification.
		ConnectionNotifier.getInstance().hideRunningNotification(this);
		
		releaseMediaPlayer();
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder binder = new MediaBinder();
    
    @Override
    public IBinder onBind(Intent intent) {
    	
		Log.v(TAG, "onBind called");
    	
		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, MediaService.class));
    	
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
		
		Log.v(TAG, "onUnbind called");
    	
		if (!mediaPlayer.isPlaying() || playingVideo()) {
			stopSelf();
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
    
    public void setCurrentStream(Stream currentStream) {
        this.currentStream = currentStream;
    }
    
    public int getNumOfQueuedFiles() {
    	return mediaFiles.size();
    }
    
    public MediaFile getCurrentMediaInfo() {
    	return mediaFiles.get(mediaFilesIndex);
    }
    
    public Stream getCurrentStream() {
    	return this.currentStream;
    }
    
    public MediaPlayer getMediaPlayer() {
    	return this.mediaPlayer;
    }
    
    public void setMediaPlayer(MediaPlayer mediaPlayer) {
    	shuffleState = SHUFFLE_OFF;
    	repeatState = REPEAT_OFF;
    	
    	this.mediaPlayer = mediaPlayer;
    	this.mediaPlayer.setOnPreparedListener(m_onPreparedListener);
    	this.mediaPlayer.setOnCompletionListener(m_onCompletionListener);
        this.mediaPlayer.setOnErrorListener(m_onErrorListener);
    }
    
    public String getShuffleState() {
    	return shuffleState;
    }
    
    public void setShuffleState(String shuffleState) {
    	this.shuffleState = shuffleState;
    }
    
    public String getRepeatState() {
    	return repeatState;
    }
    
    public void setRepeatState(String repeatState) {
    	this.repeatState = repeatState;
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

    public boolean getStreamActivityState() {
    	return streamActivityState;
    }
    
    public void setStreamActivityState(boolean state) {
    	streamActivityState = state;
    }
    
    private void startMedia(int index) {
		
		// send a message to start the "Opening file..." dialog
    	if (streamActivityState == StreamMediaActivity.VISIBLE)
		    mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, START_DIALOG));
		
    	mediaFilesIndex = index;
    	
    	// set the flag to true which kills the seek bar thread
    	isOpeningMedia = true;
    	
    	if (mediaPlayer.isPlaying())
    		mediaPlayer.stop();
    	
	    mediaPlayer.reset();
	        
	    try {
	        mediaPlayer.setDataSource(mediaFiles.get(mediaFilesIndex).getURL());
	        mediaPlayer.prepareAsync();
	        
	        //Stream stream = new Stream(mediaFiles.get(mediaFilesIndex).getURL());
	    	
	        //if (streamdb.findStream(stream) != null)
	    	//    streamdb.touchHost(stream);
	        
    	} catch (Exception ex) {
    		ex.printStackTrace();
    	}
    }

    private OnPreparedListener m_onPreparedListener = new OnPreparedListener() {

		public void onPrepared(MediaPlayer mp) {
			
			// start playing the media file
			mediaPlayer.start();
			
			// show the notification icon
			ConnectionNotifier.getInstance().showRunningNotification(MediaService.this);
			
			// we are finished opening the media, so set the flag to false
		    isOpeningMedia = false;
		    
	        // update the currently playing track
	        currentlyPlayingTrack = mediaFiles.get(mediaFilesIndex).getURL();
		    
	        // if available, send notifications to the activity
	    	if (streamActivityState == StreamMediaActivity.VISIBLE) {
	    		
	    		// send a message to prepare media information
		        mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, PREPARE_MEDIA_INFO));
	    		
		        // send a message to start the seek bar 
		        mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, START_SEEK_BAR));
			
			    // send a message to stop the "Opening file..." dialog
    		    mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, STOP_DIALOG));
    		    
		        // send a message to show the media controls 
		        mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, SHOW_MEDIA_CONTROLS));
	    	}
		}
    };
    
    private OnCompletionListener m_onCompletionListener = new OnCompletionListener() {
    	
		public void onCompletion(MediaPlayer mp) {

			// if repeat preference is set to one, play the media file again
			if (repeatState.equals(REPEAT_ONE)) {
			    startMedia(mediaFilesIndex);
				return;
			}
			
			mediaFilesIndex++;
			
			if (mediaFilesIndex == mediaFiles.size()) {
				if (repeatState.equals(REPEAT_ALL)) {
					startMedia(0);
					return;
				}
				disconnect();
			} else {
				/*if (shuffleState.equals(SHUFFLE_ON)) {
					Random random = new Random();
				    mediaFilesIndex = random.nextInt(mediaFiles.size());
				}*/
				
                startMedia(mediaFilesIndex);
			}
		}
    };
    
    private OnErrorListener m_onErrorListener = new OnErrorListener() {

		public boolean onError(MediaPlayer mp, int what, int extra) {
			
			//if (extra == -1004) {
			//}
			
	        // if available, send notification to the activity
	    	if (streamActivityState == StreamMediaActivity.VISIBLE) {
	    		mediaPlayerHandler.sendMessage(Message.obtain(mediaPlayerHandler, ERROR));
	    	} else {
	    		
	    	}
			
			return true;
		};
    	
    };
    
    private PhoneStateListener m_phoneListener = new PhoneStateListener() {
    	
    	public void onCallStateChanged(int state, String incomingNumber) {
    		try {
    			switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    if (mediaPlayer.isPlaying()) {
                    	pauseMedia();
                    	mediaPlayerState = 0;
                    }
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    /*if (mediaPlayer.isPlaying()) {
                    	pauseMedia();
                    	//mediaPlayerState = 0;
                    }*/
                case TelephonyManager.CALL_STATE_IDLE:
                	if (mediaPlayerState == 0) {
                        resumeMedia();
                        mediaPlayerState = 0;
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
    
    public void resetSurfaceView() {
    	int position = 0;
    	
        try {
        	position = mediaPlayer.getCurrentPosition();
    	    mediaPlayer.stop();
            mediaPlayer.reset();
			mediaPlayer.setDataSource(mediaFiles.get(mediaFilesIndex).getURL());
	        mediaPlayer.prepareAsync();
		    mediaPlayer.start();
		    mediaPlayer.seekTo(position);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    }
    
    public boolean queueNewMedia(Stream stream) {
    
    	URL url = null;
    	
    	//TODO add null check
    	
    	try {
    	
    		url = stream.getURL();
    		
        if (isM3UPlaylist(url.getPath())) {
            M3UPlaylistParser playlistParser = new M3UPlaylistParser(url);
            playlistParser.retrieveM3UFiles();
            mediaFiles = playlistParser.getPlaylistFiles();
        } else if (isPLSPlaylist(url.getPath())) {
            PLSPlaylistParser playlistParser = new PLSPlaylistParser(url);
            playlistParser.retrievePLSFiles();
            mediaFiles = playlistParser.getPlaylistFiles();
        } else {
        	mediaFiles = new ArrayList<MediaFile>();
        	MediaFile mediaFile = new MediaFile();
        	mediaFile.setURL(url.toString());
        	mediaFile.setTrackNumber(1);
        	mediaFiles.add(mediaFile);
        }
        
    	} catch(Exception ex) {
    		ex.printStackTrace();
    		return false;
    	}
    	
	    Stream foundStream = streamdb.findStream(stream);
		
	    if (foundStream != null) {
		    streamdb.touchHost(foundStream);
	    }
    	
    	return true;
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
