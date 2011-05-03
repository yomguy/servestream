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

import java.io.File;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
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
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
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
    
    private Stream mBaseURL = null;
	private Stream mCurrentURL = null;
	private ArrayList <Stream> mCurrentListing = new ArrayList<Stream>();	
    
	protected StreamDatabase mStreamdb = null;
	protected LayoutInflater mInflater = null;
	private Button mHomeButton = null;
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
		setContentView(R.layout.act_browsemedia);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_browse)));        
		
		try {
			Log.v(TAG, getIntent().getData().toString());
			mBaseURL = new Stream(getIntent().getData().toString());
			mCurrentURL = mBaseURL;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	    	
		new HTMLParseAsyncTask().execute(mCurrentURL);
		
		// connect with streams database
		this.mStreamdb = new StreamDatabase(this);
		
		ListView list = this.getListView();
		list.setFastScrollEnabled(true);

		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				//mCurrentURL = mCurrentListing.get(position);
				new DetermineIntentAsyncTask().execute(mCurrentListing.get(position));
			}
		});
		
		this.registerForContextMenu(list);
		
		mHomeButton = (Button) this.findViewById(R.id.home_button);
		mHomeButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				StreamBrowseActivity.this.startActivity(new Intent(StreamBrowseActivity.this, StreamListActivity.class));
			}
		});
		
		this.mInflater = LayoutInflater.from(this);
    }
    
	@Override
	public void onStart() {
		super.onStart();
		
		// connect to the stream database if we don't
		// already have a connection
		if(this.mStreamdb == null)
			this.mStreamdb = new StreamDatabase(this);
		
		// if the current URL exists in the stream database
		// update its timestamp
		try {
		    Stream stream = mStreamdb.findStream(mCurrentURL);
		
		    if (stream != null) {
			    mStreamdb.touchHost(stream);
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
		if(this.mStreamdb != null) {
			this.mStreamdb.close();
			this.mStreamdb = null;
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
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	String parent = new File(mCurrentURL.getPath().toString()).getParent();
	    	
			if (mCurrentURL.equals(mBaseURL) || parent == null) {
				finish();
			} else {
				try {
					String parentDirectory = null;
					
				    parentDirectory = getHost(mCurrentURL.getURL()) + parent;
				    
				    Log.v("Parent dir", parentDirectory);
					mCurrentURL = new Stream(parentDirectory);
					new HTMLParseAsyncTask().execute(mCurrentURL);
				} catch (MalformedURLException ex) {
					ex.printStackTrace();
				}
			}
	        return true;
	    }
	    
	    return super.onKeyDown(keyCode, event);
	}

    /**
     * 
     */
    private String getHost(URL targetURL) throws MalformedURLException {
    	return targetURL.getProtocol() + 
    			"://" + targetURL.getHost() + 
    			":" + String.valueOf(targetURL.getPort());
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
				convertView = mInflater.inflate(R.layout.item_browsestream, null, false);

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
		Stream stream = mStreamdb.findStream(targetStream);
		
		if (stream == null) {
			mStreamdb.saveStream(targetStream);
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

		private ProgressDialog mDialog;
		
	    public HTMLParseAsyncTask() {
	        super();
	    }

	    @Override
	    protected void onPreExecute() {
	    	mDialog = new ProgressDialog(StreamBrowseActivity.this);
	    	mDialog.setMessage(getString(R.string.loading_message));
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
				StreamBrowseActivity.this.mCurrentListing = streamParser.getParsedLinks();
			} catch (MalformedURLException ex) {
				ex.printStackTrace();
			}
			
	        return new StreamAdapter(StreamBrowseActivity.this, streamParser.getParsedLinks());
		}

		@Override
		protected void onPostExecute(StreamAdapter adapter) {
			setListAdapter(adapter);
			mDialog.dismiss();
		}
		
	}
    
    public class DetermineIntentAsyncTask extends AsyncTask<Stream, Void, Intent> {

		private ProgressDialog mDialog;
		private ArrayList<Stream> streams;
		private Stream mPreviousURL;
		
	    public DetermineIntentAsyncTask() {
	        super();
	    }

	    @Override
	    protected void onPreExecute() {
	    	mDialog = new ProgressDialog(StreamBrowseActivity.this);
	        mDialog.setMessage(getString(R.string.loading_message));
	        mDialog.setIndeterminate(true);
	        mDialog.setCancelable(true);
	        mDialog.show();
	    }
	    
		@Override
		protected Intent doInBackground(Stream... stream) {
			mPreviousURL = stream[0];
		    return handleStream(stream[0]);
		}

		@Override
		protected void onPostExecute(Intent result) {
			mDialog.dismiss();
			
			if (result != null) {
				StreamBrowseActivity.this.startActivity(result);
			} else {
				if (streams == null) {
				    StreamBrowseActivity.this.cannotOpenURLMessage();
				} else {
					setListAdapter(new StreamAdapter(StreamBrowseActivity.this, streams));
					StreamBrowseActivity.this.mCurrentURL = mPreviousURL;
				}
			}
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
				return null;
			}
			
			if (urlUtils.getResponseCode() == HttpURLConnection.HTTP_OK) {			
				contentTypeCode = urlUtils.getContentType();
				
				if (contentTypeCode.equalsIgnoreCase("text/html")) {
					StreamParser streamParser = null;
				    
					try {
					    streamParser = new StreamParser(stream.getURL());
					    streamParser.getListing();
					    StreamBrowseActivity.this.mCurrentListing = streamParser.getParsedLinks();
					    streams = streamParser.getParsedLinks();
				    } catch (MalformedURLException ex) {
					    ex.printStackTrace();
				    }
				} else {
					intent = new Intent(StreamBrowseActivity.this, StreamMediaActivity.class);			
				}
		    }
			
			if (intent != null) {
				intent.setData(stream.getUri());
			}
			
			return intent;
		}
	}
}
