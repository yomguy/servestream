/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
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

package net.sourceforge.servestream.bean;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import net.sourceforge.servestream.database.StreamDatabase;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class UriBean implements Parcelable {
	public static final String BEAN_NAME = "uri";

	/* Database fields */
	private long id = -1;
	private String nickname = null;
	private String username = null;
	private String password = null;
	private String hostname = null;
	private int port = -2;
	private String path = null;
	private String query = null;
	private String reference = null;
	private String protocol = null;
	private long lastConnect = -1;
	private String contentType = null;
	private int listPosition = -1;

	public UriBean() {

	}

	public UriBean(Parcel in) {
		id = in.readLong();
		nickname = in.readString();
		username = in.readString();
		password = in.readString();
		hostname = in.readString();
		port = in.readInt();
		path = in.readString();
		query = in.readString();
		reference = in.readString();
		protocol = in.readString();
		lastConnect = in.readLong();
		contentType = in.readString();
		listPosition = in.readInt();
	}

	public String getBeanName() {
		return BEAN_NAME;
	}

	public void setId(long id) {
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
	public void setPort(int port) {
		this.port = port;
	}
	public int getPort() {
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
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}
	public String getProtocol() {
		return protocol;
	}
	public void setLastConnect(long lastConnect) {
		this.lastConnect = lastConnect;
	}
	public long getLastConnect() {
		return lastConnect;
	}
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	public String getContentType() {
		return contentType;
	}
	public void setListPosition(int listPosition) {
		this.listPosition = listPosition;
	}
	public int getListPosition() {
		return listPosition;
	}
	
	public String getDescription() {
		String description = String.format("%s@%s", username, hostname);

		if (port != 22)
			description += String.format(":%d", port);

		return description;
	}

	public ContentValues getValues() {
		ContentValues values = new ContentValues();

		values.put(StreamDatabase.FIELD_STREAM_NICKNAME, nickname);
		values.put(StreamDatabase.FIELD_STREAM_PROTOCOL, protocol);
		values.put(StreamDatabase.FIELD_STREAM_USERNAME, username);
		values.put(StreamDatabase.FIELD_STREAM_PASSWORD, password);
		values.put(StreamDatabase.FIELD_STREAM_HOSTNAME, hostname);
		values.put(StreamDatabase.FIELD_STREAM_PORT, port);
		values.put(StreamDatabase.FIELD_STREAM_PATH, path);
		values.put(StreamDatabase.FIELD_STREAM_QUERY, query);
		values.put(StreamDatabase.FIELD_STREAM_REFERENCE, reference);
		values.put(StreamDatabase.FIELD_STREAM_LASTCONNECT, lastConnect);
		values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, listPosition);

		return values;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof UriBean))
			return false;

		UriBean host = (UriBean)o;

		if (id != -1 && host.getId() != -1)
			return host.getId() == id;

		if (nickname == null) {
			if (host.getNickname() != null)
				return false;
		} else if (!nickname.equals(host.getNickname()))
			return false;

		if (protocol == null) {
			if (host.getProtocol() != null)
				return false;
		} else if (!protocol.equals(host.getProtocol()))
			return false;

		if (username == null) {
			if (host.getUsername() != null)
				return false;
		} else if (!username.equals(host.getUsername()))
			return false;

		if (password == null) {
			if (host.getPassword() != null)
				return false;
		} else if (!password.equals(host.getPassword()))
			return false;
		
		if (hostname == null) {
			if (host.getHostname() != null)
				return false;
		} else if (!hostname.equals(host.getHostname()))
			return false;
		
		if (port != host.getPort())
			return false;
		
		if (path == null) {
			if (host.getPath() != null)
				return false;
		} else if (!path.equals(host.getPath()))
			return false;
		
		if (query == null) {
			if (host.getQuery() != null)
				return false;
		} else if (!query.equals(host.getQuery()))
			return false;
		
		if (reference == null) {
			if (host.getReference() != null)
				return false;
		} else if (!reference.equals(host.getReference()))
			return false;
		
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 7;

		if (id != -1)
			return (int)id;

		hash = 31 * hash + (null == nickname ? 0 : nickname.hashCode());
		hash = 31 * hash + (null == protocol ? 0 : protocol.hashCode());
		hash = 31 * hash + (null == username ? 0 : username.hashCode());
		hash = 31 * hash + (null == password ? 0 : password.hashCode());
		hash = 31 * hash + (null == hostname ? 0 : hostname.hashCode());
		hash = 31 * hash + port;
		hash = 31 * hash + (null == path ? 0 : path.hashCode());
		hash = 31 * hash + (null == query ? 0 : query.hashCode());
		hash = 31 * hash + (null == reference ? 0 : reference.hashCode());
		
		return hash;
	}

	@Override
	public String toString() {
		return nickname;
	}
	
	/**
	 * @return URI identifying this HostBean
	 */
	public Uri getUri() {
		StringBuilder sb = new StringBuilder();
		sb.append(protocol)
			.append("://");

		if (username != null && password != null) {
			sb.append(Uri.encode(username))
				.append(":")
				.append(password)
				.append('@');
		}

		if (hostname != null) {
			sb.append(hostname)
				.append(':');
		}
		
		if (port != -2) {
			sb.append(port);
		}
		
		if (path != null) {
			sb.append(path);
		}
		
		if (query != null) {
		    sb.append("?")
				.append(query);
		}
		
		if (reference != null) {
		    sb.append("#")
				.append(reference);
		}
		
		return Uri.parse(sb.toString());
	}

	/**
	 * @return URI identifying this HostBean
	 */
	public Uri getScrubbedUri() {
		StringBuilder sb = new StringBuilder();
		sb.append(protocol)
			.append("://");

		if (hostname != null) {
			sb.append(hostname)
				.append(':');
		}
		
		if (port != -2) {
			sb.append(port);
		}
		
		if (path != null) {
			sb.append(path);
		}
		
		if (query != null) {
		    sb.append("?")
				.append(query);
		}
		
		if (reference != null) {
		    sb.append("#")
				.append(reference);
		}
		
		return Uri.parse(sb.toString());
	}
	
	/**
	 * @return URL identifying this HostBean
	 */
	public URL getScrubbedURL() {
		URI encodedUri = null;
		Uri uri = getScrubbedUri();
		
		try {
			encodedUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
		} catch (URISyntaxException e) {
		}
		
		URL url = null;		
		try {
			url = encodedUri.toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		return url;		
	}
	
	public URL getURL() {
		URI encodedUri = null;
		Uri uri = getUri();
		
		try {
			encodedUri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
		} catch (URISyntaxException e) {
		}
		
		URL url = null;
		
		try {
			url = encodedUri.toURL();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		return url;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeString(nickname);
		dest.writeString(username);
		dest.writeString(password);
		dest.writeString(hostname);
		dest.writeInt(port);
		dest.writeString(path);
		dest.writeString(query);
		dest.writeString(reference);
		dest.writeString(protocol);
		dest.writeLong(lastConnect);
		dest.writeString(contentType);
		dest.writeInt(listPosition);
	}
	
    public static final Parcelable.Creator<UriBean> CREATOR
    		= new Parcelable.Creator<UriBean>() {
    	public UriBean createFromParcel(Parcel in) {
    		return new UriBean(in);
    	}

    	public UriBean[] newArray(int size) {
    		return new UriBean[size];
    	}
    };
}
