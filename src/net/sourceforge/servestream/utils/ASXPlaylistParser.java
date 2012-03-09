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
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

public class ASXPlaylistParser extends PlaylistParser {
	//private final static String TAG = ASXPlaylistParser.class.getName();

	public final static String EXTENSION = "asx";
	public final static String MIME_TYPE = "video/x-ms-asf";
    
	private final String ABSTRACT_ELEMENT = "ABSTRACT";
	private final String ASX_ELEMENT = "ASX";
	private final String AUTHOR_ELEMENT = "AUTHOR";
	private final String BASE_ELEMENT = "BASE";
	private final String COPYRIGHT_ELEMENT = "COPYRIGHT";
	private final String DURATION_ELEMENT = "DURATION";
	private final String ENDMARKER_ELEMENT = "ENDMARKER";
	private final String ENTRY_ELEMENT = "ENTRY";
	private final String ENTRYREF_ELEMENT = "ENTRYREF";
	private final String EVENT_ELEMENT = "EVENT";
	private final String MOREINFO_ELEMENT = "MOREINFO";
	private final String PARAM_ELEMENT = "PARAM";
	private final String REF_ELEMENT = "REF";
	private final String REPEAT_ELEMENT = "REPEAT";
	private final String STARTMARKER_ELEMENT = "STARTMARKER";
	private final String STARTTIME_ELEMENT = "STARTTIME";
	private final String TITLE_ELEMENT = "TITLE";
	
	private final String HREF_ATTRIBUTE = "href";
	
	/**
	 * Default constructor
	 */
	public ASXPlaylistParser(URL playlistUrl) {
		super(playlistUrl);
	}
	
	/**
	 * Retrieves the files listed in a .asx file
	 */
    public void retrieveAndParsePlaylist() {
    	
    	if (mPlaylistUrl == null)
    		return;
    	
		HttpURLConnection conn = null;
		String xml = "";
        String line = null;
        BufferedReader reader = null;
        
        try { 	
        	conn = URLUtils.getConnection(mPlaylistUrl);
		    
		    // Start the query
		    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    conn.connect();
		    
		    while ((line = reader.readLine()) != null) {
		    	xml = xml + line; 
            }
		    
		    parseXML(xml);
        } catch (Exception ex) {
        	ex.printStackTrace();
        } finally {
        	Utils.closeBufferedReader(reader);
        	Utils.closeHttpConnection(conn);
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
	    	List<Element> children = castList(Element.class, root.getChildren());
	    	
	    	for (int i = 0; i < children.size(); i++) {
	    		String tag = children.get(i).getName();
	    		
	    		if (tag != null && tag.equalsIgnoreCase(ENTRY_ELEMENT)) {
	    			List<Element> children2 = castList(Element.class, children.get(i).getChildren());
	    			
	    			buildPlaylistEntry(children2);
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
    		
    	    if (attributeName.equalsIgnoreCase(ABSTRACT_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(ASX_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(AUTHOR_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(BASE_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(COPYRIGHT_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(DURATION_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(ENDMARKER_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(ENTRY_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(ENTRYREF_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(EVENT_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(MOREINFO_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(PARAM_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(REF_ELEMENT)) {
    	    	String href = children.get(i).getAttributeValue(HREF_ATTRIBUTE);
    	    	
    	    	if (href == null) {
        	    	href = children.get(i).getAttributeValue(HREF_ATTRIBUTE.toUpperCase());
    	    	}
    	    	
    	    	if (href == null) {
    	    	    href = children.get(i).getValue();
    	    	}
    	    	
    	    	try {
					mediaFile.setUrl(URLDecoder.decode(href, "UTF-8"));
				} catch (UnsupportedEncodingException e) {
					mediaFile.setUrl(href);
				}
    	    } else if (attributeName.equalsIgnoreCase(REPEAT_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(STARTMARKER_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(STARTTIME_ELEMENT)) {
    	    } else if (attributeName.equalsIgnoreCase(TITLE_ELEMENT)) {
    	    	String title = children.get(i).getValue();
    	    	
    	    	if (title != null) {
    	    		mediaFile.setPlaylistMetadata(title);
    	    	}
    	    }
    	}
    	
    	mNumberOfFiles = mNumberOfFiles + 1;
    	mediaFile.setTrack(mNumberOfFiles);
    	mPlaylistFiles.add(mediaFile);
    }
    
    private <T> List<T> castList(Class<? extends T> castClass, List<?> c) {
        List<T> list = new ArrayList<T>(c.size());
        
        for(Object o: c) {
        	list.add(castClass.cast(o));
        }
        
        return list;
    }
}

