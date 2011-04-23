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

import java.util.ArrayList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import javax.net.ssl.HttpsURLConnection;

import net.sourceforge.servestream.dbutils.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import android.util.Log;

public class StreamParser {
	
	URL m_targetURL = null;
	URL m_indexURL = null;
	String mPath = null;
	ArrayList<Stream> parsedURLs = null;
    
	/**
	 * Default constructor
	 */
	public StreamParser(URL url) throws MalformedURLException {
		m_targetURL = url;
		m_indexURL = getHost(m_targetURL);

		mPath = m_targetURL.getPath();
		Log.v("POOP ", mPath);
		
		parsedURLs = new ArrayList<Stream>();
	}
    
    public void getListing() {
    	
        int linkCount = 0;
		HttpURLConnection conn = null;
        String html = null;
        String line = null;
        BufferedReader reader = null;;
        
        try {
        	
        	if (m_targetURL.getProtocol().equals("http")) {
        		conn = (HttpURLConnection) m_targetURL.openConnection();
        	} else if (m_targetURL.getProtocol().equals("https")) {
        		conn = (HttpsURLConnection) m_targetURL.openConnection();        		
        	}
        	
    		conn.setConnectTimeout(6000);
    		conn.setReadTimeout(6000);
		    conn.setRequestMethod("GET");
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
		    
		    while ((line = reader.readLine()) != null) {
                html = html + line;
            }
            
		    Document doc = Jsoup.parse(html);
		    Elements links = doc.select("a");

		    for (int i = 0; i < links.size(); i++) {
		    	Stream stream = null;
		    	
		    	try {
		    		
		    		String link = links.get(i).attr("href");
		    		int pathLength = link.length();
		    		if (pathLength >= 1 && !link.substring(0, 1).equals("/")) {
		    			if (mPath.length() >= 1 && !mPath.substring(mPath.length() - 1).equals("/")) {
		    			    link = mPath + "/" + link;
		    			} else {	
		    			    link = mPath + link;
		    			}
		    		}
		    		
			        stream = new Stream(URLDecoder.decode(m_indexURL + link, "UTF-8"));
			    	stream.setNickname(links.get(i).text());
		    	
			    	stream.setContentType(URLUtils.getContentType(stream.getPath()));
		    	
			    	parsedURLs.add(linkCount, stream);
			        linkCount++;
		    	} catch (MalformedURLException ex) {
		    		ex.printStackTrace();
		    		Log.v("StreamParser", "BAD URL");
		    	}
		    }		    

        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
        	closeReader(reader);
        	closeHttpConnection(conn);
        }
    }
    
    /**
     * 
     */
    private URL getHost(URL targetURL) throws MalformedURLException {
    	return new URL(targetURL.getProtocol() + 
    			"://" + targetURL.getHost() + 
    			":" + String.valueOf(targetURL.getPort()));
    }
    
    public Stream getParsedURL(Integer index) {
    	return parsedURLs.get(index);
    }
    
    public ArrayList<Stream> getParsedURLs() {
    	return parsedURLs;
    }
    
	/**
	 * Closes a BufferedReader
	 * 
	 * @param reader The reader to close
	 */
    private void closeReader(BufferedReader reader) {
    	
    	if (reader == null)
    		return;

    	try {
    	    reader.close();
    	} catch (IOException ex) {
    		
    	}
    }
    
	/**
	 * Closes a HttpURLConnection
	 * 
	 * @param conn The connection to close
	 */
    private void closeHttpConnection(HttpURLConnection conn) {
    	
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
	
}
