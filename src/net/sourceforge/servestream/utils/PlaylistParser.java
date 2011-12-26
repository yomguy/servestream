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

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.webkit.MimeTypeMap;

public abstract class PlaylistParser {
	public final static String TAG = PlaylistParser.class.getName();
	
	URL mPlaylistUrl = null;
    ArrayList<MediaFile> mPlaylistFiles = null;
    int mNumberOfFiles = 0;
	
    /**
     * Default constructor
     * 
     * @param playlistUrl A playlist Url
     */
	public PlaylistParser(URL playlistUrl) {
		this.mPlaylistUrl = playlistUrl;
		this.mPlaylistFiles = new ArrayList<MediaFile>();
	}
	
	/**
	 * Determines the extension of the specified Url and returns an instance of
	 * the appropriate PlaylistParser
	 * 
	 * @param url A playlist Url
	 * @return The appropriate PlaylistParser instance for the specified Url or
	 * NULL if the playlist type could not be determined
	 */
	public static PlaylistParser getPlaylistParser(URL url, String mimeType) {
		String extension = null;
		
		if (url == null)
			return null;
		
		extension = MimeTypeMap.getFileExtensionFromUrl(url.toString());
		
		if (extension == null)
			return null;
		
		if (extension.equalsIgnoreCase(ASXPlaylistParser.EXTENSION)
				|| mimeType.equalsIgnoreCase(ASXPlaylistParser.MIME_TYPE)) {
			return new ASXPlaylistParser(url);
		} else if (extension.equalsIgnoreCase(M3UPlaylistParser.EXTENSION)
				|| mimeType.equalsIgnoreCase(M3UPlaylistParser.MIME_TYPE)) {
			return new M3UPlaylistParser(url);
		} else if (extension.equalsIgnoreCase(M3U8PlaylistParser.EXTENSION)
				|| mimeType.equalsIgnoreCase(M3U8PlaylistParser.MIME_TYPE)) {
			return new M3U8PlaylistParser(url);
		} else if (extension.equalsIgnoreCase(PLSPlaylistParser.EXTENSION)
				|| mimeType.equalsIgnoreCase(PLSPlaylistParser.MIME_TYPE)) {
			return new PLSPlaylistParser(url);
		} else {
			return null;
		}
	}
	
    /**
     * This method should be implemented in each new playlist
     * subclass. This method should connect to the target Url and
     * parse the entries in the returned playlist
     */
    public abstract void retrieveAndParsePlaylist();
    
    /**
     * Returns the parsed playlist files
     * 
     * @return An array of MediaFile objects, one entry per playlist entry
     */
    public List<MediaFile> getPlaylistFiles() {
	    //MediaFile [] playlistFiles = new MediaFile[mPlaylistFiles.size()];
	    //return mPlaylistFiles.toArray(playlistFiles);
	    return mPlaylistFiles;
    }
}
