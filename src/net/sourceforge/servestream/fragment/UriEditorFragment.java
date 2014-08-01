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

package net.sourceforge.servestream.fragment;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.preference.PreferenceFragment;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;

public class UriEditorFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
	public class CursorPreferenceHack implements SharedPreferences {
		protected final String table;
		protected final long id;

		protected Map<String, String> values = new HashMap<String, String>();

		public CursorPreferenceHack(String table, long id) {
			this.table = table;
			this.id = id;

			cacheValues();
		}

		protected final void cacheValues() {
			// fill a cursor and cache the values locally
			// this makes sure we dont have any floating cursor to dispose later

			SQLiteDatabase db = m_streamdb.getReadableDatabase();
			Cursor cursor = db.query(table, null, "_id = ?",
					new String[] { String.valueOf(id) }, null, null, null);

			if (cursor.moveToFirst()) {
				for(int i = 0; i < cursor.getColumnCount(); i++) {
					String key = cursor.getColumnName(i);
					String value = cursor.getString(i);
					values.put(key, value);
				}
			}
			cursor.close();
			db.close();
		}

		public boolean contains(String key) {
			return values.containsKey(key);
		}

		public class Editor implements SharedPreferences.Editor {

			private ContentValues update = new ContentValues();

			public SharedPreferences.Editor clear() {
				update = new ContentValues();
				return this;
			}

			public boolean commit() {
				//Log.d(this.getClass().toString(), "commit() changes back to database");
				SQLiteDatabase db = m_streamdb.getWritableDatabase();
				db.update(table, update, "_id = ?", new String[] { String.valueOf(id) });
				db.close();

				// make sure we refresh the parent cached values
				cacheValues();

				// and update any listeners
				for(OnSharedPreferenceChangeListener listener : listeners) {
					listener.onSharedPreferenceChanged(CursorPreferenceHack.this, null);
				}

				return true;
			}

			public android.content.SharedPreferences.Editor putBoolean(String key, boolean value) {
				return this.putString(key, Boolean.toString(value));
			}

			public android.content.SharedPreferences.Editor putFloat(String key, float value) {
				return this.putString(key, Float.toString(value));
			}

			public android.content.SharedPreferences.Editor putInt(String key, int value) {
				return this.putString(key, Integer.toString(value));
			}

			public android.content.SharedPreferences.Editor putLong(String key, long value) {
				return this.putString(key, Long.toString(value));
			}

			public android.content.SharedPreferences.Editor putString(String key, String value) {
				//Log.d(this.getClass().toString(), String.format("Editor.putString(key=%s, value=%s)", key, value));
				
	            // ensure the port is not null
				if (key.equals("port") && value != null && value.equals("")) {
					value = "-1";
				}
				
				if (value != null && value.equals("")) {
					value = null;
					update.put(key, value);
				} else {
					update.put(key, value);
				}
				
				return this;
			}

			public android.content.SharedPreferences.Editor remove(String key) {
				//Log.d(this.getClass().toString(), String.format("Editor.remove(key=%s)", key));
				update.remove(key);
				return this;
			}

			// Gingerbread compatibility
			public void apply() {
				commit();
			}

			public android.content.SharedPreferences.Editor putStringSet(
					String arg0, Set<String> arg1) {
				// TODO Auto-generated method stub
				return null;
			}

		}


		public Editor edit() {
			//Log.d(this.getClass().toString(), "edit()");
			return new Editor();
		}

		public Map<String, ?> getAll() {
			return values;
		}

		public boolean getBoolean(String key, boolean defValue) {
			return Boolean.valueOf(this.getString(key, Boolean.toString(defValue)));
		}

		public float getFloat(String key, float defValue) {
			return Float.valueOf(this.getString(key, Float.toString(defValue)));
		}

		public int getInt(String key, int defValue) {
			return Integer.valueOf(this.getString(key, Integer.toString(defValue)));
		}

		public long getLong(String key, long defValue) {
			return Long.valueOf(this.getString(key, Long.toString(defValue)));
		}

		public String getString(String key, String defValue) {
			//Log.d(this.getClass().toString(), String.format("getString(key=%s, defValue=%s)", key, defValue));

			if(!values.containsKey(key)) return defValue;
			return values.get(key);
		}

		protected List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();

		public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
			listeners.add(listener);
		}

		public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
			listeners.remove(listener);
		}

		public Set<String> getStringSet(String arg0, Set<String> arg1) {
			// TODO Auto-generated method stub
			return null;
		}

	}

	protected static final String TAG = UriEditorFragment.class.getName();

	protected StreamDatabase m_streamdb = null;

	private CursorPreferenceHack pref;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (savedInstanceState == null) {
			long streamId = getActivity().getIntent().getLongExtra(Intent.EXTRA_TITLE, -1);

			m_streamdb = new StreamDatabase(getActivity());

			pref = new CursorPreferenceHack(StreamDatabase.TABLE_STREAMS, streamId);
			pref.registerOnSharedPreferenceChangeListener(this);

			addPreferencesFromResource(R.xml.stream_prefs);

			updateSummaries();
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		if(this.m_streamdb == null)
			this.m_streamdb = new StreamDatabase(getActivity());
	}

	@Override
	public void onStop() {
		super.onStop();

		if (this.m_streamdb != null) {
			this.m_streamdb.close();
			this.m_streamdb = null;
		}
	}
	
	private void updateSummaries() {
		// for all text preferences, set hint as current database value
		for (String key : this.pref.values.keySet()) {
			Preference pref = this.findPreference(key);
			if(pref == null) continue;
			if(pref instanceof CheckBoxPreference) continue;
			CharSequence value = this.pref.getString(key, "");

            // mask the password preference
			if (key.equals("password") && value != null) {
				value = new String(new char[value.length()]).replace("\0", "*");
			}
			
			if (pref instanceof ListPreference) {
				ListPreference listPref = (ListPreference) pref;
				int entryIndex = listPref.findIndexOfValue((String) value);
				if (entryIndex >= 0)
					value = listPref.getEntries()[entryIndex];
			}

			pref.setSummary(value);
		}

	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// update values on changed preference
		this.updateSummaries();

	}
}