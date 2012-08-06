/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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
import java.util.HashMap;
import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.SettingsActivity;
import net.sourceforge.servestream.activity.URLListActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.filemanager.*;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.DetermineActionTask;
import net.sourceforge.servestream.utils.MusicUtils;
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
import android.media.AudioManager;
import android.net.Uri;
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

public class BrowserActivity extends ListActivity implements ServiceConnection,
				DetermineActionTask.MusicRetrieverPreparedListener {

	private final static String TAG = BrowserActivity.class.getName();

 	public static final int MESSAGE_SHOW_DIRECTORY_CONTENTS = 1;
    public static final int MESSAGE_PARSE_WEBPAGE = 2;
	
    private final static int DETERMINE_INTENT_TASK = 1;
    
	/** Contains directories and files together */
    private ArrayList<IconifiedText> directoryEntries = new ArrayList<IconifiedText>();

    /** Dir separate for sorting */
    private List<IconifiedText> mListFiles = new ArrayList<IconifiedText>();

    private int mStepsBack;
    private UriBean [] mDirectory = null;

    private TextView mEmptyText;
     
    private DirectoryScanner mDirectoryScanner;
    private HashMap<Integer, UriBean> mPreviousDirectory = new HashMap<Integer, UriBean>();
    
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

        setContentView(R.layout.act_browser);
    	
		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_browse)));  
    	
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		try {
			Log.v(TAG, getIntent().getData().toString());
			mStepsBack = 0;
			mDirectory = new UriBean[1000];
			Uri uri = TransportFactory.getUri(getIntent().getData().toString());
			mDirectory[mStepsBack] = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
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
		UriBean uri = TransportFactory.findUri(mStreamdb, mDirectory[mStepsBack].getUri());
		
		if (uri != null) {
	        mStreamdb.touchUri(uri);
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
		final UriBean uri = it.getUri();
		
		try {
			final String streamURL = uri.getUri().toString();
		
		// set the menu title to the name attribute of the URL link
		menu.setHeaderTitle(uri.getNickname());

		// save the URL
		MenuItem save = menu.add(R.string.save);
		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(BrowserActivity.this)
					.setMessage(getString(R.string.save_message, streamURL))
					.setPositiveButton(R.string.save_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            saveUri(uri);
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
				MusicUtils.addToCurrentPlaylistFromURL(BrowserActivity.this, mQueueHandler, uri);
				return true;
			}
		});
		
		// share the URL
		MenuItem share = menu.add(R.string.share);
		share.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				String url = uri.getUri().toString();
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
			case MESSAGE_PARSE_WEBPAGE:
				mPreviousDirectory.put(mStepsBack, (UriBean) message.obj);
				mStepsBack++;
				mPreviousDirectory.put(mStepsBack, null);
				mDirectory[mStepsBack] = (UriBean) message.obj;
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

		selectInList(mPreviousDirectory.get(mStepsBack));
	    
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
     
    private void browseTo(UriBean uri) {
	    showDialog(DETERMINE_INTENT_TASK);
        new DetermineActionTask(this, uri, this).execute();
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

    private void selectInList(UriBean uri) {
    	if (uri == null) {
    		return;
    	}
    	
    	IconifiedTextListAdapter la = (IconifiedTextListAdapter) getListAdapter();
    	int count = la.getCount();
    	for (int i = 0; i < count; i++) {
    		IconifiedText it = (IconifiedText) la.getItem(i);
    		if (it.getUri().equals(uri)) {
    			getListView().setSelection(i);
    			break;
    		}
    	}
    }
    
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
        browseTo(text.getUri());
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
	private void saveUri(UriBean targetUri) {
		if (targetUri != null) {
			mStreamdb.saveUri(targetUri);
		}
	}
	
	private void showUrlNotOpenedToast() {
		Toast.makeText(this, R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
	}
	
	public void onServiceConnected(ComponentName arg0, IBinder arg1) {
		
	}

	public void onServiceDisconnected(ComponentName arg0) {
		
	}
	
	public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
		if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
			try {
				removeDialog(DETERMINE_INTENT_TASK);
			} catch (Exception ex) {
			}
			showUrlNotOpenedToast();
		} else if (action.equals(DetermineActionTask.URL_ACTION_BROWSE)) {
			mPreviousDirectory.put(mStepsBack, uri);
			mStepsBack++;
			mPreviousDirectory.put(mStepsBack, null);
			mDirectory[mStepsBack] = uri;
			refreshList();
		} else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
			try {
				removeDialog(DETERMINE_INTENT_TASK);
			} catch (Exception ex) {
			}
			MusicUtils.playAll(BrowserActivity.this, list, 0);        
		}
	}
}
