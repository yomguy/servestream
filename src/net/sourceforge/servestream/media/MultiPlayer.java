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

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;

import net.sourceforge.servestream.transport.File;
import net.sourceforge.servestream.transport.HTTP;
import net.sourceforge.servestream.transport.HTTPS;
import net.sourceforge.servestream.transport.MMS;
import net.sourceforge.servestream.transport.MMSH;
import net.sourceforge.servestream.transport.MMST;
import net.sourceforge.servestream.transport.RTSP;
import net.sourceforge.servestream.utils.HTTPRequestTask;
import net.sourceforge.servestream.utils.HTTPRequestTask.HTTPRequestListener;
import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.service.MediaPlaybackService;

/**
 * Provides a unified interface for dealing with media files.
 */
public final class MultiPlayer implements HTTPRequestListener {
	private static final String TAG = MultiPlayer.class.getName();
	
	private MultiPlayerListener mListener;
	
	private NativePlayer mNativeMediaPlayer = new NativePlayer();
	private DownloadPlayer mDownloadMediaPlayer;
	private FFmpegPlayer mFFmpegMediaPlayer;
	private AbstractMediaPlayer mMediaPlayer = mNativeMediaPlayer;
    private boolean mIsInitialized = false;

    /**
     * Default constructor
     */
    protected MultiPlayer() {
    	
    }

    public MultiPlayer(Context context) {
    	// Verify that the host activity implements the callback interface
        try {
            // Instantiate the MultiPlayerListener so we can send events with it
            mListener = (MultiPlayerListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(context.toString()
                    + " must implement MultiPlayerListener");
        }
    }
    
    public void setDataSource(Context context, long id) {
    	setDataSource(context, null, id, true, false, null);
    }
    
    public void setDataSource(String path, boolean useFFmpegPlayer) {
    	setDataSource(null, path, -1, false, useFFmpegPlayer, null);
    }
    
    public void setDataSource(FileDescriptor fd, long offset, long length)
            throws IOException, IllegalArgumentException, IllegalStateException {
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(fd, offset, length);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        mMediaPlayer.setLooping(true);
        mMediaPlayer.prepareAsync();
	}
    
    private void setDataSource(Context context, String path, long id, boolean isLocalFile, boolean useFFmpegPlayer, String contentType) {
        try {
            mMediaPlayer.reset();
            
            if (!isLocalFile && contentType == null && path.startsWith(HTTP.getProtocolName())) {
            	new HTTPRequestTask(path, useFFmpegPlayer, this).execute();
            	return;
            }
            
            AbstractMediaPlayer player = null;
            if (isLocalFile) {
            	player = getDownloadPlayer();
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
            mMediaPlayer.setOnInfoListener(onInfoListener);
            
            if (isLocalFile) {
                mMediaPlayer.setDataSource(context, id);
            	mMediaPlayer.prepareAsync();
            } else {
                mMediaPlayer.setDataSource(URLUtils.encodeURL(path));
            	mMediaPlayer.prepareAsync();
            }
            
            Log.v(TAG, "Preparing media player");
        } catch (IOException ex) {
        	Log.v(TAG, "Error initializing media player");
            mIsInitialized = false;
            if (mListener != null) {
            	mListener.onError(this, 0, 0);
            }
        } catch (IllegalArgumentException ex) {
        	Log.v(TAG, "Error initializing media player");
            mIsInitialized = false;
            if (mListener != null) {
            	mListener.onError(this, 0, 0);
            }
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
        
        if (mDownloadMediaPlayer != null) {
        	mDownloadMediaPlayer.release();
        	mDownloadMediaPlayer = null;
        }
        
        if (mFFmpegMediaPlayer != null) {
        	mFFmpegMediaPlayer.release();
        	mFFmpegMediaPlayer = null;
        }
    }
        
    public void pause() {
        mMediaPlayer.pause();
    }
        
    private AbstractMediaPlayer.OnPreparedListener onPreparedListener = new AbstractMediaPlayer.OnPreparedListener() {
		public void onPrepared(AbstractMediaPlayer mp) {
			Log.i(TAG, "onPreparedListener called");
			
	        mIsInitialized = true;
            if (mListener != null) {
            	mListener.onPrepared(MultiPlayer.this);
            }
		}
    };
    
    private AbstractMediaPlayer.OnCompletionListener onCompletionListener = new AbstractMediaPlayer.OnCompletionListener() {
        public void onCompletion(AbstractMediaPlayer mp) {
        	Log.i(TAG, "onCompletionListener called");
        	
            if (mIsInitialized) {
                if (mListener != null) {
                	mListener.onCompletion(MultiPlayer.this);
                }
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
            		
                    if (mListener != null) {
                    	mListener.onError(MultiPlayer.this, MediaPlaybackService.SERVER_DIED, 0);
                    }
            		return true;
            	default:
            		mIsInitialized = false;
                    if (mListener != null) {
                    	mListener.onError(MultiPlayer.this, 0, 0);
                    }
            		break;
            }
            return false;
        }
    };

    private AbstractMediaPlayer.OnInfoListener onInfoListener = new AbstractMediaPlayer.OnInfoListener() {
		@Override
		public boolean onInfo(AbstractMediaPlayer mp, int what, int extra) {
			switch (what) {
				case AbstractMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    if (mListener != null) {
                    	mListener.onInfo(MultiPlayer.this, 0, 0);
                    }
					return true;
    			default:
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
    
	private DownloadPlayer getDownloadPlayer() {
		// allow for lazy initialization of Download player
		// in case it is never used		
		if (mDownloadMediaPlayer == null) {
			mDownloadMediaPlayer = new DownloadPlayer();
		}
		
		return mDownloadMediaPlayer;
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
	public void onContentTypeObtained(String path, boolean useFFmpegPlayer, 
			String contentType) {
		if (contentType.equalsIgnoreCase("video/x-ms-asf") || 
    		contentType.equalsIgnoreCase("application/vnd.ms-asf")) {
			path = path.replace(HTTP.getProtocolName(), MMSH.getProtocolName());
		}
		
		setDataSource(null, path, -1, false, useFFmpegPlayer, contentType);
	}

	@Override
	public void onHTTPRequestError(String path, boolean useFFmpegPlayer) {
		setDataSource(null, path, -1, false, useFFmpegPlayer, "");
	}
	
	public interface MultiPlayerListener {
        void onPrepared(MultiPlayer mp);
        void onCompletion(MultiPlayer mp);
        void onError(MultiPlayer mp, int what, int extra);
        void onInfo(MultiPlayer mp, int what, int extra);
	}
}