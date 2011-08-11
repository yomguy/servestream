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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public abstract class PlaylistParser {
	public final static String TAG = PlaylistParser.class.getName();
	
	private final String HTTP = "http";
	private final String HTTPS = "https";
	
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
	public static PlaylistParser getPlaylistParser(URL url) {
		String extension = null;
		
		if (url == null)
			return null;
		
		extension = getExtension(url.getPath());
		
		if (extension == null)
			return null;
		
		if (extension.equalsIgnoreCase(ASXPlaylistParser.EXTENSION)) {
			return new ASXPlaylistParser(url);
		} else if (extension.equalsIgnoreCase(M3UPlaylistParser.EXTENSION)) {
			return new M3UPlaylistParser(url);
		} else if (extension.equalsIgnoreCase(M3U8PlaylistParser.EXTENSION)) {
			return new M3U8PlaylistParser(url);
		} else if (extension.equalsIgnoreCase(PLSPlaylistParser.EXTENSION)) {
			return new PLSPlaylistParser(url);
		} else {
			return null;
		}
	}

	/**
	 * Extracts the extension from a file path
	 * 
	 * @param path A file path
	 * @return A String containing the extracted file extension
	 */
	private static final String getExtension(String path) {	
		int index = 0;
	
		index = path.lastIndexOf(".");
	
		if (index == -1 || (path.length() - (index + 1)) < 3)
			return null;
		
		return path.substring(index + 1, path.length());		
	}

	protected MediaFile retrieveMetadata(MediaFile mediaFile) {
		HttpURLConnection conn = null;
		InputStream inputStream = null;
		Mp3Parser mp3Parser = new Mp3Parser();
		BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
		
		String extension = getExtension(mediaFile.getURL());
		
		if (extension == null)
			return mediaFile;
		
		try {
			if (extension.equalsIgnoreCase("mp3")) {
				conn = getConnection(new URL(mediaFile.getURL()));
				conn.setConnectTimeout(6000);
				conn.setReadTimeout(6000);
    		
				inputStream = conn.getInputStream();
			
				try {
					mp3Parser.parse(inputStream, handler, metadata, new ParseContext());
				} finally {
					inputStream.close();
				}
		    
				mediaFile.setTrack(metadata.get(Metadata.TITLE));
				mediaFile.setArtist(metadata.get(XMPDM.ARTIST));
				mediaFile.setAlbum(metadata.get(XMPDM.ALBUM));					
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (TikaException e) {
			e.printStackTrace();
		}
		
		return mediaFile;
	}
	
    /**
     * This method should be implemented in each new playlist
     * subclass. This method should connect to the target Url and
     * parse the entries in the returned playlist
     */
    public abstract void retrieveAndParsePlaylist();
    
    protected HttpURLConnection getConnection(URL playlistUrl) throws IOException {
    	HttpURLConnection conn = null;
    	
    	/*String userInfo = playlistUrl.getUserInfo();
    	
    	if (userInfo != null && (userInfo.split("\\:").length == 2)) {
        	final String username = (userInfo.split("\\:")) [0] ;
        	final String password = (userInfo.split("\\:")) [1] ;
        	Authenticator.setDefault(new Authenticator() {
        		protected PasswordAuthentication getPasswordAuthentication() {
        			return new PasswordAuthentication(username, password.toCharArray()); 
        		};
        	});
    	}*/
    	
    	Authenticator.setDefault(new Authenticator() {
    		protected PasswordAuthentication getPasswordAuthentication() {
    			return new PasswordAuthentication(new String(), new String().toCharArray()); 
    		};
    	});
    	
    	if (playlistUrl.getProtocol().equalsIgnoreCase(HTTP)) {
    		conn = (HttpURLConnection) playlistUrl.openConnection();
    	} else if (playlistUrl.getProtocol().equalsIgnoreCase(HTTPS)) {
    		conn = (HttpsURLConnection) playlistUrl.openConnection();        		
    	}
    	
    	conn.setRequestProperty("User-Agent", URLUtils.USER_AGENT);
	    conn.setRequestMethod("GET");
    	
    	return conn;
    }
    
    /**
     * Returns the parsed playlist files
     * 
     * @return An array of MediaFile objects, one entry per playlist entry
     */
    public MediaFile [] getPlaylistFiles() {
	    MediaFile [] playlistFiles = new MediaFile[mPlaylistFiles.size()];
	    return mPlaylistFiles.toArray(playlistFiles);
    }
	
	/**
	 * Closes a BufferedReader
	 * 
	 * @param reader A BufferedReader
	 */
    protected void closeReader(BufferedReader reader) {
    	
    	if (reader == null)
    		return;

    	try {
    	    reader.close();
    	} catch (IOException ex) {
    		
    	}
    }
    
	/**
	 * Closes a HttpURLConnection
	 * 
	 * @param conn A HttpURLConnection to close
	 */
    protected void closeHttpConnection(HttpURLConnection conn) {
    	
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
}
