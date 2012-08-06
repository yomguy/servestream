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
import java.util.Map;

import net.sourceforge.servestream.bean.UriBean;

import android.net.Uri;

public abstract class AbsTransport {
	UriBean uri;

	String emulation;

	public AbsTransport() {}

	public AbsTransport(UriBean uri) {
		this.uri = uri;
	}

	/**
	 * @return protocol part of the URI
	 */
	public static String getProtocolName() {
		return "unknown";
	}

	/**
	 * Encode the current transport into a URI that can be passed via intent calls.
	 * @return URI to host
	 */
	public static Uri getUri(String input) {
		return null;
	}

	/**
	 * Causes transport to connect to the target host. After connecting but before a
	 * session is started, must call back to {@link TerminalBridge#onConnected()}.
	 * After that call a session may be opened.
	 */
	public abstract void connect() throws IOException;

	/**
	 * Closes the connection to the terminal. Note that the resulting failure to read
	 * should call {@link TerminalBridge#dispatchDisconnect(boolean)}.
	 */
	public abstract void close();

	public void setUri(UriBean uri) {
		this.uri = uri;
	}

	public abstract InputStream getConnection();
	
	public abstract boolean exists();
	
	public abstract boolean isConnected();

	/**
	 * @return int default port for protocol
	 */
	public abstract int getDefaultPort();

	/**
	 * @param uri
	 * @param selectionKeys
	 * @param selectionValues
	 */
	public abstract void getSelectionArgs(Uri uri, Map<String, String> selection);

	/**
	 * @param uri
	 * @return
	 */
	public abstract UriBean createUri(Uri uri);

	public abstract String getContentType();
	
	/**
	 * @return
	 */
	public abstract boolean usesNetwork();
	
	public abstract boolean shouldSave();
}
