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

package net.sourceforge.servestream.activity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.adapter.BluetoothOptionsListAdapter;
import net.sourceforge.servestream.preference.UserPreferences;
import net.sourceforge.servestream.utils.ObjectSerializer;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.widget.ListView;

public class BluetoothOptionsActivity extends Activity {

	public static final String PREFS_NAME = "BluetoothOptionsActivity";
	public static final String PREF_BONDED_DEVICES = "bonded_devices";
	public static final String PREF_AUTOSTART_STREAM = "autostart_stream";
	
	private ListView mList;
	private BluetoothOptionsListAdapter mAdapter;
	
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bluetooth_options);
		
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
		    // Device does not support Bluetooth
			return;
		}

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

		ArrayList<String> items = new ArrayList<String>();
        
        try {
        	items = (ArrayList<String>) ObjectSerializer.deserialize(prefs.getString(PREF_BONDED_DEVICES, ObjectSerializer.serialize(new ArrayList<String>())));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		// If there are paired devices
		if (pairedDevices.size() > 0) {
		    // Loop through paired devices
		    for (BluetoothDevice device : pairedDevices) {
		        // Add the name and address to an array adapter to show in a ListView
		    	devices.add(device);
		    }
		}
		
		mAdapter = new BluetoothOptionsListAdapter(this, devices);
		
		mList = (ListView) findViewById(android.R.id.list);
		mList.setEmptyView(findViewById(android.R.id.empty));
		mList.setAdapter(mAdapter);
		mList.setFastScrollEnabled(true);
		mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		
		for (int i = 0; i < devices.size(); i++) {
			BluetoothDevice device = devices.get(i);
			
			if (shouldCheck(device, items)) {
				mList.setItemChecked(i, true);
			}
		}
	}
	
	private boolean shouldCheck(BluetoothDevice device, ArrayList<String> items) {
	   	for (int i = 0; i < items.size(); i++) {
	    	String [] values = items.get(i).split("\\_");
	    		
	    	if (values[1].equalsIgnoreCase(device.getAddress())) {
	    		return true;
	    	}
	    }
	    	
	    return false;
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		ArrayList<String> items = new ArrayList<String>();
		
		SparseBooleanArray checkedItems = mList.getCheckedItemPositions();
        for (int i = 0; i < checkedItems.size(); i++) {
            if (checkedItems.valueAt(i)) {
            	BluetoothDevice device = mAdapter.getItem(checkedItems.keyAt(i));
            	items.add(device.getName() + "_" + device.getAddress());
            }
        }
		
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Editor editor = prefs.edit();
        
        try {
            editor.putString(PREF_BONDED_DEVICES, ObjectSerializer.serialize(items));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        editor.commit();
	}
}