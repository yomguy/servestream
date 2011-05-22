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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

public class ASXPlaylistParser {
	public final static String TAG = ASXPlaylistParser.class.getName();

	private URL playlistURL = null;
    private ArrayList<MediaFile> playlistFiles = null;
    private MediaFile [] mPlayListFiles = null;
    private int numberOfFiles = 0;
    
	/**
	 * Default constructor
	 */
	public ASXPlaylistParser(URL playlistURL) {
		this.playlistURL = playlistURL;
		this.playlistFiles = new ArrayList<MediaFile>();
	}
	
	/**
	 * Retrieves the files listed in a .asx file
	 */
    public void retrieveASXFiles() {
    	
    	if (playlistURL == null)
    		return;
    	
		HttpURLConnection conn = null;
		String xml = "";
        String line = null;
        BufferedReader reader = null;;
        
        try {
        	
        	if (playlistURL.getProtocol().equalsIgnoreCase("http")) {
        		conn = (HttpURLConnection) playlistURL.openConnection();
        	} else if (playlistURL.getProtocol().equalsIgnoreCase("https")) {
        		conn = (HttpsURLConnection) playlistURL.openConnection();        		
        	}
       
        	conn.setRequestProperty("User-Agent", URLUtils.USER_AGENT);
		    conn.setRequestMethod("GET");
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
		    
		    while ((line = reader.readLine()) != null) {
		    	xml = xml + line; 
            }
		    
		    parseXML(xml);

		    mPlayListFiles = new MediaFile[playlistFiles.size()];
		    
		    for (int i = 0; i < playlistFiles.size(); i++) {
		    	mPlayListFiles[i] = playlistFiles.get(i);
		    }
		    
        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
        	closeReader(reader);
        	closeHttpConnection(conn);
        }
    }

	private void parseXML(String xml) {
		
		SAXBuilder builder = new SAXBuilder();
	    Reader in = new StringReader(xml);
	    Document doc = null;
	    Element root = null;
	    
	    try {
	    	doc = builder.build(in);
	    	root = doc.getRootElement();
	    	List<Element> children = root.getChildren();
	    	
	    	for (int i = 0; i < children.size(); i++) {
	    		String tag = children.get(i).getName();
	    		
	    		if (tag != null && tag.equalsIgnoreCase("entry")) {
	    			buildPlaylistEntry(children.get(i).getChildren());
	    		}
	    	}

	    } catch (JDOMException e) {
	    } catch (IOException e) {
	    } catch (Exception e) {
	    
	    }
	}
    
    private void buildPlaylistEntry(List<Element> children) {
    	
    	MediaFile mediaFile = new MediaFile();
    	
    	for(int i = 0; i < children.size(); i++) {
    	    String attributeName = children.get(i).getName();
    		
    	    if (attributeName.equalsIgnoreCase("ABSTRACT")) {
    	    } else if (attributeName.equalsIgnoreCase("AUTHOR")) {
    	    } else if (attributeName.equalsIgnoreCase("BASE")) {
    	    } else if (attributeName.equalsIgnoreCase("COPYRIGHT")) {
    	    } else if (attributeName.equalsIgnoreCase("DURATION")) {
    	    } else if (attributeName.equalsIgnoreCase("ENDMARKER")) {
    	    } else if (attributeName.equalsIgnoreCase("MOREINFO")) {
    	    } else if (attributeName.equalsIgnoreCase("PARAM")) {
    	    } else if (attributeName.equalsIgnoreCase("REF")) {
    	    	String href = children.get(i).getAttributeValue("href");
    	    	
    	    	if (href == null) {
    	    	    href = children.get(i).getValue();
    	    	}
    	    	
    	    	System.out.println(href);
    	    	mediaFile.setURL(href);
    	    } else if (attributeName.equalsIgnoreCase("STARTMARKER")) {
    	    } else if (attributeName.equalsIgnoreCase("STARTTIME")) {
    	    } else if (attributeName.equalsIgnoreCase("TITLE")) {
    	    	String title = children.get(i).getValue();
    	    	
    	    	if (title != null) {
    	    		System.out.println(title);
    	    		mediaFile.setTitle(title);
    	    	}
    	    }
    	}
    	
    	numberOfFiles = numberOfFiles + 1;
    	mediaFile.setTrackNumber(numberOfFiles);
    	playlistFiles.add(mediaFile);
    }
	
    public MediaFile [] getPlaylistFiles() {
    	return mPlayListFiles;
    }
    
	/**
	 * @return the numberOfFiles
	 */
	public int getNumberOfFiles() {
		return numberOfFiles;
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

