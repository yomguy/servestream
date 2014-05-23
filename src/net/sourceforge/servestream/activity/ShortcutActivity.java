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

package net.sourceforge.servestream.activity;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.preference.UserPreferences;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class ShortcutActivity extends ActionBarActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_shortcut);
		
		getSupportActionBar();
		
		ListView list = (ListView) findViewById(android.R.id.list);
		list.setEmptyView(findViewById(android.R.id.empty));
		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				UriBean uriBean = (UriBean) parent.getAdapter().getItem(position);				
				
				Intent contents = new Intent(Intent.ACTION_VIEW);
				contents.setType("net.sourceforge.servestream/" + uriBean.getUri());
				contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				
				// create shortcut if requested
				ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(ShortcutActivity.this, R.drawable.ic_launcher);

				Intent intent = new Intent();
				intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, contents);
				intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, uriBean.getNickname());
				intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

				setResult(RESULT_OK, intent);
				finish();
			}
		});
		
		StreamDatabase streamdb = new StreamDatabase(this);
		List<UriBean> uris = new ArrayList<UriBean>();
		uris = streamdb.getUris();
		streamdb.close();

		ArrayAdapter<UriBean> adapter = new ArrayAdapter<UriBean>(this, android.R.layout.simple_list_item_1, uris);
		list.setAdapter(adapter);
	}
}
