/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
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

package net.sourceforge.servestream.media;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;

import net.sourceforge.servestream.transport.File;
import net.sourceforge.servestream.transport.HTTP;
import net.sourceforge.servestream.transport.HTTPS;
import net.sourceforge.servestream.transport.MMS;
import net.sourceforge.servestream.transport.MMSH;
import net.sourceforge.servestream.transport.MMST;
import net.sourceforge.servestream.transport.RTSP;
import net.sourceforge.servestream.utils.HTTPRequestTask.HTTPRequestListener;
import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.service.MediaPlaybackService;

/**
 * Provides a unified interface for dealing with media files.
 */
public class MultiPlayer implements Parcelable, HTTPRequestListener {
	private static final String TAG = MultiPlayer.class.getName();
	
	private NativePlayer mNativeMediaPlayer = new NativePlayer();
	private FFmpegPlayer mFFmpegMediaPlayer;
	private AbstractMediaPlayer mMediaPlayer = mNativeMediaPlayer;
    private Handler mHandler;
    private boolean mIsInitialized = false;

    /**
     * Default constructor
     */
    public MultiPlayer() {
    	
    }

    public void setDataSource(String path, boolean isLocalFile, boolean useFFmpegPlayer) {
    	setDataSource(path, isLocalFile, useFFmpegPlayer, null);
    }
    
    private void setDataSource(String path, boolean isLocalFile, boolean useFFmpegPlayer, String contentType) {
        try {
            mMediaPlayer.reset();
            
            /*if (contentType == null && path.startsWith(HTTP.getProtocolName())) {
            	new HTTPRequestTask(path, isLocalFile, useFFmpegPlayer, this).execute();
            	return;
            }*/
            
            AbstractMediaPlayer player = null;
            if (isLocalFile) {
            	player = mNativeMediaPlayer;
            } else {
            	if (useFFmpegPlayer) {
            		player = getFFmpegPlayer();
            	} else {
            		player = getMediaPlayer(path);
            	}
            }
            
        	mMediaPlayer = player;
            mMediaPlayer.reset();           
            mMediaPlayer.setOnPreparedListener(onPreparedListener);
            mMediaPlayer.setOnCompletionListener(onCompletionListener);
            mMediaPlayer.setOnErrorListener(onErrorListener);
            
            if (isLocalFile) {
                mMediaPlayer.setDataSource(path);
            	mMediaPlayer.prepare();
            } else {
            	if (path.startsWith(MMS.getProtocolName() + "://")) {
            		path = path.replace(MMS.getProtocolName(), MMSH.getProtocolName());
            	}
            	
                mMediaPlayer.setDataSource(URLUtils.encodeURL(path));
            	mMediaPlayer.prepareAsync();
            }
            
            Log.v(TAG, "Preparing media player");
        } catch (IOException ex) {
        	Log.v(TAG, "Error initializing media player");
            mIsInitialized = false;
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MediaPlaybackService.PLAYER_ERROR), 2000);
        } catch (IllegalArgumentException ex) {
        	Log.v(TAG, "Error initializing media player");
            mIsInitialized = false;
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MediaPlaybackService.PLAYER_ERROR), 2000);
        }
    }
        
    public boolean isInitialized() {
        return mIsInitialized;
    }

    public void start() {
        mMediaPlayer.start();
    }

    public void stop() {
        mMediaPlayer.reset();
        mIsInitialized = false;
    }

    public void release() {
        stop();
        mMediaPlayer.release();
        
        if (mNativeMediaPlayer != null) {
        	mNativeMediaPlayer.release();
        	mNativeMediaPlayer = null;
        }
        
        if (mFFmpegMediaPlayer != null) {
        	mFFmpegMediaPlayer.release();
        	mFFmpegMediaPlayer = null;
        }
    }
        
    public void pause() {
        mMediaPlayer.pause();
    }
        
    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    private AbstractMediaPlayer.OnPreparedListener onPreparedListener = new AbstractMediaPlayer.OnPreparedListener() {
		public void onPrepared(AbstractMediaPlayer mp) {
			Log.i(TAG, "onPreparedListener called");
			
	        mIsInitialized = true;
			mHandler.sendEmptyMessage(MediaPlaybackService.PLAYER_PREPARED);
		}
    };
    
    private AbstractMediaPlayer.OnCompletionListener onCompletionListener = new AbstractMediaPlayer.OnCompletionListener() {
        public void onCompletion(AbstractMediaPlayer mp) {
        	Log.i(TAG, "onCompletionListener called");
        	
            if (mIsInitialized) {
            	mHandler.sendEmptyMessage(MediaPlaybackService.TRACK_ENDED);
            }
        }
    };

    private AbstractMediaPlayer.OnErrorListener onErrorListener = new AbstractMediaPlayer.OnErrorListener() {
        public boolean onError(AbstractMediaPlayer mp, int what, int extra) {
        	Log.i(TAG, "onErrorListener called");
        	Log.d(TAG, "Error: " + what + "," + extra);
        	
            switch (what) {
            	case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
            		release();
            		mNativeMediaPlayer = new NativePlayer();
            		mMediaPlayer = mNativeMediaPlayer; 
            		mHandler.sendMessageDelayed(mHandler.obtainMessage(MediaPlaybackService.SERVER_DIED), 2000);
            		return true;
            	default:
            		mIsInitialized = false;
            		mHandler.sendEmptyMessage(MediaPlaybackService.PLAYER_ERROR);
            		break;
            }
            return false;
        }
    };

    public long duration() {
        return mMediaPlayer.getDuration();
    }

    public long position() {
        return mMediaPlayer.getCurrentPosition();
    }

    public long seek(long msec) {
        mMediaPlayer.seekTo((int) msec);
        return msec;
    }

    public void setVolume(float vol) {
        mMediaPlayer.setVolume(vol, vol);
    }
    
    public void setAudioSessionId(int sessionId) {
    	mMediaPlayer.setAudioSessionId(sessionId);
    }
    
    public int getAudioSessionId() {
        return mMediaPlayer.getAudioSessionId();
    }

    public void setNextDataSource(String path) {
    	
    }
    
    /**
     * Detects the appropriate media player depending on the URI of 
     * a file.
     * @param uri path to a file.
     * @return a media player.
     */
	private AbstractMediaPlayer getMediaPlayer(String uri) {
		if (uri.startsWith(HTTP.getProtocolName())) {
			return mNativeMediaPlayer;
		} else if (uri.startsWith(HTTPS.getProtocolName())) {
			return mNativeMediaPlayer;
		} else if (uri.startsWith(File.getProtocolName())) {
			return mNativeMediaPlayer;
		} else if (uri.startsWith(RTSP.getProtocolName())) {
			return mNativeMediaPlayer;
		} else if (uri.startsWith(MMS.getProtocolName())) {
			return getFFmpegPlayer();
		} else if (uri.startsWith(MMSH.getProtocolName())) {
			return getFFmpegPlayer();
		} else if (uri.startsWith(MMST.getProtocolName())) {
			return getFFmpegPlayer();
		} else {
			return mNativeMediaPlayer;
		}
	}
    
	private FFmpegPlayer getFFmpegPlayer() {
		// allow for lazy initialization of FFmpeg player
		// in case it is never used		
		if (mFFmpegMediaPlayer == null) {
			mFFmpegMediaPlayer = new FFmpegPlayer();
		}
		
		return mFFmpegMediaPlayer;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		
	}
	
	public static final Parcelable.Creator<MultiPlayer> CREATOR = new
	Parcelable.Creator<MultiPlayer>() {
	    public MultiPlayer createFromParcel(Parcel in) {
	    	return null;
	    }

	    public MultiPlayer[] newArray(int size) {
	    	return null;
	    }
	};

	@Override
	public void onContentTypeObtained(String path, boolean isLocalFile,
			boolean useFFmpegPlayer, String contentType) {
		if (contentType.equalsIgnoreCase("video/x-ms-asf") || 
    		contentType.equalsIgnoreCase("application/vnd.ms-asf")) {
			path = path.replace(HTTP.getProtocolName(), MMSH.getProtocolName());
		}
		
		setDataSource(path, isLocalFile, useFFmpegPlayer, contentType);
	}

	@Override
	public void onHTTPRequestError(String path, boolean isLocalFile,
			boolean useFFmpegPlayer) {
		setDataSource(path, isLocalFile, useFFmpegPlayer, "");
	}
}