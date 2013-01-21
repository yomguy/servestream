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

package net.sourceforge.servestream.player;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;

import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.service.MediaPlaybackService;

/**
 * Provides a unified interface for dealing with midi files and
 * other media files.
 */
public class MultiPlayer implements Parcelable {
	private static final String TAG = MultiPlayer.class.getName();
	
	private AbstractMediaPlayer mMediaPlayer = new NativePlayer();
    private Handler mHandler;
    private boolean mIsInitialized = false;

    public MultiPlayer() {
        super();
    }

    public void setDataSource(String path, boolean isLocalFile, boolean useFFmpegPlayer) {
        try {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            
            AbstractMediaPlayer player = null;
            if (isLocalFile) {
            	player = new NativePlayer();
            } else {
            	if (useFFmpegPlayer) {
            		player = new FFmpegPlayer();
            	} else {
            		player = AbstractMediaPlayer.getMediaPlayer(path);
            	}
            }
        	mMediaPlayer = player;
            
            mMediaPlayer.setOnPreparedListener(onPreparedListener);
            mMediaPlayer.setOnCompletionListener(completionListener);
            mMediaPlayer.setOnErrorListener(errorListener);            
            if (isLocalFile) {
                mMediaPlayer.setDataSource(path);
            	mMediaPlayer.prepare();
            } else {
                mMediaPlayer.setDataSource(URLUtils.encodeURL(path));
            	mMediaPlayer.prepareAsync();
            }
            Log.v(TAG, "Preparing media player");
        } catch (IOException ex) {
        	Log.v(TAG, "Error initializing");
            mIsInitialized = false;
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MediaPlaybackService.PLAYER_ERROR), 2000);
        } catch (IllegalArgumentException ex) {
        	Log.v(TAG, "Error initializing");
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
    }
        
    public void pause() {
        mMediaPlayer.pause();
    }
        
    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    AbstractMediaPlayer.OnPreparedListener onPreparedListener = new AbstractMediaPlayer.OnPreparedListener() {
		public void onPrepared(AbstractMediaPlayer mp) {
			
			Log.v(TAG, "media player is prepared");
	        mIsInitialized = true;
			mHandler.sendEmptyMessage(MediaPlaybackService.PLAYER_PREPARED);
		}
    };
    
    AbstractMediaPlayer.OnCompletionListener completionListener = new AbstractMediaPlayer.OnCompletionListener() {
        public void onCompletion(AbstractMediaPlayer mp) {
            
        	Log.v(TAG, "onCompletionListener called");
        	
            if (mIsInitialized) {
            	mHandler.sendEmptyMessage(MediaPlaybackService.TRACK_ENDED);
            }
        }
    };

    AbstractMediaPlayer.OnErrorListener errorListener = new AbstractMediaPlayer.OnErrorListener() {
        public boolean onError(AbstractMediaPlayer mp, int what, int extra) {
        	Log.d(TAG, "Error: " + what + "," + extra);
        	
            switch (what) {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                mIsInitialized = false;
                mMediaPlayer.release();
                mMediaPlayer = new NativePlayer(); 
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
}