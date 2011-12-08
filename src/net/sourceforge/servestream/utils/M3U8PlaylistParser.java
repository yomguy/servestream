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

package net.sourceforge.servestream.utils;

import java.net.URL;

public class M3U8PlaylistParser extends M3UPlaylistParser {
	public final static String TAG = M3U8PlaylistParser.class.getName();

	public final static String EXTENSION = "m3u8";
	public final static String MIME_TYPE = "audio/x-mpegurl";
    
	/**
	 * Default constructor
	 */
	public M3U8PlaylistParser(URL playlistUrl) {
		super(playlistUrl);
	}
}