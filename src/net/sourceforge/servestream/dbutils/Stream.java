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

	private long id = -1;
	private String nickname;
	private String protocol;
	private String hostname;
	private String port;
	private String path;
	private String query;
	private long lastconnect = -1;
	private String color;
	private long fontsize;
	
	/**
	 * Default constructor
	 */
	public Stream() {
		id = -1;
		nickname = "";
		protocol = "";
		hostname = "";
		port = "";
		path = "";
		query = "";
		lastconnect = -1;
		color = "";
		fontsize = -1;
	}

	public void setID(long id) {
		this.id = id;
	}
	
	public long getId() {
		return id;
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

	public void setColor(String color) {
		this.color = color;
	}

	public String getColor() {
		return color;
	}
	
	public void setFontSize(long fontsize) {
		this.fontsize = fontsize;
	}

	public long getFontSize() {
		return fontsize;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof Stream))
			return false;

		Stream stream = (Stream) obj;

		if (id != -1 && stream.getId() != -1)
			return stream.getId() == id;

		if (nickname == null) {
			if (stream.getNickname() != null)
				return false;
		} else if (!nickname.equals(stream.getNickname()))
			return false;

		if (protocol == null) {
			if (stream.getProtocol() != null)
				return false;
		} else if (!protocol.equals(stream.getProtocol()))
			return false;

		if (hostname == null) {
			if (stream.getHostname() != null)
				return false;
		} else if (!hostname.equals(stream.getHostname()))
			return false;

		if (port == null) {
			if (stream.getPort() != null)
				return false;
		} else if (!port.equals(stream.getPort()))
			return false;

		if (path == null) {
			if (stream.getPath() != null)
				return false;
		} else if (!path.equals(stream.getPath()))
			return false;
		
		if (query == null) {
			if (stream.getQuery() != null)
				return false;
		} else if (!query.equals(stream.getQuery()))
			return false;

		return true;
	}
	
	@Override
	public int hashCode() {
		int hash = 7;

		if (id != -1)
			return (int) id;

		hash = 31 * hash + (null == nickname ? 0 : nickname.hashCode());
		hash = 31 * hash + (null == protocol ? 0 : protocol.hashCode());
		hash = 31 * hash + (null == hostname ? 0 : hostname.hashCode());
		hash = 31 * hash + (null == port ? 0 : port.hashCode());
		hash = 31 * hash + (null == path ? 0 : path.hashCode());
		hash = 31 * hash + (null == query ? 0 : query.hashCode());

		return hash;
	}
	
	public boolean createStream(String stringURL) {
		
		URL url;
		
		if (stringURL == null)
			return false;
		
		try {
			url = new URL(stringURL);
		} catch (MalformedURLException ex) {
			ex.printStackTrace();
			return false;
		}
		    
		// TODO keep or fix?
		//UrlValidator urlValidator = new UrlValidator();
		//return urlValidator.isValid(url);
		//if (!(URLUtil.isValidUrl(m_streamURL.toString()))) {
			nickname = url.toString();
			protocol = url.getProtocol();
			hostname = url.getHost();
			
			if (url.getPort() == -1) {
				port = String.valueOf(url.getDefaultPort());
			} else {
				port = String.valueOf(url.getPort());	
			}
			
			path = url.getPath();
			
			if (url.getQuery() == null) {
			    query = "";
			} else {
			    query = url.getQuery();
			}
		
		return true;
	}
	
	public String getStreamURL() {
		
		StringBuilder sb = new StringBuilder();
		sb.append(protocol)
			.append("://");

		sb.append(hostname)
			.append(':')
			.append(port)
			.append(path);
		
		if (!query.equals(""))
		    sb.append('?')
				.append(query);
		
		return (sb.toString());
	}
}