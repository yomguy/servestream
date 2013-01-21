package net.sourceforge.servestream.player;

import java.io.IOException;

import android.media.MediaPlayer;

public class NativePlayer extends AbstractMediaPlayer {

	private MediaPlayer mMediaPlayer;
	
	public NativePlayer() {
		super();
		mMediaPlayer = new MediaPlayer();
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
	public void setOnPreparedListener(OnPreparedListener listener) {
		mOnPreparedListener = listener;
		mMediaPlayer.setOnPreparedListener(onPreparedListener);
	}

    private OnPreparedListener mOnPreparedListener;
	
	@Override
	public void setOnCompletionListener(OnCompletionListener listener) {
		mOnCompletionListener = listener;
		mMediaPlayer.setOnCompletionListener(onCompletionListener);
	}

    private OnCompletionListener mOnCompletionListener;

	
	@Override
	public void setOnErrorListener(OnErrorListener listener) {
		mOnErrorListener = listener;
		mMediaPlayer.setOnErrorListener(onErrorListener);
	}
	
    private OnErrorListener mOnErrorListener;
    
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
