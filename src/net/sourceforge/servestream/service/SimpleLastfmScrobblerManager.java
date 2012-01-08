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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.provider.Media;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class SimpleLastfmScrobblerManager extends BroadcastReceiver {
	private static final String TAG = SimpleLastfmScrobblerManager.class.getName();
	
	private static final String BROADCAST_ACTION = "com.adam.aslfms.notify.playstatechanged";
	
	private static final int START = 0;
	private static final int RESUME = 1;
	private static final int PAUSE = 2;
	private static final int COMPLETE = 3;
	
	private static final String APP_NAME_NAME = "app-name";
	private static final String APP_PACKAGE_NAME = "app-package";
	private static final String STATE_NAME = "state";
	private static final String ARTIST_NAME = "artist";
	private static final String ALBUM_NAME = "album";
	private static final String TRACK_NAME = "track";
	private static final String DURATION_NAME = "duration";
	//private static final String TRACK_NUMBER_NAME = "track-number";
	//private static final String MBID_NAME = "mbid";
	//private static final String SOURCE_NAME = "source";
	
	private final MediaPlaybackService mMediaPlaybackService;
	private boolean mSendScrobblerInfo = false;
	
	private Object[] mLock = new Object[0];
	
	/**
	 * Default constructor.
	 * 
	 * @param mediaPlaybackService The current MediaPlaybackService context
	 * @param sendScrobblerInfo Decides if the metadata should be broadcasted to SLS.
	 */
	public SimpleLastfmScrobblerManager(MediaPlaybackService mediaPlaybackService, boolean sendScrobblerInfo) {
		mMediaPlaybackService = mediaPlaybackService;
		mSendScrobblerInfo = sendScrobblerInfo;
		
		final IntentFilter filter = new IntentFilter();
		filter.addAction(MediaPlaybackService.PLAYBACK_STARTED);
		filter.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
		filter.addAction(MediaPlaybackService.PLAYBACK_COMPLETE);
		mediaPlaybackService.registerReceiver(this, filter);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();

		if (!mSendScrobblerInfo ||
				(!action.equals(MediaPlaybackService.PLAYBACK_STARTED) &&
				!action.equals(MediaPlaybackService.PLAYSTATE_CHANGED) &&
				!action.equals(MediaPlaybackService.PLAYBACK_COMPLETE))) {
           Log.w(TAG, "onReceived() called: " + intent);
           return;
		}
		
		if (metadataPresent(intent)) {
			sendBroadcast(intent);
		}
	}

	/**
	 * Checks if an intent has the minimum metadata necessary to scrobble.
	 * 
	 * @param intent The intent to validate.
	 * @return True if the intent has valid metadata, false otherwise.
	 */
	private boolean metadataPresent(Intent intent) {
		Log.d(TAG, "Checking for valid metadata");
		
		String artist = intent.getStringExtra("artist");
		String track = intent.getStringExtra("track");
		long duration = intent.getLongExtra("duration", 0);
		
		if (artist == null || artist.equals(Media.UNKNOWN_STRING)) {
			return false;
		}
		
		if (track == null || track.equals(Media.UNKNOWN_STRING)) {
			return false;
		}
		
		if (duration == -1) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Parses a broadcasted MediaPlaybackService intent and sends a broadcast to SLS.
	 * 
	 * @param intent The intent to parse and broadcast.
	 */
	private void sendBroadcast(Intent intent) {
		int state = RESUME;
		
		Intent bCast = new Intent(BROADCAST_ACTION);
		bCast.putExtra(APP_NAME_NAME, mMediaPlaybackService.getString(R.string.app_name));
		bCast.putExtra(APP_PACKAGE_NAME, mMediaPlaybackService.getApplicationContext().getPackageName());
		
		String action = intent.getAction();
		if (action.equals(MediaPlaybackService.PLAYBACK_STARTED)) {
			state = START;
		} else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
			boolean isPlaying = intent.getBooleanExtra("playing", false);
			
			if (isPlaying) {
				state = RESUME;
			} else {
				state = PAUSE;
			}
		} else if (action.equals(MediaPlaybackService.PLAYBACK_COMPLETE)) {
			state = COMPLETE;
		}
		
		bCast.putExtra(STATE_NAME, state);
		bCast.putExtra(ARTIST_NAME, intent.getStringExtra("artist"));
		
		String album = intent.getStringExtra("album");
		if (album != null && !album.equals(Media.UNKNOWN_STRING)) {
			bCast.putExtra(ALBUM_NAME, album);
		}
		
		bCast.putExtra(TRACK_NAME, intent.getStringExtra("track"));
		bCast.putExtra(DURATION_NAME, ((int) intent.getLongExtra("duration", 0) / 1000));

		// send the broadcast
		mMediaPlaybackService.sendBroadcast(bCast);
		Log.d(TAG, "Broadcast sent");
	}
	
	/**
	 * Manually scrobbles a track.
	 * 
	 * @param intent An intent containing the track information to scrobble.
	 */
	public void scrobble(Intent intent) {
		if (!mSendScrobblerInfo) {
			return;
		}
		
		Log.d(TAG, "Manual scrobble requested");
		if (metadataPresent(intent)) {
			sendBroadcast(intent);
		}
	}
	
	/**
	 * Sets if scrobbler information should be broadcasted to SLS.
	 * 
	 * @param sendScrobblerInfo True if the information should be sent, false otherwise.
	 */
	public void setShouldSendScrobblerInfo(boolean sendScrobblerInfo) {
		synchronized (mLock) {
			mSendScrobblerInfo = sendScrobblerInfo;
		}
	}
	
	/**
	 * Unregisters the receiver.
	 */
	public void cleanup() {	
		mMediaPlaybackService.unregisterReceiver(this);
	}
}
