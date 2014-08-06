/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
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
import net.sourceforge.servestream.activity.MediaPlayerActivity;
import net.sourceforge.servestream.bitmap.DatabaseImageFetcher;
import net.sourceforge.servestream.bitmap.ImageCache;
import net.sourceforge.servestream.bitmap.RecyclingImageView;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.Utils;
import net.sourceforge.servestream.preference.PreferenceConstants;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

public class MiniControllerFragment extends Fragment implements ServiceConnection {
	
    private static final String IMAGE_CACHE_DIR = "small_album_art";
    private DatabaseImageFetcher mImageFetcher;
	
	private View mNowPlayingView;
	private RecyclingImageView mCoverart;
	private TextView mTitle;
	private TextView mArtist;
	private ImageView mPreviousButton;
	private ImageView mPauseButton;
	private ImageView mNextButton;
	
    private IMediaPlaybackService mService = null;
    
    private ServiceToken mToken;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
        int imageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
        
        ImageCache.ImageCacheParams cacheParams =
    			new ImageCache.ImageCacheParams(getActivity(), IMAGE_CACHE_DIR);

    	cacheParams.setMemCacheSizePercent(0.25f); // Set memory cache to 25% of app memory

    	// The ImageFetcher takes care of loading images into our ImageView children asynchronously
    	mImageFetcher = new DatabaseImageFetcher(getActivity(), imageThumbSize);
    	mImageFetcher.setLoadingImage(R.drawable.albumart_mp_unknown_list);
    	mImageFetcher.addImageCache(getActivity().getSupportFragmentManager(), cacheParams);
        
		mToken = MusicUtils.bindToService(getActivity(), this);
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_mini_controller, container, false);
		mNowPlayingView = view.findViewById(R.id.nowplaying);
		mCoverart = (RecyclingImageView) view.findViewById(R.id.coverart);
		mTitle = (TextView) view.findViewById(R.id.title);
		mArtist = (TextView) view.findViewById(R.id.artist);
		mPreviousButton = (ImageView) view.findViewById(R.id.previous_button);
		mPauseButton = (ImageView) view.findViewById(R.id.play_pause_button);
		mNextButton = (ImageView) view.findViewById(R.id.next_button);
		
		return view;
	}
	
	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		updateNowPlaying();
	}
 
    @Override
	public void onResume() {
		super.onResume();
		
        mImageFetcher.setExitTasksEarly(false);
		
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        getActivity().registerReceiver(mTrackListListener, f);
	}

	@Override
	public void onPause() {
		super.onPause();
		
        getActivity().unregisterReceiver(mTrackListListener);
        
        mImageFetcher.setPauseWork(false);
        mImageFetcher.setExitTasksEarly(true);
        mImageFetcher.flushCache();
	}
	
	@Override
	public void onDestroy() {
		MusicUtils.unbindFromService(mToken);
		mService = null;
		
		super.onDestroy();
		
		mImageFetcher.closeCache();
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
		updateNowPlaying();
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Animation fade_out = AnimationUtils.loadAnimation(getActivity(), R.anim.player_out);
		mNowPlayingView.startAnimation(fade_out);
		mNowPlayingView.setVisibility(View.GONE);
	}
	
	private void updateNowPlaying() {
		if (mNowPlayingView == null) {
			return;
		}
		try {
			if (true && mService != null && mService.getAudioId() != -1) {
				long id = -1;

				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
				if (preferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
					id = mService.getAudioId();
				}

				if (id == -1) {
					mCoverart.setVisibility(View.GONE);
				} else {
					mImageFetcher.loadImage(id, mCoverart);
					mCoverart.setVisibility(View.VISIBLE);
				}

				mTitle.setSelected(true);
				mArtist.setSelected(true);

				CharSequence trackName = mService.getTrackName();
				CharSequence artistName = mService.getArtistName();                

				if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
					mTitle.setText(R.string.widget_one_track_info_unavailable);
				} else {
					mTitle.setText(trackName);
				}

				if (artistName == null || artistName.equals(Media.UNKNOWN_STRING)) {
					artistName = mService.getMediaUri();
				}

				mArtist.setText(artistName);

				if (mPreviousButton != null) {
					mPreviousButton.setImageResource(Utils.getThemedIcon(getActivity(), R.attr.ic_av_previous));
					mPreviousButton.setOnClickListener(new View.OnClickListener() {

						@Override
						public void onClick(View v) {
							try {
								mService.prev();
							} catch (RemoteException e) {
							}
						}
					});
				}

				mPauseButton.setVisibility(View.VISIBLE);

				if (mService.isPlaying()) {
					mPauseButton.setImageResource(Utils.getThemedIcon(getActivity(), R.attr.ic_mini_controller_pause));
				} else {
					mPauseButton.setImageResource(Utils.getThemedIcon(getActivity(), R.attr.ic_mini_controller_play));
				}

				mPauseButton.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						try {
							if (mService.isPlaying()) {
								mService.pause();
							} else {
								mService.play();
							}
						} catch (RemoteException e) {
						}
					}
				});

				if (mNextButton != null) {
					mNextButton.setImageResource(Utils.getThemedIcon(getActivity(), R.attr.ic_av_next));
					mNextButton.setOnClickListener(new View.OnClickListener() {

						@Override
						public void onClick(View v) {
							try {
								mService.next();
							} catch (RemoteException e) {
							}
						}
					});
				}

				if (mNowPlayingView.getVisibility() != View.VISIBLE) {
					Animation fade_in = AnimationUtils.loadAnimation(getActivity(), R.anim.player_in);
					mNowPlayingView.startAnimation(fade_in);
				}

				mNowPlayingView.setVisibility(View.VISIBLE);
				mNowPlayingView.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						Context c = v.getContext();
						c.startActivity(new Intent(c, MediaPlayerActivity.class));
					}
				});

				return;
			}
		} catch (RemoteException ex) {
		}
		Animation fade_out = AnimationUtils.loadAnimation(getActivity(), R.anim.player_out);
		mNowPlayingView.startAnimation(fade_out);
		mNowPlayingView.setVisibility(View.GONE);
	}
}
