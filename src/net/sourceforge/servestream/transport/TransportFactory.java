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

import java.util.HashMap;
import java.util.Map;

import net.sourceforge.servestream.database.StreamDatabase;

import net.sourceforge.servestream.bean.UriBean;

import android.net.Uri;
import android.util.Log;

public class TransportFactory {
	private static final String TAG = TransportFactory.class.getName();

	private static String[] transportNames = {
		HTTP.getProtocolName(),
		HTTPS.getProtocolName(),
		File.getProtocolName(),
		RTSP.getProtocolName(),
		MMS.getProtocolName(),
		MMSH.getProtocolName(),
		MMST.getProtocolName(),
		RTMP.getProtocolName()
	};

	/**
	 * @param protocol
	 * @return
	 */
	public static AbsTransport getTransport(String protocol) {
		if (HTTP.getProtocolName().equals(protocol)) {
			return new HTTP();
		} else if (HTTPS.getProtocolName().equals(protocol)) {
			return new HTTPS();
		} else if (File.getProtocolName().equals(protocol)) {
			return new File();
		} else if (RTSP.getProtocolName().equals(protocol)) {
			return new RTSP();
		} else if (MMS.getProtocolName().equals(protocol)) {
			return new MMS();
		} else if (MMSH.getProtocolName().equals(protocol)) {
			return new MMSH();
		} else if (MMST.getProtocolName().equals(protocol)) {
			return new MMST();
		} else if (RTMP.getProtocolName().equals(protocol)) {
			return new RTMP();
		} else {
			return null;
		}
	}

	public static Uri getUri(String input) {
		Uri uri = null;
		String scheme = null;
		
		try {
			uri = Uri.parse(input);
		} catch (Exception e) {
			return null;
		}
		
		// ensure the URI has a scheme since this is necessary to determine
		// the transport type
		if (uri.getScheme() == null) {
			return null;
		}
		
		if (uri.getScheme().equals(HTTP.getProtocolName())) {
			scheme = HTTP.getProtocolName();
		} else if (uri.getScheme().equals(HTTPS.getProtocolName())) {
			scheme = HTTPS.getProtocolName();
		} else if (uri.getScheme().equals(File.getProtocolName())) {
			scheme = File.getProtocolName();
		} else if (uri.getScheme().equals(RTSP.getProtocolName())) {
			scheme = RTSP.getProtocolName();
		} else if (uri.getScheme().equals(MMS.getProtocolName())) {
			scheme = MMS.getProtocolName();
		} else if (uri.getScheme().equals(MMSH.getProtocolName())) {
			scheme = MMSH.getProtocolName();
		} else if (uri.getScheme().equals(MMST.getProtocolName())) {
			scheme = MMST.getProtocolName();
		} else if (uri.getScheme().equals(RTMP.getProtocolName())) {
			scheme = RTMP.getProtocolName();
		}
		
		//Log.d("TransportFactory", String.format(
		//		"Attempting to discover URI for scheme=%s on input=%s", scheme,
		//		input));
		if (HTTP.getProtocolName().equals(scheme))
			return HTTP.getUri(input);
		else if (HTTPS.getProtocolName().equals(scheme))
			return HTTPS.getUri(input);
		else if (File.getProtocolName().equals(scheme)) {
			return File.getUri(input);
		} else if (RTSP.getProtocolName().equals(scheme)) {
			return RTSP.getUri(input);
		} else if (MMS.getProtocolName().equals(scheme)) {
			return MMS.getUri(input);
		} else if (MMSH.getProtocolName().equals(scheme)) {
			return MMSH.getUri(input);
		} else if (MMST.getProtocolName().equals(scheme)) {
			return MMST.getUri(input);
		} else if (RTMP.getProtocolName().equals(scheme)) {
			return RTMP.getUri(input);
		} else
			return null;
	}

	public static String[] getTransportNames() {
		return transportNames;
	}

	public static boolean isSameTransportType(AbsTransport a, AbsTransport b) {
		if (a == null || b == null)
			return false;

		return a.getClass().equals(b.getClass());
	}

	/**
	 * @param hostdb Handle to HostDatabase
	 * @param uri URI to target server
	 * @param host HostBean in which to put the results
	 * @return true when host was found
	 */
	public static UriBean findUri(StreamDatabase streamdb, Uri uri) {
		AbsTransport transport = getTransport(uri.getScheme());

		Map<String, String> selection = new HashMap<String, String>();

		transport.getSelectionArgs(uri, selection);
		if (selection.size() == 0) {
			Log.e(TAG, String.format("Transport %s failed to do something useful with URI=%s",
					uri.getScheme(), uri.toString()));
			throw new IllegalStateException("Failed to get needed selection arguments");
		}

		return streamdb.findUri(selection);
	}
}
