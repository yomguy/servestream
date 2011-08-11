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

import java.io.UnsupportedEncodingException;
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
	
	/**
	 * Default constructor
	 */
	public MediaFile() {
		
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

}
