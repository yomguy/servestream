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

/*
 * This code is a modified version of the code from:
 * http://uniqueculture.net/2010/11/stream-metadata-plain-java/
 */

package net.sourceforge.servestream.media;

import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.Utils;

public class SHOUTcastMetadata extends BroadcastReceiver {
	private static final String TAG = SHOUTcastMetadata.class.getName();
	
    private static final int POLLING_FREQUENCY = 10000;
	
	private static final String ARTIST = "artist";
	private static final String TITLE = "title";  
	private static final String STREAM_TITLE = "StreamTitle";
	private static final String STREAM_TITLE_REPLAY = "StreamTitleReplay";
	
	private final MediaPlaybackService mMediaPlaybackService;
	private boolean mRetrieveSHOUTcastMetadata;
    private Map<String, String> mMetadata = null;
    
    private long mId = -1;
	private UriBean mUri = null;
	private boolean mContainsMetadata = false;	
	
	private PollingAsyncTask mPollingAsyncTask = null;
	
	private Object[] mLock = new Object[0];
	
    /**
     * Default constructor
     * 
     * @param url The url to extract SHOUTcast metadata from.
     */
	public SHOUTcastMetadata(MediaPlaybackService mediaPlaybackService, boolean retrieveSHOUTcastMetadata) {
		mMediaPlaybackService = mediaPlaybackService;
		mRetrieveSHOUTcastMetadata = retrieveSHOUTcastMetadata;
		mMetadata = new HashMap<String, String>();
		
		IntentFilter filter = new IntentFilter();
		filter.addAction(MediaPlaybackService.PLAYBACK_STARTED);
		filter.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
		filter.addAction(MediaPlaybackService.PLAYBACK_COMPLETE);		
		mediaPlaybackService.registerReceiver(this, filter);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		final String action = intent.getAction();

		if (!mRetrieveSHOUTcastMetadata ||
				(!action.equals(MediaPlaybackService.PLAYBACK_STARTED) &&
				!action.equals(MediaPlaybackService.PLAYSTATE_CHANGED) &&
				!action.equals(MediaPlaybackService.PLAYBACK_COMPLETE))) {
           Log.w(TAG, "onReceived() called: " + intent);
           return;
		}

		long id = intent.getLongExtra("id", -1);
		
		if (action.equals(MediaPlaybackService.PLAYBACK_STARTED)) {
			changeUrl(id);
			start();
		} else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
			boolean isPlaying = intent.getBooleanExtra("playing", false);
			
			if (id != mId) {
				return;
			}
			
        	if (isPlaying) {
        		// If the url does not contain metadata so don't try
        		// to start another thread
        		if (!mContainsMetadata) {
        			Log.v(TAG, "Not starting thread because URL doesn't have metadata");
        			return;
        		}
        			   
        		start();
        	} else {
        		cancel();
        	}
		} else if (action.equals(MediaPlaybackService.PLAYBACK_COMPLETE)) {
			cancel();
		}
	}
	
	/**
	 * Retrieves the corresponding URI of id and creates a URL using that value.
	 * 
	 * @param id The id to generate a URL for.
	 */
	private void changeUrl(long id) {
		Uri uri = TransportFactory.getUri(getUri(mMediaPlaybackService, id));

		if (uri == null) {
			return;
		}

		UriBean uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
		
		if (uriBean != null) {
			mUri = uriBean;
			mId = id;
		}
	}
    
    /**
     * Establishes a connection to the specified Url and, if available, obtains and
     * parses the SHOUTcast metadata returned.
     */
    private void retrieveMetadata() {
    	URL url = null;
    	int metaDataOffset = 0;
    	HttpURLConnection conn = null;
    	InputStream stream = null;
    	int bytesRead = 0;
    	mContainsMetadata = false;
	  
    	if (mUri == null) {
    		return;
    	}
    	
    	try {
        	final String username = mUri.getUsername();
        	final String password = mUri.getPassword();
        		
        	if (username != null && password != null) {
        		Authenticator.setDefault(new Authenticator() {
        			protected PasswordAuthentication getPasswordAuthentication() {
        				return new PasswordAuthentication(username, password.toCharArray()); 
        			};
        		});
            	
        		url = mUri.getScrubbedURL();
        	} else {
        		url = mUri.getURL();
        	}
    	
    		if (url.getProtocol().equalsIgnoreCase("http")) {
    			conn = (HttpURLConnection) url.openConnection();
    		} else if (url.getProtocol().equalsIgnoreCase("https")) {
    			conn = (HttpsURLConnection) url.openConnection();        		
    		}
    	
    		conn.setRequestProperty("User-Agent", "ServeStream");
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
    	    		bytesRead++;
    	    		strHeaders.append(c);
    	    		if ((strHeaders.length() > 5 && (strHeaders.substring((strHeaders.length() - 4), strHeaders.length()).equals("\r\n\r\n")) || bytesRead > 200)) {
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
     * Parses a string of SHOUTcast metadata.
     * 
     * @param metaString The metadata to parse.
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
		
		if (streamTitle == null || streamTitle.trim().equals("")) {
			streamTitle = metadata.get(STREAM_TITLE_REPLAY);
			
			if (streamTitle == null || streamTitle.trim().equals("")) {
				return;
			}	
		}
		
		// check if the stream title contain a "-" character. This is usually done
		// to indicate "artist - title". If not, don't try to parse up the string
		// just store it
		if (streamTitle.indexOf("-") != -1) {
			add(ARTIST, streamTitle.substring(0, streamTitle.indexOf("-")).trim());
			add(TITLE, streamTitle.substring(streamTitle.indexOf("-") + 1).trim());
		} else {
			add(ARTIST, streamTitle.trim());
			add(TITLE, "");
		}
		
		mContainsMetadata = true;
	}
	
    /**
     * Get the value associated to a metadata name. If many values are assiociated
     * to the specified name, then the first one is returned.
     * 
     * @param name Name of the metadata.
     * @return the value associated to the specified metadata name.
     */
    private String get(final String name) {
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
     * @param name The metadata name.
     * @param value The metadata value.
     */
    private void add(final String name, final String value) {
    	if (value == null) {
    		mMetadata.put(name, "Unknown");
    	} else {
    		mMetadata.put(name, value);
    	}
    }
    
    private void start() {
    	if (mPollingAsyncTask != null) {
    		cancel();
    	}
    	
    	mPollingAsyncTask = new PollingAsyncTask();
    	mPollingAsyncTask.execute();
    }

    private void cancel() {
    	if (mPollingAsyncTask != null) {
        	PollingAsyncTask pollingAsyncTask = mPollingAsyncTask;
        	pollingAsyncTask.cancel();
        	mPollingAsyncTask = null;
    	}
    }
    
	/**
	 * Sets if metadata should be retrieved and starts or stops the polling thread.
	 * 
	 * @param retrieveMetadata True if metadata should be retrieved, false otherwise.
	 */
	public void setShouldRetrieveMetadata(boolean retrieveMetadata) {
		synchronized (mLock) {
			mRetrieveSHOUTcastMetadata = retrieveMetadata;

			if (mRetrieveSHOUTcastMetadata) {
				long id = mMediaPlaybackService.getAudioId();
				changeUrl(id);
				start();
			} else {
				cancel();
			}
		}
	}
    
	/**
	 * Cancels any running polling task and unregisters the receiver.
	 */
	public void cleanup() {
		cancel();		
		mMediaPlaybackService.unregisterReceiver(this);
	}
    
	private class PollingAsyncTask implements Runnable {
		
		private boolean mIsCancelled;
		private AsyncTask.Status mStatus;
		
	    public PollingAsyncTask() {
			mIsCancelled = false;
			mStatus = AsyncTask.Status.PENDING;
	    }
	    
		@Override
		public void run() {
			int retries = 0;
			boolean metadataFound = false;
	
			Log.v(TAG, "Starting polling thread");
			try {
				while (retries < 2 && !isCancelled()) {
					retrieveMetadata();
					metadataFound = mContainsMetadata;
					retries++;
					
					if (metadataFound) {
						retries = 0;
						updateMetadata();

						Log.v(TAG, "Metadata found");
						
						mMediaPlaybackService.updateMetadata();
					} else {
						Log.v(TAG, "Metadata not found");
					}
				
					Log.v(TAG, "Sleeping...");
					Thread.sleep(POLLING_FREQUENCY);
				}
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			} finally {
				Log.v(TAG, "Stopping polling thread");
			}
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
		
		public void execute() {
			new Thread(this, "").start();
		}
	}
    
	private String getUri(Context context, long id) {
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
    
	private int updateMetadata() {
		int rows = 0;
		
		// Form an array specifying which columns to return. 
		ContentValues values = new ContentValues();
		values.put(Media.MediaColumns.ARTIST, get(ARTIST));
		values.put(Media.MediaColumns.TITLE, get(TITLE));

		// Get the base URI for the Media Files table in the Media content provider.
		Uri mediaFile =  Media.MediaColumns.CONTENT_URI;

		// Execute the update.
		rows = mMediaPlaybackService.getContentResolver().update(mediaFile, 
				values, 
				Media.MediaColumns._ID + "= ? ", 
				new String [] { String.valueOf(mId) } );
	
		// return the number of rows updated.
		return rows;
	}
}