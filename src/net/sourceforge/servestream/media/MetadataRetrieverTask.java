/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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

import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.utils.PreferenceConstants;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

public class MetadataRetrieverTask extends AsyncTask<long [], Void, Void> {
	private static final String TAG = MetadataRetrieverTask.class.getName();
	
	private Context mContext = null;
	private MetadataRetrieverListener mListener;
		
	public MetadataRetrieverTask(Context context) {
		mContext = context;
	    
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
	protected Void doInBackground(long [] ... list) {
		MediaMetadataRetriever mmr = new MediaMetadataRetriever();
			
		for (int i = 0; i < list[0].length; i++) {
			String uri = getUri(mContext, list[0][i]);
			
			if (uri != null) {
				try {
					mmr.setDataSource(uri.toString());
					
					if (updateMetadata(mContext, list[0][i], mmr) > 0) {
						if (mListener != null) {
							mListener.onMetadataParsed(list[0][i]);
						}
					}
				} catch(IllegalArgumentException ex) {
					Log.e(TAG, "Metadata for track could not be retrieved");
				}
			}
		}
			
		mmr.release();
			
    	return null;
    }
	
	private static String getUri(Context context, long id) {
		String uri = null;
		
		// Form an array specifying which columns to return. 
		String [] projection = new String [] { Media.MediaColumns.URI };

		// Get the base URI for the Media Files table in the Media content provider.
        Uri mediaFile = ContentUris.withAppendedId(Media.MediaColumns.CONTENT_URI, id);
		
		// Make the query.
		Cursor cursor = context.getContentResolver().query(mediaFile, 
				projection,
				null,
				null,
				null);    	
	
		if (cursor.moveToFirst()) {
			uri = cursor.getString(cursor.getColumnIndex(Media.MediaColumns.URI));
		}
		
		cursor.close();
		
		return uri;
	}
	
	private static int updateMetadata(Context context, long id, MediaMetadataRetriever mmr) {
		int rows = 0;
		byte [] artwork = null;
		
		String title =  mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
		String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
		String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
		String duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
		
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
			return 0;
		}
		
		// Form an array specifying which columns to return. 
		ContentValues values = new ContentValues();
		values.put(Media.MediaColumns.TITLE, validateAttribute(title));
		values.put(Media.MediaColumns.ALBUM, validateAttribute(album));
		values.put(Media.MediaColumns.ARTIST, validateAttribute(artist));
		values.put(Media.MediaColumns.DURATION, convertToInteger(duration));
		
		if (artwork != null) {
			values.put(Media.MediaColumns.ARTWORK, artwork);
		}

		// Get the base URI for the Media Files table in the Media content provider.
        Uri mediaFile = ContentUris.withAppendedId(Media.MediaColumns.CONTENT_URI, id);
		
		// Execute the update.
		rows = context.getContentResolver().update(mediaFile, 
				values, 
				null,
				null);
	
		// return the number of rows updated.
		return rows;
	}
	
	private static String validateAttribute(String attribute) {
		if (attribute == null) {
			return Media.UNKNOWN_STRING;
		}
		
		return attribute.trim();
	}
	
	private static int convertToInteger(String attribute) {
		int integerAttribute = Media.UNKNOWN_INTEGER;
		
		String validatedAttribute = validateAttribute(attribute);
		
		if (!validatedAttribute.equals(Media.UNKNOWN_STRING)) {
			try {
				integerAttribute = Integer.valueOf(validatedAttribute);
			} catch(NumberFormatException e) {
				// there was a problem converting the string
			}
		}
		
		return integerAttribute;
	}
	
    /* The activity that creates an instance of this class must
     * implement this interface in order to receive event callbacks. */
    public interface MetadataRetrieverListener {
        public void onMetadataParsed(long id);
    }
}
