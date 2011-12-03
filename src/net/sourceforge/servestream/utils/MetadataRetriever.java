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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

public class MetadataRetriever {
	
	private static final String MP3_CONTENT_TYPE = "audio/mpeg";
	
	/**
	 * 
	 */
	public MetadataRetriever() {
		
	}
	
	public static MediaFile retrieveMetadata(MediaFile mediaFile) {
	    String contentType = null;
	    HttpURLConnection conn = null;
	    InputStream inputStream = null;	
	    URL url;
	    
	    if (mediaFile == null)
	    	return mediaFile;
	    
		try {
			url = new URL(mediaFile.getURL());
	    		
			conn = URLUtils.getConnection(url);
	    	
			conn.setRequestProperty("User-Agent", URLUtils.USER_AGENT);
			conn.setConnectTimeout(6000);
			conn.setReadTimeout(6000);
			conn.setRequestMethod("GET");
		    
			contentType = conn.getContentType();
	        
			if (contentType == null)
				return mediaFile;
	    	
			inputStream = conn.getInputStream();
			
			if (inputStream == null)
				return mediaFile;
			
			if (contentType.equalsIgnoreCase(MP3_CONTENT_TYPE))
				mediaFile = retrieveMP3metadata(mediaFile, inputStream);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (TikaException e) {
			e.printStackTrace();
		} finally {
			closeInputStream(inputStream);
		}
				
		//Log.v(TAG, "retrieved metadata for" + mediaFile.getURL());
		
		return mediaFile;
	}
	
	private static MediaFile retrieveMP3metadata(MediaFile mediaFile, InputStream inputStream) throws IOException, SAXException, TikaException {
		Mp3Parser mp3Parser = new Mp3Parser();
		BodyContentHandler handler = new BodyContentHandler();
	    Metadata metadata = new Metadata();
		
		mp3Parser.parse(inputStream, handler, metadata, new ParseContext());
    
		mediaFile.setTrack(metadata.get(Metadata.TITLE));
		mediaFile.setArtist(metadata.get(XMPDM.ARTIST));
		mediaFile.setAlbum(metadata.get(XMPDM.ALBUM));
		
		return mediaFile;
	}
	
	private static void closeInputStream(InputStream inputStream) {
		if (inputStream == null)
			return;
		
		try {
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
