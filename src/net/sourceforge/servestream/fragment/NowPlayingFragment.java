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
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.PreferenceConstants;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

public class NowPlayingFragment extends Fragment implements ServiceConnection {
	
	private View mNowPlayingView;
	private ImageView mCoverart;
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
		
		mToken = MusicUtils.bindToService(getActivity(), this);
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_nowplaying, container, false);
		mNowPlayingView = view.findViewById(R.id.nowplaying);
		mCoverart = (ImageView) view.findViewById(R.id.coverart);
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
	}
	
	@Override
	public void onDestroy() {
		MusicUtils.unbindFromService(mToken);
		mService = null;
		
		super.onDestroy();
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
				Drawable d = null;

				SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
				if (preferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
					long id = mService.getAudioId();
					if (id >= 0) {
						Bitmap b = BitmapFactory.decodeResource(getActivity().getResources(), R.drawable.albumart_mp_unknown_list);
						BitmapDrawable defaultAlbumIcon = new BitmapDrawable(getActivity().getResources(), b);
						// no filter or dither, it's a lot faster and we can't tell the difference
						defaultAlbumIcon.setFilterBitmap(false);
						defaultAlbumIcon.setDither(false);

						d = MusicUtils.getCachedArtwork(getActivity(), mService.getAudioId(), defaultAlbumIcon, true);
					}
				}

				if (d == null) {
					mCoverart.setVisibility(View.GONE);
				} else {
					mCoverart.setVisibility(View.VISIBLE);
					mCoverart.setImageDrawable(d);
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
					mPreviousButton.setImageResource(R.drawable.ic_av_previous);
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
					mPauseButton.setImageResource(R.drawable.ic_av_pause_over_video_large);
				} else {
					mPauseButton.setImageResource(R.drawable.ic_av_play_over_video_large);
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
					mNextButton.setImageResource(R.drawable.ic_av_next);
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
