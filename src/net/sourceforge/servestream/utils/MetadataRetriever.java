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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.MediaPlaybackService;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

public class MetadataRetriever {
	
	private static final String MP3_CONTENT_TYPE = "audio/mpeg";
	
	// This class cannot be instantiated
	private MetadataRetriever() {
		
	}
	
	/*
	 * Retrieves metadata for an audio file and stores the information in the
	 * corresponding media table row.
	 */
	public static void retrieve(Context context, long id) {
		new RetrieveMetadataAsyncTask(context).execute(id);
	}
	
	/*
	 * Retrieves metadata for a set of audio files and stores the information in the
	 * corresponding media table rows.
	 */
	public static void retrieve(Context context, long [] list) {
		for (int i = 0; i < list.length; i++) {
			new RetrieveMetadataAsyncTask(context).execute(list[i]);
		}
	}
	
	private static class RetrieveMetadataAsyncTask extends AsyncTask<Long, Void, Boolean> {
	    
		private Context mContext = null;
		
		public RetrieveMetadataAsyncTask(Context context) {
	        super();
	        mContext = context;
	    }
	    
		@Override
		protected Boolean doInBackground(Long ... list) {
			String uri = getUri(mContext, list[0]);
			
			if (uri != null) {
				Metadata metadata = retrieveMetadata(uri);
				
				if (metadata != null) {
					updateMetadata(mContext, list[0], metadata);
					
					// send a broadcast so our activities can use the updated metadata 
			        Intent intent = new Intent(MediaPlaybackService.META_CHANGED);
					mContext.sendBroadcast(intent);
				}
			}
			
    		return true;
		}
    }
	
	private static String getUri(Context context, long id) {
		String uri = null;
		
		// Form an array specifying which columns to return. 
		String [] projection = new String [] { Media.MediaColumns.URI };

		// Get the base URI for the Media Files table in the Media content provider.
		Uri mediaFile =  Media.MediaColumns.CONTENT_URI;

		// Make the query.
		Cursor cursor = context.getContentResolver().query(mediaFile, 
				projection,
				Media.MediaColumns._ID + "= ? ",
				new String [] { String.valueOf(id) },
				null);    	
	
		if (cursor.moveToFirst()) {
			int uriColumn = cursor.getColumnIndex(Media.MediaColumns.URI);
			uri = cursor.getString(uriColumn);
		}
		
		cursor.close();
		
		return uri;
	}
	
	private static Metadata retrieveMetadata(String uri) {
		Metadata metadata = null;		
	    String contentType = null;
	    HttpURLConnection conn = null;
	    InputStream inputStream = null;	
	    URL url;
	    
	    if (uri == null) {
	    	return null;
	    }
	    
		try {
			url = new URL(uri);
	    		
			conn = URLUtils.getConnection(url);
	    	
			conn.setRequestProperty("User-Agent", URLUtils.USER_AGENT);
			conn.setConnectTimeout(6000);
			conn.setReadTimeout(6000);
			conn.setRequestMethod("GET");
		    
			contentType = conn.getContentType();
	        
			if (contentType == null) {
				return null;
			}
	    	
			inputStream = conn.getInputStream();
			
			if (inputStream == null) {
				return null;
			}
			
			if (contentType.equalsIgnoreCase(MP3_CONTENT_TYPE)) {
				metadata = retrieveMP3metadata(inputStream);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (TikaException e) {
			e.printStackTrace();
		} finally {
			Utils.closeInputStream(inputStream);
		}
		
		return metadata;
	}
	
	private static Metadata retrieveMP3metadata(InputStream inputStream) throws IOException, SAXException, TikaException {
		Mp3Parser mp3Parser = new Mp3Parser();
		BodyContentHandler handler = new BodyContentHandler();
	    Metadata metadata = new Metadata();
		
		mp3Parser.parse(inputStream, handler, metadata, new ParseContext());
		
		return metadata;
	}
	
	private static int updateMetadata(Context context, long id, Metadata metadata) {
		int rows = 0;
		
		// Form an array specifying which columns to return. 
		ContentValues values = new ContentValues();
		values.put(Media.MediaColumns.TITLE, metadata.get(Metadata.TITLE));
		values.put(Media.MediaColumns.ALBUM, metadata.get(XMPDM.ALBUM));
		values.put(Media.MediaColumns.ARTIST, metadata.get(XMPDM.ARTIST));

		// Get the base URI for the Media Files table in the Media content provider.
		Uri mediaFile =  Media.MediaColumns.CONTENT_URI;

		// Execute the update.
		rows = context.getContentResolver().update(mediaFile, 
				values, 
				Media.MediaColumns._ID + "= ? ", 
				new String [] { String.valueOf(id) } );
	
		// return the number of rows updated.
		return rows;
	}
}
