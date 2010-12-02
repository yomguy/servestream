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
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;

public class StreamMediaActivity extends Activity {
	
	private Button m_previousButton = null;
	private Button m_nextButton = null;
	
    private ArrayList<String> m_mediaFiles = null;
	private int m_currentMediaFileIndex = 0;
	private VideoView m_videoView = null;
    private String m_path = "";
	private int m_mediaPosition = 0;
    
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
        
		m_previousButton = (Button) this.findViewById(R.id.previous_button);
		if (m_currentMediaFileIndex == 0) {
		    m_previousButton.setEnabled(false);
			m_previousButton.setVisibility(View.INVISIBLE);
		}
		m_previousButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				m_videoView.stopPlayback();
				m_currentMediaFileIndex--;
	            setPlaylistButtons();
	            m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
	            m_videoView.start();
			}
			
		});
		
		m_nextButton = (Button) this.findViewById(R.id.next_button);
		if (!(m_mediaFiles.size() > 1)) {
			m_nextButton.setEnabled(false);
			m_nextButton.setVisibility(View.INVISIBLE);
		}
		m_nextButton.setOnClickListener(new OnClickListener() {

			public void onClick(View arg0) {
				m_videoView.stopPlayback();
				m_currentMediaFileIndex++;
	            setPlaylistButtons();
	            m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
	            m_videoView.start();
			}
			
		});
        
        m_videoView = (VideoView) findViewById(R.id.surface_view);
        
        // attempt to get data from before device configuration change
        Bundle returnData = (Bundle) getLastNonConfigurationInstance();
        
        if (returnData == null) {
            m_videoView.setOnCompletionListener(m_onCompletionListener);
            m_videoView.setVideoURI(Uri.parse(m_mediaFiles.get(m_currentMediaFileIndex)));
            m_videoView.setMediaController(new MediaController(this));
            //m_media.show(0);
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
		
		MenuItem settings = menu.add(R.string.list_menu_settings);
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(StreamMediaActivity.this, SettingsActivity.class));

		MenuItem help = menu.add(R.string.title_help);
		help.setIcon(android.R.drawable.ic_menu_help);
		help.setIntent(new Intent(StreamMediaActivity.this, HelpActivity.class));

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
		        m_previousButton.setEnabled(false);
		        m_nextButton.setEnabled(true);
		    } else if (m_currentMediaFileIndex == m_mediaFiles.size() - 1) {
		        m_previousButton.setEnabled(true);
		        m_nextButton.setEnabled(false);		    	
		    } else {
		        m_previousButton.setEnabled(true);
		        m_nextButton.setEnabled(true);
		    }
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
