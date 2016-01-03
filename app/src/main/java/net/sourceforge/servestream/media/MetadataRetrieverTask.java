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

package net.sourceforge.servestream.media;

import java.util.HashMap;

import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.preference.PreferenceConstants;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

public class MetadataRetrieverTask implements Runnable {
	private static final String TAG = MetadataRetrieverTask.class.getName();
	
	private boolean mIsCancelled;
	private AsyncTask.Status mStatus;
	private Context mContext = null;
	private long [] mList;
	private MetadataRetrieverListener mListener;
		
	public MetadataRetrieverTask(Context context, long [] list) {
		mIsCancelled = false;
		mStatus = AsyncTask.Status.PENDING;
		
		mContext = context;
	    mList = list;
		
		// Verify that the host activity implements the callback interface
	    try {
	    	// Instantiate the MetadataRetrieverListener so we can send events to the host
	        mListener = (MetadataRetrieverListener) context;
	    } catch (ClassCastException e) {
	        // The activity doesn't implement the interface, throw exception
	        throw new ClassCastException(context.toString()
	        	+ " must implement MetadataRetrieverListener");
	    }
	}
	    
	@Override
	public void run() {
		mStatus = AsyncTask.Status.RUNNING;
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
		}
		
		FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
		
		SparseArray<String> uris = getUris(mContext, mList);
		
		for (int i = 0; i < mList.length; i++) {
			if (isCancelled()) {
				break;
			}
			
			String uri = uris.get((int) mList[i]);
			
			if (uri != null) {
				try {
					mmr.setDataSource(uri.toString());
					
					Metadata metadata;
					
					if ((metadata = getMetadata(mContext, mList[i], mmr)) != null) {
						if (mListener != null) {
							mListener.onMetadataParsed(mList[i], metadata);
						}
					}
					
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				} catch(IllegalArgumentException ex) {
					Log.e(TAG, "Metadata for track could not be retrieved");
				}
			}
		}
			
		mmr.release();
		
		mStatus = AsyncTask.Status.FINISHED;
    }
	
	private SparseArray<String> getUris(Context context, long [] list) {
		SparseArray<String> uris = new SparseArray<String>();
		StringBuffer selection = new StringBuffer(Media.MediaColumns._ID + " IN (");
		int id = -1;
		String uri = null;
		
		for (int i = 0; i < list.length; i++) {
			if (i == 0) {
				selection.append(list[i]);
			} else {
				selection.append("," + list[i]);
			}
		}

		selection.append(")");
		
		// Form an array specifying which columns to return. 
		String [] projection = new String [] { Media.MediaColumns._ID, Media.MediaColumns.URI };

		// Get the base URI for the Media Files table in the Media content provider.
        Uri mediaFile = Media.MediaColumns.CONTENT_URI;
		
		// Make the query.
		Cursor cursor = context.getContentResolver().query(mediaFile, 
				projection,
				selection.toString(),
				null,
				null);    	
	
		while (cursor.moveToNext()) {
			id = cursor.getInt(cursor.getColumnIndex(Media.MediaColumns._ID));
			uri = cursor.getString(cursor.getColumnIndex(Media.MediaColumns.URI));
			uris.put(id, uri);
		}
		
		cursor.close();
		
		return uris;
	}
	
	private Metadata getMetadata(Context context, long id, FFmpegMediaMetadataRetriever mmr) {
		byte [] artwork = null;
		
		String title =  mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_TITLE);
		String album = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ALBUM);
		String artist = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ARTIST);
		String duration = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION);
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
		// only attempt to retrieve album art if the user has enabled that option
		if (preferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
			artwork = mmr.getEmbeddedPicture();
		}
		
		// if we didn't obtain at least the title, album or artist then don't store
		// the metadata since it's pretty useless
		if (title == null && 
				album == null && 
				artist == null) {
			return null;
		}
		
		HashMap<String, Object> meta = new HashMap<String, Object>();
		meta.put(Metadata.METADATA_KEY_TITLE, title);
		meta.put(Metadata.METADATA_KEY_ALBUM, album);
		meta.put(Metadata.METADATA_KEY_ARTIST, artist);
		meta.put(Metadata.METADATA_KEY_DURATION, duration);
		meta.put(Metadata.METADATA_KEY_ARTWORK, artwork);
		
		// Form an array specifying which columns to return. 
		Metadata metadata = new Metadata();
		metadata.parse(meta);
		
		return metadata;
	}
	
	private synchronized boolean isCancelled() {
		return mIsCancelled;
	}
	
	public synchronized boolean cancel() {
		if (mStatus != AsyncTask.Status.FINISHED) {
			mIsCancelled = true;
			return true;
		}
		
		return false;
	}
	
	public synchronized AsyncTask.Status getStatus() {
		return mStatus;
	}
	
	public void execute() {
		new Thread(this, "").start();
	}
}
