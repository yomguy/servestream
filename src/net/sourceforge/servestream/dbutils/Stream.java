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

import android.net.Uri;

public class Stream {

	private static String DEFAULT_HTTP_PORT = "80";
	private static String DEFAULT_HTTPS_PORT = "443";
	
	private long id = -1;
	private String nickname = null;
	private String protocol = null;
	private String username = null;
	private String password = null;
	private String hostname = null;
	private String port = null;
	private String path = null;
	private String query = null;
	private String reference = null;
	private long lastconnect = -1;
	private String contentType = null;
	private String color = null;
	private long fontsize;
	
	/**
	 * Default constructor
	 * 
	 * @throws MalformedURLException 
	 */
	public Stream() {
	
	}

	public Stream(String URLString) throws MalformedURLException {
		
		if (URLString == null)
			throw new MalformedURLException();
		
		URL url = new URL(URLString);
		
		this.nickname = url.toString();
		this.protocol = url.getProtocol();
		
		if (protocol != null && 
			!protocol.equalsIgnoreCase("http") && 
			!protocol.equalsIgnoreCase("https"))
				throw new MalformedURLException();
		
		if (url.getUserInfo() != null) {
			String [] authInfo = url.getUserInfo().split("\\:");
			
			if (authInfo.length == 2) {
				this.username = authInfo[0];
				this.password = authInfo[1];
			} else {
				this.username = "";
				this.password = "";
			}
		} else {
			this.username = "";
			this.password = "";
		}
		
		this.hostname = url.getHost();
		
		if (url.getPort() == -1) {
			if (protocol.equalsIgnoreCase("http")) {
				this.port = DEFAULT_HTTP_PORT;
			} else if (protocol.equalsIgnoreCase("https")) {
				this.port = DEFAULT_HTTPS_PORT;
			}
		} else {
			this.port = String.valueOf(url.getPort());	
		}
		
		this.path = url.getPath();
		
		if (url.getQuery() != null) {
			this.query = url.getQuery();
		} else {
			this.query = "";
		}
		
		if (url.getRef() != null) {
			this.reference = url.getRef();
		} else {
			this.reference = "";
		}
		
		// the following code is used as a temporary fix to deal with Android's
		// handling of URL's that contain "special characters" such as "[" (Issue 12724)
		String [] split = hostname.split("\\:");
		
		if (split.length == 2) {
			hostname = split[0];
			port = split[1];
		}
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

	public void setUsername(String username) {
		this.username = username;
	}

	public String getUsername() {
		return username;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}

	public String getPassword() {
		return password;
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
	
	public void setReference(String reference) {
		this.reference = reference;
	}
	
	public String getReference() {
		return reference;
	}
	
	public void setLastConnect(long lastconnect) {
		this.lastconnect = lastconnect;
	}

	public long getLastConnect() {
		return lastconnect;
	}

	public String getContentType() {
		return contentType;
	}
	
	public void setContentType(String contentType) {
		this.contentType = contentType;
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

		if (username == null) {
			if (stream.getUsername() != null)
				return false;
		} else if (!username.equals(stream.getUsername()))
			return false;
		
		if (password == null) {
			if (stream.getPassword() != null)
				return false;
		} else if (!password.equals(stream.getPassword()))
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

		if (reference == null) {
			if (stream.getReference() != null)
				return false;
		} else if (!reference.equals(stream.getReference()))
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
		hash = 31 * hash + (null == username ? 0 : username.hashCode());
		hash = 31 * hash + (null == password ? 0 : password.hashCode());
		hash = 31 * hash + (null == hostname ? 0 : hostname.hashCode());
		hash = 31 * hash + (null == port ? 0 : port.hashCode());
		hash = 31 * hash + (null == path ? 0 : path.hashCode());
		hash = 31 * hash + (null == query ? 0 : query.hashCode());
		hash = 31 * hash + (null == reference ? 0 : reference.hashCode());

		return hash;
	}
	
	/**
	 * Returns a Uri representing the media stream
	 * 
	 * @return Uri The Uri representing this media stream
	 */
	public Uri getUri() {
		
		StringBuilder sb = new StringBuilder();
		sb.append(protocol)
			.append("://");

		if (!username.equals("")) {
		    sb.append(username)
		    .append(":")
		    .append(password)
		    .append("@");
		}
		
		sb.append(hostname)
			.append(':')
			.append(port)
			.append(path);
		
		if (!query.equals("")) {
		    sb.append('?')
			.append(query);
		}
		
		if (!reference.equals("")) {
		    sb.append('#')
			.append(reference);
		}
		
		return Uri.parse(sb.toString());
	}
	
	/**
	 * Returns a URL representing the media stream
	 * 
	 * @return URL The URL representing this media stream
	 * @throws MalformedURLException 
	 */
	public URL getURL() throws MalformedURLException {
		
		StringBuilder sb = new StringBuilder();
		sb.append(protocol)
			.append("://");

		if (!username.equals("")) {
		    sb.append(username)
		    .append(":")
		    .append(password)
		    .append("@");
		}
		
		sb.append(hostname)
			.append(':')
			.append(port)
			.append(path);
		
		if (!query.equals("")) {
		    sb.append('?')
			.append(query);
		}
		
		if (!reference.equals("")) {
		    sb.append('#')
			.append(reference);
		}
		
		return new URL(sb.toString());
	}
	
	/**
	 * Converts invalid URL characters to UTF-8 format. This method is used as a 
	 * temporary fix to deal with Android's handling of URL's that contain 
	 * "special characters" such as "[" (Issue 12724).
	 * 
	 * @param path The path to scrub
	 * @return The path with invalid characters converted
	 */
	/*private String scrubPath(String path) {
		String scrubbedPath = null;
		
		if (path == null) {
			return null;
		}
		
		scrubbedPath = path.replace("[", "%5B");
		scrubbedPath = scrubbedPath.replace("]", "%5D");
		
		return scrubbedPath;
	}*/
}