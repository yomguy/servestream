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

package net.sourceforge.servestream.activity;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.SettingsActivity;
import net.sourceforge.servestream.activity.URLListActivity;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.filemanager.*;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.URLUtils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BrowserActivity extends ListActivity implements ServiceConnection { 
	private final static String TAG = BrowserActivity.class.getName();

 	public static final int MESSAGE_SHOW_DIRECTORY_CONTENTS = 1;
    public static final int MESSAGE_HANDLE_INTENT = 2;
    public static final int MESSAGE_PARSE_WEBPAGE = 3;
	
    private static final int NO_INTENT = -1;
    private static final int STREAM_MEDIA_INTENT = 1;
    
    private final static int DETERMINE_INTENT_TASK = 1;
    
	/** Contains directories and files together */
    private ArrayList<IconifiedText> directoryEntries = new ArrayList<IconifiedText>();

    /** Dir separate for sorting */
    private List<IconifiedText> mListFiles = new ArrayList<IconifiedText>();

    private int mStepsBack;
    private Stream [] mDirectory = null;

    private TextView mEmptyText;
     
    private DirectoryScanner mDirectoryScanner;

	private InputMethodManager mInputManager = null;
	private StreamDatabase mStreamdb = null;
	private Button mHomeButton = null;

    private ServiceToken mToken;
	
	protected Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {			
			BrowserActivity.this.handleMessage(msg);
		}
	};
	
	protected Handler mQueueHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			MusicUtils.addToCurrentPlaylist(BrowserActivity.this, (long []) msg.obj);
		}
	};
	
    /** Called when the activity is first created. */ 
    @Override 
    public void onCreate(Bundle icicle) { 
    	super.onCreate(icicle); 

        setContentView(R.layout.act_webpagebrowser);
    	
		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_browse)));  
    	
		try {
			Log.v(TAG, getIntent().getData().toString());
			mStepsBack = 0;
			mDirectory = new Stream[1000];
			mDirectory[mStepsBack] = new Stream(getIntent().getData().toString());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		// connect with streams database
		this.mStreamdb = new StreamDatabase(this);
        
		ListView list = this.getListView();
		list.setOnCreateContextMenuListener(this);
		list.setEmptyView(findViewById(R.id.empty));
		list.setFastScrollEnabled(true);
	    list.setTextFilterEnabled(true);
        
        mEmptyText = (TextView) findViewById(R.id.empty_text);
        mEmptyText.setVisibility(View.GONE);
	    
		mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
	    
		mHomeButton = (Button) this.findViewById(R.id.home_button);
		mHomeButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				BrowserActivity.this.startActivity(new Intent(BrowserActivity.this, URLListActivity.class));
			}
		});
	    
		refreshList();
    }
  
	@Override
	public void onStart() {
		super.onStart();
		
        mToken = MusicUtils.bindToService(this, this);
		
		// connect to the stream database if we don't
		// already have a connection
		if(this.mStreamdb == null)
			this.mStreamdb = new StreamDatabase(this);
		
		// if the current URL exists in the stream database
		// update its timestamp
		try {
		    Stream stream = mStreamdb.findStream(mDirectory[mStepsBack]);
		
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
		
        MusicUtils.unbindFromService(mToken);
	}
	
	@Override
    public void onDestroy() {
    	super.onDestroy();
    	 
    	// stop the scanner
    	if (mDirectoryScanner != null) {
    		mDirectoryScanner.cancel = true;
    	}
    	 
    	mDirectoryScanner = null;
    }
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
        	case (R.id.menu_item_refresh):
        		refreshList();
        		break;
        	case (R.id.menu_item_settings):
        		startActivity(new Intent(BrowserActivity.this, SettingsActivity.class));
    			break;
    	}
    	
		return false;
    }
	
	protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    ProgressDialog progressDialog = null;
	    switch(id) {
	    case DETERMINE_INTENT_TASK:
	    	progressDialog = new ProgressDialog(BrowserActivity.this);
	    	progressDialog.setMessage(getString(R.string.loading_message));
	    	progressDialog.setCancelable(true);
	    	return progressDialog;
	    default:
	        dialog = null;
	    }
	    return dialog;
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stream_browse_menu, menu);
        return true;
    }
    
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle stream URLs
		
		// create menu to handle deleting and sharing lists		
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final IconifiedTextListAdapter adapter = (IconifiedTextListAdapter) getListAdapter();
        IconifiedText it = (IconifiedText) adapter.getItem(info.position);
		final Stream stream = it.getStream();
		
		try {
			final String streamURL = stream.getURL().toString();
		
		// set the menu title to the name attribute of the URL link
		menu.setHeaderTitle(stream.getNickname());

		// save the URL
		MenuItem save = menu.add(R.string.save);
		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(BrowserActivity.this)
					.setMessage(getString(R.string.save_message, streamURL))
					.setPositiveButton(R.string.save_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            saveStream(stream);
						}
						})
					.setNegativeButton(android.R.string.cancel, null).create().show();
				return true;
			}
		});
	
		// view the URL
		MenuItem view = menu.add(R.string.view_url);
		view.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// display the URL
				new AlertDialog.Builder(BrowserActivity.this)
					.setMessage(streamURL)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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
		
		// add to playlist
		MenuItem add = menu.add(R.string.add_to_playlist);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				MusicUtils.addToCurrentPlaylistFromURL(BrowserActivity.this, mQueueHandler, stream);
				return true;
			}
		});
		
		// share the URL
		MenuItem share = menu.add(R.string.share);
		share.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				String url = stream.getUri().toString();
				String appName = getString(R.string.app_name);
				
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_signature, url, appName));
				startActivity(Intent.createChooser(intent, getString(R.string.title_share)));
				return true;
			}
		});
	}
    
    private void handleMessage(Message message) {    	 
    	switch (message.what) {
    		case MESSAGE_SHOW_DIRECTORY_CONTENTS:
    			showDirectoryContents((DirectoryContents) message.obj);
    			break;
    		case MESSAGE_HANDLE_INTENT:
    			handleIntent(message);
    			break;
			case MESSAGE_PARSE_WEBPAGE:
				mStepsBack++;
				mDirectory[mStepsBack] = (Stream) message.obj;
				refreshList();
				break;
    	}
    }
     
    private void showDirectoryContents(DirectoryContents contents) {
    	mDirectoryScanner = null;
    	 
    	mListFiles = contents.getListFiles();
    	 
        addAllElements(directoryEntries, mListFiles);

        IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this); 
        itla.setListItems(directoryEntries, getListView().hasTextFilter());          
        setListAdapter(itla);
	    getListView().requestFocus();

    	mEmptyText.setVisibility(View.VISIBLE);
    	
		try {
			removeDialog(DETERMINE_INTENT_TASK);
		} catch (Exception ex) {
		}
    }
      
    /** 
     * This function browses up one level 
     * according to the field: currentDirectory 
     */ 
    private void upOneLevel(){
    	if (mStepsBack > 0) {
    		mStepsBack--;
    		refreshList();
    		//browseTo(mDirectory[mStepsBack]);
    	}
    }
     
    private void browseTo(Stream url) {
	    showDialog(DETERMINE_INTENT_TASK);
        new DetermineIntentAsyncTask().execute(url);
    }

    private void refreshList() {
    	showDialog(DETERMINE_INTENT_TASK);
    	
    	// Cancel an existing scanner, if applicable.
    	DirectoryScanner scanner = mDirectoryScanner;
    	  
    	if (scanner != null) {
    	    scanner.cancel = true;
    	}
    	  
    	directoryEntries.clear(); 
        mListFiles.clear();
          
        //setProgressBarIndeterminateVisibility(true);
          
        // Don't show the "folder empty" text since we're scanning.
        mEmptyText.setVisibility(View.GONE);
          
        setListAdapter(null); 
          
        mDirectoryScanner = new DirectoryScanner(mDirectory[mStepsBack], this, mHandler);
	    mDirectoryScanner.start();
    } 
     
    /*private void selectInList(Stream selectFile)
     */
     
    private void addAllElements(List<IconifiedText> addTo, List<IconifiedText> addFrom) {
        int size = addFrom.size();
    	for (int i = 0; i < size; i++) {
            addTo.add(addFrom.get(i));
    	}
    }
     
    @Override 
    protected void onListItemClick(ListView l, View v, int position, long id) { 
        super.onListItemClick(l, v, position, id); 
          
        IconifiedTextListAdapter adapter = (IconifiedTextListAdapter) getListAdapter();
          
        if (adapter == null) {
        	return;
        }
        
        IconifiedText text = (IconifiedText) adapter.getItem(position);
        browseTo(text.getStream());
    }
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (mStepsBack > 0) {
				upOneLevel();
				return true;
			}
		} else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
			mInputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            return true;
		}
		
		return super.onKeyDown(keyCode, event);
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
	
	private void handleIntent(Message message) {
		try {
			removeDialog(DETERMINE_INTENT_TASK);
		} catch (Exception ex) {
		}
		
		switch (message.arg1) {
			case STREAM_MEDIA_INTENT:
		        MusicUtils.playAll(BrowserActivity.this, (long []) message.obj, 0);
				break;
			case NO_INTENT:
				BrowserActivity.this.showUrlNotOpenedToast();
				break;
		}
	}
	
	private void showUrlNotOpenedToast() {
		Toast.makeText(this, R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
	}
    
	public class DetermineIntentAsyncTask extends AsyncTask<Stream, Void, Message> {
		
	    public DetermineIntentAsyncTask() {
	        super();
	    }
	    
		@Override
		protected Message doInBackground(Stream... stream) {
		    return handleURL(stream[0]);
		}
		
		@Override
		protected void onPostExecute(Message message) {
			message.sendToTarget();
		}

		private Message handleURL(Stream stream) {
			String contentType = null;
			URLUtils urlUtils = null;
			Message message = null;
			
			try {
				urlUtils = new URLUtils(stream.getURL());
				Log.v(TAG, "URI is: " + stream.getURL());
			} catch (Exception ex) {
				ex.printStackTrace();
				return null;
			}
			
			if (urlUtils.getResponseCode() == HttpURLConnection.HTTP_OK) {			
				contentType = urlUtils.getContentType();
		    }
			
			if (contentType == null) {
				message = mHandler.obtainMessage(URLListActivity.MESSAGE_HANDLE_INTENT);
				message.arg1 = NO_INTENT;
			} else if (contentType.contains("text/html")) {
				message = mHandler.obtainMessage(BrowserActivity.MESSAGE_PARSE_WEBPAGE);
				message.obj = stream;
			} else {
				long[] list = null;
				try {
					list = MusicUtils.getFilesInPlaylist(BrowserActivity.this, stream.getURL(), contentType);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
				
				message = mHandler.obtainMessage(URLListActivity.MESSAGE_HANDLE_INTENT);
		        message.arg1 = STREAM_MEDIA_INTENT;
		        message.obj = list;
			}
			
			return message;
		}	
	}

	public void onServiceConnected(ComponentName arg0, IBinder arg1) {
		
	}

	public void onServiceDisconnected(ComponentName arg0) {
		
	}
}
