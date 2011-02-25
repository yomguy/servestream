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
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.Reader;
import java.io.StringReader;
import java.util.Formatter;
import java.util.Locale;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import net.sourceforge.servestream.R;

public class MusicUtils {

    //private static final String TAG = "ServeStream.MusicUtils";
    
    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    private static StringBuilder sFormatBuilder = new StringBuilder();
    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
    private static final Object[] sTimeArgs = new Object[5];

    public static String makeTimeString(Context context, long secs) {
        String durationformat = context.getString(
                secs < 3600 ? R.string.durationformatshort : R.string.durationformatlong);
        
        /* Provide multiple arguments so the format can be changed easily
         * by modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }
    
    public static String mediaFileToXML(MediaFile mediaFile) {
    	String XML;
    	XMLOutputter outputter = new XMLOutputter();
    	
    	Document doc = new Document(new Element("MediaFile"));
        doc.getRootElement().
          addContent(new Element("URL").
                setText(mediaFile.getURL()));
        doc.getRootElement().
          addContent(new Element("TrackNumber").
                setText(String.valueOf(mediaFile.getTrackNumber())));
        doc.getRootElement().
        addContent(new Element("Title").
              setText(String.valueOf(mediaFile.getTitle())));
        doc.getRootElement().
        addContent(new Element("Length").
              setText(String.valueOf(mediaFile.getLength())));
        
	    outputter.setFormat(Format.getCompactFormat().setOmitDeclaration(true));
	    XML = outputter.outputString(doc);
	    
        return XML;
    }
    
    public static MediaFile XMLToMediaFile(String XML) {
    	MediaFile mediaFile = new MediaFile();
    	
    	SAXBuilder builder = new SAXBuilder();
    	Reader in = new StringReader(XML);
    	//Document doc = null;
    	Element root = null;
    	Element url = null;
    	Element trackNumber = null;
    	Element title = null;
    	Element length = null;
    	
    	try {
    	    root = builder.build(in).getRootElement();
   	        System.out.println("Root: " + root.toString());
   	        url = root.getChild("URL");
   	        mediaFile.setURL(url.getText());
   	        System.out.println("URL: " + url.getText());
   	        trackNumber = root.getChild("TrackNumber");
   	        mediaFile.setTrackNumber(Integer.valueOf(trackNumber.getText()));
   	        System.out.println("Track Number: " + trackNumber.getText());
   	        title = root.getChild("Title");
   	        mediaFile.setTitle(title.getText());
   	        System.out.println("Title: " + title.getText());
   	        length = root.getChild("Length");
   	        mediaFile.setLength(Integer.valueOf(length.getText()));
   	        System.out.println("Length: " + length.getText());
    	} catch(Exception ex) {
    		ex.printStackTrace();
    	}
    	
    	return mediaFile;
    }
    
    public static int getCardId(Context context) {
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse("content://media/external/fs_id"), null, null, null, null);
        int id = -1;
        if (c != null) {
            c.moveToFirst();
            id = c.getInt(0);
            c.close();
        }
        return id;
    }
}
