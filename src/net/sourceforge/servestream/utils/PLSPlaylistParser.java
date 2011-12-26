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
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;

public class PLSPlaylistParser extends PlaylistParser {
	public final static String TAG = PLSPlaylistParser.class.getName();

	public final static String EXTENSION = "pls";
	public final static String MIME_TYPE = "audio/x-scpls";
    
    private MediaFile mediaFile = null;
    private boolean processingEntry = false;
    
	/**
	 * Default constructor
	 */
	public PLSPlaylistParser(URL playlistUrl) {
		super(playlistUrl);
	}
	
	/**
	 * Retrieves the files listed in a .pls file
	 */
    public void retrieveAndParsePlaylist() {
    	
    	if (mPlaylistUrl == null)
    		return;
    	
		HttpURLConnection conn = null;
        String line = null;
        BufferedReader reader = null;;
        
        try {
        	conn = URLUtils.getConnection(mPlaylistUrl);
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
		    
		    while ((line = reader.readLine()) != null) {
		    	if (!line.trim().equals("")) {
			    	String [] parsedLine = line.split("\\=");
			    	
			    	if (parsedLine.length == 2) {
                        if (parsedLine[0].trim().contains("File")) {
                        	if (processingEntry)
                                savePlaylistFile();
                        	else
                        		processingEntry = true;
                        	
        		    		mediaFile = new MediaFile();
        		    		
    		    	    	try {
    							mediaFile.setURL(URLDecoder.decode(parsedLine[1].trim(), "UTF-8"));
    						} catch (UnsupportedEncodingException e) {
    							mediaFile.setURL(parsedLine[1].trim());
    						}
                        } else if (parsedLine[0].trim().contains("Title")) {
                            mediaFile.setPlaylistMetadata(parsedLine[1].trim());
                        } else if (parsedLine[0].trim().contains("Length")) {
                            savePlaylistFile();
                        }			
			    	}
		    	}           
            }
		    
		    // added in case the file doesn't follow the standard pls
		    // structure:
		    // FileX:
		    // TitleX:
		    // LengthX:
        	if (processingEntry) {
                savePlaylistFile();
        	}
        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
        	Utils.closeBufferedReader(reader);
        	Utils.closeHttpConnection(conn);
        }
    }
    
    public void savePlaylistFile() {
    	mNumberOfFiles = mNumberOfFiles + 1;
    	mediaFile.setTrackNumber(mNumberOfFiles);
    	mPlaylistFiles.add(mediaFile);
    	processingEntry = false;
    }
}

