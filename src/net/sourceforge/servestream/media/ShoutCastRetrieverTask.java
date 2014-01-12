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

package net.sourceforge.servestream.media;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.servestream.provider.Media;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

public class ShoutCastRetrieverTask {
	private Context mContext = null;
	private long mId;
	private MetadataRetrieverListener mListener;
	private MetadataTask mTask;
	private boolean mNoMetadata;
	
	public ShoutCastRetrieverTask(Context context, long id) {
		mContext = context;
	    mId = id;
		
	    mTask = null;
	    mNoMetadata = false;
	    
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
			
			String uri = getUri(mContext, mId);
		
			if (uri != null) {
				while (!isCancelled()) {
					Metadata metadata = null;
					metadataFound = false;
					FFmpegMediaMetadataRetriever mmr = new FFmpegMediaMetadataRetriever();
					try {
						mmr.setDataSource(uri);
						
						String icyMetadata = mmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_ICY_METADATA);
						
						if ((metadata = parseMetadata(icyMetadata)) != null) {
							metadataFound = true;
						}
					} catch (IllegalArgumentException ex) {
					} finally {
						mmr.release();
					}
					
					if (metadataFound) {
						if (mListener != null) {
							mListener.onMetadataParsed(mId, metadata);
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
		
		private Metadata parseMetadata(String icyMetadata) {		
			String title = null;
			String artist = null;
			Metadata metadata = null;
			
			String streamTitle = null;
			Map<String, String> parsedMetadata = new HashMap<String, String>();
			String[] metaParts = icyMetadata.split(";");
			Pattern p = Pattern.compile("^([a-zA-Z]+)=\\'([^\\']*)\\'$");
			Matcher m;
			
			for (int i = 0; i < metaParts.length; i++) {
				m = p.matcher(metaParts[i]);
				if (m.find()) {
					parsedMetadata.put((String)m.group(1), (String)m.group(2));				
				}
			}

			streamTitle = parsedMetadata.get("StreamTitle");
			
			if (streamTitle == null || streamTitle.trim().equals("")) {
				streamTitle = parsedMetadata.get("StreamTitleReplay");
				
				if (streamTitle == null || streamTitle.trim().equals("")) {
					return metadata;
				}	
			}
			
			// check if the stream title contain a "-" character. This is usually done
			// to indicate "artist - title". If not, don't try to parse up the string
			// just store it
			if (streamTitle.indexOf("-") != -1) {
				artist = streamTitle.substring(0, streamTitle.indexOf("-")).trim();
				title = streamTitle.substring(streamTitle.indexOf("-") + 1).trim();
			} else {
				artist = streamTitle.trim();
				title = "";
			}
			
			// if we didn't obtain at least the title and artist then don't store
			// the metadata since it's pretty useless
			if (title != null && 
					artist != null) {
			
				HashMap<String, Object> meta = new HashMap<String, Object>();
				meta.put(Metadata.METADATA_KEY_TITLE, title);
				meta.put(Metadata.METADATA_KEY_ARTIST, artist);
				
				// Form an array specifying which columns to return. 
				metadata = new Metadata();
				metadata.parse(meta);
			}
			
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
}
