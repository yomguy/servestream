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

package net.sourceforge.servestream.filemanager;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.BrowserActivity;
import net.sourceforge.servestream.dbutils.Stream;
import net.sourceforge.servestream.utils.WebpageParser;
import net.sourceforge.servestream.utils.URLUtils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class DirectoryScanner extends Thread {
	private final static String TAG = DirectoryScanner.class.getName();
	
	private Stream currentDirectory;
	public boolean cancel;

	private Context context;
	private Handler handler;

	public DirectoryScanner(Stream directory, Context context, Handler handler) {
		super("Directory Scanner");
		currentDirectory = directory;
		this.context = context;
		this.handler = handler;
	}
	
	private void clearData() {
		// Remove all references so we don't delay the garbage collection.
		context = null;
		handler = null;
	}

	public void run() {
		Log.v(TAG, "Scanning directory " + currentDirectory);
		
		List<Stream> files = null;
		
		WebpageParser webpageParser;
		try {
			webpageParser = new WebpageParser(currentDirectory.getURL());
			webpageParser.parse();
			files = webpageParser.getParsedLinks();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		int totalCount = 0;
		
		if (cancel) {
			Log.v(TAG, "Scan aborted");
			clearData();
			return;
		}
		
		if (files == null) {
			Log.v(TAG, "Returned null - inaccessible directory?");
			totalCount = 0;
		} else {
			totalCount = files.size();
		}
		
		Log.v(TAG, "Counting files... (total count=" + totalCount + ")");

		/** Files separate for sorting */
		List<IconifiedText> listFiles = new ArrayList<IconifiedText>(totalCount);

		// Cache some commonly used icons.
		Drawable folderIcon = context.getResources().getDrawable(R.drawable.folder);
		Drawable genericFileIcon = context.getResources().getDrawable(R.drawable.audio);

		Drawable currentIcon = null; 
		
		if (files != null) {
			for (Stream currentFile : files){ 
				if (cancel) {
					// Abort!
					Log.v(TAG, "Scan aborted while checking files");
					clearData();
					return;
				}				
				
				if (isDirectory(currentFile)) { 
					currentIcon = folderIcon;
					listFiles.add(new IconifiedText(currentFile.getNickname(), currentFile.getUri().toString(), currentFile, currentIcon));
				} else { 
					String fileName = currentFile.getNickname(); 

					String mimetype = URLUtils.getContentType(fileName);

					currentIcon = getDrawableForMimetype(currentFile.getUri(), mimetype);
					if (currentIcon == null) {
						currentIcon = genericFileIcon;
					} else {
						int width = genericFileIcon.getIntrinsicWidth();
						int height = genericFileIcon.getIntrinsicHeight();
						// Resizing image.
						currentIcon = resizeDrawable(currentIcon, width, height);
					}

					listFiles.add(new IconifiedText(currentFile.getNickname(), currentFile.getUri().toString(), currentFile, currentIcon));
				}
			}
		}
		
		Log.v(TAG, "Sorting results...");

		if (!cancel) {
			Log.v(TAG, "Sending data back to main thread");
			
			DirectoryContents contents = new DirectoryContents();

			contents.setListFiles(listFiles);

			Message msg = handler.obtainMessage(BrowserActivity.MESSAGE_SHOW_DIRECTORY_CONTENTS);
			msg.obj = contents;
			msg.sendToTarget();
		}

		clearData();
	}

	private boolean isDirectory(Stream stream) {
		if (stream.getContentType() == null)
			return false;
		
		return stream.getContentType().contains("text/");
	}
	
	/**
     * Return the Drawable that is associated with a specific mime type
     * for the VIEW action.
     * 
     * @param mimetype
     * @return
     */
    Drawable getDrawableForMimetype(Uri data, String mimetype) {
     if (mimetype == null) {
    	 return null;
     }
     
   	 PackageManager pm = context.getPackageManager();
   	
   	 Intent intent = new Intent(Intent.ACTION_VIEW);
   	 //intent.setType(mimetype);
   	 
   	 // Let's probe the intent exactly in the same way as the VIEW action
   	 // is performed in FileManagerActivity.openFile(..)
     intent.setDataAndType(data, mimetype);
     
   	 final List<ResolveInfo> lri = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
   	 
   	 if (lri != null && lri.size() > 0) {
   		 //Log.i(TAG, "lri.size()" + lri.size());
   		 
   		 // return first element
   		 int index = 0;
   		 
   		 // Actually first element should be "best match",
   		 // but it seems that more recently installed applications
   		 // could be even better match.
   		 index = lri.size()-1;
   		 
   		 final ResolveInfo ri = lri.get(index);
   		 return ri.loadIcon(pm);
   	 }
   	 
   	 return null;
    }

    /**		
     * Resizes specific a Drawable with keeping ratio.		
     * Added for the issue #319.		
     * 
     * @since 2011-09-28
	 */		
    private Drawable resizeDrawable(Drawable drawable, int desireWidth, int desireHeight) {		
        int width = drawable.getIntrinsicWidth();		
    	int height = drawable.getIntrinsicHeight();	
    		
        if (0 < width && 0 < height && desireWidth < width || desireHeight < height) {		
            // Calculate scale		
        	float scale = Math.min((float) desireWidth / (float) width, 
                    (float) desireHeight / (float) height);

            // Draw resized image		
        	Matrix matrix = new Matrix();	
        	matrix.postScale(scale, scale);	
        	Bitmap bitmap = Bitmap.createBitmap(((BitmapDrawable) drawable).getBitmap(), 0, 0, width, height, matrix, true);	
        	Canvas canvas = new Canvas(bitmap);	
        	canvas.drawBitmap(bitmap, 0, 0, null);	
            		
            drawable = new BitmapDrawable(bitmap);		
        }		
    		
        return drawable;		
    }		
}