package net.sourceforge.servestream.media;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.media.MediaPlayer;
import android.util.Log;

public class NativePlayer extends AbstractMediaPlayer {
    private static final String TAG = NativePlayer.class.getName();
	
	private MediaPlayer mMediaPlayer;
	
	public NativePlayer() {
		mMediaPlayer = new MediaPlayer();
		initializeStaticCompatMethods();
	}
	
	@Override
	public void setDataSource(String path, boolean isLocalFile) throws IOException,
		IllegalArgumentException, SecurityException, IllegalStateException {
		mMediaPlayer.setDataSource(path);
	}

	@Override
	public void setDataSource(String path) throws IOException,
			IllegalArgumentException, SecurityException, IllegalStateException {
		mMediaPlayer.setDataSource(path);
	}

	@Override
	public void prepare() throws IOException, IllegalStateException {
		mMediaPlayer.prepare();
		
	}

	@Override
	public void prepareAsync() throws IllegalStateException {
		mMediaPlayer.prepareAsync();		
	}

	@Override
	public void start() throws IllegalStateException {
		mMediaPlayer.start();
	}

	@Override
	public void stop() throws IllegalStateException {
		mMediaPlayer.stop();
		
	}

	@Override
	public void pause() throws IllegalStateException {
		mMediaPlayer.pause();
		
	}

	@Override
	public void seekTo(int msec) throws IllegalStateException {
		mMediaPlayer.seekTo(msec);
	}

	@Override
	public int getCurrentPosition() {
		return mMediaPlayer.getCurrentPosition();
	}

	@Override
	public int getDuration() {
		return mMediaPlayer.getDuration();
	}
	
	@Override
	public void release() {
		mMediaPlayer.release();
	}

	@Override
	public void reset() {
		mMediaPlayer.reset();
	}

	@Override
	public void setVolume(float leftVolume, float rightVolume) {
		mMediaPlayer.setVolume(leftVolume, rightVolume);
	}
	
	@Override
	public void setAudioSessionId(int sessionId) {
		setAudioSessionIdCompat(mMediaPlayer, sessionId);
	}
	
	@Override
	public int getAudioSessionId() {
		return getAudioSessionIdCompat(mMediaPlayer);
	}
	
    private static Method sMethodRegisterSetAudioSessionId;
    private static Method sMethodRegisterGetAudioSessionId;
    
    private static void initializeStaticCompatMethods() {
        try {
        	sMethodRegisterGetAudioSessionId = MediaPlayer.class.getMethod(
                    "setAudioSessionId", int.class);
        	sMethodRegisterGetAudioSessionId = MediaPlayer.class.getMethod(
                    "getAudioSessionId");
        } catch (NoSuchMethodException e) {
            // Silently fail when running on an OS before API level 9.
        }
    }
    
    private static void setAudioSessionIdCompat(MediaPlayer mediaPlayer, int sessionId) {
		if (sMethodRegisterSetAudioSessionId == null) {
            return;
		}

        try {
        	sMethodRegisterSetAudioSessionId.invoke(mediaPlayer, sessionId);
        } catch (InvocationTargetException e) {
            // Unpack original exception when possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                // Unexpected checked exception; wrap and re-throw
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException invoking setAudioSessionId.");
            e.printStackTrace();
        }
    }
    
	private static int getAudioSessionIdCompat(MediaPlayer mediaPlayer) {
        int audioSessionId = 0;
		
		if (sMethodRegisterGetAudioSessionId == null) {
            return audioSessionId;
		}

        try {
        	audioSessionId = (Integer) sMethodRegisterGetAudioSessionId.invoke(mediaPlayer);
        } catch (InvocationTargetException e) {
            // Unpack original exception when possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                // Unexpected checked exception; wrap and re-throw
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException invoking getAudioSessionId.");
            e.printStackTrace();
        }
        
        return audioSessionId;
    }
	
	@Override
	public void setOnPreparedListener(OnPreparedListener listener) {
		mOnPreparedListener = listener;
		mMediaPlayer.setOnPreparedListener(onPreparedListener);
	}

	@Override
	public void setOnCompletionListener(OnCompletionListener listener) {
		mOnCompletionListener = listener;
		mMediaPlayer.setOnCompletionListener(onCompletionListener);
	}

	
	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		mOnErrorListener = listener;
		mMediaPlayer.setOnErrorListener(onErrorListener);
	}
	
    private MediaPlayer.OnPreparedListener onPreparedListener = new MediaPlayer.OnPreparedListener() {

		@Override
		public void onPrepared(MediaPlayer mp) {
			if (mOnPreparedListener != null) {
				mOnPreparedListener.onPrepared(NativePlayer.this);
			}
		}
    };
    
    private MediaPlayer.OnCompletionListener onCompletionListener = new MediaPlayer.OnCompletionListener() {

		@Override
		public void onCompletion(MediaPlayer mp) {
			if (mOnCompletionListener != null) {
				mOnCompletionListener.onCompletion(NativePlayer.this);
			}
		}
    };


    private MediaPlayer.OnErrorListener onErrorListener = new MediaPlayer.OnErrorListener() {

		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			if (mOnErrorListener != null) {
				mOnErrorListener.onError(NativePlayer.this, what, extra);
			}
			
			return false;
		}
    };
}
