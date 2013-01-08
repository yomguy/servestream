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

package net.sourceforge.servestream.activity;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.utils.BackupUtils;
import net.sourceforge.servestream.utils.PreferenceConstants;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;

public class SettingsActivity extends SherlockPreferenceActivity {
	private static final String TAG = SettingsActivity.class.getName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
	        setContentView(R.layout.activity_settings);
			
			ActionBar actionBar = getSupportActionBar();
			actionBar.setTitle("Settings");
			actionBar.setDisplayHomeAsUpEnabled(true);
	        
			addPreferencesFromResource(R.xml.preferences);
		} catch (ClassCastException e) {
			Log.e(TAG, "Shared preferences are corrupt! Resetting to default values.");

			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

			// Blow away all the preferences
			SharedPreferences.Editor editor = preferences.edit();
			editor.clear();
			editor.commit();

			PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

			addPreferencesFromResource(R.xml.preferences);
		}
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case android.R.id.home:
    			Intent intent = new Intent(this, MainActivity.class);
    			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
    			return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
	
	@Override
	public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen, Preference preference) {
		if (preference.getKey() != null) {
			if (preference.getKey().equals(PreferenceConstants.BACKUP)) {
				BackupUtils.backup(this);
			} else if (preference.getKey().equals(PreferenceConstants.RESTORE)) {
				BackupUtils.restore(this);
			} else if (preference.getKey().equals(PreferenceConstants.DONATE)) {
				try {
	        		Intent intent = new Intent(Intent.ACTION_VIEW);
	        		intent.setData(Uri.parse("market://details?id=net.sourceforge.donate.servestream"));
	        		startActivity(intent);
	        	} catch (ActivityNotFoundException ex ) {
	        		// the market couldn't be opening or the application couldn't be found
	        		// lets take the user to the project's webpage instead.
	        		Intent intent = new Intent(Intent.ACTION_VIEW);
	        		intent.setData(Uri.parse("https://sourceforge.net/p/servestream/donate/"));
	        		startActivity(intent);
	            }
			}
		}
		
		return true;
	}
}
