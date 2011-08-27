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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Formatter;
import java.util.Locale;

import net.sourceforge.servestream.R;

import android.content.Context;

public class Utils {

    /*  Try to use String.format() as little as possible, because it creates a
     *  new Formatter every time you call it, which is very inefficient.
     *  Reusing an existing Formatter more than tripled the speed of
     *  makeTimeString().
     *  This Formatter/StringBuilder are also used by makeAlbumSongsLabel()
     */
    public static String makeTimeString(Context context, long secs) {
        StringBuilder sFormatBuilder = new StringBuilder();
        Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());
        final Object[] sTimeArgs = new Object[5];
        
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
    
    /**
	 * Closes a BufferedReader
	 * 
	 * @param reader A BufferedReader to close
	 */
    public static void closeBufferedReader(BufferedReader bufferedReader) {  	
    	if (bufferedReader == null)
    		return;

    	try {
    		bufferedReader.close();
    	} catch (IOException ex) {
    		
    	}
    }
    
	/**
	 * Closes a HttpURLConnection
	 * 
	 * @param conn A HttpURLConnection to close
	 */
    public static void closeHttpConnection(HttpURLConnection conn) {
    	if (conn == null)
    		return;
    	
    	conn.disconnect();
    }
    
	/**
	 * Closes a InputStream
	 * 
	 * @param conn A InputStream to close
	 */
    public static void closeInputStream(InputStream inputStream) {
    	if (inputStream == null)
    		return;
    	
    	try {
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
