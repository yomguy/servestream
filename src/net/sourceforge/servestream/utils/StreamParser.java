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
import java.util.HashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;

import javax.net.ssl.HttpsURLConnection;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class StreamParser {

	URL m_targetURL = null;
	URL m_indexURL = null;
    ArrayList<String> m_textLinks = null;
	HashMap<Integer, String> m_fileHrefs = null;
    
	/**
	 * Default constructor
	 */
	public StreamParser(String targetURL) throws MalformedURLException {
		m_targetURL = new URL(targetURL);
		m_indexURL = getHost(m_targetURL);

		m_textLinks = new ArrayList<String>();
		m_fileHrefs = new HashMap<Integer, String>();
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
        	
		    //conn = (HttpURLConnection) m_targetURL.openConnection();
		    conn.setRequestMethod("GET");
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
		    
		    while ((line = reader.readLine()) != null) {
                html = html + line;
            }
            
		    // add ".." to the list if came from a directory
            if (!(m_targetURL.getPath().equals("/") || m_targetURL.getPath().equals(""))) {
		        String directoryUpOneLevel = new File(m_targetURL.getPath().toString()).getParent();
		        m_textLinks.add("..");
		        m_fileHrefs.put(linkCount, m_indexURL + directoryUpOneLevel);
		        linkCount++;
            }
            
		    Document doc = Jsoup.parse(html);
		    Elements links = doc.select("a");

		    for (int i = 0; i < links.size(); i++) {
		    	m_textLinks.add(links.get(i).text());
		    	m_fileHrefs.put(linkCount, URLDecoder.decode(m_indexURL + links.get(i).attr("href"), "UTF-8"));
		        //m_fileHrefs.put(linkCount, m_indexURL + links.get(i).attr("href"));
		        linkCount++;
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
    
    /**
     * 
     */
    public ArrayList<String> getTextLinks() {
    	return m_textLinks;
    }
    
    /**
     * 
     */
    public String getHREF(Integer index) {
    	return m_fileHrefs.get(index);
    }
    
    private void closeReader(BufferedReader reader) {
    	
    	if (reader == null)
    		return;

    	try {
    	    reader.close();
    	} catch (IOException ex) {
    		
    	}
    }
    
    private void closeHttpConnection(HttpURLConnection conn) {
    	
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
	
}
