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

package net.sourceforge.servestream.transport;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;

import android.net.Uri;

public class HTTP extends AbsTransport {

	private static final String PROTOCOL = "http";
	private static final int DEFAULT_PORT = 80;
	
	public HTTP() {
		super();
	}
	
	public HTTP(UriBean uri) {
		super(uri);
	}
	
	public static String getProtocolName() {
		return PROTOCOL;
	}

	/**
	 * Encode the current transport into a URI that can be passed via intent calls.
	 * @return URI to host
	 */
	public static Uri getUri(String input) {
		return getUri(input, false);
	}
	
	/**
	 * Encode the current transport into a URI that can be passed via intent calls.
	 * @return URI to host
	 */
	private static Uri getUri(String input, boolean scrubUri) {
		if (input == null) {
			return null;
		}
		
		String hostname = null;
		int port = -1;
		
		try {
			input = URLDecoder.decode(input, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			return null;
		}
		
		URL url = null;
		
		try {
			url = new URL(input);
		} catch (MalformedURLException e) {
			return null;
		}
		
		// the following code is used as a temporary fix to deal with Android's
		// handling of URL's that contain "special characters" such as "[" (Issue 12724)
		String [] split = url.getHost().split("\\:");
		
		if (split.length == 2) {
			hostname = split[0];
			port = Integer.valueOf(split[1]);
		}
		
		StringBuilder sb = new StringBuilder();

		sb.append(PROTOCOL)
			.append("://");
		
		if (!scrubUri) {
			if (url.getUserInfo() != null) {
				String [] authInfo = url.getUserInfo().split("\\:");
			
				if (authInfo.length == 2) {
					sb.append(authInfo[0])
						.append(":")
						.append(authInfo[1])
						.append("@");
				}
			}
		}
		
		if (hostname != null) {
			sb.append(hostname)
				.append(":");
		} else {
			sb.append(url.getHost())
				.append(":");
		}
		
		if (port != -1) {
			sb.append(port);
		} else {
			if (url.getPort() == -1) {
				sb.append(DEFAULT_PORT);
			} else {
				sb.append(url.getPort());	
			}
		}
		
		sb.append(url.getPath());
		
		if (url.getQuery() != null) {
		    sb.append("?")
				.append(url.getQuery());
		}
		
		if (url.getRef() != null) {
		    sb.append("#")
				.append(url.getRef());
		}
		
		Uri uri = Uri.parse(sb.toString());

		return uri;
	}
	
	@Override
	public void connect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(StreamDatabase.FIELD_STREAM_NICKNAME, getUri(uri.toString(), true).toString());
		selection.put(StreamDatabase.FIELD_STREAM_PROTOCOL, PROTOCOL);
		
		if (uri.getUserInfo() != null) {
			String [] authInfo = uri.getUserInfo().split("\\:");
			
			if (authInfo.length == 2) {
				selection.put(StreamDatabase.FIELD_STREAM_USERNAME, authInfo[0]);
				selection.put(StreamDatabase.FIELD_STREAM_PASSWORD, authInfo[1]);
			}
		} else {
			selection.put(StreamDatabase.FIELD_STREAM_USERNAME, null);
			selection.put(StreamDatabase.FIELD_STREAM_PASSWORD, null);
		}
		
		selection.put(StreamDatabase.FIELD_STREAM_HOSTNAME, uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		selection.put(StreamDatabase.FIELD_STREAM_PORT, Integer.toString(port));
		
		if (uri.getPath() != null) {
			selection.put(StreamDatabase.FIELD_STREAM_PATH, uri.getPath());
		}
		selection.put(StreamDatabase.FIELD_STREAM_QUERY, uri.getQuery());
		selection.put(StreamDatabase.FIELD_STREAM_REFERENCE, uri.getFragment());		
	}

	@Override
	public UriBean createUri(Uri uri) {
		UriBean host = new UriBean();

		host.setProtocol(PROTOCOL);

		if (uri.getUserInfo() != null) {
			String [] authInfo = uri.getUserInfo().split("\\:");
			
			if (authInfo.length == 2) {
				host.setUsername(authInfo[0]);
				host.setPassword(authInfo[1]);
			}
		}
		
		host.setHostname(uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		host.setPort(port);

		host.setPath(uri.getPath());
		host.setQuery(uri.getQuery());
		host.setReference(uri.getFragment());

		String nickname = getUri(uri.toString(), true).toString();
		host.setNickname(nickname);

		return host;
	}

	@Override
	public String getContentType() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public boolean usesNetwork() {
	    return true;
	}
	
	@Override
	public boolean shouldSave() {
	    return true;
	}

	@Override
	public InputStream getConnection() {
		// TODO Auto-generated method stub
		return null;
	}
}
