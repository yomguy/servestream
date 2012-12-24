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

package net.sourceforge.servestream.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.xml.sax.SAXException;

import net.sourceforge.jplaylistparser.exception.JPlaylistParserException;
import net.sourceforge.jplaylistparser.parser.AutoDetectParser;
import net.sourceforge.jplaylistparser.playlist.Playlist;
import net.sourceforge.jplaylistparser.playlist.PlaylistEntry;
import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MediaPlaybackActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;
import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MusicUtils {
	
    public interface Defs {
        public final static int OPEN_URL = 0;
        public final static int ADD_TO_PLAYLIST = 1;
        public final static int USE_AS_RINGTONE = 2;
        public final static int PLAYLIST_SELECTED = 3;
        public final static int NEW_PLAYLIST = 4;
        public final static int PLAY_SELECTION = 5;
        public final static int GOTO_START = 6;
        public final static int GOTO_PLAYBACK = 7;
        public final static int PARTY_SHUFFLE = 8;
        public final static int SHUFFLE_ALL = 9;
        public final static int DELETE_ITEM = 10;
        public final static int SCAN_DONE = 11;
        public final static int QUEUE = 12;
        public final static int EFFECTS_PANEL = 13;
        public final static int CHILD_MENU_BASE = 14; // this should be the last item
    }
	
    public static IMediaPlaybackService sService = null;
    private static HashMap<Context, ServiceBinder> sConnectionMap = new HashMap<Context, ServiceBinder>();
	
    public static class ServiceToken {
        ContextWrapper mWrappedContext;
        ServiceToken(ContextWrapper context) {
            mWrappedContext = context;
        }
    }
    
    public static ServiceToken bindToService(Activity context) {
        return bindToService(context, null);
    }
    
    public static ServiceToken bindToService(Activity context, ServiceConnection callback) {
        Activity realActivity = context.getParent();
        if (realActivity == null) {
            realActivity = context;
        }
        ContextWrapper cw = new ContextWrapper(realActivity);
        cw.startService(new Intent(cw, MediaPlaybackService.class));
        ServiceBinder sb = new ServiceBinder(callback);
        if (cw.bindService((new Intent()).setClass(cw, MediaPlaybackService.class), sb, 0)) {
            sConnectionMap.put(cw, sb);
            return new ServiceToken(cw);
        }
        Log.e("Music", "Failed to bind to service");
        return null;
    }
    
    public static ServiceToken bindToService(Service context, ServiceConnection callback) {
        ContextWrapper cw = new ContextWrapper(context);
        cw.startService(new Intent(cw, MediaPlaybackService.class));
        ServiceBinder sb = new ServiceBinder(callback);
        if (cw.bindService((new Intent()).setClass(cw, MediaPlaybackService.class), sb, 0)) {
            sConnectionMap.put(cw, sb);
            return new ServiceToken(cw);
        }
        Log.e("Music", "Failed to bind to service");
        return null;
    }
    
    public static void unbindFromService(ServiceToken token) {
        if (token == null) {
            Log.e("MusicUtils", "Trying to unbind with null token");
            return;
        }
        ContextWrapper cw = token.mWrappedContext;
        ServiceBinder sb = sConnectionMap.remove(cw);
        if (sb == null) {
            Log.e("MusicUtils", "Trying to unbind for unknown Context");
            return;
        }
        cw.unbindService(sb);
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this point,
            // so don't hang on to the ServiceConnection
            sService = null;
        }
    }
    
    private static class ServiceBinder implements ServiceConnection {
        ServiceConnection mCallback;
        ServiceBinder(ServiceConnection callback) {
            mCallback = callback;
        }
        
        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
            sService = IMediaPlaybackService.Stub.asInterface(service);
            if (mCallback != null) {
                mCallback.onServiceConnected(className, service);
            }
        }
        
        public void onServiceDisconnected(ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            sService = null;
        }
    }
    
    public static AddToCurrentPlaylistAsyncTask addToCurrentPlaylistFromURL(Context context, UriBean uri, Handler handler) {
    	AddToCurrentPlaylistAsyncTask playlistTask = new AddToCurrentPlaylistAsyncTask(context, uri, handler);
    	playlistTask.execute();
    	
    	return playlistTask;
    }
    
    public static void addToCurrentPlaylist(Context context, long [] list) {
        if (list.length == 0 || sService == null) {
            Log.d("MusicUtils", "attempt to play empty song list");
            // Don't try to play empty playlists. Nothing good will come of it.
            String message = context.getString(R.string.emptyplaylist, list.length);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            sService.enqueue(list, MediaPlaybackService.LAST);
            String message = context.getResources().getQuantityString(
                    R.plurals.NNNtrackstoplaylist, list.length, Integer.valueOf(list.length));
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (RemoteException ex) {
        }
    }
    
    public static class AddToCurrentPlaylistAsyncTask extends AsyncTask<Void, Void, Void> {
		
    	Context mContext = null;
    	private UriBean mUri;
    	Handler mHandler = null;
    	
	    public AddToCurrentPlaylistAsyncTask(Context context, UriBean uri, Handler handler) {
	        super();
	        mContext = context;
	        mUri = uri;
	        mHandler = handler;
	    }

		@Override
		protected Void doInBackground(Void... arg0) {
			long [] list = new long[0];
			
			AbsTransport transport = TransportFactory.getTransport(mUri.getProtocol());
			transport.setUri(mUri);
			
			try { 
				transport.connect();
			
				if (transport.getContentType() != null && !transport.getContentType().contains("text/html")) {
					list = MusicUtils.getFilesInPlaylist(mContext, mUri.getScrubbedUri().toString(), transport.getContentType(), transport.getConnection());
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				transport.close();
			}
			
	        Message msg = new Message();
	        msg.obj = list;
			mHandler.sendMessage(msg);
	        
			return null;
		}
    }
    
    public static Cursor query(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder, int limit) {
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return null;
            }
            if (limit > 0) {
                uri = uri.buildUpon().appendQueryParameter("limit", "" + limit).build();
            }
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
         } catch (UnsupportedOperationException ex) {
            return null;
        }
        
    }
    public static Cursor query(Context context, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        return query(context, uri, projection, selection, selectionArgs, sortOrder, 0);
    }
    
    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    public static String makeTimeString(Context context, long secs) {
        StringBuilder sFormatBuilder = new StringBuilder();
        Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
        final Object[] sTimeArgs = new Object[5];
        
        String durationformat = context.getString(
                secs < 3600 ? R.string.durationformatshort : R.string.durationformatlong);
        
        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }
    
    public static void playAll(Context context, long [] list, int position) {
        playAll(context, list, position, false);
    }
    
    private static void playAll(Context context, long [] list, int position, boolean force_shuffle) {
        if (list.length == 0 || sService == null) {
            Log.d("MusicUtils", "attempt to play empty song list");
            // Don't try to play empty playlists. Nothing good will come of it.
            String message = context.getString(R.string.emptyplaylist, list.length);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (force_shuffle) {
                sService.setShuffleMode(MediaPlaybackService.SHUFFLE_ON);
            }
            long curid = sService.getAudioId();
            int curpos = sService.getQueuePosition();
            if (position != -1 && curpos == position && curid == list[position]) {
                // The selected file is the file that's currently playing;
                // figure out if we need to restart with a new playlist,
                // or just launch the playback activity.
                long [] playlist = sService.getQueue();
                if (Arrays.equals(list, playlist)) {
                    // we don't need to set a new list, but we should resume playback if needed
                    sService.play();
                    return; // the 'finally' block will still run
                }
            }
            if (position < 0) {
                position = 0;
            }
            sService.open(list, force_shuffle ? -1 : position);
        } catch (RemoteException ex) {
        } finally {
            Intent intent = new Intent("net.sourceforge.servestream.PLAYBACK_VIEWER")
            	.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }

    // A really simple BitmapDrawable-like class, that doesn't do
    // scaling, dithering or filtering.
    private static class FastBitmapDrawable extends Drawable {
        private Bitmap mBitmap;
        public FastBitmapDrawable(Bitmap b) {
            mBitmap = b;
        }
        @Override
        public void draw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
        @Override
        public void setAlpha(int alpha) {
        }
        @Override
        public void setColorFilter(ColorFilter cf) {
        }
        public Bitmap getBitmap() {
        	return mBitmap;
        }
    }

    private static final BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
    private static final Uri sArtworkUri = Media.MediaColumns.CONTENT_URI;
    private static final HashMap<Long, Drawable> sArtCache = new HashMap<Long, Drawable>();
    
    static {
        // for the cache, 
        // 565 is faster to decode and display
        // and we don't want to dither here because the image will be scaled down later
        sBitmapOptionsCache.inPreferredConfig = Bitmap.Config.RGB_565;
        sBitmapOptionsCache.inDither = false;
    }

    public static void clearAlbumArtCache() {
        synchronized(sArtCache) {
            sArtCache.clear();
        }
    }
    
    public static Drawable getCachedArtwork(Context context, long artIndex, boolean allowdefault) {
        Drawable d = null;
        
        float density = context.getResources().getDisplayMetrics().density;
        int scale = Math.round((float) 56 * density);
        
        synchronized(sArtCache) {
            d = sArtCache.get(artIndex);
        }
        if (d == null) {
            Bitmap b = MusicUtils.getArtworkQuick(context, artIndex, scale, scale);
            if (b != null) {
                d = new FastBitmapDrawable(b);
                synchronized(sArtCache) {
                    // the cache may have changed since we checked
                    Drawable value = sArtCache.get(artIndex);
                    if (value == null) {
                        sArtCache.put(artIndex, d);
                    } else {
                        d = value;
                    }
                }
            }
        }
        
        if (d == null && allowdefault) {
        	Bitmap b = getDefaultArtworkQuick(context, scale, scale);
        
        	if (b != null) {
        		d = new FastBitmapDrawable(b);
        	}
        }
        
        return d;
    }
    
    // Get album art for specified album. This method will not try to
    // fall back to getting artwork directly from the file, nor will
    // it attempt to repair the database.
    private static Bitmap getArtworkQuick(Context context, long id, int w, int h) {
    	Bitmap b = null;
        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, id);
        if (uri != null) {
            Cursor cursor = res.query(uri, null, null, null, null);            	
            int sampleSize = 1;
                
            // Compute the closest power-of-two scale factor 
            // and pass that to sBitmapOptionsCache.inSampleSize, which will
            // result in faster decoding and better quality
            sBitmapOptionsCache.inJustDecodeBounds = true;
                
            if (cursor.moveToNext()) {
            	byte [] blob = cursor.getBlob(cursor.getColumnIndexOrThrow(Media.MediaColumns.ARTWORK));
                	
                if (blob != null) {
                	BitmapFactory.decodeByteArray(blob, 0, blob.length, sBitmapOptionsCache);
                	int nextWidth = sBitmapOptionsCache.outWidth >> 1;
                	int nextHeight = sBitmapOptionsCache.outHeight >> 1;
                	while (nextWidth>w && nextHeight>h) {
                		sampleSize <<= 1;
                		nextWidth >>= 1;
                		nextHeight >>= 1;
                	}

                	sBitmapOptionsCache.inSampleSize = sampleSize;
                	sBitmapOptionsCache.inJustDecodeBounds = false;
                	b = BitmapFactory.decodeByteArray(blob, 0, blob.length, sBitmapOptionsCache);
                }
                
                blob = null;
            }

            cursor.close();
            
            if (b != null) {
            	// finally rescale to exactly the size we need
                if (sBitmapOptionsCache.outWidth != w || sBitmapOptionsCache.outHeight != h) {
                    Bitmap tmp = Bitmap.createScaledBitmap(b, w, h, true);
                    // Bitmap.createScaledBitmap() can return the same bitmap
                    if (tmp != b) b.recycle();
                        b = tmp;
                }
            }
                
            return b;
        }
        return null;
    }
    
    // Get album art for specified album. This method will not try to
    // fall back to getting artwork directly from the file, nor will
    // it attempt to repair the database.
    private static Bitmap getDefaultArtworkQuick(Context context, int w, int h) {
    	Bitmap b = null;
        int sampleSize = 1;
                
        // Compute the closest power-of-two scale factor 
        // and pass that to sBitmapOptionsCache.inSampleSize, which will
        // result in faster decoding and better quality
        sBitmapOptionsCache.inJustDecodeBounds = true;
                
        BitmapFactory.decodeResource(context.getResources(), R.drawable.albumart_mp_unknown_list, sBitmapOptionsCache);
        int nextWidth = sBitmapOptionsCache.outWidth >> 1;
        int nextHeight = sBitmapOptionsCache.outHeight >> 1;
        while (nextWidth>w && nextHeight>h) {
        	sampleSize <<= 1;
            nextWidth >>= 1;
            nextHeight >>= 1;
        }

        sBitmapOptionsCache.inSampleSize = sampleSize;
        sBitmapOptionsCache.inJustDecodeBounds = false;
        b = BitmapFactory.decodeResource(context.getResources(), R.drawable.albumart_mp_unknown_list, sBitmapOptionsCache);
            
        if (b != null) {
        	// finally rescale to exactly the size we need
            if (sBitmapOptionsCache.outWidth != w || sBitmapOptionsCache.outHeight != h) {
            	Bitmap tmp = Bitmap.createScaledBitmap(b, w, h, true);
                // Bitmap.createScaledBitmap() can return the same bitmap
                if (tmp != b) b.recycle();
                	b = tmp;
            }
        }
                
        return b;
    }
    
    public static Bitmap getLargeCachedArtwork(Context context, long artIndex, int w, int h) {
        return MusicUtils.getLargeArtworkQuick(context, artIndex, w, h);
    }
    
    // Get album art for specified album. This method will not try to
    // fall back to getting artwork directly from the file, nor will
    // it attempt to repair the database.
    private static Bitmap getLargeArtworkQuick(Context context, long id, int w, int h) {
    	Bitmap b = null;
        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, id);
        if (uri != null) {
            Cursor cursor = res.query(uri, null, null, null, null);            	
            int sampleSize = 1;
                
            // Compute the closest power-of-two scale factor 
            // and pass that to sBitmapOptionsCache.inSampleSize, which will
            // result in faster decoding and better quality
            sBitmapOptionsCache.inJustDecodeBounds = true;
                
            if (cursor.moveToNext()) {
            	byte [] blob = cursor.getBlob(cursor.getColumnIndexOrThrow(Media.MediaColumns.ARTWORK));
                	
                if (blob != null) {
                	BitmapFactory.decodeByteArray(blob, 0, blob.length, sBitmapOptionsCache);
                	int nextWidth = sBitmapOptionsCache.outWidth >> 1;
                	int nextHeight = sBitmapOptionsCache.outHeight >> 1;
                	while (nextWidth>w && nextHeight>h) {
                		sampleSize <<= 1;
                		nextWidth >>= 1;
                		nextHeight >>= 1;
                	}

                	sBitmapOptionsCache.inSampleSize = sampleSize;
                	sBitmapOptionsCache.inJustDecodeBounds = false;
                	b = BitmapFactory.decodeByteArray(blob, 0, blob.length, sBitmapOptionsCache);
                }
                
                blob = null;
            }

            cursor.close();
            
            if (b != null) {
            	// finally rescale to exactly the size we need
                if (sBitmapOptionsCache.outWidth != w || sBitmapOptionsCache.outHeight != h) {
                    Bitmap tmp = Bitmap.createScaledBitmap(b, w, h, true);
                    // Bitmap.createScaledBitmap() can return the same bitmap
                    if (tmp != b) b.recycle();
                        b = tmp;
                }
            }
            
            return b;
        }
        return null;
    }
    
    public static Bitmap getDefaultArtwork(Context context, int id, int w, int h) {
    	BitmapFactory.Options sBitmapOptionsCache = new BitmapFactory.Options();
    	Bitmap b = null;
        int sampleSize = 1;
        
        sBitmapOptionsCache.inPreferredConfig = Bitmap.Config.ARGB_8888;
        
        // Compute the closest power-of-two scale factor 
        // and pass that to sBitmapOptionsCache.inSampleSize, which will
        // result in faster decoding and better quality
        sBitmapOptionsCache.inJustDecodeBounds = true;
                
        BitmapFactory.decodeResource(context.getResources(), id, sBitmapOptionsCache);
        int nextWidth = sBitmapOptionsCache.outWidth >> 1;
        int nextHeight = sBitmapOptionsCache.outHeight >> 1;
        while (nextWidth>w && nextHeight>h) {
        	sampleSize <<= 1;
        	nextWidth >>= 1;
        	nextHeight >>= 1;
        }

        sBitmapOptionsCache.inSampleSize = sampleSize;
        sBitmapOptionsCache.inJustDecodeBounds = false;
        b = BitmapFactory.decodeResource(context.getResources(), id, sBitmapOptionsCache);
            
        if (b != null) {
        	// finally rescale to exactly the size we need
            if (sBitmapOptionsCache.outWidth != w || sBitmapOptionsCache.outHeight != h) {
            	Bitmap tmp = Bitmap.createScaledBitmap(b, w, h, true);
            	// Bitmap.createScaledBitmap() can return the same bitmap
                if (tmp != b) b.recycle();
                	b = tmp;
            }
        }
            
        return b;
    }
    
    public static Bitmap getCachedBitmapArtwork(Context context, long artIndex) {
        FastBitmapDrawable d = (FastBitmapDrawable) MusicUtils.getCachedArtwork(context, artIndex, false);

		if (d != null) {
			return d.getBitmap();
		}
		
		return null;
    }
        
    public static void updateNowPlaying(Activity a) {
        View nowPlayingView = a.findViewById(R.id.nowplaying);
        if (nowPlayingView == null) {
            return;
        }
        try {
            if (true && MusicUtils.sService != null && MusicUtils.sService.getAudioId() != -1) {
            	Drawable d = null;
            	
            	ImageView coverart = (ImageView) nowPlayingView.findViewById(R.id.coverart);
            	
            	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(a);
                if (preferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
                	d = MusicUtils.getCachedArtwork(a, sService.getAudioId(), true);
                }
            	
            	if (d == null) {
            		coverart.setVisibility(View.GONE);
            	} else {
            		coverart.setVisibility(View.VISIBLE);
            		coverart.setImageDrawable(d);
            	}
                
            	TextView title = (TextView) nowPlayingView.findViewById(R.id.title);
                title.setSelected(true);
                TextView artist = (TextView) nowPlayingView.findViewById(R.id.artist);
                artist.setSelected(true);
        		
                CharSequence trackName = sService.getTrackName();
            	CharSequence artistName = sService.getArtistName();                
            	
                if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
            		title.setText(R.string.widget_one_track_info_unavailable);
            	} else {
            		title.setText(trackName);
            	}
            		
            	if (artistName == null || artistName.equals(Media.UNKNOWN_STRING)) {
            		artistName = sService.getMediaUri();
            	}
                
            	artist.setText(artistName);
            	
                final ImageView pauseButton = (ImageView) nowPlayingView.findViewById(R.id.play_pause_button);
            	pauseButton.setVisibility(View.VISIBLE);
            	
            	if (sService.isPlaying()) {
				    pauseButton.setImageResource(R.drawable.btn_playerpreview_pause);
				} else {
				    pauseButton.setImageResource(R.drawable.btn_playerpreview_play);
				}
            	
            	pauseButton.setOnClickListener(new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						try {
							if (sService.isPlaying()) {
							    sService.pause();
							} else {
							    sService.play();
							}
						} catch (RemoteException e) {
						}
					}
            	});
            	
                nowPlayingView.setVisibility(View.VISIBLE);
                nowPlayingView.setOnClickListener(new View.OnClickListener() {

                	@Override
                    public void onClick(View v) {
                        Context c = v.getContext();
                        c.startActivity(new Intent(c, MediaPlaybackActivity.class));
                    }
                });
                
                return;
            }
        } catch (RemoteException ex) {
        }
        nowPlayingView.setVisibility(View.GONE);
    }
    
    private final static long [] sEmptyList = new long[0];
    
    public static long [] getFilesInPlaylist(Context context, String uri, String contentType, InputStream is) {
    	
    	if (uri == null) {
    		return sEmptyList;
    	}
    	
        AutoDetectParser parser = new AutoDetectParser(); // Should auto-detect!
        Playlist playlist = new Playlist();
        
        try {
			parser.parse(uri, contentType, is, playlist);
		} catch (IOException e) {
			playlist = null;
		} catch (SAXException e) {
			playlist = null;
		} catch (JPlaylistParserException e) {
			playlist = null;
		} finally {
			Utils.closeInputStream(is);
		}
			
		if (playlist == null) {
			playlist = new Playlist();
			PlaylistEntry playlistEntry = new PlaylistEntry();
			playlistEntry.set(PlaylistEntry.URI, uri);
			playlistEntry.set(PlaylistEntry.TRACK, "1");
			playlist.add(playlistEntry);
		}
    	
        return addFilesToMediaStore(context, playlist);
    }
    
    public static long [] storeFile(Context context, String uri) {
    	
    	if (uri == null) {
    		return sEmptyList;
    	}
    	
        Playlist playlist = new Playlist();
		playlist = new Playlist();
		PlaylistEntry playlistEntry = new PlaylistEntry();
		playlistEntry.set(PlaylistEntry.URI, uri);
		playlistEntry.set(PlaylistEntry.TRACK, "1");
		playlist.add(playlistEntry);
	
    	return addFilesToMediaStore(context, playlist);
	}
    
    private static long [] addFilesToMediaStore(Context context, Playlist playlist) {
    	if (playlist == null || playlist.getPlaylistEntries().size() == 0) {
    		return sEmptyList;
    	}	
    	
    	List<ContentValues> contentValues = new ArrayList<ContentValues>();
    	
    	ContentResolver contentResolver = context.getContentResolver();
    	
    	Map<String, Integer> uriList = retrieveAllRows(context);
    	
    	long [] list = new long[playlist.getPlaylistEntries().size()];
    	
    	// process the returned media files
    	for (int i = 0; i < playlist.getPlaylistEntries().size(); i++) {
    		long id = -1;
    	
    		String uri = null;
    		
        	try {
        		uri = URLDecoder.decode(playlist.getPlaylistEntries().get(i).get(PlaylistEntry.URI), "UTF-8");
    		} catch (UnsupportedEncodingException ex) {
    			ex.printStackTrace();
    			uri = playlist.getPlaylistEntries().get(i).get(PlaylistEntry.URI);
    		}
    		
    		if (uriList.get(uri) != null) {
    			id = uriList.get(uri);
        		list[i] = id;
    		} else {    			
    			// the item doesn't exist, lets put it into the list to be inserted
    			ContentValues value = new ContentValues();
        		value.put(Media.MediaColumns.URI, uri);
        		
        		if (playlist.getPlaylistEntries().get(i).get(PlaylistEntry.PLAYLIST_METADATA) != null) {
        			value.put(Media.MediaColumns.TITLE, playlist.getPlaylistEntries().get(i).get(PlaylistEntry.PLAYLIST_METADATA));
        		}

        		contentValues.add(value);
    		}
    	}
    	
    	if (contentValues.size() > 0) {
    		ContentValues [] values = new ContentValues[contentValues.size()];
    		values = contentValues.toArray(values);
    		
    		int numInserted = contentResolver.bulkInsert(Media.MediaColumns.CONTENT_URI, values);
    	
    		if (numInserted > 0) {
    			/*uriList = retrieveAllRows(context);
    		
    			for (int i = 0; i < mediaFiles.size(); i++) {
    				if (uriList.get(mediaFiles.get(i).getUrl()) != null) {
    					int id = uriList.get(mediaFiles.get(i).getUrl());
    					list[i] = id;
    				}
    			}*/
    			list = addFilesToMediaStore(context, playlist);
    		}
    	}
    	
    	return list;
    }
    
    private static Map<String, Integer> retrieveAllRows(Context context) {
    	Map<String, Integer> list = new HashMap<String, Integer>();
    	
		// Form an array specifying which columns to return. 
		String [] projection = new String [] { Media.MediaColumns._ID, Media.MediaColumns.URI };

		// Get the base URI for the Media Files table in the Media content provider.
		Uri mediaFile =  Media.MediaColumns.CONTENT_URI;
    	
		// Make the query.
		Cursor cursor = context.getContentResolver().query(mediaFile, 
				projection,
				null,
				null,
				null);    	
	
		while (cursor.moveToNext()) {
			int uriColumn = cursor.getColumnIndex(Media.MediaColumns.URI);
			int idColumn = cursor.getColumnIndex(Media.MediaColumns._ID);
			String uri = cursor.getString(uriColumn);
			int id = cursor.getInt(idColumn);
			list.put(uri, id);
		}

		cursor.close();
		
		return list;
    }
}
