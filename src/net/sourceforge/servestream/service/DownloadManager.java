package net.sourceforge.servestream.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.utils.Utils;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class DownloadManager {
	private static final String TAG = DownloadManager.class.getName();

	private MediaPlaybackService mMediaPlaybackService = null;

	private long mTotalSizeInBytes = -1;
	
	private long mLength = -1;
	private File mPartialFile = null;
	private File mCompleteFile = null;
	private DownloadTask mDownloadTask = null;
	private PollingAsyncTask mPollingAsyncTask = null;
	
	public DownloadManager(MediaPlaybackService mediaPlaybackService) {
		mMediaPlaybackService = mediaPlaybackService;
	}
	
	public void download(long id) {
		URL url = null;
		String uri = getUri(mMediaPlaybackService, id);
		
		if (uri != null) {
			try {
				url = new URL(uri);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				return;
			}
		}

		mTotalSizeInBytes = -1;
		mLength = -1;
		mPartialFile = new File(Utils.getDownloadDirectory(), "mediafile" + id + ".partial.dat");
        mCompleteFile = new File(Utils.getDownloadDirectory(), "mediafile" + id + ".complete.dat");
        Utils.deleteFile(mPartialFile);
        Utils.deleteFile(mCompleteFile);
        
        Log.v(TAG, "=============> " + mPartialFile.toString());
		mDownloadTask = new DownloadTask(mPartialFile, mCompleteFile);
        mDownloadTask.execute(url);
        mPollingAsyncTask = new PollingAsyncTask();
        mPollingAsyncTask.execute();
	}
	
	public void cancelDownload() {
		if (mDownloadTask != null) {
	    	DownloadTask downloadTask = mDownloadTask;
	    	downloadTask.cancel(false);
	    	mDownloadTask = null;
		}
	}
	
	public void cancelPollingTask() {
		if (mPollingAsyncTask != null) {
			PollingAsyncTask pollingAsyncTask = mPollingAsyncTask;
			pollingAsyncTask.cancel(false);
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
	
	private class DownloadTask extends AsyncTask<URL, Void, Void> {
		
		private File mPartialFile = null;
		private File mCompleteFile = null;
		
		public DownloadTask(File partialFile, File completeFile) {
			mPartialFile = partialFile;
			mCompleteFile = completeFile;
		}
		
		@Override
		protected Void doInBackground(URL... url) {
        	HttpURLConnection conn = null;
        	BufferedInputStream in = null;
            FileOutputStream out = null;
            boolean appendToFile = false;
            
            byte[] buffer = new byte[1024 * 16];
            //long count = 0;
            
			Log.v(TAG, "starting download task");
            while (!mCompleteFile.exists() && !isCancelled()) {
            	try {
                	conn = determineRange(url[0], mPartialFile.length());
            
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
            	} catch (IOException e) {
            		e.printStackTrace();
            	} finally {
            		Utils.closeInputStream(in);
            		Utils.closeHttpConnection(conn);
            		Utils.closeOutputStream(out);
            	}
            }
            
            return null;
        }
	
        private HttpURLConnection determineRange(URL url, long bytesProcessed) {
        	HttpURLConnection conn = null;
		
        	conn = URLUtils.getConnection(null, url);
		
        	if (conn == null) {
        		return null;
        	}
		
        	conn.setConnectTimeout(6000);
        	conn.setReadTimeout(6000);
        	conn.setRequestProperty("Range", "bytes=" + bytesProcessed + "-");
		
        	try {
        		conn.setRequestMethod("GET");
        		conn.connect();
        	} catch (IOException e) {
        		e.printStackTrace();
        	}
		
        	return conn;
        }
	}
        
	private class PollingAsyncTask extends AsyncTask<Void, Void, Void> {
		
		//int INITIAL_BUFFER = Math.max(100000, 160 * 1024 / 8 * 5);
		int INITIAL_BUFFER = 81920;
		
	    public PollingAsyncTask() {
	        super();
	    }
	    
		@Override
		protected Void doInBackground(Void... stream) {  
			Log.v(TAG, "polling task started");
			
			while (!bufferingComplete() && !isCancelled()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			Log.v(TAG, "setDataSource called");
			mMediaPlaybackService.getMediaPlayer().setDataSource(getPartialFile().getPath(), true);
			
			return null;
		}
		
		private boolean bufferingComplete() {
			return getPartialFile().length() >= INITIAL_BUFFER;
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