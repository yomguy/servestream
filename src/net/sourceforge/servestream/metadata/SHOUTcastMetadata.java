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

/*
 * This code is a modified version of the code from:
 * http://uniqueculture.net/2010/11/stream-metadata-plain-java/
 */

package net.sourceforge.servestream.metadata;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import net.sourceforge.servestream.utils.Utils;

public class SHOUTcastMetadata {
	private static final String TAG = SHOUTcastMetadata.class.getName();
	
	public static final String ARTIST = "artist";
	public static final String TITLE = "title";  
	
	private static final String STREAM_TITLE = "StreamTitle";
	private static final String STREAM_TITLE_REPLAY = "StreamTitleReplay";
	
    /**
     * A map of all metadata attributes.
     */
    private Map<String, String> mMetadata = null;
    
	private URL mUrl = null;
	private boolean mContainsMetadata = false;
	
	private Handler mHandler = null;
	private int mWhat = -1;
  
	private final AtomicReference<Thread> mThread = new AtomicReference<Thread>();
	
    /**
     * Default constructor
     * 
     * @param url The url to extract SHOUTcast metadata from
     */
    public SHOUTcastMetadata(URL url) {
    	mUrl = url;
    	mMetadata = new HashMap<String, String>();
    }
  
    /**
     * Default constructor
     * 
     * @param url The url to extract SHOUTcast metadata from
     */
    public SHOUTcastMetadata(URL url, Handler handler, int what) {
    	mUrl = url;
    	mHandler = handler;
    	mWhat = what;
    	mMetadata = new HashMap<String, String>();
    }
    
    /**
     * Refreshes the SHOUTcast metadata
     */
    public void refreshMetadata() {
    	retrieveMetadata();
    }

    /**
     * Establishes a connection to the specified Url and, if available, obtains and
     * parses the SHOUTcast metadata returned
     */
    private void retrieveMetadata() {
    	int metaDataOffset = 0;
    	HttpURLConnection conn = null;
    	InputStream stream = null;
	  
    	try {
        	conn = (HttpURLConnection) mUrl.openConnection();
      
    	    conn.setRequestProperty("Icy-MetaData", "1");
    	    conn.setRequestProperty("Connection", "close");
    	    conn.setRequestProperty("Accept", null);
    		conn.setConnectTimeout(6000);
    		conn.setReadTimeout(6000);
    	    conn.connect();
		
    	    Map<String, List<String>> headers = conn.getHeaderFields();
    	    stream = conn.getInputStream();

    	    if (headers.containsKey("icy-metaint")) {
    	    	metaDataOffset = Integer.parseInt(headers.get("icy-metaint").get(0));
    	    } else {
    	    	StringBuffer strHeaders = new StringBuffer();
    	    	char c;
    	    	while ((c = (char)stream.read()) != -1) {
    	    		strHeaders.append(c);
    	    		if (strHeaders.length() > 5 && (strHeaders.substring((strHeaders.length() - 4), strHeaders.length()).equals("\r\n\r\n"))) {
    	    			// end of headers
    	    			break;
    	    		}
    		    }

    		    // Match headers to get metadata offset within a stream
    		    Pattern p = Pattern.compile("\\r\\n(icy-metaint):\\s*(.*)\\r\\n");
    		    Matcher m = p.matcher(strHeaders.toString());
    		    if (m.find()) {
    		    	metaDataOffset = Integer.parseInt(m.group(2));
    		    }
    	    }

    	    // In case no data was sent
    	    if (metaDataOffset == 0) {
			    return;
    	    }

	    	mContainsMetadata = true;
	    	
		    // Read metadata
		    int b;
		    int count = 0;
		    int metaDataLength = 4080; // 4080 is the max length
		    boolean inData = false;
		    StringBuilder metaData = new StringBuilder();
		    // Stream position should be either at the beginning or right after headers
		    // Read the data stream as you normally would, keeping a byte count as you go. 
		    // When the number of bytes equals the metadata interval, you will get a metadata 
		    // block. The first part of the block is a length specifier, which is the next 
		    // byte in the stream. This byte will equal the metadata length / 16. Multiply by 16
		    // to get the actual metadata length. (Max byte size = 255 so metadata max length = 4080.)
		    // Now read that many bytes and you will have a string containing the metadata. Restart your
		    // byte count, and repeat. Success!
		    while ((b = stream.read()) != -1) {
		    	count++;

			    // Length of the metadata
			    if (count == metaDataOffset + 1) {
			    	metaDataLength = b * 16;
			    }

			    if (count > metaDataOffset + 1 && count < (metaDataOffset + metaDataLength)) { 				
			    	inData = true;
			    } else { 				
			    	inData = false; 			
			    } 	 			
			    if (inData) { 				
			    	if (b != 0) { 					
			    		metaData.append((char)b); 				
				    } 			
			    } 	 			
			    if (count > (metaDataOffset + metaDataLength)) {
			    	break;
			    }

		    }

		    // parse the returned metadata
		    parseMetadata(metaData.toString());
        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
			Utils.closeInputStream(stream);
		    Utils.closeHttpConnection(conn);
        }
    }

    /**
     * Parses the metadata returned
     * 
     * @param metaString The metadata to parse
     * @return 
     */
	private void parseMetadata(String metadataString) {
		String streamTitle = null;
		Map<String, String> metadata = new HashMap<String, String>();
		String[] metaParts = metadataString.split(";");
		Pattern p = Pattern.compile("^([a-zA-Z]+)=\\'([^\\']*)\\'$");
		Matcher m;
		
		for (int i = 0; i < metaParts.length; i++) {
			m = p.matcher(metaParts[i]);
			if (m.find()) {
				metadata.put((String)m.group(1), (String)m.group(2));				
			}
		}

		streamTitle = metadata.get(STREAM_TITLE);
		
		if (streamTitle == null) {
			streamTitle = metadata.get(STREAM_TITLE_REPLAY);
			
			if (streamTitle == null) {
				return;
			}	
		}
		
		try {
			add(ARTIST, streamTitle.substring(0, streamTitle.indexOf("-")).trim());
			add(TITLE, streamTitle.substring(streamTitle.indexOf("-") + 1).trim());
		} catch (Exception ex) {
		}
	}
	
    /**
     * Get the value associated to a metadata name. If many values are assiociated
     * to the specified name, then the first one is returned.
     * 
     * @param name
     *          of the metadata.
     * @return the value associated to the specified metadata name.
     */
    public String get(final String name) {
        String value = mMetadata.get(name);
        if (value == null) {
            return "Unknown";
        } else {
            return value;
        }
    }
  	
    /**
     * Add a metadata name/value mapping. Add the specified value to the list of
     * values associated to the specified metadata name.
     * 
     * @param name
     *          the metadata name.
     * @param value
     *          the metadata value.
     */
    public void add(final String name, final String value) {
    	if (value == null) {
    		mMetadata.put(name, "Unknown");
    	} else {
    		mMetadata.put(name, value);
    	}
    }
    
    /**
     * Returns the number of metadata names in this metadata.
     * 
     * @return number of metadata names
     */
    public int size() {
        return mMetadata.size();
    }
    
    /**
     * Method to check of the url return SHOUTcast metadata
     * 
     * @return true of the url returns SHOUTcast metadata, false otherwise
     */
    public boolean containsMetadata() {
    	return mContainsMetadata;
    }

    public void start() {
    	mThread.set(new Thread() {
    		public void run() {
    			int retries = 0;
    			boolean metadataFound = false;
    	
    			//running.set(true);
    			Log.d(TAG, "Starting thread");
    			try {
    				while (retries < 5 && !metadataFound) {
    					refreshMetadata();
    					metadataFound = containsMetadata();
    					retries++;
    					
    					// sleep 2 seconds before retrying
    					if (!metadataFound) {
    						sleep(2000);
    					}
    				}
    				
    				if (metadataFound) {
    					Message msg = null;							
    					msg = mHandler.obtainMessage(mWhat);
    					msg.obj = SHOUTcastMetadata.this;
    					msg.sendToTarget();
    				}
    			} catch (InterruptedException ex) {
    				ex.printStackTrace();
    			} finally {
    				//	running.set(false);
    				Log.d(TAG, "Stopping thread");
    			}
    		}
    	});
    	
    	mThread.get().start();
    }
    
}