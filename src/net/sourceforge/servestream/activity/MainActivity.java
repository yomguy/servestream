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

import java.util.ArrayList;

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
			BrowseIntentListener {
	
	private final static String DOWNLOAD_SCANNER_DIALOG = "download_scanner_dialog";
	
	private static UrlListFragment mUrlListFragment;
	private static BrowseFragment mBrowseFragment;
	
	private ViewPager viewpager;
	private TabsAdapter pagerAdapter;

	private ServiceToken mToken;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		viewpager = (ViewPager) findViewById(R.id.pager);
		pagerAdapter = new TabsAdapter(this, viewpager);

		viewpager.setAdapter(pagerAdapter);

		Tab urlsTab = getSupportActionBar().newTab();
		urlsTab.setText(getString(R.string.url_label));
		Tab browseTab = getSupportActionBar().newTab();
		browseTab.setText(getString(R.string.browse_label));

		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, getUri());
		
		pagerAdapter.addTab(urlsTab, UrlListFragment.class, args);
		pagerAdapter.addTab(browseTab, BrowseFragment.class, null);
		
		if (savedInstanceState != null) {
			getSupportActionBar().setSelectedNavigationItem(
					savedInstanceState.getInt("tab", 0));
		}
		
        mToken = MusicUtils.bindToService(this, this);
	}

    @Override
    public void onNewIntent(Intent intent) {
    	super.onNewIntent(intent);
		
        setIntent(intent);
        
		Bundle args = new Bundle();
		args.putString(UrlListFragment.ARG_TARGET_URI, getUri());
		mUrlListFragment.refresh(args);
    }
	
    private String getUri() {
		String intentUri = null;
		String contentType = null;
		
		Intent intent = getIntent();
		
        // check to see if we were called from a home screen shortcut
		if ((contentType = intent.getType()) != null) {
			if (contentType.contains("net.sourceforge.servestream/")) {
				intentUri = intent.getType().toString().replace("net.sourceforge.servestream/", "");
				setIntent(new Intent());
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

		setIntent(new Intent());
		
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
				mUrlListFragment.refresh(args);
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
		if (keyCode == KeyEvent.KEYCODE_BACK &&
				getSupportActionBar().getSelectedTab().getPosition() == 1) {
			mBrowseFragment.onBackKeyPressed();
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	public static class TabsAdapter extends FragmentPagerAdapter implements
			ActionBar.TabListener, ViewPager.OnPageChangeListener {
		private final Context mContext;
		private final ActionBar mActionBar;
		private final ViewPager mViewPager;
		private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

		static final class TabInfo {
			private final Class<?> clss;
			private final Bundle args;

			TabInfo(Class<?> _class, Bundle _args) {
				clss = _class;
				args = _args;
			}
		}

		public TabsAdapter(MainActivity activity, ViewPager pager) {
			super(activity.getSupportFragmentManager());
			mContext = activity;
			mActionBar = activity.getSupportActionBar();
			mViewPager = pager;
			mViewPager.setAdapter(this);
			mViewPager.setOnPageChangeListener(this);
		}

		public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args) {
			TabInfo info = new TabInfo(clss, args);
			tab.setTag(info);
			tab.setTabListener(this);
			mTabs.add(info);
			mActionBar.addTab(tab);
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mTabs.size();
		}

		@Override
		public Fragment getItem(int position) {
			TabInfo info = mTabs.get(position);
			Fragment fragment = Fragment.instantiate(mContext, info.clss.getName(),
					info.args);
			
			if (fragment instanceof UrlListFragment) {
				mUrlListFragment = (UrlListFragment) fragment;
			} else if (fragment instanceof BrowseFragment) {
				mBrowseFragment = (BrowseFragment) fragment;
			}
			
			return fragment;
		}

		@Override
		public void onPageScrolled(int position, float positionOffset,
				int positionOffsetPixels) {
		}

		@Override
		public void onPageSelected(int position) {
			mActionBar.setSelectedNavigationItem(position);
		}

		@Override
		public void onPageScrollStateChanged(int state) {
		}

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			Object tag = tab.getTag();
			for (int i = 0; i < mTabs.size(); i++) {
				if (mTabs.get(i) == tag) {
					mViewPager.setCurrentItem(i);
				}
			}
		}

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) {

		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {
		}
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
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		finish();
	}

	@Override
	public void browseToUri(Uri uri) {
		getSupportActionBar().setSelectedNavigationItem(1);
		mBrowseFragment.browseTo(uri);
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
