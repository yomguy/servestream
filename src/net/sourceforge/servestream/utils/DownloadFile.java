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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadFile {
	static final String TAG = DownloadFile.class.getName();
	
	private URL mUrl = null;
	private File mPartialFile = null;
	private File mCompleteFile = null;
	private DownloadTask downloadTask;
	private boolean mCancelled = false;
	
	public DownloadFile(URL url) {        
		mUrl = url;
        mPartialFile = new File(FileUtils.getDownloadDirectory(), "mediaFile.partial.dat");
		delete();
        mCompleteFile = new File(FileUtils.getDownloadDirectory(), "mediaFile.complete.dat");
        
        downloadTask = new DownloadTask();
    }
	
	public void startDownload() {
        downloadTask.start();
	}

	public void cancelDownload() {
		mCancelled = true;
	}
	
	public File getPartialFile() {
		return mPartialFile;
	}
	
	public File getCompleteFile() {
		return mCompleteFile;
	}
	
	public void delete() {
		FileUtils.deleteFile(mPartialFile);
	}
	
	private class DownloadTask extends Thread {

        public void run() {
        	HttpURLConnection conn = null;
        	BufferedInputStream in = null;
            FileOutputStream out = null;
            boolean appendToFile = false;
            
            byte[] buffer = new byte[1024 * 16];
            long count = 0;
            
            conn = determineRange(mUrl, mPartialFile.length());
            
            try {            	
            	if (conn.getResponseCode() == HttpURLConnection.HTTP_PARTIAL)
            		appendToFile = true;            		
            
				in = new BufferedInputStream(conn.getInputStream());
	            out = new FileOutputStream(mPartialFile, appendToFile);

				int i;
				while (!mCancelled && (i = in.read(buffer)) != -1) {
	                out.write(buffer, 0, i);
	                count += i;
	            }
				
                out.flush();
                out.close();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				Utils.closeHttpConnection(conn);
			}
        }
	}
	
	private HttpURLConnection determineRange(URL url, long bytesProcessed) {
		HttpURLConnection conn = null;
		
		conn = URLUtils.getConnection(url);
		
		if (conn == null)
			return null;
		
		conn.setConnectTimeout(6000);
		conn.setReadTimeout(6000);
		conn.setRequestProperty("Range", "bytes=" + bytesProcessed + "-");
		
		try {
			conn.setRequestMethod("GET");
			conn.connect();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return conn;
	}
}
