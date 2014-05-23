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

package net.sourceforge.servestream.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import net.sourceforge.servestream.R;

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * createInstance() or otherwise every public method will throw an Exception
 * when called.
 */
public class UserPreferences implements
		SharedPreferences.OnSharedPreferenceChangeListener {
	private static UserPreferences instance;
	private final Context mContext;

	// Preferences
	private int mTheme;

	private UserPreferences(Context context) {
		mContext = context;
		loadPreferences();
	}

	/**
	 * Sets up the UserPreferences class.
	 *
	 * @throws IllegalArgumentException
	 *             if context is null
	 * */
	public static void createInstance(Context context) {
		if (context == null)
			throw new IllegalArgumentException("Context must not be null");
		instance = new UserPreferences(context);

		PreferenceManager.getDefaultSharedPreferences(context)
				.registerOnSharedPreferenceChangeListener(instance);

	}

	private void loadPreferences() {
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		mTheme = readThemeValue(sp.getString(PreferenceConstants.THEME, "0"));
	}

	private int readThemeValue(String valueFromPrefs) {
		switch (Integer.parseInt(valueFromPrefs)) {
		case 0:
			return R.style.Theme_ServeStream_DarkActionBar;
		case 1:
			return R.style.Theme_ServeStream_Dark;
		default:
			return R.style.Theme_ServeStream_DarkActionBar;
		}
	}

	private static void instanceAvailable() {
		if (instance == null) {
			throw new IllegalStateException(
					"UserPreferences was used before being set up");
		}
	}

	public static int getTheme() {
		instanceAvailable();
		return instance.mTheme;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		if (key.equals(PreferenceConstants.THEME)) {
			mTheme = readThemeValue(sp.getString(PreferenceConstants.THEME, ""));
		}
	}
}
