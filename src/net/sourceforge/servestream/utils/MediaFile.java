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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class MediaFile implements Parcelable {

	private String mUrl = null;
	private int trackNumber = -1;
	private long length = -1;
	private String mPlaylistMetadata = null;
	private String mTrack = null;
	private String mArtist = null;
	private String mAlbum = null;
	
	private File mPartialFile = null;
	private File mCompleteFile = null;
	private DownloadTask mDownloadTask;
	
	/**
	 * Default constructor
	 */
	public MediaFile() {
        mPartialFile = new File(FileUtils.getDownloadDirectory(), "mediaFile.partial.dat");
        mCompleteFile = new File(FileUtils.getDownloadDirectory(), "mediaFile.complete.dat");
	}

	public void startDownload() {
        delete();
        mDownloadTask = new DownloadTask();
        mDownloadTask.start();
	}
	
	public void cancelDownload() {
		if (mDownloadTask != null) {
			mDownloadTask.cancel();
		}
	}
	
	public File getPartialFile() {
		return mPartialFile;
	}
	
	public File getCompleteFile() {
		if (mCompleteFile.exists())
			return mCompleteFile;
		
		return null;
	}
	
	public void delete() {
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
		this.trackNumber = trackNumber;
	}

	/**
	 * @return the trackNumber
	 */
	public int getTrackNumber() {
		return trackNumber;
	}
	
	/**
	 * @param length the length to set
	 */
	public void setLength(long length) {
		this.length = length;
	}

	/**
	 * @return the length
	 */
	public long getLength() {
		return length;
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
    
	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {
		
	}

	public static final Parcelable.Creator<MediaFile> CREATOR = new
	Parcelable.Creator<MediaFile>() {
	    public MediaFile createFromParcel(Parcel in) {
	    	Log.v("ParcelableTest","Creating from parcel");
	    	return new MediaFile();
	    }

	    public MediaFile[] newArray(int size) {
	    	return new MediaFile[size];
	    }
	};
	
	private class DownloadTask extends Thread {

		private boolean mCancelled = false;
		
        public void run() {
        	HttpURLConnection conn = null;
        	BufferedInputStream in = null;
            FileOutputStream out = null;
            boolean appendToFile = false;
            
            byte[] buffer = new byte[1024 * 16];
            long count = 0;
            
            try {
                if (mCompleteFile.exists())
                    return;
            	
            	conn = determineRange(new URL(mUrl), mPartialFile.length());
            
            	if (conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL)
            		appendToFile = true;            		
            
				in = new BufferedInputStream(conn.getInputStream());
	            out = new FileOutputStream(mPartialFile, appendToFile);

				int i;
				while (!mCancelled && (i = in.read(buffer)) != -1) {
	                out.write(buffer, 0, i);
	                count += i;
	            }
                out.flush();
                out.close();
                
                FileUtils.copyFile(mPartialFile, mCompleteFile);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				Utils.closeHttpConnection(conn);
				Utils.closeInputStream(in);
				Utils.closeOutputStream(out);
			}
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
	
        private void cancel() {
        	mCancelled = true;
        }
	}
}
