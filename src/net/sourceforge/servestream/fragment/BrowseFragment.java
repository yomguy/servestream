/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
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

package net.sourceforge.servestream.fragment;

import java.util.ArrayList;
import java.util.List;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.PreferenceActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.filemanager.*;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.DetermineActionTask;
import net.sourceforge.servestream.utils.LoadingDialog;
import net.sourceforge.servestream.utils.LoadingDialog.LoadingDialogListener;
import net.sourceforge.servestream.utils.MusicUtils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class BrowseFragment extends SherlockListFragment implements
				DetermineActionTask.MusicRetrieverPreparedListener,
				LoadingDialogListener {

    private final static String LOADING_DIALOG = "loading_dialog";
	
 	public static final int MESSAGE_SHOW_DIRECTORY_CONTENTS = 1;
    public static final int MESSAGE_PARSE_WEBPAGE = 2;
	
	/** Contains directories and files together */
    private ArrayList<IconifiedText> directoryEntries = new ArrayList<IconifiedText>();

    /** Dir separate for sorting */
    private List<IconifiedText> mListFiles = new ArrayList<IconifiedText>();

    private int mStepsBack;
    private UriBean [] mDirectory = null;

    private TextView mEmptyText;
     
    private DirectoryScanner mDirectoryScanner;
    private SparseArray<UriBean> mPreviousDirectory = new SparseArray<UriBean>();
    
	private InputMethodManager mInputManager = null;
	private StreamDatabase mStreamdb = null;

	protected Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {			
			BrowseFragment.this.handleMessage(msg);
		}
	};
	
	protected Handler mQueueHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			MusicUtils.addToCurrentPlaylist(BrowseFragment.this.getActivity(), (long []) msg.obj);
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Tell the framework to try to keep this fragment around
        // during a configuration change.
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View result = inflater.inflate(R.layout.fragment_browse, container, false);
		
		return result;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		// connect with streams database
		this.mStreamdb = new StreamDatabase(this.getActivity());
        
		ListView list = this.getListView();
		list.setOnCreateContextMenuListener(this);
		list.setEmptyView(getActivity().findViewById(R.id.empty));
		list.setFastScrollEnabled(true);
	    list.setTextFilterEnabled(true);
	    list.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position,
					long arg3) {
		        IconifiedTextListAdapter adapter = (IconifiedTextListAdapter) getListAdapter();
		          
		        if (adapter == null) {
		        	return;
		        }
		        
		        IconifiedText text = (IconifiedText) adapter.getItem(position);
		        browseTo(text.getUri());
			}
	    });
        
        mEmptyText = (TextView) this.getActivity().findViewById(R.id.empty_text);
        mEmptyText.setVisibility(View.VISIBLE);
	    
		//mInputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    }
  
	public void browseTo(Uri uri) {
		mStepsBack = 0;
		mDirectory = new UriBean[1000];
		mDirectory[mStepsBack] = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
		
		refreshList();
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		// connect to the stream database if we don't
		// already have a connection
		if(this.mStreamdb == null)
			this.mStreamdb = new StreamDatabase(this.getActivity());
		
		// if the current URL exists in the stream database
		// update its timestamp
		//UriBean uri = TransportFactory.findUri(mStreamdb, mDirectory[mStepsBack].getUri());
		
		//if (uri != null) {
	      //  mStreamdb.touchUri(uri);
		//}
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
        switch (item.getItemId()) {
            case (R.id.menu_item_refresh):
            	refreshList();
            	return true;
            case (R.id.menu_item_settings):
            	startActivity(new Intent(BrowseFragment.this.getActivity(), PreferenceActivity.class));
        		return true;
        	default:
        		return super.onOptionsItemSelected(item);
        }
    }
	
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.browse, menu);
        super.onCreateOptionsMenu(menu, inflater);
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
		android.view.MenuItem save = menu.add(R.string.save_label);
		save.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(android.view.MenuItem arg0) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(BrowseFragment.this.getActivity())
					.setMessage(getString(R.string.url_save_confirmation_msg, streamURL))
					.setPositiveButton(R.string.confirm_label, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            saveUri(uri);
                            BrowseFragment.this.getActivity().sendBroadcast(new Intent(UrlListFragment.UPDATE_LIST));
						}
						})
					.setNegativeButton(android.R.string.cancel, null).create().show();
				return true;
			}
		});
	
		// view the URL
		android.view.MenuItem view = menu.add(R.string.view_url_label);
		view.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(android.view.MenuItem arg0) {
				// display the URL
				new AlertDialog.Builder(BrowseFragment.this.getActivity())
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
		android.view.MenuItem add = menu.add(R.string.add_to_playlist_label);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(android.view.MenuItem item) {
				MusicUtils.addToCurrentPlaylistFromURL(BrowseFragment.this.getActivity(), uri, mQueueHandler);
				return true;
			}
		});
		
		// share the URL
		android.view.MenuItem share = menu.add(R.string.share_label);
		share.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(android.view.MenuItem item) {
				String url = uri.getUri().toString();
				String appName = getString(R.string.app_name);
				
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_signature, url, appName));
				startActivity(Intent.createChooser(intent, getString(R.string.share_label)));
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

        IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this.getActivity()); 
        itla.setListItems(directoryEntries, getListView().hasTextFilter());          
        setListAdapter(itla);
	    getListView().requestFocus();

		selectInList(mPreviousDirectory.get(mStepsBack));
	    
    	mEmptyText.setVisibility(View.VISIBLE);
    	
		dismissDialog(LOADING_DIALOG);
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
	    showDialog(LOADING_DIALOG);
        new DetermineActionTask(this.getActivity(), uri, this).execute();
    }

    private void refreshList() {
    	if (mDirectory == null) {
    		return;
    	}
    	
    	showDialog(LOADING_DIALOG);
    	
    	// Cancel an existing scanner, if applicable.
    	DirectoryScanner scanner = mDirectoryScanner;
    	  
    	if (scanner != null) {
    	    scanner.cancel = true;
    	}
    	  
    	directoryEntries.clear(); 
        mListFiles.clear();
          
        // Don't show the "folder empty" text since we're scanning.
        mEmptyText.setVisibility(View.GONE);
          
        setListAdapter(null); 
          
        mDirectoryScanner = new DirectoryScanner(mDirectory[mStepsBack], this.getActivity(), mHandler);
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
     
	public void onBackKeyPressed() {
		if (mStepsBack > 0) {
			upOneLevel();
		} else {
			getActivity().finish();
		}
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
		Toast.makeText(this.getActivity(), R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
	}
	
	public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
		if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
			dismissDialog(LOADING_DIALOG);
			showUrlNotOpenedToast();
		} else if (action.equals(DetermineActionTask.URL_ACTION_BROWSE)) {
			mPreviousDirectory.put(mStepsBack, uri);
			mStepsBack++;
			mPreviousDirectory.put(mStepsBack, null);
			mDirectory[mStepsBack] = uri;
			refreshList();
		} else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
			dismissDialog(LOADING_DIALOG);
			MusicUtils.playAll(BrowseFragment.this.getActivity(), list, 0);        
		}
	}
	
	private void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction.  We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		Fragment prev = getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		// Create and show the dialog.
		//if (tag.equals(LOADING_DIALOG)) {
		newFragment = LoadingDialog.newInstance(this, getString(R.string.opening_url_message));
		//}

		ft.add(0, newFragment, tag);
		ft.commit();
	}

	private void dismissDialog(String tag) {
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		DialogFragment prev = (DialogFragment) getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		ft.commit();
	}

	@Override
	public void onLoadingDialogCancelled(DialogFragment dialog) {
		// TODO Auto-generated method stub
		
	}
}
