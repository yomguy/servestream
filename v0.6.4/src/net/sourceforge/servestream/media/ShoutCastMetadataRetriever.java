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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.moraleboost.streamscraper.ScrapeException;
import net.moraleboost.streamscraper.Scraper;
import net.moraleboost.streamscraper.Stream;
import net.moraleboost.streamscraper.scraper.IceCastScraper;
import net.moraleboost.streamscraper.scraper.ShoutCastScraper;

/**
 * ShoutCastMetadataRetriever class provides a unified interface for retrieving
 * meta data from an input media file.
 */
public class ShoutCastMetadataRetriever
{
	public static final String SHOUTCAST_STREAM = "shoutcast";
	public static final String ICECAST_STREAM = "icecast";
	
    private Map<String, String> mMetadata;
	
    public ShoutCastMetadataRetriever() {
		mMetadata = new HashMap<String, String>();
    }

    /**
     * Sets the data source (file pathname) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     * 
     * @param path The path of the input media file.
     * @return The stream type on success; 
     * @throws URISyntaxException If the path cannot be parsed. 
     * @throws ScrapeException If metadata cannot be retrieved.
     */
    public String setDataSource(String path) throws ScrapeException, URISyntaxException {
    	try {
    		setShoutCastDataSource(path);
    		return SHOUTCAST_STREAM;
    	} catch (ScrapeException ex) {
    	} catch (NullPointerException ex) {
    	}
    	
    	try {
    		setIceCastDataSource(path);
    		return ICECAST_STREAM;
    	} catch (ScrapeException ex) {
    	} catch (NullPointerException ex) {
    	}
    	
    	throw new ScrapeException();
    }
    
    /**
     * Sets the data source (file pathname) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     * 
     * @param path The path of the input media file.
     * @throws URISyntaxException If the path cannot be parsed. 
     * @throws ScrapeException If metadata cannot be retrieved.
     */
    public void setShoutCastDataSource(String path) throws ScrapeException, URISyntaxException {
    	retrieveMetadata(path, new ShoutCastScraper());
    }
    
    /**
     * Sets the data source (file pathname) to use. Call this
     * method before the rest of the methods in this class. This method may be
     * time-consuming.
     * 
     * @param path The path of the input media file.
     * @throws URISyntaxException If the path cannot be parsed. 
     * @throws ScrapeException If metadata cannot be retrieved.
     */
    public void setIceCastDataSource(String path) throws ScrapeException, URISyntaxException {
    	retrieveMetadata(path, new IceCastScraper());
    }
    
    /**
     * Retrieves metadata from a specified path.
     * 
     * @param path The path of the input media file
     * @param scraper The scraper
     * @throws URISyntaxException If the path cannot be parsed. 
     * @throws ScrapeException If metadata cannot be retrieved.
     */
    private void retrieveMetadata(String path, Scraper scraper) throws URISyntaxException, ScrapeException {
        List<Stream> streams = new ArrayList<Stream>();
        
    	mMetadata.clear();
        
        URI uri = new URI(path);
		streams = scraper.scrape(uri);
			
		for (int i = 0; i < streams.size(); i++) {
			if (streams.get(i).getUri().toString().contains(uri.toString())) {
				parseMetadata(streams.get(i).getCurrentSong());
			}
		}
	}
    
    /**
     * Parses a string of metadata.
     * 
     * @param metaString The metadata to parse.
     */
	private void parseMetadata(String metadata) {		
		// check if the stream title contain a "-" character. This is usually done
		// to indicate "artist - title". If not, don't try to parse up the string
		// just store it
		if (metadata.indexOf("-") != -1) {
			mMetadata.put(METADATA_KEY_ARTIST, metadata.substring(0, metadata.indexOf("-")).trim());
			mMetadata.put(METADATA_KEY_TITLE, metadata.substring(metadata.indexOf("-") + 1).trim());
		} else {
			mMetadata.put(METADATA_KEY_ARTIST, metadata.trim());
			mMetadata.put(METADATA_KEY_TITLE, "");
		}
	}
    
    /**
     * Call this method after setDataSource(). This method retrieves the 
     * meta data value associated with the keyCode.
     * 
     * The keyCode currently supported is listed below as METADATA_XXX
     * constants. With any other value, it returns a null pointer.
     * 
     * @param key One of the constants listed below at the end of the class.
     * @return The meta data value associate with the given keyCode on success; 
     * null on failure.
     */
    public String extractMetadata(String key) {
        return mMetadata.get(key);
    }

    /**
     * The metadata key to retrieve the main creator of the work.
     */
    public static final String METADATA_KEY_ARTIST = "artist";
    /**
     * The metadata key to retrieve the name of the work.
     */
    public static final String METADATA_KEY_TITLE = "title";
}
