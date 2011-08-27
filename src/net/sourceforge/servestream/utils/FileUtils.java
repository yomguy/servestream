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

import java.io.File;

import android.os.Environment;

public class FileUtils {

	private static final String DOWNLOAD_DIRECTORY_NAME = "servestream";
	
    public static File getDownloadDirectory() {
        File file = new File(Environment.getExternalStorageDirectory(), DOWNLOAD_DIRECTORY_NAME);
        
        if (!file.exists() && !file.mkdirs())
        	return null;
        	
    	return file;
    }
    
    public static boolean deleteFile(File file) {
        if (file != null && file.exists()) {
            return file.delete();
        }
        
        return false;
    }
}
