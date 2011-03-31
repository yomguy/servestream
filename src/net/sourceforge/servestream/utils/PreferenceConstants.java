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
/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

import android.os.Build;

/**
 * @author Kenny Root
 *
 */
public class PreferenceConstants {
	public static final boolean PRE_ECLAIR = (Integer.parseInt(Build.VERSION.SDK) <= 4);
	public static final boolean PRE_FROYO = PRE_ECLAIR ? true :
		(Integer.parseInt(Build.VERSION.SDK) <= 7);

	public static final String UPDATE = "update";

	public static final String UPDATE_DAILY = "Daily";
	public static final String UPDATE_WEEKLY = "Weekly";
	public static final String UPDATE_NEVER = "Never";

	public static final String AUTOSAVE = "autosave";
	
	public static final String LAST_CHECKED = "lastchecked";

	public static final String ROTATION = "rotation";

	public static final String ROTATION_DEFAULT = "Default";
	public static final String ROTATION_LANDSCAPE = "Force landscape";
	public static final String ROTATION_PORTRAIT = "Force portrait";
	public static final String ROTATION_AUTOMATIC = "Automatic";

	public static final String FULLSCREEN = "fullscreen";

	/* Backup identifiers */
	public static final String BACKUP_PREF_KEY = "prefs";
	
	public static final String REPEAT = "repeat";
	
	public static final String REPEAT_OFF = "Off";
	public static final String REPEAT_ONE = "One";
	public static final String REPEAT_ALL = "All";
}
