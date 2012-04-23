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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;

import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.MediaPlaybackService;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.flac.FlacParser;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.parser.ogg.OggParser;
import org.apache.tika.parser.vorbis.VorbisParser;
import org.apache.tika.sax.BodyContentHandler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

public class MetadataRetriever {
	
	// This class cannot be instantiated
	private MetadataRetriever() {
		
	}
	
	/*
	 * Retrieves metadata for an audio file and stores the information in the
	 * corresponding media table row.
	 */
	public static void retrieve(Context context, long id, int position) {
		//new RetrieveMetadataAsyncTask(context, position).execute(id);
	}
	
	/*
	 * Retrieves metadata for a set of audio files and stores the information in the
	 * corresponding media table rows.
	 */
	public static void retrieve(Context context, long [] list, int position) {
		new RetrieveMetadataAsyncTask(context, position).execute(list);
	}
	
	private static class RetrieveMetadataAsyncTask extends AsyncTask<long [], Void, Boolean> {
	    
		private Context mContext = null;
		int mPosition = -1;
		
		public RetrieveMetadataAsyncTask(Context context, int position) {
	        super();
	        mContext = context;
	        mPosition = position;
	    }
	    
		@Override
		protected Boolean doInBackground(long [] ... list) {
			for (int i = 0; i < list[0].length; i++) {
			
				String uri = getUri(mContext, list[0][i]);
			
				if (uri != null) {
					Metadata metadata = retrieveMetadata(uri);
				
					if (metadata != null) {
						updateMetadata(mContext, list[0][i], metadata);
					
						if (i == mPosition) {
							// send a broadcast so our activities can use the updated metadata 
							((MediaPlaybackService) mContext).updateMetadata();
						}
					}
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
			
			AbstractParser parser = detectParser(contentType);
			
			if (parser == null) {
				return null;
			}
			
			metadata = retrieveMetadata(inputStream, parser);
		} catch (Exception e) {
		} finally {
			Utils.closeInputStream(inputStream);
		}
		
		return metadata;
	}
	
	private static AbstractParser detectParser(String contentType) {
		AbstractParser parser = null;
		
		ParseContext parseContext = new ParseContext();
		
		FlacParser flacParser = new FlacParser();
		Mp3Parser mp3Parser = new Mp3Parser();
		OggParser oggParser = new OggParser();
		VorbisParser vorbisParser = new VorbisParser();
		
		if (contains(flacParser.getSupportedTypes(parseContext), contentType)) {
			parser = flacParser;
		} else if (contains(mp3Parser.getSupportedTypes(parseContext), contentType)) {
			parser = mp3Parser;
		} else if (contains(oggParser.getSupportedTypes(parseContext), contentType)) {
			parser = oggParser;
		} else if (contains(vorbisParser.getSupportedTypes(parseContext), contentType)) {
			parser = vorbisParser;
		}
		
		return parser;
	}
	
	private static boolean contains(Set<MediaType> types, String contentType) {
		Iterator<MediaType> iterator = types.iterator();
		
		while (iterator.hasNext()) {
			if (iterator.next().getBaseType().toString().equalsIgnoreCase(contentType)) {
				return true;
			}
		}
		
		return false;
	}
	
	private static Metadata retrieveMetadata(InputStream inputStream, AbstractParser parser) throws Exception {
		BodyContentHandler handler = new BodyContentHandler();
	    Metadata metadata = new Metadata();
		
		parser.parse(inputStream, handler, metadata, new ParseContext());
		
		return metadata;
	}
	
	private static int updateMetadata(Context context, long id, Metadata metadata) {
		int rows = 0;
		
		// if we didn't obtain at least the title, album or artist then don't store
		// the metadata since it's pretty useless
		if (metadata.get(Metadata.TITLE) == null && 
				metadata.get(XMPDM.ALBUM) == null && 
				metadata.get(XMPDM.ARTIST) == null) {
			return 0;
		}
		
		// Form an array specifying which columns to return. 
		ContentValues values = new ContentValues();
		values.put(Media.MediaColumns.TITLE, validateAttribute(metadata.get(Metadata.TITLE)));
		values.put(Media.MediaColumns.ALBUM, validateAttribute(metadata.get(XMPDM.ALBUM)));
		values.put(Media.MediaColumns.ARTIST, validateAttribute(metadata.get(XMPDM.ARTIST)));
		values.put(Media.MediaColumns.TRACK, validateAttribute(metadata.get(XMPDM.TRACK_NUMBER)));
		values.put(Media.MediaColumns.YEAR, convertToInteger(metadata.get(XMPDM.RELEASE_DATE)));

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
}
