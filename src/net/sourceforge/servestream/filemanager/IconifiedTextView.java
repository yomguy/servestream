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
 
import net.sourceforge.servestream.R;
import android.content.Context; 
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.widget.ImageView; 
import android.widget.LinearLayout; 
import android.widget.TextView; 

public class IconifiedTextView extends LinearLayout { 
      
    private TextView mText; 
    private TextView mInfo; 
    private ImageView mIcon; 
     
    public IconifiedTextView(Context context, IconifiedText aIconifiedText) { 
    	super(context); 
		
		// inflate rating
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		inflater.inflate(R.layout.item_browser, this, true);
		
		mIcon = (ImageView) findViewById(R.id.icon);
		mText = (TextView) findViewById(R.id.text);
		mInfo = (TextView) findViewById(R.id.info);
    } 

    public void setText(String words) { 
    	mText.setText(words); 
    } 
     
    public void setInfo(String info) { 
        mInfo.setText(info);
    } 
     
    public void setIcon(Drawable bullet) { 
    	mIcon.setImageDrawable(bullet); 
    }
}