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

public class MediaFile {

	private String mUrl = null;
	private String mTitle = null;
	private String mAlbum = null;
	private String mArtist = null;
	private long mDuration = -1;
	private int mTrack = -1;
	private int mYear = -1;
	private String mPlaylistMetadata = null;

	/**
	 * Default constructor
	 */
	public MediaFile() {

	}

	/**
	 * @return the mUrl
	 */
	public String getUrl() {
		return mUrl;
	}

	/**
	 * @param mUrl the mUrl to set
	 */
	public void setUrl(String mUrl) {
		this.mUrl = mUrl;
	}

	/**
	 * @return the mTitle
	 */
	public String getTitle() {
		return mTitle;
	}

	/**
	 * @param mTitle the mTitle to set
	 */
	public void setTitle(String mTitle) {
		this.mTitle = mTitle;
	}

	/**
	 * @return the mAlbum
	 */
	public String getAlbum() {
		return mAlbum;
	}

	/**
	 * @param mAlbum the mAlbum to set
	 */
	public void setAlbum(String mAlbum) {
		this.mAlbum = mAlbum;
	}

	/**
	 * @return the mArtist
	 */
	public String getArtist() {
		return mArtist;
	}

	/**
	 * @param mArtist the mArtist to set
	 */
	public void setArtist(String mArtist) {
		this.mArtist = mArtist;
	}

	/**
	 * @return the mDuration
	 */
	public long getDuration() {
		return mDuration;
	}

	/**
	 * @param mDuration the mDuration to set
	 */
	public void setDuration(long mDuration) {
		this.mDuration = mDuration;
	}

	/**
	 * @return the mTrack
	 */
	public int getTrack() {
		return mTrack;
	}

	/**
	 * @param mTrack the mTrack to set
	 */
	public void setTrack(int mTrack) {
		this.mTrack = mTrack;
	}

	/**
	 * @return the mYear
	 */
	public int getYear() {
		return mYear;
	}

	/**
	 * @param mYear the mYear to set
	 */
	public void setYear(int mYear) {
		this.mYear = mYear;
	}

	/**
	 * @return the mPlaylistMetadata
	 */
	public String getPlaylistMetadata() {
		return mPlaylistMetadata;
	}

	/**
	 * @param mPlaylistMetadata the mPlaylistMetadata to set
	 */
	public void setPlaylistMetadata(String mPlaylistMetadata) {
		this.mPlaylistMetadata = mPlaylistMetadata;
	}
	
	/**
	 * 
	 * @return
	 */
    public String getDecodedURL(){
		String decodedURL = null;
    	
    	try {
			decodedURL = URLDecoder.decode(getUrl(), "UTF-8");
		} catch (UnsupportedEncodingException ex) {
			ex.printStackTrace();
			decodedURL = "";
		}
		
		return decodedURL;
    }
}
