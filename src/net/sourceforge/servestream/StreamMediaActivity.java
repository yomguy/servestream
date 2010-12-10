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

package net.sourceforge.servestream;

import java.util.ArrayList;

import net.sourceforge.servestream.utils.PlaylistHandler;

import android.app.Activity;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.MediaController;
import android.widget.VideoView;

public class StreamMediaActivity extends Activity {
	
    private ArrayList<String> m_mediaFiles = null;
	private int m_currentMediaFileIndex = 0;
	private VideoView m_videoView = null;
    private String m_path = "";
	private int m_mediaPosition = 0;
    
	MenuItem m_menuPrevious = null;
	MenuItem m_menuNext = null;
	
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.acc_streammedia);
        
		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_stream_play)));
        
        String stream = getIntent().getExtras().getString("net.sourceforge.servestream.TargetStream");
        
        if (isPlaylist(stream)) {
            PlaylistHandler playlistHandler = new PlaylistHandler(stream);
            playlistHandler.buildPlaylist();
            m_mediaFiles = playlistHandler.getPlayListFiles();
        } else {
        	m_mediaFiles = new ArrayList<String>();
        	m_mediaFiles.add(stream);
        }
        
        m_videoView = (VideoView) findViewById(R.id.surface_view);
        
        // attempt to get data from before device configuration change
        Bundle returnData = (Bundle) getLastNonConfigurationInstance();
        
        if (returnData == null) {
            m_videoView.setOnCompletionListener(m_onCompletionListener);
            m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
            m_videoView.setMediaController(new MediaController(this));
        } else {
        	m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
        	m_videoView.seekTo(m_mediaPosition);
        }
        
    	m_videoView.start();
        
    }
    
    private OnCompletionListener m_onCompletionListener = new OnCompletionListener() {
		public void onCompletion(MediaPlayer mp) {
			m_currentMediaFileIndex++;
			if (m_currentMediaFileIndex == m_mediaFiles.size()) {
				finish();
			} else {
			    m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
	            m_videoView.start();
			}
		}
    };
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		// build the previous button
		m_menuPrevious = menu.add(R.string.text_previous_button);
		m_menuPrevious.setIcon(android.R.drawable.ic_media_previous);
		m_menuPrevious.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem arg0) {
				m_videoView.stopPlayback();
				m_currentMediaFileIndex--;
				setPlaylistButtons();
			    m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
			    m_videoView.start();
			    return true;
			}
		});
		
		// build the next button
		m_menuNext = menu.add(R.string.text_next_button);
		m_menuNext.setIcon(android.R.drawable.ic_media_next);
		m_menuNext.setOnMenuItemClickListener(new OnMenuItemClickListener() {
	        public boolean onMenuItemClick(MenuItem arg0) {
				m_videoView.stopPlayback();
				m_currentMediaFileIndex++;
				setPlaylistButtons();
		        m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
		        m_videoView.start();
		        return true;
		    }
		});

		setPlaylistButtons();
		
		return true;
	}
	
    @Override
    public Object onRetainNonConfigurationInstance() {
        m_mediaPosition = m_videoView.getCurrentPosition();
 
        // Build bundle to save data for return
        Bundle data = new Bundle();
        data.putString("LOCATION", m_path);
        data.putInt("POSITION", m_mediaPosition);
      return data;
    }
    
    private void setPlaylistButtons() {
    	if (!(m_mediaFiles.size() == 1)) {
	 	    if (m_currentMediaFileIndex == 0) {
	 	    	m_menuPrevious.setEnabled(false);
	 	    	m_menuNext.setEnabled(true);
		    } else if (m_currentMediaFileIndex == m_mediaFiles.size() - 1) {
		    	m_menuPrevious.setEnabled(true);
		    	m_menuNext.setEnabled(false);		    	
		    } else {
		    	m_menuPrevious.setEnabled(true);
		    	m_menuNext.setEnabled(true);
		    }
    	} else {
    	    m_menuPrevious.setEnabled(false);
    	    m_menuNext.setEnabled(false);
    	}
    }
    
    private boolean isPlaylist(String streamLink) {
    	if (streamLink.length() > 4) {
    	    if (streamLink.substring(streamLink.length() - 4, streamLink.length()).equalsIgnoreCase(".m3u")) {
    	    	return true;
    	    }
    	}
    	
    	return false;
    }
    
}
