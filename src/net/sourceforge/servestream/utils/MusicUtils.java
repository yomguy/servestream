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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
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
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
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
    
    public static AddToCurrentPlaylistAsyncTask addToCurrentPlaylistFromURL(Context context, Handler handler, Stream stream) {
    	AddToCurrentPlaylistAsyncTask playlistTask = new AddToCurrentPlaylistAsyncTask(context, handler);
    	playlistTask.execute(stream);
    	
    	return playlistTask;
    }
    
    public static void addToCurrentPlaylist(Context context, long [] list) {
        if (sService == null) {
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
    
    public static class AddToCurrentPlaylistAsyncTask extends AsyncTask<Stream, Void, Void> {
		
    	Context mContext = null;
    	Handler mHandler = null;
    	
	    public AddToCurrentPlaylistAsyncTask(Context context, Handler handler) {
	        super();
	        mContext = context;
	        mHandler = handler;
	    }

		@Override
		protected Void doInBackground(Stream... stream) {
			String contentType = null;
			URLUtils urlUtils = null;
			URL url = null;
			long [] list = new long[0];
			
			try {
				url = stream[0].getURL();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			
			try {
				urlUtils = new URLUtils(url);
			} catch (Exception ex) {
				ex.printStackTrace();
				return null;
			}
			
			if (urlUtils.getResponseCode() == HttpURLConnection.HTTP_OK) {			
				contentType = urlUtils.getContentType();
		    }
	
			if (contentType != null && !contentType.contains("text/html")) {
				list = MusicUtils.getFilesInPlaylist(mContext, url, contentType);
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
    
    public static void playAll(Context context, long [] list, int position, boolean fromActivity) {
        playAll(context, list, position, false, fromActivity);
    }
    
    public static void playAll(Context context, long [] list, int position) {
        playAll(context, list, position, false, true);
    }
    
    private static void playAll(Context context, long [] list, int position, boolean force_shuffle, boolean fromActivity) {
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
            Intent intent = new Intent("net.sourceforge.servestream.PLAYBACK_VIEWER");
            
            if (fromActivity) {
            	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            } else {
            	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            
            context.startActivity(intent);
        }
    }

    private final static long [] sEmptyList = new long[0];
    
    public static long [] getFilesInPlaylist(Context context, URL url, String contentType) {
    	List<MediaFile> mediaFiles = new ArrayList<MediaFile>();
    	
    	if (url == null) {
    		return sEmptyList;
    	}
    	
		PlaylistParser playlist = PlaylistParser.getPlaylistParser(url, contentType);
			
		if (playlist != null) {
			playlist.retrieveAndParsePlaylist();
			mediaFiles = playlist.getPlaylistFiles();
		} else {
			MediaFile mediaFile = new MediaFile();
			mediaFile.setUrl(url.toString());
			mediaFile.setTrack(1);
			mediaFiles.add(mediaFile);
		}
    	
		System.out.println("=====> Getting files from media store");
		
        return addFilesToMediaStore(context, mediaFiles);
    }
    
    private static long [] addFilesToMediaStore(Context context, List<MediaFile> mediaFiles) {
    	if (mediaFiles == null) {
    		return sEmptyList;
    	}	
    	
    	ContentResolver contentResolver = context.getContentResolver();
		ContentValues values = new ContentValues();
    	
    	Map<String, Integer> uriList = retrieveAllRows(context);
    	
    	long [] list = new long[mediaFiles.size()];
    	
    	// process the returned media files
    	for (int i = 0; i < mediaFiles.size(); i++) {
    		long id = -1;
    	
    		if (uriList.get(mediaFiles.get(i).getUrl()) != null) {
    			id = uriList.get(mediaFiles.get(i).getUrl());
    		} else {
    			// the item doesn't exist, insert it
        		values.put(Media.MediaColumns.URI, mediaFiles.get(i).getUrl());
        		values.put(Media.MediaColumns.TITLE, mediaFiles.get(i).getPlaylistMetadata());

                Uri uri = contentResolver.insert(
                		Media.MediaColumns.CONTENT_URI, values);
                
                id = (int) ContentUris.parseId(uri);
    		}
    		
    		list[i] = id;
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
