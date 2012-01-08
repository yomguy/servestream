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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import android.os.Environment;

public class Utils {
    
	private static final String DOWNLOAD_DIRECTORY_NAME = "ServeStream";
	
    public static File getDownloadDirectory() {
        File file = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIRECTORY_NAME);
        
        if (!file.exists() && !file.mkdirs())
        	return null;
        	
    	return file;
    }

    public static boolean deleteAllFiles() {
    	boolean success = true;
    	
    	File file = getDownloadDirectory();
    	File [] files = file.listFiles();
    	
        for (int i = 0; i < files.length; i++) {
        	if (!deleteFile(files[i]));
        		success = false;
        }
        
        return success;
    }
    
    public static boolean deleteFile(File file) {
        if (file != null && file.exists()) {
            return file.delete();
        }
        
        return false;
    }
    
    public static void copyFile(File fromFile, File toFile) {
    	InputStream in = null;
    	OutputStream out = null;
    	
    	if (fromFile == null || toFile == null)
    		return;
    	
    	File tempFile = new File(toFile.getPath() + ".tmp");
    
    	try {
    		
    		in = new FileInputStream(fromFile);
    		out = new FileOutputStream(tempFile);

    		byte[] buf = new byte[1024];
    		int len;
    		while ((len = in.read(buf)) != -1){
    			out.write(buf, 0, len);
    		}
    		out.close();
            tempFile.renameTo(toFile);    		
    	} catch (IOException e) {
			e.printStackTrace();
		} finally {
    		Utils.closeInputStream(in);
    		Utils.closeOutputStream(out);
    		deleteFile(tempFile);
    	}
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
	 * Closes a OutputStream
	 * 
	 * @param conn A OutputStream to close
	 */
    public static void closeOutputStream(OutputStream outputStream) {
    	if (outputStream == null)
    		return;
    	
    	try {
			outputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
}
