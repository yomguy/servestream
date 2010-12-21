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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.utils.URLUtils;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class StreamListActivity extends ListActivity {
	public final static String TAG = "ServeStream.StreamListActivity";
	
	public final static int REQUEST_EDIT = 1;
	
	//private Spinner m_protocolSpinner = null;
	private TextView m_quickconnect = null;
	private Button m_goButton = null;
	
	private Stream m_targetStream = null;
	
	protected StreamDatabase m_streamdb = null;
	protected LayoutInflater m_inflater = null;
	protected boolean m_makingShortcut = false;
	//private SharedPreferences m_preferences = null;
	protected boolean m_sortedByColor = false;
	
	//private final String [] m_protocolTypes = {"http", "https"};
	
	protected Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			StreamListActivity.this.updateList();
		}
	};
	
	@Override
	public void onStart() {
		super.onStart();
		
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
		setContentView(R.layout.act_streamlist);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_list)));

		//ExceptionHandler.register(this);

		//m_preferences = PreferenceManager.getDefaultSharedPreferences(this);
		
		/*m_protocolSpinner = (Spinner)findViewById(R.id.protocol_selection);
		ArrayAdapter<String> protocolSelection = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, m_protocolTypes);
		protocolSelection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		m_protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
				String formatHint = ProtocolFactory.getProtocolHint(
						(String) m_protocolSpinner.getSelectedItem());

				m_quickconnect.setHint(formatHint);
				m_quickconnect.setError(null);
				m_quickconnect.requestFocus();
			}
			public void onNothingSelected(AdapterView<?> arg0) { }
		});
		m_protocolSpinner.setAdapter(protocolSelection);
		*/
		
		// connect with streams database and populate list
		this.m_streamdb = new StreamDatabase(this);
		ListView list = this.getListView();

		list.setOnItemClickListener(new OnItemClickListener() {

			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				m_targetStream = (Stream) parent.getAdapter().getItem(position);
				handleStream(m_targetStream);
			}
		});

		this.registerForContextMenu(list);

		m_goButton = (Button) this.findViewById(R.id.go_button);
		m_goButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
			    if (isValidStream()) {
			    	saveStream();
			    	handleStream(m_targetStream);
			    }
			}
			
		});
		
		m_quickconnect = (TextView) this.findViewById(R.id.front_quickconnect);
		m_quickconnect.setVisibility(m_makingShortcut ? View.GONE : View.VISIBLE);
		
		this.m_inflater = LayoutInflater.from(this);
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

		// create menu to handle hosts

		// create menu to handle deleting and sharing lists
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final Stream stream = (Stream) this.getListView().getItemAtPosition(info.position);

		// set the menu to the name of the host
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
							m_streamdb.deleteStream(stream);
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

		if (m_streamdb == null)
			m_streamdb = new StreamDatabase(this);   

		streams = m_streamdb.getStreams();

		StreamAdapter adapter = new StreamAdapter(this, streams);

		this.setListAdapter(adapter);
	}

	public void handleStream(Stream stream) {
		
		Intent intent = null;
		int contentTypeCode = URLUtils.getContentTypeCode(stream.getStream());
		
		if (contentTypeCode == URLUtils.DIRECTORY) {
			intent = new Intent(StreamListActivity.this, StreamBrowseActivity.class);
		} else if (contentTypeCode == URLUtils.MEDIA_FILE) {
			intent = new Intent(StreamListActivity.this, StreamMediaActivity.class);			
		}
		
		if (intent == null) {
			new AlertDialog.Builder(StreamListActivity.this)
			.setMessage("The following Stream cannot be found!")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
                    return;
				}
				}).create().show();
		} else {
		    intent.putExtra("net.sourceforge.servestream.TargetStream", stream.getStream());
			this.startActivity(intent);	
		}	
	}
	
	class StreamAdapter extends ArrayAdapter<Stream> {
		
		private ArrayList<Stream> m_streams;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public StreamAdapter(Context context, ArrayList<Stream> streams) {
			super(context, R.layout.item_stream, streams);

			this.m_streams = streams;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = m_inflater.inflate(R.layout.item_stream, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView)convertView.findViewById(android.R.id.text1);
				holder.caption = (TextView)convertView.findViewById(android.R.id.text2);
				holder.icon = (ImageView)convertView.findViewById(android.R.id.icon);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			Stream stream = m_streams.get(position);
			if (stream == null) {
				// Well, something bad happened. We can't continue.
				Log.e("HostAdapter", "Host is null!");

				holder.nickname.setText("Error during lookup");
				holder.caption.setText("see 'adb logcat' for more");
				return convertView;
			}

			holder.nickname.setText(stream.getNickname());

			Context context = convertView.getContext();

			holder.nickname.setTextAppearance(context, android.R.attr.textAppearanceLarge);
			holder.caption.setTextAppearance(context, android.R.attr.textAppearanceSmall);

			long now = System.currentTimeMillis() / 1000;

			String nice = context.getString(R.string.bind_never);
			if (stream.getLastConnect() > 0) {
				int minutes = (int)((now - stream.getLastConnect()) / 60);
				if (minutes >= 60) {
					int hours = (minutes / 60);
					if (hours >= 24) {
						int days = (hours / 24);
						nice = context.getString(R.string.bind_days, days);
					} else
						nice = context.getString(R.string.bind_hours, hours);
				} else
					nice = context.getString(R.string.bind_minutes, minutes);
			}

			holder.caption.setText(nice);

			return convertView;
		}
	}
	
	public boolean isValidStream() {
		
		//String protocol = ProtocolFactory.getProtocol((String) m_protocolSpinner
		//		.getSelectedItem());

		String stringStream = m_quickconnect.getText().toString();	
		
		if (stringStream == null) {
			new AlertDialog.Builder(StreamListActivity.this)
			.setMessage(R.string.invalid_url_message)
			.setPositiveButton(R.string.invalid_url_pos, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
				}
				}).create().show();
			
            return false;
		}
		
		//stringStream = protocol + stringStream;
		
		m_targetStream = new Stream();
		if (!m_targetStream.createStream(stringStream)) {
			new AlertDialog.Builder(StreamListActivity.this)
			.setMessage(R.string.invalid_url_message)
			.setPositiveButton(R.string.invalid_url_pos, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
				}
				}).create().show();
				
	        return false;
		}
		
		return true;
	}
	
	private void saveStream() {
		Stream stream = m_streamdb.findStream(m_targetStream);
		
		if (stream == null) {
			m_streamdb.saveStream(m_targetStream);
		}
	}
	
}
