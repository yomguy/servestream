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

package net.sourceforge.servestream.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

import android.media.MediaPlayer;
import android.os.AsyncTask;

public class MediaFile {

	private String mUrl = null;
	private int mTrackNumber = -1;
	private long mLength = -1;
	private String mPlaylistMetadata = null;
	private String mTrack = null;
	private String mArtist = null;
	private String mAlbum = null;

	private boolean mIsStreaming = true;
	private File mPartialFile = null;
	private File mCompleteFile = null;
	private DownloadTask mDownloadTask;
	
	/**
	 * Default constructor
	 */
	public MediaFile() {

	}

	public void download() {
		mIsStreaming = false;
		mPartialFile = new File(FileUtils.getDownloadDirectory(), "mediafile" + mTrackNumber + ".partial.dat");
        mCompleteFile = new File(FileUtils.getDownloadDirectory(), "mediafile" + mTrackNumber + ".complete.dat");
		mDownloadTask = new DownloadTask();
        mDownloadTask.execute();
	}
	
	public void cancelDownload() {
		if (mDownloadTask != null) {
			mDownloadTask.cancel(true);
		}
	}
	
	public File getCompleteFile() {
		if (mCompleteFile.exists())
			return mCompleteFile;
		
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
			return duration;
		}
		
		return duration;
	}
	
	public File getPartialFile() {
		return mPartialFile;
	}
	
	public boolean isStreaming() {
		return mIsStreaming;
	}
	
	public synchronized boolean isCompleteFileAvailable() {
		return mCompleteFile.exists();
	}
	
	public synchronized boolean isDownloadCancelled() {
		return mDownloadTask != null && mDownloadTask.isCancelled();
	}
	
	public void delete() {
		cancelDownload(); 
		FileUtils.deleteFile(mPartialFile);
		FileUtils.deleteFile(mCompleteFile);
	}
	
	/**
	 * @param url the url to set
	 */
	public void setURL(String url) {
		this.mUrl = url;
	}

	/**
	 * @return the url
	 */
	public String getURL() {
		return mUrl;
	}

	/**
	 * @param trackNumber the trackNumber to set
	 */
	public void setTrackNumber(int trackNumber) {
		this.mTrackNumber = trackNumber;
	}

	/**
	 * @return the trackNumber
	 */
	public int getTrackNumber() {
		return mTrackNumber;
	}
	
	/**
	 * @param length the length to set
	 */
	public void setLength(long length) {
		this.mLength = length;
	}

	/**
	 * @return the length
	 */
	public long getLength() {
		if (isStreaming())
			return mLength;
			
		if (isCompleteFileAvailable()) {
			if (mLength == -1)
				mLength = getCompleteFileDuration();
		}
		
		return mLength;
	}
	
	public void setPlaylistMetadata(String mPlaylistMetadata) {
		this.mPlaylistMetadata = mPlaylistMetadata;
	}

	public String getPlaylistMetadata() {
		return mPlaylistMetadata;
	}
	
	public void setTrack(String mTrack) {
		this.mTrack = mTrack;
	}

	public String getTrack() {
		return mTrack;
	}
	
	public void setArtist(String mArtist) {
		this.mArtist = mArtist;
	}

	public String getArtist() {
		return mArtist;
	}

	public void setAlbum(String mAlbum) {
		this.mAlbum = mAlbum;
	}

	public String getAlbum() {
		return mAlbum;
	}
	
	/**
	 * 
	 * @return
	 */
    public String getDecodedURL(){
		String decodedURL = null;
    	
    	try {
			decodedURL = URLDecoder.decode(mUrl, "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			decodedURL = "";
		}
		
		return decodedURL;
    }
	
	private class DownloadTask extends AsyncTask<Void, Void, Void> {
		
		@Override
		protected Void doInBackground(Void... params) {
        	HttpURLConnection conn = null;
        	BufferedInputStream in = null;
            FileOutputStream out = null;
            boolean appendToFile = false;
            
            byte[] buffer = new byte[1024 * 16];
            long count = 0;
            
            while (!mCompleteFile.exists()) {
            	try {
            	
                	conn = determineRange(new URL(mUrl), mPartialFile.length());
            
                	if (conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL)
                		appendToFile = true;            		
            
                	in = new BufferedInputStream(conn.getInputStream());
                	out = new FileOutputStream(mPartialFile, appendToFile);

                	int i;
                	while ((i = in.read(buffer)) != -1) {
                		out.write(buffer, 0, i);
                		count += i;
                	}
                	out.flush();
                	out.close();
                
                	FileUtils.copyFile(mPartialFile, mCompleteFile);
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
		
        	conn = URLUtils.getConnection(url);
		
        	if (conn == null)
        		return null;
		
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
}
