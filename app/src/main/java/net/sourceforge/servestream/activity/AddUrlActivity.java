/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this.getActivity() file except in compliance with the License.
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

import net.sourceforge.servestream.fragment.AddUrlFragment;
import net.sourceforge.servestream.preference.UserPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

public class AddUrlActivity extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
	    if (null == savedInstanceState){
	    	Fragment fragment = new AddUrlFragment();
		
	    	Bundle bundle = new Bundle();
	    	bundle.putString(AddUrlFragment.URI_EXTRA, getUri());
	    	fragment.setArguments(bundle);
		
	    	getSupportFragmentManager()
				.beginTransaction()
				.add(android.R.id.content, fragment, "add_url")
				.commit();
	    }
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
    			Intent intent = new Intent(this, MainActivity.class);
    			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
    			finish();
				return true;
     		default:
     			return super.onOptionsItemSelected(item);
		}
	}
	
	private String getUri() {
		String intentUri = null;
		
		Intent intent = getIntent();
		
		if (intent == null) {
			return null;
		}
		
		// check to see if we were called by clicking on a URL
		if (intent.getData() != null) {
			intentUri = intent.getData().toString();
		}
		
		// check to see if the application was opened from a share intent
		if (intent.getExtras() != null && intent.getExtras().getCharSequence(Intent.EXTRA_TEXT) != null) {
			intentUri = intent.getExtras().getCharSequence(Intent.EXTRA_TEXT).toString();
		}

		setIntent(null);
		
		return intentUri;
    }
}
