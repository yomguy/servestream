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

import javax.net.ssl.HttpsURLConnection;

import android.webkit.MimeTypeMap;

public class URLUtils {
	
	private static final MimeTypeMap mMimeTypeMap = MimeTypeMap.getSingleton();
	
	public static final String HTTP = "http";
	public static final String HTTPS = "https";
	
	public final static String USER_AGENT = "ServeStream";
	
	public final static int NOT_FOUND = -1;
	
	private int mResponseCode = -1;
	private String mContentType = "";
	
	/*
	 * Default constructor
	 */
    public URLUtils(URL url) {
    	getURLInformation(url);
    }
	
	private void getURLInformation(URL url) {
		
	    HttpURLConnection conn = null;
	    
		try {
			
			if (url == null) {
				return;
			}
			
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
			
        	if (conn == null) {
        		return;
        	}
        	
        	conn.setRequestProperty("User-Agent", USER_AGENT);
    		conn.setConnectTimeout(6000);
    		conn.setReadTimeout(6000);
	        conn.setRequestMethod("GET");
	        
	        mResponseCode = conn.getResponseCode();
	    
	        if (mResponseCode == -1) {
	        	mResponseCode = HttpURLConnection.HTTP_OK;
	        }

		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (IllegalArgumentException ex) {
		    ex.printStackTrace();
		} finally {
			Utils.closeHttpConnection(conn);
		}
		
	}
	
	public static String getContentType(String url) {
		String extension = null;
		String contentType = null;
		
		if (url == null) {
			return null;
		}
		
		extension = MimeTypeMap.getFileExtensionFromUrl(url);
    
		if (extension.equals("")) {
			if (url.lastIndexOf("/") == (url.length() - 1)) {
				contentType = "text/html";
			}
		} else {
			contentType = mMimeTypeMap.getMimeTypeFromExtension(extension);
		}
		
    	return contentType;
	}
	
	/**
	 * Creates a HttpURLConnection using the specific URL
	 * 
	 * @param url The URL to create a HttpURLConnection to
	 * @return A HttpURLConnection if successful, null otherwise
	 */
	public static HttpURLConnection getConnection(URL url) {
		HttpURLConnection conn = null;
		
		if (url == null) {
			return null;
		}
    	
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
