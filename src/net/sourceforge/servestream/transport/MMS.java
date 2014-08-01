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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.utils.Utils;

import android.net.Uri;
import android.webkit.MimeTypeMap;

public class MMS extends AbsTransport {

	private static final String PROTOCOL = "mms";
	
	public MMS() {
		super();
	}
	
	public MMS(UriBean uri) {
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
		return Uri.parse(input);
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
	public InputStream getConnection() {
		return null;
	}
	
	@Override
	public boolean isConnected() {
		return true;
	}

	@Override
	public int getDefaultPort() {
		return -1;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(StreamDatabase.FIELD_STREAM_PROTOCOL, getPrivateProtocolName());
		
		selection.put(StreamDatabase.FIELD_STREAM_HOSTNAME, uri.getHost());

		selection.put(StreamDatabase.FIELD_STREAM_PORT, Integer.toString(uri.getPort()));
		
		if (uri.getPath() != null) {
			selection.put(StreamDatabase.FIELD_STREAM_PATH, uri.getPath());
		}
		selection.put(StreamDatabase.FIELD_STREAM_QUERY, uri.getQuery());
		selection.put(StreamDatabase.FIELD_STREAM_REFERENCE, uri.getFragment());		
	}

	@Override
	public UriBean createUri(Uri uri) {
		UriBean host = new UriBean();

		host.setProtocol(getPrivateProtocolName());

		host.setHostname(uri.getHost());

		host.setPort(uri.getPort());

		host.setPath(uri.getPath());
		host.setQuery(uri.getQuery());
		host.setReference(uri.getFragment());

		String nickname = uri.toString();
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
	    return false;
	}
	
	@Override
	public boolean isPotentialPlaylist() {
		return false;
	}
}