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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class PlaylistHandler {
	public final static String TAG = "ServeStream.PlaylistHandler";

	private URL targetURL = null;
    private ArrayList<String> playlistFiles = null;
    
	/**
	 * Default constructor
	 */
	public PlaylistHandler(URL targetURL) {

		this.targetURL = targetURL;
		
		playlistFiles = new ArrayList<String>();
	}
    
	public void buildPlaylist() {
		
		String mediaFileName = targetURL.toString();
		
    	if (mediaFileName.length() > 4) {
    	    if (mediaFileName.substring(mediaFileName.length() - 4, mediaFileName.length()).equalsIgnoreCase(".m3u")) {
    	    	retrieveM3uFiles();
    	    }
    	    
    	    if (mediaFileName.contains(".pls")) {
    	    	retrievePsuFiles();
    	    }
    	}
	}
	
	/**
	 * Retrieves the files listed in a .m3u file
	 */
    public void retrieveM3uFiles() {
    	
		HttpURLConnection conn = null;
        String line = null;
        BufferedReader reader = null;;
        
        try {
        	
        	if (targetURL.getProtocol().equals("http")) {
        		conn = (HttpURLConnection) targetURL.openConnection();
        	} else if (targetURL.getProtocol().equals("https")) {
        		conn = (HttpsURLConnection) targetURL.openConnection();        		
        	}
        	
		    conn.setRequestMethod("GET");
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
        
		    while ((line = reader.readLine()) != null) {
		    	if (!(line.contains("#EXTM3U") || line.contains("#EXTINF"))) {
		    		playlistFiles.add(line);
		    	}           
            }

        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
        	closeReader(reader);
        	closeHttpConnection(conn);
        }
    }
    
    /**
	 * Retrieves the files listed in a .psu file
	 */
    public void retrievePsuFiles() {
    	
		HttpURLConnection conn = null;
        String line = null;
        BufferedReader reader = null;;
        
        try {
        	
        	if (targetURL.getProtocol().equals("http")) {
        		conn = (HttpURLConnection) targetURL.openConnection();
        	} else if (targetURL.getProtocol().equals("https")) {
        		conn = (HttpsURLConnection) targetURL.openConnection();        		
        	}
        	
    		conn.setConnectTimeout(6000);
    		conn.setReadTimeout(6000);
		    conn.setRequestMethod("GET");
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
        
		    while ((line = reader.readLine()) != null) {
		    	if (line.contains("File")) {
		    		String [] parsedLine = line.split("\\=");
		    		
		    		if (parsedLine.length == 2)
		    	        playlistFiles.add(parsedLine[1]);
		    	}           
            }

        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
        	closeReader(reader);
        	closeHttpConnection(conn);
        }
    }
    
    /**
     * Checks if a file is a playlist file
     * 
     * @param mediaFileName The file to check
     * @return boolean True if the file is a playlist, false otherwise
     */
    public static boolean isPlaylist(URL url) {
    	
    	if (url.getPath().length() > 4) {
    	    if (url.getPath().substring(url.getPath().length() - 4, url.getPath().length()).equalsIgnoreCase(".m3u")) {
    	    	return true;
    	    }
    	    
    	    if (url.getPath().contains(".pls")) {
    	    	return true;
    	    }
    	}
    	
    	return false;
    }
    
    /**
     * Returns the files in the playlist
     * 
     * @return ArrayList<String> An array containing the files from the playlist
     */
    public ArrayList<String> getPlayListFiles() {
    	return playlistFiles;
    }

	/**
	 * Closes a BufferedReader
	 * 
	 * @param reader The reader to close
	 */
    private void closeReader(BufferedReader reader) {
    	
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
	 * @param conn The connection to close
	 */
    private void closeHttpConnection(HttpURLConnection conn) {
    	
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
}

