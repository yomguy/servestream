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

import net.sourceforge.servestream.R;
import android.content.Context;
import android.os.Environment;
import android.util.TypedValue;

public class Utils {
    
	private static final String DOWNLOAD_DIRECTORY_NAME = "ServeStream";
	
    public static File getDownloadDirectory() {
        File file = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIRECTORY_NAME);
        
        if (!file.exists() && !file.mkdirs()) {
        	return null;
        }
        	
    	return file;
    }

    public static boolean deleteAllFiles() {
    	boolean success = true;
    	
    	File file = getDownloadDirectory();
    	
    	if (file == null) {
    		return false;
    	}
    	
    	File [] files = file.listFiles();
    	
        for (int i = 0; i < files.length; i++) {
        	if (!deleteFile(files[i])) {
        		success = false;
        	}
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
     * Gets the extension of a filename.
     * <p>
     * This method returns the textual part of the filename after the last dot.
     * There must be no directory separator after the dot.
     * <pre>
     * foo.txt      --> "txt"
     * a/b/c.jpg    --> "jpg"
     * a/b.txt/c    --> ""
     * a/b/c        --> ""
     * </pre>
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     *
     * @param filename the filename to retrieve the extension of.
     * @return the extension of the file or an empty string if none exists or {@code null}
     * if the filename is {@code null}.
     */
	public static String getExtension(String filename) {
		if (filename == null) {
			return null;
		}
		
		int index = indexOfExtension(filename);
		
		if (index == -1) {
			return "";
		} else {
			return filename.substring(index + 1);
		}
	}
	
	/**
     * Returns the index of the last extension separator character, which is a dot.
     * <p>
     * This method also checks that there is no directory separator after the last dot.
     * To do this it uses {@link #indexOfLastSeparator(String)} which will
     * handle a file in either Unix or Windows format.
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     * 
     * @param filename  the filename to find the last path separator in, null returns -1
     * @return the index of the last separator character, or -1 if there
     * is no such character
     */
	private static int indexOfExtension(String filename) {
		if (filename == null) {
			return -1;
		}
		
		int extensionPos = filename.lastIndexOf('.');
		int lastSeparator = indexOfLastSeparator(filename);
		return lastSeparator > extensionPos ? -1 : extensionPos;
	}
	
	/**
     * Returns the index of the last extension separator character, which is a dot.
     * <p>
     * This method also checks that there is no directory separator after the last dot.
     * To do this it uses {@link #indexOfLastSeparator(String)} which will
     * handle a file in either Unix or Windows format.
     * <p>
     * The output will be the same irrespective of the machine that the code is running on.
     * 
     * @param filename  the filename to find the last path separator in, null returns -1
     * @return the index of the last separator character, or -1 if there
     * is no such character
     */
	private static int indexOfLastSeparator(String filename) {
		if (filename == null) {
			return -1;
		}
		
		int lastUnixPos = filename.lastIndexOf('/');
		int lastWindowsPos = filename.lastIndexOf('\\');
		return Math.max(lastUnixPos, lastWindowsPos);
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
    
    public static int getThemedIcon(Context context, int resid) {
    	TypedValue typedvalueattr = new TypedValue();
    	context.getTheme().resolveAttribute(resid, typedvalueattr, true);
    	return typedvalueattr.resourceId;
    }
}
