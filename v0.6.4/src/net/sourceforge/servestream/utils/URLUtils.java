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

package net.sourceforge.servestream.utils;

import java.net.URI;
import java.net.URISyntaxException;

import net.sourceforge.servestream.transport.TransportFactory;
import android.net.Uri;
import android.webkit.MimeTypeMap;

public class URLUtils {
	private static final MimeTypeMap mMimeTypeMap = MimeTypeMap.getSingleton();
	
	public static String getContentType(String url) {
		String extension = null;
		String contentType = null;
		
		if (url == null) {
			return null;
		}
		
		extension = MimeTypeMap.getFileExtensionFromUrl(url);
    
		if (extension.equals("")) {
			if (url.lastIndexOf("/") == (url.length() - 1)) {
				contentType = "text/html";
			}
		} else {
			contentType = mMimeTypeMap.getMimeTypeFromExtension(extension);
		}
		
    	return contentType;
	}
	
	public static String encodeURL(String path) {
		Uri uri = TransportFactory.getUri(path);

		if (uri == null) {
			return path;
		}
		
		try {
			return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toASCIIString();
		} catch (URISyntaxException e) {
			return path;
		}
	}
}
