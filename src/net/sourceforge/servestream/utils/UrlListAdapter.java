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

import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bean.UriBean;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
 
public class UrlListAdapter extends ArrayAdapter<UriBean> {
    private Context context;
    private List<UriBean> rowItems;
 
    public UrlListAdapter(Context context, List<UriBean> rowItems) {
		super(context, R.layout.item_stream, rowItems);
        this.context = context;
        this.rowItems = rowItems;
    }
 
    /*private view holder class*/
    private class ViewHolder {
        TextView nickname;
        TextView caption;
    }
 
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
 
        LayoutInflater mInflater = (LayoutInflater)
            context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_stream, null);
            holder = new ViewHolder();
			holder.nickname = (TextView) convertView.findViewById(android.R.id.text1);
			holder.caption = (TextView) convertView.findViewById(android.R.id.text2);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
 
        UriBean uri = rowItems.get(position);

		holder.nickname.setText(uri.getNickname());

		long now = System.currentTimeMillis() / 1000;

		String lastConnect = context.getString(R.string.bind_never);
		if (uri.getLastConnect() > 0) {
			int minutes = (int)((now - uri.getLastConnect()) / 60);
			if (minutes >= 60) {
				int hours = (minutes / 60);
				if (hours >= 24) {
					int days = (hours / 24);
					lastConnect = context.getString(R.string.bind_days, days);
				} else
					lastConnect = context.getString(R.string.bind_hours, hours);
			} else
				lastConnect = context.getString(R.string.bind_minutes, minutes);
		}

		holder.caption.setText(lastConnect);

        return convertView;
    }
}