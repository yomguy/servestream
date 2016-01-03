/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.utils.Utils;

import android.net.Uri;
import android.webkit.MimeTypeMap;

public class RTSP extends AbsTransport {

	private static final String PROTOCOL = "rtsp";
	private static final int DEFAULT_PORT = 80;
	
	public RTSP() {
		super();
	}
	
	public RTSP(UriBean uri) {
		super(uri);
	}
	
	public static String getProtocolName() {
		return PROTOCOL;
	}

	protected String getPrivateProtocolName() {
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
		
		Uri uri = Uri.parse(input);
		
		// the following code is used as a temporary fix to deal with Android's
		// handling of URL's that contain "special characters" such as "[" (Issue 12724)
		String [] split = uri.getHost().split("\\:");
		
		if (split.length == 2) {
			hostname = split[0];
			port = Integer.valueOf(split[1]);
		}
		
		StringBuilder sb = new StringBuilder();

		sb.append(PROTOCOL)
			.append("://");
		
		if (!scrubUri) {
			if (uri.getUserInfo() != null) {
				String [] authInfo = uri.getUserInfo().split("\\:");
			
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
			sb.append(uri.getHost())
				.append(":");
		}
		
		if (port != -1) {
			sb.append(port);
		} else {
			if (uri.getPort() == -1) {
				sb.append(DEFAULT_PORT);
			} else {
				sb.append(uri.getPort());	
			}
		}
		
		sb.append(uri.getPath());
		
		if (uri.getQuery() != null) {
		    sb.append("?")
				.append(uri.getQuery());
		}
		
		if (uri.getAuthority() != null) {
		    sb.append("#")
				.append(uri.getAuthority());
		}
		
		Uri uri2 = Uri.parse(sb.toString());

		return uri2;
	}
	
	@Override
	public void connect() throws IOException {
	}

	@Override
	public void close() {
	}

	@Override
	public boolean exists() {
		return true;
	}
	
	@Override
	public boolean isConnected() {
		return false;
	}

	@Override
	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
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
		String extension = Utils.getExtension(uri.getUri().toString());
		
		String mimeType = (MimeTypeMap.getSingleton()).getMimeTypeFromExtension(extension);
		
		if (mimeType == null) {
			return "";
		} else {
			return mimeType;
		}
	}
	
	@Override
	public boolean usesNetwork() {
	    return true;
	}
	
	@Override
	public InputStream getConnection() {
		return null;
	}
	
	@Override
	public boolean isPotentialPlaylist() {
		return false;
	}
}
