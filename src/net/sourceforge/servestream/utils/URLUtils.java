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
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;

public class URLUtils {
	
	public static final String HTTP = "http";
	public static final String HTTPS = "https";
	
	public final static String USER_AGENT = "ServeStream";
	
	public final static int NOT_FOUND = -1;
	
	private int mResponseCode = -1;
	private String mContentType = "";
	private boolean mContentFound = false;
	
	/*
	 * Default constructor
	 */
    public URLUtils(URL url) {
    	getURLInformation(url);
    }
	
	private void getURLInformation(URL url) {
		
	    HttpURLConnection conn = null;
	    
		try {
			
			if (url == null)
				return;
			
		    if (url.getUserInfo() != null) {
		    	String [] userInfo = url.getUserInfo().split("\\:");
		    	
		    	if (userInfo.length == 2) {
		    		final String username = userInfo[0];
		    		final String password = userInfo[1];
		    		Authenticator.setDefault(new Authenticator() {
		    			protected PasswordAuthentication getPasswordAuthentication() {
		    				return new PasswordAuthentication(username, password.toCharArray());
		    			}  
		    		});
		    	}
		    }
			
        	if (url.getProtocol().equals("http")) {
        		conn = (HttpURLConnection) url.openConnection();
        	} else if (url.getProtocol().equals("https")) {
        		conn = (HttpsURLConnection) url.openConnection();        		
        	}
			
        	if (conn == null)
        		return;
        	
        	conn.setRequestProperty("User-Agent", USER_AGENT);
    		conn.setConnectTimeout(6000);
    		conn.setReadTimeout(6000);
	        conn.setRequestMethod("GET");
	        
	        mResponseCode = conn.getResponseCode();
	    
	        if (mResponseCode == -1)
	        	mResponseCode = HttpURLConnection.HTTP_OK;
	        
	        if ((mContentType = conn.getContentType()) != null)
	        	mContentFound = true;
            
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (IllegalArgumentException ex) {
		    ex.printStackTrace();
		} finally {
			closeHttpConnection(conn);
		}
		
	}
	
	public static String getContentType(String path) {
		
		HashMap<String, String> mimeTypes = new HashMap<String, String>();
		
		mimeTypes.put("amr","audio");
		mimeTypes.put("awb","audio");
		mimeTypes.put("amr","audio");
		mimeTypes.put("awb","audio");
		mimeTypes.put("axa","audio");
		mimeTypes.put("au","audio");
		mimeTypes.put("snd","audio");
		mimeTypes.put("flac","audio");
		mimeTypes.put("mid","audio");
		mimeTypes.put("midi","audio");
		mimeTypes.put("kar","audio");
		mimeTypes.put("mpga","audio");
		mimeTypes.put("mpega","audio");
		mimeTypes.put("mp2","audio");
		mimeTypes.put("mp3","audio");
		mimeTypes.put("m4a","audio");
		mimeTypes.put("m3u","audio");
		mimeTypes.put("oga","audio");
		mimeTypes.put("ogg","audio");
		mimeTypes.put("spx","audio");
		mimeTypes.put("sid","audio");
		mimeTypes.put("aif","audio");
		mimeTypes.put("aiff","audio");
		mimeTypes.put("aifc","audio");
		mimeTypes.put("gsm","audio");
		mimeTypes.put("wma","audio");
		mimeTypes.put("wmx","audio");
		mimeTypes.put("ra","audio");
		mimeTypes.put("rm","audio");
		mimeTypes.put("ram","audio");
		mimeTypes.put("pls","audio");
		mimeTypes.put("sd2","audio");
		mimeTypes.put("wav","audio");
		mimeTypes.put("text", "html");
		mimeTypes.put("3gp","video");
		mimeTypes.put("axv","video");
		mimeTypes.put("dl","video");
		mimeTypes.put("dif","video");
		mimeTypes.put("dv","video");
		mimeTypes.put("fli","video");
		mimeTypes.put("gl","video");
		mimeTypes.put("mpeg","video");
		mimeTypes.put("mpg","video");
		mimeTypes.put("mpe","video");
		mimeTypes.put("mp4","video");
		mimeTypes.put("qt","video");
		mimeTypes.put("mov","video");
		mimeTypes.put("ogv","video");
		mimeTypes.put("mxu","video");
		mimeTypes.put("flv","video");
		mimeTypes.put("lsf","video");
		mimeTypes.put("lsx","video");
		mimeTypes.put("mng","video");
		mimeTypes.put("asf","video");
		mimeTypes.put("asx","video");
		mimeTypes.put("wm","video");
		mimeTypes.put("wmv","video");
		mimeTypes.put("wmx","video");
		mimeTypes.put("avi","video");
		mimeTypes.put("movie","video");
		mimeTypes.put("mpv","video");
		mimeTypes.put("mkv","video");
		
    	int index = 0;
    	
    	if (path == null)
    	    return null;
    	
        index = path.lastIndexOf(".");
    		
    	if (index == -1) {
    		index = path.lastIndexOf("/");

    		if (index == path.length() - 1) {
    			return "text";
    		} else {
    			return null;
    		}
    	}
    	
    	return mimeTypes.get(path.substring(index + 1, path.length()));
	}
	
	/**
	 * Creates a HttpURLConnection using the specific URL
	 * 
	 * @param url The URL to create a HttpURLConnection to
	 * @return A HttpURLConnection if successful, null otherwise
	 */
	public static HttpURLConnection getConnection(URL url) {
		HttpURLConnection conn = null;
		
		if (url == null)
			return null;
    	
    	String userInfo = url.getUserInfo();
    	
    	if (userInfo != null && (userInfo.split("\\:").length == 2)) {
        	final String username = (userInfo.split("\\:")) [0] ;
        	final String password = (userInfo.split("\\:")) [1] ;
        	Authenticator.setDefault(new Authenticator() {
        		protected PasswordAuthentication getPasswordAuthentication() {
        			return new PasswordAuthentication(username, password.toCharArray()); 
        		};
        	});
    	}
    	
    	try {
    		if (url.getProtocol().equalsIgnoreCase(HTTP)) {
    			conn = (HttpURLConnection) url.openConnection();
    		} else if (url.getProtocol().equalsIgnoreCase(HTTPS)) {
    			conn = (HttpsURLConnection) url.openConnection();        		
    		}
    	
    		conn.setRequestProperty("User-Agent", URLUtils.USER_AGENT);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	return conn;
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
    
    /**
     * Returns the response code from the URL
     * 
     * @return int The response code from the URL
     */
	public int getResponseCode() {
	    return mResponseCode;
	}

    /**
     * Returns the content type from the URL
     * 
     * @return String The content type from the URL
     */
	public String getContentType() {
		return mContentType;
	}
}
