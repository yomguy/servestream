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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bitmap.DatabaseImageFetcher;
import net.sourceforge.servestream.bitmap.ImageCache;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.CoverView;
import net.sourceforge.servestream.utils.CoverView.CoverViewListener;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class MediaPlayerFragment extends Fragment implements CoverViewListener {
	
    private static final String IMAGE_CACHE_DIR = "large_album_art";
	
    private IMediaPlaybackService mService = null;
    
    private ServiceToken mToken;

    private TextView mTrackNumber;
    
    private DatabaseImageFetcher mImageFetcher;
    
	@SuppressLint("HandlerLeak")
	private Handler mAlbumArtHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (mImageFetcher != null && mService != null) {
				mImageFetcher.loadImage(msg.obj, mAlbum);
			}
		}
	};
    
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_media_player, container, false);
		
        mAlbum = (CoverView) view.findViewById(R.id.album_art);
        mAlbum.setup(this);
        mTrackName = (TextView) view.findViewById(R.id.trackname);
        mTrackName.setSelected(true);
        mArtistAndAlbumName = (TextView) view.findViewById(R.id.artist_and_album);
        mArtistAndAlbumName.setSelected(true);
        mTrackNumber = (TextView) view.findViewById(R.id.track_number_text);
		
		return view;
	}
    
    @Override
    public void onStart() {
        super.onStart();
        
        mToken = MusicUtils.bindToService(getActivity(), osc);
        if (mToken == null) {
            // something went wrong
            //mHandler.sendEmptyMessage(QUIT);
        }
        
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.ART_CHANGED);
        getActivity().registerReceiver(mStatusListener, new IntentFilter(f));
        updateTrackInfo();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (mImageFetcher != null) {
        	mImageFetcher.setExitTasksEarly(false);
        }
        updateTrackInfo();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mImageFetcher != null) {
        	mImageFetcher.setPauseWork(false);
        	mImageFetcher.setExitTasksEarly(true);
        	mImageFetcher.flushCache();
        }
    }
    
    @Override
    public void onStop() {
        getActivity().unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mImageFetcher != null) {
        	mImageFetcher.closeCache();
        }
    }
    
    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            mService = IMediaPlaybackService.Stub.asInterface(obj);
            try {
                // Assume something is playing when the service says it is,
                // but also if the audio ID is valid but the service is paused.
                if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                        mService.getPath() != null) {
                    // something is playing now, we're done
                    return;
                }
            } catch (RemoteException ex) {
            }
            // Service is dead or not playing anything. Return to the previous
            // activity.
            getActivity().finish();
        }
        public void onServiceDisconnected(ComponentName classname) {
            mService = null;
        }
    };
    
    private CoverView mAlbum;
    private TextView mArtistAndAlbumName;
    private TextView mTrackName;

    //private static final int GET_ALBUM_ART = 3;
    //private static final int REFRESH_ALBUM_ART = 4;

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
            } else if (action.equals(MediaPlaybackService.ART_CHANGED)) {
                try {
                	if (mService != null) {
                		Message message  = mAlbumArtHandler.obtainMessage();
                		message.obj = mService.getTrackId();
                		mAlbumArtHandler.sendMessage(message);
                	}
				} catch (RemoteException e) {
				}
            }
        }
    };
    
    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                getActivity().finish();
                return;
            }
            
            mTrackNumber.setText(mService.getTrackNumber());
            
            String trackName = mService.getTrackName();
            if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
            	trackName = mService.getMediaUri();
            }
            
            mTrackName.setText(trackName);
            
            String artistName = mService.getArtistName();
            String albumName = mService.getAlbumName();
            String artistAndAlbumName = "";
            
            if (artistName != null && !artistName.equals(Media.UNKNOWN_STRING)) {
            	artistAndAlbumName = artistName;
            }
            
            if (albumName != null && !albumName.equals(Media.UNKNOWN_STRING)) {
            	if (artistAndAlbumName.equals("")) {
            		artistAndAlbumName = albumName;
            	} else {
            		artistAndAlbumName = artistAndAlbumName + " - " + albumName;
            	}
            }
            
            mArtistAndAlbumName.setText(artistAndAlbumName);

            //mAlbumArtHandler.removeMessages();
       		Message message  = mAlbumArtHandler.obtainMessage();
    		message.obj = mService.getTrackId();
    		mAlbumArtHandler.sendMessage(message);
        } catch (RemoteException ex) {
        }
    }
    
	@Override
	public void onCoverViewInitialized(int width, int height) {
        if (mImageFetcher == null) {
        	int imageThumbSize = Math.min(width, height);
        
        	ImageCache.ImageCacheParams cacheParams =
        			new ImageCache.ImageCacheParams(getActivity(), IMAGE_CACHE_DIR);

        	cacheParams.setMemCacheSizePercent(0.25f); // Set memory cache to 25% of app memory

        	// The ImageFetcher takes care of loading images into our ImageView children asynchronously
        	mImageFetcher = new DatabaseImageFetcher(getActivity(), imageThumbSize);
        	mImageFetcher.setLoadingImage(R.drawable.albumart_mp_unknown);
        	mImageFetcher.addImageCache(getActivity().getSupportFragmentManager(), cacheParams);
        }
        
		if (mService == null) {
            return;
        }
       
        try {
            //mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
       		
            Message message  = mAlbumArtHandler.obtainMessage();
    		message.obj = mService.getTrackId();
    		mAlbumArtHandler.sendMessage(message);
		} catch (RemoteException e) {
		}
	}
}
