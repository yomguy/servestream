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

package net.sourceforge.servestream.adapter;

import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
 
public class BluetoothOptionsListAdapter extends ArrayAdapter<BluetoothDevice> {
	
    private Context mContext;
    private List<BluetoothDevice> mDevices;
 
    public BluetoothOptionsListAdapter(Context context, List<BluetoothDevice> devices) {
		super(context, android.R.layout.simple_list_item_multiple_choice, devices);
        mContext = context;
        mDevices = devices;
    }
 
    // private view holder class
    private class ViewHolder {
        CheckedTextView txtTitle;
    }
 
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
 
        LayoutInflater inflater = (LayoutInflater)
            mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = inflater.inflate(android.R.layout.simple_list_item_multiple_choice, null);
            holder = new ViewHolder();
            holder.txtTitle = (CheckedTextView) convertView.findViewById(android.R.id.text1);
            convertView.setTag(holder);
        }
        else {
            holder = (ViewHolder) convertView.getTag();
        }
 
        BluetoothDevice device = (BluetoothDevice) getItem(position);
 
        holder.txtTitle.setText(device.getName() + "\n" + device.getAddress());
 
        return convertView;
    }
    
    @Override
    public int getCount() {
        return mDevices.size();
    }
 
    @Override
    public BluetoothDevice getItem(int position) {
        return mDevices.get(position);
    }
 
    @Override
    public long getItemId(int position) {
        return mDevices.indexOf(getItem(position));
    }
}