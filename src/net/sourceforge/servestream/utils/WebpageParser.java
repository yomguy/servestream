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
import java.util.List;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.transport.TransportFactory;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import android.net.Uri;

public class WebpageParser {
	//private static final String TAG = WebpageParser.class.getName();
	
	private static final String REQUEST_METHOD = "GET";
	
	private URL mURL = null;
	private List<UriBean> mParsedLinks = null;
    
	/**
	 * Default constructor
	 */
	public WebpageParser(URL url) {
		mURL = url;
		mParsedLinks = new ArrayList<UriBean>();
	}
    
    public void parse() {
        int linkCount = 0;
		HttpURLConnection conn = null;
        StringBuffer html = new StringBuffer();
        String line = null;
        BufferedReader reader = null;
        String link = null;
        
        try {
        	if (mURL == null) {
        		return;
        	}
        	
    		if (mURL.getProtocol().equalsIgnoreCase("http")) {
    			conn = (HttpURLConnection) mURL.openConnection();
    		} else if (mURL.getProtocol().equalsIgnoreCase("https")) {
    			conn = (HttpsURLConnection) mURL.openConnection();        		
    		}
    	
    		conn.setRequestProperty("User-Agent", URLUtils.USER_AGENT);
    		conn.setConnectTimeout(6000);
    		conn.setReadTimeout(6000);
		    conn.setRequestMethod(REQUEST_METHOD);
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
		    
		    while ((line = reader.readLine()) != null) {
		    	html = html.append(line);
            }
            
		    Document doc = Jsoup.parse(html.toString());
		    Elements links = doc.select("a[href]");

		    for (int i = 0; i < links.size(); i++) {
		    	UriBean uriBean = null;
		    	
		    	links.get(i).setBaseUri(mURL.toString());
		    	link = links.get(i).attr("abs:href");

		    	Uri uri = TransportFactory.getUri(link);

		    	if (uri != null) {
			    	uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
			    	uriBean.setNickname(links.get(i).text());
			    	uriBean.setContentType(URLUtils.getContentType(link));
		    	
			    	mParsedLinks.add(linkCount, uriBean);
			    	linkCount++;
		    	}
		    }
        } catch (Exception ex) {
        } finally {
        	Utils.closeBufferedReader(reader);
        	Utils.closeHttpConnection(conn);
        }
    }
    
    /**
     * Method to return a specific link from the list of parsed links
     * 
     * @param index The position of a link object in the list
     * @return Stream A link from the list of parsed links
     */
    public UriBean getParsedLinks(int index) {
    	return mParsedLinks.get(index);
    }
    
    /**
     * Method to return a list of parsed links
     * 
     * @return ArrayList List of parsed links
     */
    public List<UriBean> getParsedLinks() {
    	return mParsedLinks;
    }
}
