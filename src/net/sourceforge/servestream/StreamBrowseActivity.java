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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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
	public final static String TAG = "ServeStream.StreamBrowseActivity";
	
    StreamParser streamURLs = null;
	String currentStreamURL = null;
    
    ArrayAdapter<String> adapter = null;
    ProgressDialog dialog = null;
    
	protected StreamDatabase streamdb = null;
	protected LayoutInflater inflater = null;

    private final Handler handler = new Handler();
    private final UILoadingHelperClass uiLoadingThread = new UILoadingHelperClass();
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
		setContentView(R.layout.act_browsemedia);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_browse)));        
        
		dialog = ProgressDialog.show(StreamBrowseActivity.this, "", 
                "Loading. Please wait...", true);
		
		currentStreamURL = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
		
		try {
	    	streamURLs = new StreamParser(currentStreamURL);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	    	
	    new DataLoadingThread(handler, uiLoadingThread, streamURLs);
		
		this.streamdb = new StreamDatabase(this);
		
		ListView list = this.getListView();
		uiLoadingThread.setListView(this.getListView());

		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				try {
				    String targetStream = streamURLs.getHREF(position);
				    handleStream(targetStream);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		
		this.registerForContextMenu(list);
		
		this.inflater = LayoutInflater.from(this);
    }
    
	@Override
	public void onStart() {
		super.onStart();
		
		// connect to the stream database if we don't
		// already have a connection
		if(this.streamdb == null)
			this.streamdb = new StreamDatabase(this);
		
		// if the current URL exists in the stream database
		// update its timestamp
		Stream tempStream = new Stream();
		tempStream.createStream(currentStreamURL);
		Stream stream = streamdb.findStream(tempStream);
		
		if (stream != null) {
			streamdb.touchHost(stream);
		}
	}
    
	@Override
	public void onStop() {
		super.onStop();
		
		// close the connection to the database
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
		settings.setIntent(new Intent(StreamBrowseActivity.this, SettingsActivity.class));

		MenuItem help = menu.add(R.string.title_help);
		help.setIcon(android.R.drawable.ic_menu_help);
		help.setIntent(new Intent(StreamBrowseActivity.this, HelpActivity.class));

		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle stream URLs
		
		// create menu to handle deleting and sharing lists
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final String streamString = (String) this.getListView().getItemAtPosition(info.position);
		
		final Stream stream = new Stream();
		stream.createStream(streamURLs.getHREF(info.position));
		
		// set the menu title to the name attribute of the URL link
		menu.setHeaderTitle(streamString);

		// save the URL
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
		
		// view the URL
		MenuItem view = menu.add(R.string.list_stream_view);
		view.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// display the URL
				new AlertDialog.Builder(StreamBrowseActivity.this)
					.setMessage(streamURLs.getHREF(info.position))
					.setPositiveButton(R.string.view_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            return;
						}
						}).create().show();
				return true;
			}
		});		
	}
	
	public void handleStream(String stream) {
		
		Intent intent = null;
		int contentTypeCode = URLUtils.getContentTypeCode(stream);
		
		if (contentTypeCode == URLUtils.DIRECTORY) {
			dialog = ProgressDialog.show(StreamBrowseActivity.this, "", 
	                "Loading. Please wait...", true);
			try {
				currentStreamURL = stream;
				streamURLs = new StreamParser(currentStreamURL);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
	    	new DataLoadingThread(handler, uiLoadingThread, streamURLs);
		} else if (contentTypeCode == URLUtils.MEDIA_FILE) {
			intent = new Intent(StreamBrowseActivity.this, StreamMediaActivity.class);
		    intent.putExtra("net.sourceforge.servestream.TargetStream", stream);
			this.startActivity(intent);
		} else if (contentTypeCode == URLUtils.NOT_FOUND) {
			new AlertDialog.Builder(StreamBrowseActivity.this)
			.setMessage("The following stream cannot be opened!")
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
                    return;
				}
				}).create().show();
		}
	}
	
	class StreamAdapter extends ArrayAdapter<String> {
		
		private ArrayList<String> streams;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public StreamAdapter(Context context, ArrayList<String> streams) {
			super(context, R.layout.item_browsestream, streams);

			this.streams = streams;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.item_browsestream, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView)convertView.findViewById(android.R.id.text1);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			String stream = streams.get(position);

			holder.nickname.setText(stream);

			Context context = convertView.getContext();

			holder.nickname.setTextAppearance(context, android.R.attr.textAppearanceSmall);

			return convertView;
		}
	}
	
	/**
	 * Adds a stream URL to the stream database if it doesn't exist
	 * 
	 * @param targetStream The stream URL to add to the database
	 */
	private void saveStream(Stream targetStream) {
		Stream stream = streamdb.findStream(targetStream);
		
		if (stream == null) {
			streamdb.saveStream(targetStream);
		}
	}

	private class DataLoadingThread extends Thread {

        private final Handler handler;
        private final UILoadingHelperClass uiLoadingThread;
        private final StreamParser streamURLs;

        public DataLoadingThread(Handler handler, UILoadingHelperClass uiLoadingThread, StreamParser streamURLs) {
            this.handler = handler;
            this.uiLoadingThread = uiLoadingThread;
            this.streamURLs = streamURLs;
            this.start();
        }

        @Override
        public void run() {
        	
    		streamURLs.getListing();
            uiLoadingThread.setStreams(streamURLs.getTextLinks());
            handler.post(uiLoadingThread);
   
            // we are done retrieving, parsing and adding links
            // to the list so dismiss the dialog
            dialog.dismiss();
        }
    }
	
    private class UILoadingHelperClass implements Runnable {
        
    	private ListView m_listView = null;
        private ArrayList<String> m_streams = null;

        public void setStreams(ArrayList<String> streams){
            this.m_streams = streams;
        }

        public void setListView(ListView listView){
            m_listView = listView;
        }

        public void run() {
    		StreamAdapter adapter = new StreamAdapter(StreamBrowseActivity.this, m_streams);
        	m_listView.setAdapter(adapter);
        }      
    }
}
