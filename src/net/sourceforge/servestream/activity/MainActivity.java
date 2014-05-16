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

package net.sourceforge.servestream.activity;

import com.google.android.gms.cast.MediaStatus;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.exceptions.CastException;
import net.sourceforge.servestream.exceptions.NoConnectionException;
import net.sourceforge.servestream.exceptions.TransientNetworkDisconnectionException;
import net.sourceforge.servestream.fragment.AlarmClockFragment;
import net.sourceforge.servestream.fragment.BrowseFragment;
import net.sourceforge.servestream.fragment.UrlListFragment;
import net.sourceforge.servestream.fragment.UrlListFragment.BrowseIntentListener;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.DownloadScannerDialog;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.PreferenceConstants;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;
import net.sourceforge.servestream.utils.Utils;
import net.sourceforge.servestream.widgets.MiniController;
import net.sourceforge.servestream.widgets.MiniController.OnMiniControllerChangedListener;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class MainActivity extends ActionBarActivity implements
			ServiceConnection,
			BrowseIntentListener,
			OnMiniControllerChangedListener {
	
	private static final String STATE_SELECTED_NAVIGATION_ITEM = "selected_navigation_item";
	
	private final static String DOWNLOAD_SCANNER_DIALOG = "download_scanner_dialog";
	
	private String mTag;
	
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

    private CharSequence mDrawerTitle;
    private CharSequence mTitle;
    private String[] mDrawerItems;

    private IMediaPlaybackService mService;
	private ServiceToken mToken;
    
    private MiniController mMini;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTitle = mDrawerTitle = getTitle();
        mDrawerItems = getResources().getStringArray(R.array.drawer_items);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(Utils.getThemedIcon(this, R.attr.drawer_shadow), GravityCompat.START);
        // set up the drawer's list view with items and click listener
        mDrawerList.setAdapter(new ArrayAdapter<String>(this,
                R.layout.drawer_list_item, mDrawerItems));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        // enable ActionBar app icon to behave as action to toggle nav drawer
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // -- Adding MiniController
        mMini = (MiniController) findViewById(R.id.miniController1);
        
        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                Utils.getThemedIcon(this, R.attr.ic_drawer),  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
                ) {
            public void onDrawerClosed(View view) {
                getSupportActionBar().setTitle(mTitle);
            }

            public void onDrawerOpened(View drawerView) {
                getSupportActionBar().setTitle(mDrawerTitle);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        if (savedInstanceState == null) {
        	openUri(getUri());
            //selectItem(0);
        }
        
        mToken = MusicUtils.bindToService(this, this);
    }

    @Override
    public void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
		
        setIntent(intent);
        
        openUri(getUri());
    }
    
    @Override
	public void onResume() {
		super.onResume();
		
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        registerReceiver(mTrackListListener, f);
        
        updateNowPlaying();
	}

	@Override
	public void onPause() {
		super.onPause();
		
        unregisterReceiver(mTrackListListener);
	}
    
	@Override
	public void onDestroy() {
		super.onDestroy();

        MusicUtils.unbindFromService(mToken);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		// Restore the previously serialized current dropdown position.
		if (savedInstanceState.containsKey(STATE_SELECTED_NAVIGATION_ITEM)) {
			mTag = savedInstanceState.getString(STATE_SELECTED_NAVIGATION_ITEM);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Serialize the current dropdown position.
		outState.putString(STATE_SELECTED_NAVIGATION_ITEM, mTag);
	}
	
    /* Called whenever we call invalidateOptionsMenu() */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
         // The action bar home/up action should open or close the drawer.
         // ActionBarDrawerToggle will take care of this.
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle action buttons
        switch (item.getItemId()) {
			case (R.id.menu_item_organize_urls):
				startActivity(new Intent(this, OrganizeUrlsActivity.class));
				return true;
			case (R.id.menu_item_settings):
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case (R.id.menu_item_scan):
				try {
					Intent intent = new Intent("com.google.zxing.client.android.SCAN");
					intent.setPackage("com.google.zxing.client.android");
					intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
					startActivityForResult(intent, 0);
				} catch (ActivityNotFoundException ex) {
					showDialog(DOWNLOAD_SCANNER_DIALOG);
				}
        		return true;
			default:
				return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void startActivity(Intent intent) {      
        // check if search intent
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
        	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
        	if (fragment != null && fragment instanceof BrowseFragment) {
    			intent.putParcelableArrayListExtra("uris", ((BrowseFragment) fragment).getUris());
        	}
        }

        super.startActivity(intent);
    }
    
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
			if (fragment != null && fragment instanceof BrowseFragment) {
				((BrowseFragment) fragment).onBackKeyPressed();
				return true;
			}
		}
		
		return super.onKeyDown(keyCode, event);
	}
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
	    if (requestCode == 0) {
	        if (resultCode == RESULT_OK) {
	            String contents = intent.getStringExtra("SCAN_RESULT");
	            // Handle successful scan
	            openUri(contents);
	        } else if (resultCode == RESULT_CANCELED) {
	            // Handle cancel
	        }
	    }
	}
	
    /* The click listener for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    private void selectItem(int position) {
    	FragmentManager fragmentManager = getSupportFragmentManager();
    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
    	
    	if (fragment != null) {
    		fragmentManager.beginTransaction().detach(fragment).commit();
    	}
    	
    	String tag = String.valueOf(position);
    	fragment = getSupportFragmentManager().findFragmentByTag(tag);
    	
    	if (fragment == null) {
            if (position == 0) {
            	fragment = new UrlListFragment();
            	fragment.setArguments(new Bundle());
            } else if (position == 1) {
            	fragment = new BrowseFragment();
            	fragment.setArguments(new Bundle());
            } else if (position == 2) {
            	fragment = new AlarmClockFragment();
            }
    		
    		fragmentManager.beginTransaction().add(R.id.content_frame, fragment, tag).commit();
    	} else {
    		fragmentManager.beginTransaction().attach(fragment).commit();
    	}
    	
    	mTag = tag;
    	
    	// update selected item and title, then close the drawer
    	mDrawerList.setItemChecked(position, true);
    	setTitle(mDrawerItems[position]);
    	mDrawerLayout.closeDrawer(mDrawerList);
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        getSupportActionBar().setTitle(mTitle);
    }

    /**
     * When using the ActionBarDrawerToggle, you must call it during
     * onPostCreate() and onConfigurationChanged()...
     */

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggls
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    private String getUri() {
		String intentUri = null;
		String contentType = null;
		
		Intent intent = getIntent();
		
		if (intent == null) {
			return null;
		}
		
        // check to see if we were called from a home screen shortcut
		if ((contentType = intent.getType()) != null) {
			if (contentType.contains("net.sourceforge.servestream/")) {
				intentUri = intent.getType().toString().replace("net.sourceforge.servestream/", "");
				setIntent(null);
				return intentUri;
			}
		}
		
		// check to see if we were called by clicking on a URL
		if (intent.getData() != null) {
			intentUri = intent.getData().toString();
		}
		
		// check to see if the application was opened from a share intent
		if (intent.getExtras() != null && intent.getExtras().getCharSequence(Intent.EXTRA_TEXT) != null) {
			intentUri = intent.getExtras().getCharSequence(Intent.EXTRA_TEXT).toString();
		}

		setIntent(null);
		
		return intentUri;
    }
    
    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
        	updateNowPlaying();
        }
    };
    
	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		mService = IMediaPlaybackService.Stub.asInterface(service);
		mMini.setOnMiniControllerChangedListener(this);
		updateNowPlaying();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Animation fade_out = AnimationUtils.loadAnimation(this, R.anim.player_out);
		mMini.startAnimation(fade_out);
		mMini.setVisibility(View.GONE);
		finish();
	}

	private void openUri(String uri) {
		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, uri);
    	
    	FragmentManager fragmentManager = getSupportFragmentManager();
    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
    	
    	if (fragment != null) {
    		fragmentManager.beginTransaction().detach(fragment).commit();
    	}
    	
    	String tag = String.valueOf(0);
    	fragment = getSupportFragmentManager().findFragmentByTag(tag);
    	
    	if (fragment == null) {
           	fragment = new UrlListFragment();
           	fragment.setArguments(args);
    		fragmentManager.beginTransaction().add(R.id.content_frame, fragment, tag).commit();
    	} else {
           	fragment.getArguments().putString(UrlListFragment.ARG_TARGET_URI, uri);
    		fragmentManager.beginTransaction().attach(fragment).commit();
    	}
    	
    	mTag = tag;
    	
    	mDrawerList.setItemChecked(0, true);
    	setTitle(mDrawerItems[0]);
    	mDrawerLayout.closeDrawer(mDrawerList);
	}
	
	@Override
	public void browseToUri(Uri uri) {
		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, uri.toString());
    	
    	FragmentManager fragmentManager = getSupportFragmentManager();
    	Fragment fragment = getSupportFragmentManager().findFragmentByTag(mTag);
    	
    	if (fragment != null) {
    		fragmentManager.beginTransaction().detach(fragment).commit();
    	}
    	
    	String tag = String.valueOf(1);
    	fragment = getSupportFragmentManager().findFragmentByTag(tag);
    	
    	if (fragment == null) {
           	fragment = new BrowseFragment();
           	fragment.setArguments(args);
    		fragmentManager.beginTransaction().add(R.id.content_frame, fragment, tag).commit();
    	} else {
           	fragment.getArguments().putString(UrlListFragment.ARG_TARGET_URI, uri.toString());
    		fragmentManager.beginTransaction().attach(fragment).commit();
    	}
    	
    	mTag = tag;
    	
    	mDrawerList.setItemChecked(1, true);
    	setTitle(mDrawerItems[1]);
    	mDrawerLayout.closeDrawer(mDrawerList);
	}
	
	private void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction.  We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		// Create and show the dialog.
		newFragment = DownloadScannerDialog.newInstance();

		ft.add(0, newFragment, tag);
		ft.commit();
	}

	@Override
	public void onFailed(int resourceId, int statusCode) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onPlayPauseClicked(View v) throws CastException,
			TransientNetworkDisconnectionException, NoConnectionException {
		try {
			if (mService.isPlaying()) {
				mService.pause();
			} else {
				mService.play();
			}
		} catch (RemoteException e) {
		}
		
	}

	@Override
	public void onPreviousClicked(View v) throws CastException,
			TransientNetworkDisconnectionException, NoConnectionException {
		try {
			mService.prev();
		} catch (RemoteException e) {
		}
	}

	@Override
	public void onNextClicked(View v) throws CastException,
			TransientNetworkDisconnectionException, NoConnectionException {
		try {
			mService.next();
		} catch (RemoteException e) {
		}
	}
	
	@Override
	public void onTargetActivityInvoked(Context context)
			throws TransientNetworkDisconnectionException,
			NoConnectionException {
		startActivity(new Intent(this, MediaPlayerActivity.class));
	}
	
	private void updateNowPlaying() {
		if (mMini == null) {
			return;
		}
		try {
			if (true && mService != null && mService.getAudioId() != -1) {
				Drawable d = null;

				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
				if (preferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
					long id = mService.getAudioId();
					if (id >= 0) {
						Bitmap b = BitmapFactory.decodeResource(this.getResources(), R.drawable.albumart_mp_unknown_list);
						BitmapDrawable defaultAlbumIcon = new BitmapDrawable(this.getResources(), b);
						// no filter or dither, it's a lot faster and we can't tell the difference
						defaultAlbumIcon.setFilterBitmap(false);
						defaultAlbumIcon.setDither(false);

						d = MusicUtils.getCachedArtwork(this, mService.getAudioId(), defaultAlbumIcon, true);
					}
				}

				if (d == null) {
					//mCoverart.setVisibility(View.GONE);
				} else {
					//mCoverart.setVisibility(View.VISIBLE);
					mMini.setIcon(d);
				}

				CharSequence trackName = mService.getTrackName();
				CharSequence artistName = mService.getArtistName();                

				if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
					mMini.setTitle(getString(R.string.widget_one_track_info_unavailable));
				} else {
					mMini.setTitle(trackName.toString());
				}

				if (artistName == null || artistName.equals(Media.UNKNOWN_STRING)) {
					artistName = mService.getMediaUri();
				}

				mMini.setSubTitle(artistName.toString());

				if (mService.isPlaying()) {
					mMini.setPlaybackStatus(MediaStatus.PLAYER_STATE_PLAYING, -1);
				} else {
					mMini.setPlaybackStatus(MediaStatus.PLAYER_STATE_PAUSED, -1);
				}

				if (mMini.getVisibility() != View.VISIBLE) {
					Animation fade_in = AnimationUtils.loadAnimation(this, R.anim.player_in);
					mMini.startAnimation(fade_in);
				}

				mMini.setVisibility(View.VISIBLE);
				
				return;
			}
		} catch (RemoteException ex) {
		}
		Animation fade_out = AnimationUtils.loadAnimation(this, R.anim.player_out);
		mMini.startAnimation(fade_out);
		mMini.setVisibility(View.GONE);
	}
}