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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.transport.HTTP;
import net.sourceforge.servestream.transport.HTTPS;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.Utils;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

public class DownloadPlayer extends FFmpegPlayer {
	private static final String TAG = DownloadPlayer.class.getName();
	
	private long mTotalSizeInBytes = -1;
	
	private URL mUrl = null;
	private long mId = -1;
	private long mLength = -1;
	private File mPartialFile = null;
	private File mCompleteFile = null;
	private DownloadTask mDownloadTask = null;
	private PollingAsyncTask mPollingAsyncTask = null;
	
	public DownloadPlayer() {
		super();
	}
	
	@Override
	public void setDataSource(Context context, long id) throws IOException,
			IllegalArgumentException, SecurityException, IllegalStateException {
		mId = id;
		String path = getUri(context, id);		
		Uri uri = TransportFactory.getUri(path);

		if (uri == null ||
				(!uri.getScheme().equals(HTTP.getProtocolName()) &&
				!uri.getScheme().equals(HTTPS.getProtocolName()))) {
			throw new IllegalArgumentException();
		}
		
		UriBean uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
		
		if (uriBean == null) {
			throw new IllegalArgumentException();
		}
		
		mUrl = uriBean.getURL();
	}
	
	@Override
	public void prepareAsync() throws IllegalStateException {
		download();
	}
	
	@Override
	public void stop() {
		super.stop();
        cancelDownload();
	}
	
	@Override
	public void seekTo(int msec) throws IllegalStateException {
		if (isCompleteFileAvailable()) {
			super.seekTo(msec);
		}
	}
	
	@Override
	public int getDuration() {
		if (isCompleteFileAvailable()) {
			return super.getDuration();
		} else {
			return 0;
		}
	}
	
	@Override
	public void release() {
		super.release();
    	cancelDownload();
    	Utils.deleteAllFiles();
	}
	
	@Override
	public void reset() {
		super.reset();
    	cancelDownload();
    	Utils.deleteAllFiles();
	}
	
	private void download() {
		mTotalSizeInBytes = -1;
		mLength = -1;
		mPartialFile = new File(Utils.getDownloadDirectory(), "mediafile" + mId + ".partial.dat");
        mCompleteFile = new File(Utils.getDownloadDirectory(), "mediafile" + mId + ".complete.dat");
        Utils.deleteFile(mPartialFile);
        Utils.deleteFile(mCompleteFile);
        
        Log.v(TAG, "=============> " + mPartialFile.toString());
		mDownloadTask = new DownloadTask(mUrl, mPartialFile, mCompleteFile);
        mDownloadTask.execute();
        mPollingAsyncTask = new PollingAsyncTask();
        mPollingAsyncTask.execute();
	}
	
	public void cancelDownload() {
		if (mDownloadTask != null) {
	    	DownloadTask downloadTask = mDownloadTask;
	    	downloadTask.cancel();
	    	mDownloadTask = null;
		}
	}
	
	public void cancelPollingTask() {
		if (mPollingAsyncTask != null) {
			PollingAsyncTask pollingAsyncTask = mPollingAsyncTask;
			pollingAsyncTask.cancel();
	    	mPollingAsyncTask = null;
		}
	}
	
	public File getCompleteFile() {
		if (mCompleteFile != null && mCompleteFile.exists()) {
			return mCompleteFile;
		}
		
		return null;
	}
	
	public long getCompleteFileDuration() {
        long duration = 0;
        
    	MediaPlayer mediaPlayer = new MediaPlayer();
        try {
			mediaPlayer.setDataSource(getCompleteFile().toString());
			mediaPlayer.prepare();
			duration = mediaPlayer.getDuration();
			mediaPlayer.release();
		} catch (Exception e) {
		}
		
		return duration;
	}
	
	public File getPartialFile() {
		return mPartialFile;
	}
	
	public synchronized boolean isCompleteFileAvailable() {
		if (mCompleteFile != null && mCompleteFile.exists()) {
			return true;
		}
		
		return false;
	}
	
	public synchronized double getPercentDownloaded() {
		if (mCompleteFile != null && mCompleteFile.exists()) {
			return 1.0;
		}

		return (double) mPartialFile.length() / (double) mTotalSizeInBytes;
	}

	public synchronized boolean isDownloadCancelled() {
		return mDownloadTask != null && mDownloadTask.isCancelled();
	}
	
	public void delete() {
		cancelDownload();
		cancelPollingTask();
		Utils.deleteFile(mPartialFile);
		Utils.deleteFile(mCompleteFile);
	}
	
	/**
	 * @return the length
	 */
	public long getLength() {
		long length = -1;
		
		if (isCompleteFileAvailable()) {
			if (mLength == -1) {
				mLength = getCompleteFileDuration();
			}
			
			length = mLength;
		}
		
		return length;
	}
	
	private class DownloadTask implements Runnable {
		
		private boolean mIsCancelled;
	
		private URL mUrl = null;
		private File mPartialFile = null;
		private File mCompleteFile = null;
		
		public DownloadTask(URL url, File partialFile, File completeFile) {
	    	mIsCancelled = false;
	    	mUrl = url;
			mPartialFile = partialFile;
			mCompleteFile = completeFile;
		}
		
		@Override
		public void run() {
        	HttpURLConnection conn = null;
        	BufferedInputStream in = null;
            FileOutputStream out = null;
            boolean appendToFile = false;
            
            byte[] buffer = new byte[1024 * 16];
            //long count = 0;
            
			Log.v(TAG, "starting download task");
            while (!mCompleteFile.exists() && !isCancelled()) {
            	try {
                	conn = determineRange(mUrl, mPartialFile.length());
            
                	if (conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
                		appendToFile = true;
                	} else {
                		mTotalSizeInBytes = conn.getContentLength();
                	}
            
                	in = new BufferedInputStream(conn.getInputStream());
                	out = new FileOutputStream(mPartialFile, appendToFile);

                	int i;
                	while (((i = in.read(buffer)) != -1) && !isCancelled()) {
                		out.write(buffer, 0, i);
                		//count += i;
                	}
                	out.flush();
                	out.close();
                
                	Utils.copyFile(mPartialFile, mCompleteFile);
        			Log.v(TAG, "download task is complete");
        			
        			if (mOnInfoListener != null) {
        				mOnInfoListener.onInfo(DownloadPlayer.this, AbstractMediaPlayer.MEDIA_INFO_METADATA_UPDATE, 0);
        			}
            	} catch (IOException e) {
            		e.printStackTrace();
            	} finally {
            		Utils.closeInputStream(in);
            		Utils.closeHttpConnection(conn);
            		Utils.closeOutputStream(out);
            	}
            }
        }
	
        private HttpURLConnection determineRange(URL url, long bytesProcessed) {
        	HttpURLConnection conn = null;
		
        	try {
        		if (url.getProtocol().equalsIgnoreCase("http")) {
        			conn = (HttpURLConnection) url.openConnection();
        		} else if (url.getProtocol().equalsIgnoreCase("https")) {
        			conn = (HttpsURLConnection) url.openConnection();        		
        		}
    	
        		if (conn == null) {
        			return null;
        		}
		
        		conn.setRequestProperty("User-Agent", "ServeStream");
        		conn.setConnectTimeout(6000);
        		conn.setReadTimeout(6000);
        		conn.setRequestProperty("Range", "bytes=" + bytesProcessed + "-");
        		conn.setRequestMethod("GET");
        		conn.connect();
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
		
        	return conn;
        }
        
		private synchronized boolean isCancelled() {
			return mIsCancelled;
		}
		
		public synchronized void cancel() {
			mIsCancelled = true;
		}
		
		public synchronized void execute() {
			new Thread(this, "").start();
		}
	}
        
	private class PollingAsyncTask implements Runnable {
		
		private boolean mIsCancelled;
		
		//int INITIAL_BUFFER = Math.max(100000, 160 * 1024 / 8 * 5);
		int INITIAL_BUFFER = 81920;
		
	    public PollingAsyncTask() {
	    	mIsCancelled = false;
	    }
	    
		@Override
		public void run() {
			Log.v(TAG, "polling task started");
			
			while (!bufferingComplete() && !isCancelled()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			Log.v(TAG, "setDataSource called");
			try {
				DownloadPlayer.super.setDataSource(getPartialFile().getPath());
				DownloadPlayer.super.prepareAsync();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private boolean bufferingComplete() {
			return getPartialFile().length() >= INITIAL_BUFFER;
		}
		
		private synchronized boolean isCancelled() {
			return mIsCancelled;
		}
		
		public synchronized void cancel() {
			mIsCancelled = true;
		}
		
		public synchronized void execute() {
			new Thread(this, "").start();
		}
	}
	
	private String getUri(Context context, long id) {
		String uri = null;
		
		// Form an array specifying which columns to return. 
		String [] projection = new String [] { Media.MediaColumns.URI };

		// Get the base URI for the Media Files table in the Media content provider.
		Uri mediaFile =  Media.MediaColumns.CONTENT_URI;

		// Make the query.
		Cursor cursor = context.getContentResolver().query(mediaFile, 
				projection,
				Media.MediaColumns._ID + "= ? ",
				new String [] { String.valueOf(id) },
				null);    	
	
		if (cursor.moveToFirst()) {
			int uriColumn = cursor.getColumnIndex(Media.MediaColumns.URI);
			uri = cursor.getString(uriColumn);
		}
		
		cursor.close();
		
		return uri;
	}
}
