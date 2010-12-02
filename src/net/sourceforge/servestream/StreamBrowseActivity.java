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

package net.sourceforge.servestream;

import java.util.ArrayList;

import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.utils.StreamParser;
import net.sourceforge.servestream.utils.URLUtils;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class StreamBrowseActivity extends ListActivity {

	public final static int REQUEST_SAVE = 1;
	
    StreamParser m_streamURLs = null;
	String m_currentStreamURL = null;
    
    ArrayAdapter<String> m_adapt = null;
    Context m_currentContext = null;
    
	protected StreamDatabase m_streamdb = null;
	protected LayoutInflater m_inflater = null;
    
	@Override
	public void onStart() {
		super.onStart();
		
		try {
    	m_streamURLs = new StreamParser(m_currentStreamURL);
		} catch (Exception ex) {
		}
    	updateList();
		
		if(this.m_streamdb == null)
			this.m_streamdb = new StreamDatabase(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		if(this.m_streamdb != null) {
			this.m_streamdb.close();
			this.m_streamdb = null;
		}
	}
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
		setContentView(R.layout.act_browsemedia);;

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_browse)));        
        
		m_currentStreamURL = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
		
		this.m_streamdb = new StreamDatabase(this);
		ListView list = this.getListView();

		list.setOnItemClickListener(new OnItemClickListener() {

			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				//Toast.makeText(getApplicationContext(), view.,
				//    Toast.LENGTH_SHORT).show();
				
				try {
				    String targetStream = m_streamURLs.getHREF(position);
				    handleStream(targetStream);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

			}
		});
		
		this.registerForContextMenu(list);
		
		this.m_inflater = LayoutInflater.from(this);
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem settings = menu.add(R.string.list_menu_settings);
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(StreamBrowseActivity.this, SettingsActivity.class));

		MenuItem help = menu.add(R.string.title_help);
		help.setIcon(android.R.drawable.ic_menu_help);
		help.setIntent(new Intent(StreamBrowseActivity.this, HelpActivity.class));

		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle streams
		
		// create menu to handle deleting and sharing lists
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final String streamString = (String) this.getListView().getItemAtPosition(info.position);

		Log.v("stream string",streamString);
		Log.v("url string", m_streamURLs.getHREF(info.position));
		
		final Stream stream = new Stream();
		stream.createStream(m_streamURLs.getHREF(info.position));
		
		// set the menu to the name of the stream
		menu.setHeaderTitle(streamString);

		// save the stream
		MenuItem save = menu.add(R.string.list_stream_save);
		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(StreamBrowseActivity.this)
					.setMessage(getString(R.string.save_message, streamString))
					.setPositiveButton(R.string.save_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            saveStream(stream);
						}
						})
					.setNegativeButton(R.string.save_neg, null).create().show();

				return true;
			}
		});
		
		// view the stream
		MenuItem view = menu.add(R.string.list_stream_view);
		view.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(StreamBrowseActivity.this)
					.setMessage(m_streamURLs.getHREF(info.position))
					.setPositiveButton(R.string.view_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            return;
						}
						}).create().show();

				return true;
			}
		});		
	}
	
	protected void updateList() {
		
		m_streamURLs.getListing();

		ArrayList<String> streams = m_streamURLs.getTextLinks();

		StreamAdapter adapter = new StreamAdapter(this, streams);

		this.setListAdapter(adapter);
	}
	
	public void handleStream(String stream) {
		
		Intent intent = null;
		int contentTypeCode = URLUtils.getContentTypeCode(stream);
		
		if (contentTypeCode == URLUtils.DIRECTORY) {
			try {
				m_currentStreamURL = stream;
				m_streamURLs = new StreamParser(m_currentStreamURL);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			updateList();
		} else if (contentTypeCode == URLUtils.MEDIA_FILE) {
			intent = new Intent(StreamBrowseActivity.this, StreamMediaActivity.class);
		    intent.putExtra("net.sourceforge.servestream.TargetStream", stream);
			this.startActivity(intent);
		}
	}
	
	class StreamAdapter extends ArrayAdapter<String> {
		
		private ArrayList<String> m_streams;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public StreamAdapter(Context context, ArrayList<String> streams) {
			super(context, R.layout.item_browsestream, streams);

			this.m_streams = streams;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = m_inflater.inflate(R.layout.item_browsestream, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView)convertView.findViewById(android.R.id.text1);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			String stream = m_streams.get(position);
			if (stream == null) {
				// Well, something bad happened. We can't continue.
				Log.e("URLAdapter", "URL is null!");

				holder.nickname.setText("Error during lookup");
				holder.caption.setText("see 'adb logcat' for more");
				return convertView;
			}

			holder.nickname.setText(stream);

			Context context = convertView.getContext();

			holder.nickname.setTextAppearance(context, android.R.attr.textAppearanceSmall);

			return convertView;
		}
	}
	
	private void saveStream(Stream targetStream) {
		Stream stream = m_streamdb.findStream(targetStream);
		
		if (stream == null) {
			m_streamdb.saveStream(targetStream);
		}
	}

}
