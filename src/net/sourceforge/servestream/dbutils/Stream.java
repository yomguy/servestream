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

	private long m_rowID = -1;
	private String m_nickname = null;
	private String m_protocol = null;
	private String m_hostname = null;
	private String m_port = null;
	private String m_path = null;
	private long m_lastconnect = -1;
	private URL m_streamURL = null;
	
	public Stream() {
		
	}

	public void setID(long m_rowID) {
		this.m_rowID = m_rowID;
	}
	
	public long getId() {
		return m_rowID;
	}
	
	public void setNickname(String m_nickname) {
		this.m_nickname = m_nickname;
	}

	public String getNickname() {
		return m_nickname;
	}

	public void setProtocol(String m_protocol) {
		this.m_protocol = m_protocol;
	}

	public String getProtocol() {
		return m_protocol;
	}

	public void setHostname(String m_hostname) {
		this.m_hostname = m_hostname;
	}

	public String getHostname() {
		return m_hostname;
	}

	public void setPort(String m_port) {
		this.m_port = m_port;
	}

	public String getPort() {
		return m_port;
	}

	public void setPath(String m_path) {
		this.m_path = m_path;
	}

	public String getPath() {
		return m_path;
	}

	public void setLastConnect(long m_lastconnect) {
		this.m_lastconnect = m_lastconnect;
	}

	public long getLastConnect() {
		return m_lastconnect;
	}
	
	public boolean createStream(String stringURL) {
		
		if (stringURL == null)
			return false;
		
		try {
			m_streamURL = new URL(stringURL);
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
			return false;
		}
		    
		// TODO keep or fix?
		//UrlValidator urlValidator = new UrlValidator();
		//return urlValidator.isValid(url);
		//if (!(URLUtil.isValidUrl(m_streamURL.toString()))) {
			this.setNickname(m_streamURL.toString());
			this.setProtocol(m_streamURL.getProtocol());
			this.setHostname(m_streamURL.getHost());
			
			if (m_streamURL.getPort() == -1) {
				this.setPort(String.valueOf(m_streamURL.getDefaultPort()));
			} else {
				this.setPort(String.valueOf(m_streamURL.getPort()));	
			}
			
			this.setPath(m_streamURL.getPath());
		
		return true;
	}
	
	public String getStream() {
		return this.getProtocol() + "://" + this.getHostname() + ":" +
		       this.getPort() + this.getPath();
	}
	
	public URL getStreamURL() {
	 	return m_streamURL;
	}
	
}