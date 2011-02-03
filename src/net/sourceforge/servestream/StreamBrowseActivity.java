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
	public final static String TAG = "ServeStream.StreamBrowseActivity";
	
    StreamParser streamURLs = null;
	Stream requestedStreamURL = null;
    
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
		
		try {
			Log.v(TAG, getIntent().getData().toString());			
			requestedStreamURL = new Stream(getIntent().getData().toString());		
	        streamURLs = new StreamParser(requestedStreamURL.getURL());
		} catch (Exception ex) {
			//TODO add handling here
			ex.printStackTrace();
		}
	    	
	    new DataLoadingThread(handler, uiLoadingThread, streamURLs);
		
		this.streamdb = new StreamDatabase(this);
		
		ListView list = this.getListView();
		uiLoadingThread.setListView(this.getListView());

		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		        handleStream(streamURLs.getParsedURL(position));
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
		try {
		    Stream stream = streamdb.findStream(requestedStreamURL);
		
		    if (stream != null) {
			    streamdb.touchHost(stream);
		    }
		}
		catch (Exception ex) {
		    ex.printStackTrace();
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
		final Stream stream = (Stream) this.getListView().getItemAtPosition(info.position);
		
		try {
			final String streamURL = stream.getURL().toString();
		
		// set the menu title to the name attribute of the URL link
		menu.setHeaderTitle(stream.getNickname());

		// save the URL
		MenuItem save = menu.add(R.string.list_stream_save);
		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(StreamBrowseActivity.this)
					.setMessage(getString(R.string.save_message, streamURL))
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
					.setMessage(streamURL)
					.setPositiveButton(R.string.view_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            return;
						}
						}).create().show();
				return true;
			}
		});
		
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public void handleStream(Stream stream) {
		
		Intent intent = null;
		int contentTypeCode = -1;
		
		try {
			contentTypeCode = URLUtils.getContentTypeCode(stream.getURL());
			Log.v(TAG, "STREAM is: " + stream.getURL());
		} catch (Exception ex) {
			cannotOpenURLMessage();
			return;
		}
		
		if (contentTypeCode == URLUtils.DIRECTORY) {
			dialog = ProgressDialog.show(StreamBrowseActivity.this, "", 
	                "Loading. Please wait...", true);
			try {
				requestedStreamURL = stream;
				streamURLs = new StreamParser(requestedStreamURL.getURL());
		    	new DataLoadingThread(handler, uiLoadingThread, streamURLs);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		} else if (contentTypeCode == URLUtils.MEDIA_FILE) {
			intent = new Intent(StreamBrowseActivity.this, StreamMediaActivity.class);
			intent.setData(stream.getUri());
			this.startActivity(intent);
		} else if (contentTypeCode == URLUtils.NOT_FOUND) {
			cannotOpenURLMessage();
			return;
		}
	}
	
	class StreamAdapter extends ArrayAdapter<Stream> {
		
		private ArrayList<Stream> streams;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public StreamAdapter(Context context, ArrayList<Stream> streams) {
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
				holder.icon = (ImageView) convertView.findViewById(R.id.icon);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			Stream stream = streams.get(position);

			//holder.icon.setImageState(new int[] { android.R.attr.state_pressed }, true);
			String contentType = null;
			if ((contentType = stream.getContentType()) != null) {
			    if (contentType.equals("text"))
			    	holder.icon.setBackgroundResource(R.drawable.folder);
			    else if (contentType.equals("audio"))
			    	holder.icon.setBackgroundResource(R.drawable.audio);
			    else if (contentType.equals("video"))
			    	holder.icon.setBackgroundResource(R.drawable.video);
			    else
			    	holder.icon.setBackgroundResource(R.drawable.none);
			} else {
				holder.icon.setBackgroundResource(R.drawable.none);
			}
			
			holder.nickname.setText(stream.getNickname());

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
            uiLoadingThread.setStreams(streamURLs.getParsedURLs());
            handler.post(uiLoadingThread);
   
            // we are done retrieving, parsing and adding links
            // to the list so dismiss the dialog
            dialog.dismiss();
        }
    }
	
    private class UILoadingHelperClass implements Runnable {
        
    	private ListView m_listView = null;
        private ArrayList<Stream> m_streams = null;

        public void setStreams(ArrayList<Stream> streams){
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
    
    private void cannotOpenURLMessage() {
		new AlertDialog.Builder(StreamBrowseActivity.this)
		.setMessage("Sorry, the following URL cannot be opened")
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
			}).create().show();
    }
}
