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

package net.sourceforge.servestream.preference;

import android.os.Build;

public final class PreferenceConstants {
	public static final boolean PRE_ECLAIR = (Build.VERSION.SDK_INT <= 4);
	public static final boolean PRE_FROYO = PRE_ECLAIR ? true :
		(Build.VERSION.SDK_INT <= 7);

	public static final String AUTOSAVE = "autosave";
	public static final String PROGRESSIVE_DOWNLOAD = "progressivedownload";
	public static final String USE_FFMPEG_PLAYER = "ffmpegplayer";
	public static final String WAKELOCK = "wakelock";
	public static final String WIFI_LOCK = "wifilock";
	public static final String HEADPHONE_PAUSE = "headphonepause";
	public static final String RETRIEVE_METADATA = "retrievemetadata";
	public static final String RETRIEVE_ALBUM_ART = "retrievealbumart";
	public static final String RETRIEVE_SHOUTCAST_METADATA = "retrieveshoutcastmetadata";
	public static final String SEND_SCROBBLER_INFO = "sendscrobblerinfo";
	public static final String SEND_BLUETOOTH_METADATA = "sendbluetoothmetadata";
	public static final String RATE_APPLICATION_FLAG = "rateapplicationflag";
	public static final String THEME = "theme";
	
	/* Backup identifiers */
	public static final String BACKUP_PREF_KEY = "prefs";
}
