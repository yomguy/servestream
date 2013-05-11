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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.fragment.BrowseFragment;
import net.sourceforge.servestream.fragment.UrlListFragment;
import net.sourceforge.servestream.fragment.UrlListFragment.BrowseIntentListener;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.DownloadScannerDialog;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/** The activity that is shown when the user launches the app. */
public class MainActivity extends SherlockFragmentActivity implements
			ServiceConnection,
			BrowseIntentListener,
			ActionBar.TabListener {
	
	private final static String DOWNLOAD_SCANNER_DIALOG = "download_scanner_dialog";
	
	private static Bundle mSavedInstanceState;
	private static UrlListFragment mUrlListFragment;
	private static BrowseFragment mBrowseFragment;
	
	private SectionsPagerAdapter mSectionsPagerAdapter;
	private ViewPager mViewPager;

	private ServiceToken mToken;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, getUri());
		
		mUrlListFragment = (UrlListFragment) Fragment.instantiate(this, UrlListFragment.class.getName(), args);
		mBrowseFragment = (BrowseFragment) Fragment.instantiate(this, BrowseFragment.class.getName(), null);
		
		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(
				getSupportFragmentManager());
		
		mViewPager = (ViewPager) findViewById(R.id.pager);

		mSavedInstanceState = savedInstanceState;
		
        mToken = MusicUtils.bindToService(this, this);
	}

    @Override
    public void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
		
        setIntent(intent);
        
		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, getUri());
		
		UrlListFragment fragment = (UrlListFragment) mSectionsPagerAdapter.getItem(0);
		fragment.refresh(args);
    }
	
    private String getUri() {
		String intentUri = null;
		String contentType = null;
		
		Intent intent = getIntent();
		
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
    
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt("tab", getSupportActionBar()
				.getSelectedNavigationIndex());
	}

	@Override
	public void onResume() {
		super.onResume();
		
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        registerReceiver(mTrackListListener, f);
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
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
	    if (requestCode == 0) {
	        if (resultCode == RESULT_OK) {
	            String contents = intent.getStringExtra("SCAN_RESULT");
	            // Handle successful scan
				Bundle args = new Bundle();
				args.putString(UrlListFragment.ARG_TARGET_URI, contents);
				UrlListFragment fragment = (UrlListFragment) mSectionsPagerAdapter.getItem(0);
				fragment.refresh(args);
	        } else if (resultCode == RESULT_CANCELED) {
	            // Handle cancel
	        }
	    }
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.add_uri:
				startActivity(new Intent(this, AddUrlActivity.class));
				return true;
			case (R.id.menu_item_organize_urls):
    			startActivity(new Intent(this, OrganizeUrlsActivity.class));
				return true;
        	case (R.id.menu_item_settings):
        		startActivity(new Intent(this, PreferenceActivity.class));
    			return true;
            case (R.id.menu_item_alarms):
                startActivity(new Intent(this, AlarmClockActivity.class));
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
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = new MenuInflater(this);
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Tab tab;
			if ((tab = getSupportActionBar().getSelectedTab()) != null &&
					tab.getPosition() == 1) {
				BrowseFragment fragment = (BrowseFragment) mSectionsPagerAdapter.getItem(1);
				fragment.onBackKeyPressed();
				return true;
			}
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	public class SectionsPagerAdapter extends FragmentPagerAdapter {
		
		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			if (position == 0) {
				return mUrlListFragment;
			} else {
				return mBrowseFragment;
			}
		}
		
		@Override
		public int getCount() {
			return 2;
		}

	}
	
	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {

	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
	
	}

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MusicUtils.updateNowPlaying(MainActivity.this);
        }
    };
	
	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		MusicUtils.updateNowPlaying(this);
		
		mViewPager.setAdapter(mSectionsPagerAdapter);
		
		Tab urlsTab = getSupportActionBar().newTab();
		urlsTab.setText(getString(R.string.url_label));
		urlsTab.setTabListener(this);
		
		Tab browseTab = getSupportActionBar().newTab();
		browseTab.setText(getString(R.string.browse_label));
		browseTab.setTabListener(this);
		
		getSupportActionBar().addTab(urlsTab);
		getSupportActionBar().addTab(browseTab);
		
		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager
				.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						MainActivity.this.getSupportActionBar().setSelectedNavigationItem(position);
					}
				});
		
		if (mSavedInstanceState != null) {
			getSupportActionBar().setSelectedNavigationItem(
					mSavedInstanceState.getInt("tab", 0));
		}
		
		mSavedInstanceState = null;
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		finish();
	}

	@Override
	public void browseToUri(Uri uri) {
		getSupportActionBar().setSelectedNavigationItem(1);
		BrowseFragment fragment = (BrowseFragment) mSectionsPagerAdapter.getItem(1);
		fragment.browseTo(uri);
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
}
