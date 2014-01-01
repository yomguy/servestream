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

import java.net.URISyntaxException;

import net.moraleboost.streamscraper.ScrapeException;
import net.sourceforge.servestream.provider.Media;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class ShoutCastRetrieverTask {
	private static final String TAG = ShoutCastRetrieverTask.class.getName();
	
	private Context mContext = null;
	private long mId;
	private MetadataRetrieverListener mListener;
	private MetadataTask mTask;
	private boolean mNoMetadata;
	private String mPreferredScraper;
	
	public ShoutCastRetrieverTask(Context context, long id) {
		mContext = context;
	    mId = id;
		
	    mTask = null;
	    mNoMetadata = false;
	    mPreferredScraper = "";
	    
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
	
	private synchronized String getUri(Context context, long id) {
		String uri = null;
		
		// Form an array specifying which columns to return. 
		String [] projection = new String [] { Media.MediaColumns.URI };

		// Get the base URI for the Media Files table in the Media content provider.
        Uri mediaFile = Media.MediaColumns.CONTENT_URI;
		
		// Make the query.
		Cursor cursor = context.getContentResolver().query(mediaFile, 
				projection,
				Media.MediaColumns._ID + " = " + id,
				null,
				null);    	
	
		if (cursor.moveToNext()) {
			uri = cursor.getString(cursor.getColumnIndex(Media.MediaColumns.URI));
		}
		
		cursor.close();
		
		return uri;
	}
	
	private Metadata getMetadata(Context context, long id, ShoutCastMetadataRetriever mmr) {
		String title =  mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
		String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
		
		// if we didn't obtain at least the title, album or artist then don't store
		// the metadata since it's pretty useless
		if (title == null && 
				artist == null) {
			return null;
		}
		
		// Form an array specifying which columns to return. 
		Metadata metadata = new Metadata();
		metadata.setTitle(title);
		metadata.setArtist(artist);
		
		return metadata;
	}
	
	private boolean retrieveMetadata(String uri, ShoutCastMetadataRetriever smr) {
		try {
			if (mPreferredScraper.equals(ShoutCastMetadataRetriever.SHOUTCAST_STREAM)) {
				smr.setShoutCastDataSource(uri);
			} else if (mPreferredScraper.equals(ShoutCastMetadataRetriever.ICECAST_STREAM)) {
				smr.setIceCastDataSource(uri);
			} else {
				mPreferredScraper = smr.setDataSource(uri);
			}
			return true;
		} catch (ScrapeException e) {
			Log.e(TAG, "ShoutCast metadata for track could not be retrieved");
		} catch (URISyntaxException e) {
			Log.e(TAG, "ShoutCast metadata for track could not be retrieved");
		}
		
		return false;
	}
	
	public synchronized void start() {
		if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
			mTask.cancel();
		}
		
		if (!mNoMetadata) {
			mTask = new MetadataTask();
			mTask.execute();
		}
	}
	
	public synchronized void stop() {
		if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
			mTask.cancel();
		}
	}
	
	private class MetadataTask implements Runnable {
		private boolean mIsCancelled;
		private AsyncTask.Status mStatus;
		
		private MetadataTask() {
			mStatus = AsyncTask.Status.PENDING;
		}
		
		@Override
		public void run() {
			mStatus = AsyncTask.Status.RUNNING;
			
			int retries = 0;
			boolean metadataFound;
		
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
			}
			
			if (isCancelled()) {
				return;
			}
			
			ShoutCastMetadataRetriever smr = new ShoutCastMetadataRetriever();
		
			String uri = getUri(mContext, mId);
		
			if (uri != null) {
				while (!isCancelled()) {
					metadataFound = false;
					metadataFound = retrieveMetadata(uri, smr);
					
					Metadata metadata;
					
					if (metadataFound) {
						if ((metadata = getMetadata(mContext, mId, smr)) != null) {
							if (mListener != null) {
								mListener.onMetadataParsed(mId, metadata);
							}
						}
						
						retries = 0;
					} else {
						retries++;
						if (retries >= 2) {
							mNoMetadata = true;
							break;
						}
					}
					
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
					}
				}
			}
			
			mStatus = AsyncTask.Status.FINISHED;
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
}
