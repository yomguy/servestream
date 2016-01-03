/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.utils.Utils;

import android.net.Uri;
import android.webkit.MimeTypeMap;

public class File extends AbsTransport {
	
	private static final String PROTOCOL = "file";
	
	private InputStream is = null;
	
	public File() {
		super();
	}
	
	public File(UriBean uri) {
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
	public void connect() {
		java.io.File file = new java.io.File(uri.getUri().toString().replace(PROTOCOL + "://", ""));
		
		try {
			is = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}		
	}

	@Override
	public void close() {
		Utils.closeInputStream(is);
	}

	@Override
	public boolean exists() {
		java.io.File file = new java.io.File(uri.getUri().toString().replace(PROTOCOL + "://", ""));
		
		return file.exists();
	}
	
	@Override
	public InputStream getConnection() {
		return is;
	}
	
	@Override
	public boolean isConnected() {
		return is != null;
	}
	
	@Override
	public int getDefaultPort() {
		return 0;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(StreamDatabase.FIELD_STREAM_NICKNAME, uri.toString());
		selection.put(StreamDatabase.FIELD_STREAM_PROTOCOL, PROTOCOL);
		selection.put(StreamDatabase.FIELD_STREAM_PATH, uri.getPath());
	}

	@Override
	public UriBean createUri(Uri uri) {
		UriBean host = new UriBean();

		host.setProtocol(PROTOCOL);
		host.setPath(uri.getPath());
		
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
		return true;
	}
}
