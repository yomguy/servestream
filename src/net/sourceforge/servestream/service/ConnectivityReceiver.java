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

package net.sourceforge.servestream.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.util.Log;

/**
* @author kroot
*
*/
public class ConnectivityReceiver extends BroadcastReceiver {
	private static final String TAG = ConnectivityReceiver.class.getName();

	private boolean mIsConnected = false;

	final private MediaPlaybackService mMediaPlaybackService;

	final private WifiLock mWifiLock;

	private int mNetworkRef = 0;

	private boolean mLockingWifi;

	private Object[] mLock = new Object[0];

	public ConnectivityReceiver(MediaPlaybackService MediaPlaybackService, boolean lockingWifi) {
		mMediaPlaybackService = MediaPlaybackService;

		final ConnectivityManager cm =
				(ConnectivityManager) MediaPlaybackService.getSystemService(Context.CONNECTIVITY_SERVICE);

		final WifiManager wm = (WifiManager) MediaPlaybackService.getSystemService(Context.WIFI_SERVICE);
		
		// prevent WIFI throttling when the screen is off
	    int lockType = WifiManager.WIFI_MODE_FULL;
	    if (Build.VERSION.SDK_INT >= 12) {
	    	lockType = 3;
	    }
		
		mWifiLock = wm.createWifiLock(lockType, TAG);

		final NetworkInfo info = cm.getActiveNetworkInfo();
		if (info != null) {
			mIsConnected = (info.getState() == State.CONNECTED);
		}

		mLockingWifi = lockingWifi;

		final IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		MediaPlaybackService.registerReceiver(this, filter);
	}

	/* (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();

		if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
           Log.w(TAG, "onReceived() called: " + intent);
           return;
		}

		boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
		boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

		Log.d(TAG, "onReceived() called; noConnectivity? " + noConnectivity + "; isFailover? " + isFailover);

		if (noConnectivity && !isFailover && mIsConnected) {
			mIsConnected = false;
			mMediaPlaybackService.onConnectivityLost();
		} else if (!mIsConnected) {
			NetworkInfo info = (NetworkInfo) intent.getExtras()
					.get(ConnectivityManager.EXTRA_NETWORK_INFO);

			if (mIsConnected = (info.getState() == State.CONNECTED)) {
				mMediaPlaybackService.onConnectivityRestored();
			}
		}
	}

	/**
	 *
	 */
	public void cleanup() {
		if (mWifiLock.isHeld())
			mWifiLock.release();

		mMediaPlaybackService.unregisterReceiver(this);
	}

	/**
	 * Increase the number of things using the network. Acquire a Wi-Fi lock
	 * if necessary.
	 */
	public void incRef() {
		synchronized (mLock) {
			mNetworkRef  += 1;

			acquireWifiLockIfNecessaryLocked();
		}
	}

	/**
	 * Decrease the number of things using the network. Release the Wi-Fi lock
	 * if necessary.
	 */
	public void decRef() {
		synchronized (mLock) {
			mNetworkRef -= 1;

			releaseWifiLockIfNecessaryLocked();
		}
	}

	/**
	 * @param mLockingWifi
	 */
	public void setWantWifiLock(boolean lockingWifi) {
		synchronized (mLock) {
			mLockingWifi = lockingWifi;

			if (mLockingWifi) {
				acquireWifiLockIfNecessaryLocked();
			} else {
				releaseWifiLockIfNecessaryLocked();
			}
		}
	}

	private void acquireWifiLockIfNecessaryLocked() {
		if (mLockingWifi && mNetworkRef > 0 && !mWifiLock.isHeld()) {
			mWifiLock.acquire();
		}
	}

	private void releaseWifiLockIfNecessaryLocked() {
		if (mNetworkRef == 0 && mWifiLock.isHeld()) {
			mWifiLock.release();
		}
	}

	/**
	 * @return whether we're connected to a network
	 */
	public boolean isConnected() {
		return mIsConnected;
	}
}
