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

public class PlaylistHandler {

	URL m_targetURL = null;
    ArrayList<String> m_playlistFiles = null;
    
	/**
	 * Default constructor
	 */
	public PlaylistHandler(String targetURL) {

		try {
		m_targetURL = new URL(targetURL);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		m_playlistFiles = new ArrayList<String>();
	}
    
	/**
	 * Retrieve the files listed in the m3u file
	 */
    public void buildPlaylist() {
    	
		HttpURLConnection conn = null;
        String line = null;
        BufferedReader reader = null;;
        
        try {
		    conn = (HttpURLConnection) m_targetURL.openConnection();
		    conn.setRequestMethod("GET");
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
        
		    while ((line = reader.readLine()) != null) {
		    	if (!(line.contains("#EXTM3U") || line.contains("#EXTINF"))) {
		    		m_playlistFiles.add(line);
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
     * 
     */
    public ArrayList<String> getPlayListFiles() {
    	return m_playlistFiles;
    }

    private void closeReader(BufferedReader reader) {
    	
    	if (reader == null)
    		return;

    	try {
    	    reader.close();
    	} catch (IOException ex) {
    		
    	}
    }
    
    private void closeHttpConnection(HttpURLConnection conn) {
    	
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
	
}

