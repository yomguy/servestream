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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.utils.OverflowClickListener;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
 
public class BrowseAdapter extends ArrayAdapter<UriBean> {
    private Context mContext;
    private List<UriBean> mRowItems;
    private OverflowClickListener mListener;
 
    public BrowseAdapter(Context context, List<UriBean> rowItems, OverflowClickListener listener) {
		super(context, R.layout.list_item_browse, rowItems);
        mContext = context;
        mRowItems = rowItems;
        mListener = listener;
    }
 
    /*private view holder class*/
    private class ViewHolder {
        TextView nickname;
        TextView caption;
        ImageView overflowMenuButton;
    }
 
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
 
        LayoutInflater mInflater = (LayoutInflater)
            mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.list_item_browse, null);
            holder = new ViewHolder();
			holder.nickname = (TextView) convertView.findViewById(android.R.id.text1);
			holder.caption = (TextView) convertView.findViewById(android.R.id.text2);
			holder.overflowMenuButton = (ImageView) convertView.findViewById(R.id.overflow_menu_button);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
 
        final UriBean uri = mRowItems.get(position);

		holder.nickname.setText(uri.getNickname());
		holder.caption.setText(uri.getUri().toString());

		holder.overflowMenuButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (mListener != null) {
					mListener.onClick(v, uri);
				}
			}
			
		});
		
        return convertView;
    }
    
    public List<UriBean> getItems() {
        return mRowItems;
    }
}