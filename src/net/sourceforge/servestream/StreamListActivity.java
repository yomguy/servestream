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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.utils.PreferenceConstants;
import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.utils.UpdateHelper;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class StreamListActivity extends ListActivity {
	public final static String TAG = "ServeStream.StreamListActivity";
	
	public final static int REQUEST_EDIT = 1;
	
	private TextView quickconnect = null;
	private Button goButton = null;
	
	private Stream requestedStream = null;
	
	protected StreamDatabase streamdb = null;
	protected LayoutInflater inflater = null;
	
	private SharedPreferences mPreferences = null;
	
	protected boolean m_makingShortcut = false;
	
	protected Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			StreamListActivity.this.updateList();
		}
	};
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_streamlist);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_list)));
		
		// check for eula agreement
		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		quickconnect = (TextView) this.findViewById(R.id.front_quickconnect);
		quickconnect.setVisibility(m_makingShortcut ? View.GONE : View.VISIBLE);
		
		try {
			if (getIntent().getData() != null)
				quickconnect.setText(URLDecoder.decode(getIntent().getData().toString(), "UTF-8"));
		} catch (UnsupportedEncodingException ex) {
			quickconnect.setText(getIntent().getData().toString());
		}
		
		// start thread to check for new version
		new UpdateHelper(this);
		
		// connect with streams database and populate list
		this.streamdb = new StreamDatabase(this);
		
		ListView list = this.getListView();
		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				hideKeyboard();
				
				requestedStream = (Stream) parent.getAdapter().getItem(position);
				handleStream(requestedStream);
			}
		});

		this.registerForContextMenu(list);

		goButton = (Button) this.findViewById(R.id.go_button);
		goButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				
				hideKeyboard();
				
			    if (isValidStream()) {
			    	handleStream(requestedStream);
			    	//saveStream();
			    }
			}
		});
		
		this.inflater = LayoutInflater.from(this);
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		updateList();
		
		if(this.streamdb == null)
			this.streamdb = new StreamDatabase(this);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		if(this.streamdb != null) {
			this.streamdb.close();
			this.streamdb = null;
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem settings = menu.add(R.string.list_menu_settings);
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(StreamListActivity.this, SettingsActivity.class));

		MenuItem help = menu.add(R.string.title_help);
		help.setIcon(android.R.drawable.ic_menu_help);
		help.setIntent(new Intent(StreamListActivity.this, HelpActivity.class));

		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle editing and deleting streams
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final Stream stream = (Stream) this.getListView().getItemAtPosition(info.position);

		// set the menu to the name of the stream
		menu.setHeaderTitle(stream.getNickname());

		// edit the host
		MenuItem edit = menu.add(R.string.list_stream_edit);
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				Intent intent = new Intent(StreamListActivity.this, StreamEditorActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, stream.getId());
				StreamListActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});
		
		// delete the host
		MenuItem delete = menu.add(R.string.list_stream_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(StreamListActivity.this)
					.setMessage(getString(R.string.delete_message, stream.getNickname()))
					.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							streamdb.deleteStream(stream);
							updateHandler.sendEmptyMessage(-1);
						}
						})
					.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});
	}
	
	protected void updateList() {
		
		ArrayList<Stream> streams = new ArrayList<Stream>();

		if (streamdb == null)
			streamdb = new StreamDatabase(this);   

		streams = streamdb.getStreams();

		StreamAdapter adapter = new StreamAdapter(this, streams);

		this.setListAdapter(adapter);
	}

	public void handleStream(Stream stream) {
		
		Intent intent = null;
		int contentTypeCode = -1;
		
		try {
			contentTypeCode = URLUtils.getContentTypeCode(stream.getURL());
			Log.v(TAG, "STREAM is: " + stream.getURL());
		} catch (Exception ex) {
			ex.printStackTrace();
			showURLNotFoundMessage();
			return;
		}
		
		if (contentTypeCode == URLUtils.DIRECTORY) {
			intent = new Intent(StreamListActivity.this, StreamBrowseActivity.class);
		} else if (contentTypeCode == URLUtils.MEDIA_FILE) {
			intent = new Intent(StreamListActivity.this, StreamMediaActivity.class);			
		}
		
		if (intent == null) {
			showURLNotFoundMessage();
			return;
		} else {
			intent.setData(stream.getUri());
			this.startActivity(intent);
			
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true))
			    saveStream();
		}	
	}
	
	class StreamAdapter extends ArrayAdapter<Stream> {
		
		private ArrayList<Stream> streams;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
		}

		public StreamAdapter(Context context, ArrayList<Stream> streams) {
			super(context, R.layout.item_stream, streams);

			this.streams = streams;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.item_stream, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView)convertView.findViewById(android.R.id.text1);
				holder.caption = (TextView)convertView.findViewById(android.R.id.text2);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			Stream stream = streams.get(position);

			holder.nickname.setText(stream.getNickname());

			Context context = convertView.getContext();

			holder.nickname.setTextAppearance(context, android.R.attr.textAppearanceLarge);
			holder.caption.setTextAppearance(context, android.R.attr.textAppearanceSmall);

			long now = System.currentTimeMillis() / 1000;

			String lastConnect = context.getString(R.string.bind_never);
			if (stream.getLastConnect() > 0) {
				int minutes = (int)((now - stream.getLastConnect()) / 60);
				if (minutes >= 60) {
					int hours = (minutes / 60);
					if (hours >= 24) {
						int days = (hours / 24);
						lastConnect = context.getString(R.string.bind_days, days);
					} else
						lastConnect = context.getString(R.string.bind_hours, hours);
				} else
					lastConnect = context.getString(R.string.bind_minutes, minutes);
			}

			holder.caption.setText(lastConnect);

			return convertView;
		}
	}
	
	private boolean isValidStream() {

		String inputStream = quickconnect.getText().toString();	
		
		try {
			requestedStream = new Stream(inputStream);
		} catch (Exception ex) {
			ex.printStackTrace();
			showInvalidURLMessage();
            return false;
		}
		
		return true;
	}
	
	/**
	 * Saves a stream to the stream database
	 */
	private void saveStream() {
		Stream stream = streamdb.findStream(requestedStream);
		
		if (stream == null) {
			streamdb.saveStream(requestedStream);
		}
	}
	
	/**
	 * Hides the keyboard
	 */
	private void hideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		View textView = quickconnect;
		inputManager.hideSoftInputFromWindow(textView.getWindowToken(), 0);
	}
	
	private void showInvalidURLMessage() {
	    new AlertDialog.Builder(StreamListActivity.this)
		.setMessage(R.string.invalid_url_message)
		.setPositiveButton(R.string.invalid_url_pos, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
			}).create().show();
	}
	
	private void showURLNotFoundMessage() {
		new AlertDialog.Builder(StreamListActivity.this)
		.setMessage(R.string.url_not_found_message)
		.setPositiveButton(R.string.url_not_found_pos, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
			}).create().show();	
	}
}
