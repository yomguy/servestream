/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
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

import net.sourceforge.servestream.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class DownloadScannerDialog extends DialogFragment {
	
    /* Call this to instantiate a new BarcodeScannerDialog.
     * @returns A new instance of BarcodeScannerDialog.
     */
    public static DownloadScannerDialog newInstance() {
        return new DownloadScannerDialog();
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
    	AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    	builder.setMessage(R.string.find_barcode_scanner_message)
    	       .setCancelable(true)
    	       .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   try {
    	        		   Intent intent = new Intent(Intent.ACTION_VIEW);
    	        		   intent.setData(Uri.parse(Constants.ZXING_PLAYSTORE_URI));
    	        		   startActivity(intent);
    	        	   } catch (ActivityNotFoundException ex ) {
    	        		   // the Google Play store couldn't be opened,
    	        		   // lets take the user to the project's webpage instead.
    	        		   Intent intent = new Intent(Intent.ACTION_VIEW);
    	        		   intent.setData(Uri.parse(Constants.ZXING_PROJECT_PAGE));
    	        		   startActivity(intent);
    	        	   }
    	           }
    	       })
    	       .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                dialog.cancel();
    	           }
    	       });
	    
        return builder.create();
    }
}