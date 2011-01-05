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

package net.sourceforge.servestream.dbutils;

import java.net.MalformedURLException;
import java.net.URL;

public class Stream {

	private long rowID = -1;
	private String nickname;
	private String protocol;
	private String hostname;
	private String port;
	private String path;
	private String query;
	private long lastconnect = -1;
	private URL streamURL;
	
	/**
	 * Default constructor
	 */
	public Stream() {
		rowID = -1;
		nickname = "";
		protocol = "";
		hostname = "";
		port = "";
		path = "";
		query = "";
		lastconnect = -1;
		streamURL = null;
	}

	public void setID(long rowID) {
		this.rowID = rowID;
	}
	
	public long getId() {
		return rowID;
	}
	
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getNickname() {
		return nickname;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getHostname() {
		return hostname;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getPort() {
		return port;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return path;
	}
	
	public void setQuery(String query) {
		this.query = query;
	}

	public String getQuery() {
		return query;
	}
	
	public void setLastConnect(long lastconnect) {
		this.lastconnect = lastconnect;
	}

	public long getLastConnect() {
		return lastconnect;
	}
	
	public boolean createStream(String stringURL) {
		
		if (stringURL == null)
			return false;
		
		try {
			streamURL = new URL(stringURL);
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
			return false;
		}
		    
		// TODO keep or fix?
		//UrlValidator urlValidator = new UrlValidator();
		//return urlValidator.isValid(url);
		//if (!(URLUtil.isValidUrl(m_streamURL.toString()))) {
			setNickname(streamURL.toString());
			setProtocol(streamURL.getProtocol());
			setHostname(streamURL.getHost());
			
			if (streamURL.getPort() == -1) {
				setPort(String.valueOf(streamURL.getDefaultPort()));
			} else {
				setPort(String.valueOf(streamURL.getPort()));	
			}
			
			setPath(streamURL.getPath());
			
			setQuery(streamURL.getQuery());
		
		return true;
	}
	
	public String getStream() {
		return getProtocol() + "://" + getHostname() + ":" +
		       getPort() + getPath() + "?" + getQuery();
	}
	
	public URL getStreamURL() {
	 	return streamURL;
	}
	
}