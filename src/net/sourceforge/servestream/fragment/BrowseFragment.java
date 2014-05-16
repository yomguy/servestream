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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.BluetoothOptionsActivity;
import net.sourceforge.servestream.adapter.BrowseAdapter;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.DetermineActionTask;
import net.sourceforge.servestream.utils.LoadingDialog;
import net.sourceforge.servestream.utils.OverflowClickListener;
import net.sourceforge.servestream.utils.LoadingDialog.LoadingDialogListener;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.WebpageParserTask;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.widget.PopupMenu;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class BrowseFragment extends ListFragment implements
				DetermineActionTask.MusicRetrieverPreparedListener,
				LoadingDialogListener,
				OverflowClickListener {

    private final static String LOADING_DIALOG = "loading_dialog";
	
 	public static final int MESSAGE_SHOW_DIRECTORY_CONTENTS = 1;
    public static final int MESSAGE_PARSE_WEBPAGE = 2;
	
    private int mStepsBack;
    private UriBean [] mDirectory;

    private WebpageParserTask mWebpageParserTask;
    private SparseArray<UriBean> mPreviousDirectory = new SparseArray<UriBean>();
    
    private BrowseAdapter mAdapter;
    
    private UriBean mSelectedMenuItem;
    
	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {			
			BrowseFragment.this.handleMessage(msg);
		}
	};
	
	@SuppressLint("HandlerLeak")
	private Handler mQueueHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			MusicUtils.addToCurrentPlaylist(BrowseFragment.this.getActivity(), (long []) msg.obj);
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_browse, container, false);
		ListView list = (ListView) view.findViewById(android.R.id.list);
		list.setEmptyView(view.findViewById(android.R.id.empty));
		
		return view;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ListView list = getListView();
		list.setFastScrollEnabled(true);
	    list.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		        UriBean uri = (UriBean) parent.getItemAtPosition(position);
		        browseTo(uri);
			}
	    });
		registerForContextMenu(list);

	    if (mAdapter == null) {
	    	mAdapter = new BrowseAdapter(getActivity(), new ArrayList<UriBean>(), this);
	    }
	    
		list.setAdapter(mAdapter);
    }

	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		if (getArguments().getString("target_uri") != null) {
			Uri uri = Uri.parse(getArguments().getString("target_uri"));
			browseTo(uri);
			getArguments().putString("target_uri", null);
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (mWebpageParserTask != null &&
				mWebpageParserTask.getStatus() != AsyncTask.Status.FINISHED) {
			showDialog(LOADING_DIALOG);
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		dismissDialog(LOADING_DIALOG);
	}
	
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle stream URLs
		
		// create menu to handle deleting and sharing lists		
		final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        final UriBean uri = (UriBean) getListView().getAdapter().getItem(info.position);
		
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
	
	private void browseTo(Uri uri) {
		mStepsBack = 0;
		mDirectory = new UriBean[1000];
		mDirectory[mStepsBack] = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
		
		refreshList();
	}
	
	@Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.browse, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case (R.id.menu_item_refresh):
            	refreshList();
            	return true;
            case (R.id.menu_search):
            	getActivity().onSearchRequested();
        	default:
        		return super.onOptionsItemSelected(item);
        }
    }
    
    @SuppressWarnings("unchecked")
	private void handleMessage(Message message) {    	 
    	switch (message.what) {
    		case MESSAGE_SHOW_DIRECTORY_CONTENTS:
    			showDirectoryContents((List<UriBean>) message.obj);
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
     
    private void showDirectoryContents(List<UriBean> contents) {
    	mAdapter.clear();
    	
    	for (int i = 0; i < contents.size(); i++) {
    		mAdapter.add(contents.get(i));
    	}
    	
    	mAdapter.notifyDataSetChanged();

		selectInList(mPreviousDirectory.get(mStepsBack));
	    
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
    	}
    }
     
    @SuppressLint("NewApi")
	private void browseTo(UriBean uri) {
	    showDialog(LOADING_DIALOG);
	    
	    DetermineActionTask determineActionTask = new DetermineActionTask(getActivity(), uri, this);
        
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
    		determineActionTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    	} else {
    		determineActionTask.execute();
    	}
    }

    @SuppressLint("NewApi")
	private void refreshList() {
    	if (mDirectory == null) {
    		return;
    	}
    	
    	showDialog(LOADING_DIALOG);
    	
    	mWebpageParserTask = new WebpageParserTask(mHandler, mDirectory[mStepsBack]);
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
    		mWebpageParserTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    	} else {
    		mWebpageParserTask.execute();
    	}
    }

    private void selectInList(UriBean uri) {
    	if (uri == null) {
    		return;
    	}
    	
    	for (int i = 0; i < mAdapter.getCount(); i++) {
    		UriBean it = (UriBean) mAdapter.getItem(i);
    		if (it.equals(uri)) {
    			getListView().setSelection(i);
    			break;
    		}
    	}
    }
    
	public void onBackKeyPressed() {
		if (mStepsBack > 0) {
			upOneLevel();
		} else {
			getActivity().finish();
		}
	}
	
	public ArrayList<UriBean> getUris() {
		ArrayList<UriBean> uris = new ArrayList<UriBean>();
		
		for (int i = 0; i < mAdapter.getItems().size(); i++) {
			uris.add(mAdapter.getItem(i));
		}
				
		return uris;
	}
	
	/**
	 * Adds a stream URL to the stream database if it doesn't exist
	 * 
	 * @param targetStream The stream URL to add to the database
	 */
	private void saveUri(UriBean targetUri) {
		if (targetUri != null) {
			StreamDatabase db = new StreamDatabase(this.getActivity());
			db.saveUri(targetUri);
			db.close();
		}
	}
	
	private void showUrlNotOpenedToast() {
		Toast.makeText(this.getActivity(), R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
	}
	
	@Override
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
		if (tag.equals(LOADING_DIALOG)) {
			newFragment = LoadingDialog.newInstance(this, getString(R.string.opening_url_message));
		}

		ft.add(0, newFragment, tag);
		ft.commit();
	}

	private void dismissDialog(String tag) {
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		DialogFragment prev = (DialogFragment) getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
			ft.commit();
		}
	}

	@Override
	public void onLoadingDialogCancelled(DialogFragment dialog) {
		if (mWebpageParserTask != null &&
				mWebpageParserTask.getStatus() != AsyncTask.Status.FINISHED) {
			mWebpageParserTask.cancel(false);
		}
	}

    private void showPopup(View v, UriBean uri) {
    	mSelectedMenuItem = uri;
    	
        PopupMenu popup = new PopupMenu(getActivity(), v);
        Menu menu = popup.getMenu();
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.browse_uri_actions, menu);
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			menu.removeItem(R.id.menu_autostart_on_bluetooth);
		}
        popup.setOnMenuItemClickListener(mPopupMenuOnMenuItemClickListener);
        popup.show();
    }
	
	private PopupMenu.OnMenuItemClickListener mPopupMenuOnMenuItemClickListener = new PopupMenu.OnMenuItemClickListener() {

		@Override
		public boolean onMenuItemClick(MenuItem item) {
		    Intent intent;
			
			switch (item.getItemId()) {
				case R.id.menu_item_save:
					saveUri(mSelectedMenuItem);
                    BrowseFragment.this.getActivity().sendBroadcast(new Intent(UrlListFragment.UPDATE_LIST));
					return true;
				case R.id.menu_item_view_url:
					final String streamURL = mSelectedMenuItem.getUri().toString();
					new AlertDialog.Builder(BrowseFragment.this.getActivity())
					.setMessage(streamURL)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
                            return;
						}
						}).create().show();
					return true;
				case R.id.menu_item_add_to_playlist_label:
					MusicUtils.addToCurrentPlaylistFromURL(getActivity(), mSelectedMenuItem, mQueueHandler);
					return true;
				case R.id.menu_item_share:
					String url = mSelectedMenuItem.getUri().toString();
					String appName = getString(R.string.app_name);

					intent = new Intent(Intent.ACTION_SEND);
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_signature, url, appName));
					startActivity(Intent.createChooser(intent, getString(R.string.share_label)));
					return true;
				case R.id.menu_autostart_on_bluetooth:
			        SharedPreferences prefs = getActivity().getSharedPreferences(BluetoothOptionsActivity.PREFS_NAME, Context.MODE_PRIVATE);
			        Editor editor = prefs.edit();
			        editor.putString(BluetoothOptionsActivity.PREF_AUTOSTART_STREAM, mSelectedMenuItem.getUri().toString());
			        editor.commit();		
					return true;
				default:
					return false;
		    }
		}
    	
    };
	
	@Override
	public void onClick(View view, UriBean uri) {
		showPopup(view, uri);
	}
}
