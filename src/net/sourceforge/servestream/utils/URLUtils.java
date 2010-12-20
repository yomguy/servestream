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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class URLUtils {
	
	public final static int DIRECTORY = 100;
	public final static int MEDIA_FILE = 200;
	public final static int NOT_FOUND = -1;
	
	/*
	 * Default constructor
	 */
    public URLUtils() {
    	
    }
	
	public static int getContentTypeCode(String stream) {
		
		URL url = null;
		
		try {
		    url = new URL(stream);
		} catch (Exception ex) {
		}
		
		HttpURLConnection conn = null;
		
		String header = null;
		String contentType = null;
		
		boolean contentFound = false;
		
		int contentTypeCode = NOT_FOUND;
		
		try {
			
        	if (url.getProtocol().equals("http")) {
        		conn = (HttpURLConnection) url.openConnection();
        	} else if (url.getProtocol().equals("https")) {
        		conn = (HttpsURLConnection) url.openConnection();        		
        	}
			
		    //conn = (HttpURLConnection) url.openConnection();
	        conn.setRequestMethod("GET");
	    
            int i = 1;
            while ((header = conn.getHeaderField(i)) != null) {
                String key = conn.getHeaderFieldKey(i);
                if (key != null && key.equalsIgnoreCase("Content-type")) {
                	contentType = header;
                	contentFound = true;
                }
                i++;
            }
            
            if (contentFound) {
                //if (contentType.trim().equalsIgnoreCase("text/html")) {
            	//TODO fix this
            	if (contentType.contains("text/html")) {
        			contentTypeCode = DIRECTORY;
                } else {
        			contentTypeCode = MEDIA_FILE;
                }
            }
            
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			closeHttpConnection(conn);
		}
		
		return contentTypeCode;
	}
	
	/**
	 * Closes a HttpURLConnection
	 * 
	 * @param conn The connection to close
	 */
    private static void closeHttpConnection(HttpURLConnection conn) {
    	
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
	
}
