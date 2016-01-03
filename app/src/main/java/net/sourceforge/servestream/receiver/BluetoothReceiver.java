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

package net.sourceforge.servestream.receiver;

import java.io.IOException;
import java.util.ArrayList;

import net.sourceforge.servestream.activity.BluetoothOptionsActivity;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.ObjectSerializer;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BluetoothReceiver extends BroadcastReceiver {

	@SuppressWarnings("unchecked")
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		
		if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
			BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			
	        // load tasks from preference
	        SharedPreferences prefs = context.getSharedPreferences(BluetoothOptionsActivity.PREFS_NAME, Context.MODE_PRIVATE);

	        ArrayList<String> items = new ArrayList<String>();
	        
	        try {
	        	items = (ArrayList<String>) ObjectSerializer.deserialize(prefs.getString(BluetoothOptionsActivity.PREF_BONDED_DEVICES, ObjectSerializer.serialize(new ArrayList<String>())));
	        } catch (IOException e) {
	        	return;
	        }
	        
	        for (int i = 0; i < items.size(); i++) {
		    	String [] values = items.get(i).split("\\_");
		    		
		    	if (values[1].equalsIgnoreCase(device.getAddress())) {
			        String uri = prefs.getString(BluetoothOptionsActivity.PREF_AUTOSTART_STREAM, null);
			        
			        if (uri == null) {
			        	return;
			        }
		    		
		            final ComponentName serviceName = new ComponentName(context, MediaPlaybackService.class);
		            Intent service = new Intent(MediaPlaybackService.BLUETOOTH_DEVICE_PAIRED);
		            service.setComponent(serviceName);
		            service.putExtra("uri", uri);
		    		context.startService(service);
		    		break;
		    	}
		    }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
        }
	}

}
