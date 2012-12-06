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
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;

import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.service.MediaPlaybackService;

/**
 * Provides a unified interface for dealing with midi files and
 * other media files.
 */
public class NativeMediaPlayer extends AbstractMediaPlayer {
	private static final String TAG = NativeMediaPlayer.class.getName();
	
	private MediaPlayer mMediaPlayer = new MediaPlayer();
    private Handler mHandler;
    private boolean mIsInitialized = false;

    public NativeMediaPlayer() {
        super();
    }

    public void setDataSource(String path, boolean isLocalFile) {
        try {
            mMediaPlayer.reset();
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

    MediaPlayer.OnPreparedListener onPreparedListener = new MediaPlayer.OnPreparedListener() {
		public void onPrepared(MediaPlayer mp) {
			
			Log.v(TAG, "media player is prepared");
	        mIsInitialized = true;
			mHandler.sendEmptyMessage(MediaPlaybackService.PLAYER_PREPARED);
		}
    };
    
    MediaPlayer.OnCompletionListener completionListener = new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
            
        	Log.v(TAG, "onCompletionListener called");
        	
            // Acquire a temporary wakelock, since when we return from
            // this callback the MediaPlayer will release its wakelock
            // and allow the device to go to sleep.
            // This temporary wakelock is released when the RELEASE_WAKELOCK
            // message is processed, but just in case, put a timeout on it.
            if (mIsInitialized) {
            	mHandler.sendEmptyMessage(MediaPlaybackService.TRACK_ENDED);
            }
        }
    };

    MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int what, int extra) {
        	Log.d(TAG, "Error: " + what + "," + extra);
        	
            switch (what) {
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                mIsInitialized = false;
                mMediaPlayer.release();
                mMediaPlayer = new MediaPlayer(); 
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

    public void setDisplay(SurfaceHolder holder) {
    	mMediaPlayer.setDisplay(holder);
    }
}