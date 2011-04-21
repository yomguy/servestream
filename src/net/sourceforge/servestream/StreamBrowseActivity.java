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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
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
import android.os.AsyncTask;
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

public class StreamBrowseActivity extends ListActivity {
	public final static String TAG = "ServeStream.StreamBrowseActivity";
	
	private final static int START_ACTIVITY = 2;
	private final static int ERROR_MESSAGE = 3;
	
	ArrayList<Stream> mStreamURLs = new ArrayList<Stream>();
	Stream requestedStreamURL = null;
    
    ArrayAdapter<String> adapter = null;
    
	protected StreamDatabase streamdb = null;
	protected LayoutInflater inflater = null;
	private Button mHomeButton = null;

	protected Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == START_ACTIVITY) {
				beginActivity((Intent)msg.obj);
			} else if (msg.what == ERROR_MESSAGE) {
				cannotOpenURLMessage();
			}
		}
	};
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
		setContentView(R.layout.act_browsemedia);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_browse)));        
		
		try {
			Log.v(TAG, getIntent().getData().toString());			
			requestedStreamURL = new Stream(getIntent().getData().toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	    	
		HTMLParseAsyncTask htmlParseAsyncTask = new HTMLParseAsyncTask();
		htmlParseAsyncTask.execute(requestedStreamURL);
		
		this.streamdb = new StreamDatabase(this);
		
		ListView list = this.getListView();
		list.setFastScrollEnabled(true);

		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				DetermineIntentAsyncTask determineIntentAsyncTask = new DetermineIntentAsyncTask();
				determineIntentAsyncTask.setHandler(mHandler);
				determineIntentAsyncTask.execute(mStreamURLs.get(position));
			}
		});
		
		this.registerForContextMenu(list);
		
		mHomeButton = (Button) this.findViewById(R.id.home_button);
		mHomeButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				returnHome();
			}
		});
		
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
	
	private void setStreamURLs(ArrayList<Stream> streamURLs) {
		mStreamURLs = streamURLs;
	}
	
	private void beginActivity(Intent intent) {
		this.startActivity(intent);
	}
	
	private void returnHome() {
		this.startActivity(new Intent(StreamBrowseActivity.this, StreamListActivity.class));
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
    
    private void cannotOpenURLMessage() {
		new AlertDialog.Builder(StreamBrowseActivity.this)
		.setMessage("Sorry, the following URL cannot be opened")
		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
			}).create().show();
    }
    
    public class HTMLParseAsyncTask extends AsyncTask<Stream, Void, StreamAdapter> {

		ProgressDialog mDialog;
		StreamAdapter adapter;
		
	    public HTMLParseAsyncTask() {
	        super();
	    }

	    @Override
	    protected void onPreExecute() {
	    	mDialog = new ProgressDialog(StreamBrowseActivity.this);
	        mDialog.setMessage("Loading. Please wait...");
	        mDialog.setIndeterminate(true);
	        mDialog.setCancelable(true);
	        mDialog.show();
	    }
	    
		@Override
		protected StreamAdapter doInBackground(Stream... stream) {
			StreamParser streamParser = null;
			
			try {
				streamParser = new StreamParser(stream[0].getURL());
				streamParser.getListing();
			} catch (MalformedURLException ex) {
				ex.printStackTrace();
			}
			
			setStreamURLs(streamParser.getParsedURLs());
	        return new StreamAdapter(StreamBrowseActivity.this, streamParser.getParsedURLs());
		}

		@Override
		protected void onPostExecute(StreamAdapter adapter) {
			setListAdapter(adapter);
			mDialog.dismiss();
		}
		
	}
    
    public class DetermineIntentAsyncTask extends AsyncTask<Stream, Void, Intent> {

		ProgressDialog mDialog;
		Handler mHandler;
		
	    public DetermineIntentAsyncTask() {
	        super();
	    }

	    @Override
	    protected void onPreExecute() {
	    	mDialog = new ProgressDialog(StreamBrowseActivity.this);
	        mDialog.setMessage(getString(R.string.opening_url_message));
	        mDialog.setIndeterminate(true);
	        mDialog.setCancelable(true);
	        mDialog.show();
	    }
	    
		@Override
		protected Intent doInBackground(Stream... stream) {
		    return handleStream(stream[0]);
		}

		@Override
		protected void onPostExecute(Intent result) {
			if (result != null) {
				Message msg = Message.obtain();
				msg.what = START_ACTIVITY;
				msg.obj = result;
				mHandler.sendMessage(msg);
			} else {
				Message msg = Message.obtain();
				msg.what = ERROR_MESSAGE;
				mHandler.sendMessage(msg);
			}
			
			mDialog.dismiss();
		}

		public Intent handleStream(Stream stream) {
			
			Intent intent = null;
			String contentTypeCode = null;
			URLUtils urlUtils = null;
			
			try {
				urlUtils = new URLUtils(stream.getURL());
				Log.v(TAG, "STREAM is: " + stream.getURL());
			} catch (Exception ex) {
				ex.printStackTrace();
				//showURLNotFoundMessage();
				return null;
			}
			
			if (urlUtils.getResponseCode() == HttpURLConnection.HTTP_OK) {
			
				contentTypeCode = urlUtils.getContentType();
				
				if (contentTypeCode.equalsIgnoreCase("text/html")) {
					intent = new Intent(StreamBrowseActivity.this, StreamBrowseActivity.class);
				} else { //if (contentTypeCode == URLUtils.MEDIA_FILE) {
					intent = new Intent(StreamBrowseActivity.this, StreamMediaActivity.class);			
				}
		    }
			
			if (intent == null) {
				//showURLNotFoundMessage();
				return null;
			} else {
				intent.setData(stream.getUri());
			}
			
			return intent;
		}
		
		public void setHandler(Handler mHandler) {
			this.mHandler = mHandler;
		}
			
	}
}
