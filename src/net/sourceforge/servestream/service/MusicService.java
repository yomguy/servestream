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

import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class MusicService extends Service {
	public final static String TAG = "ServeStream.MusicService";
    
    private ArrayList<String> m_mediaFiles = null;
	private int m_currentMediaIndex = 0;
	private int m_mediaPosition = 0;
    private MediaPlayer m_mediaPlayer = null;

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {

    	m_mediaPlayer = new MediaPlayer();
    	
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MusicService", "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
		ConnectionNotifier.getInstance().hideRunningNotification(this);
		Log.v("should have rmoved isonc","ok");
    }

    @Override
    public IBinder onBind(Intent intent) {

		// Make sure we stay running to maintain the bridges
		startService(new Intent(this, MusicService.class));
    	
        return m_binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
    	
		stopSelf();
		
		return true;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder m_binder = new MusicBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
		ConnectionNotifier.getInstance().showRunningNotification(this);
    }
    
    public void startMusicInBackground(ArrayList<String> mediaFiles, int currentMediaIndex , int currentPosition) throws Exception {    	
 
    	Log.v("current pos", String.valueOf(currentPosition));
    	
    	this.m_currentMediaIndex = currentMediaIndex;
    	
    	m_mediaPlayer.setDataSource(mediaFiles.get(currentMediaIndex));
    	m_mediaPlayer.prepare();
    	m_mediaPlayer.start();
    	m_mediaPlayer.seekTo(currentPosition);
    }
    
    public void stopMusicInBackground() throws Exception {    	
    	
    	this.m_mediaPosition = m_mediaPlayer.getCurrentPosition();

    	m_mediaPlayer.stop();
    }
    
    public int getCurrentPosition() {
    	return this.m_mediaPosition;
    }
    
    public int getCurrentMediaFileIndex() {
    	return this.m_currentMediaIndex;
    }
}