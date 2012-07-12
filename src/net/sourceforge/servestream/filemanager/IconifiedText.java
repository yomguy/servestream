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

import net.sourceforge.servestream.bean.UriBean;
import android.graphics.drawable.Drawable; 

public class IconifiedText { 
    
    private String mText = ""; 
    private String mInfo = "";
    private UriBean mUri = null;
    private Drawable mIcon; 
    private boolean mSelectable = true; 
    private boolean mSelected; 

    public IconifiedText(String text, String info, UriBean uri, Drawable icon) { 
        mText = text; 
        mInfo = info;
        mUri = uri;
        mIcon = icon;
    } 
      
    public boolean isSelected() {
    	return mSelected;
    }

 	public void setSelected(boolean selected) {
     	this.mSelected = selected;
    }

 	public boolean isSelectable() { 
 		return mSelectable; 
    }
      
    public void setSelectable(boolean selectable) { 
    	mSelectable = selectable; 
    }
    
    public String getText() { 
    	return mText; 
    }
    
    public void setText(String text) { 
    	mText = text; 
    }
    
    public String getInfo() { 
    	return mInfo; 
    }
    
    public void setInfo(String info) { 
    	mInfo = info; 
    }

	public UriBean getUri() {
		return mUri;
    }

	public void setUri(UriBean uri) {
		mUri = uri;
    }
   
    public void setIcon(Drawable icon) { 
    	mIcon = icon; 
    }
      
    public Drawable getIcon() { 
    	return mIcon; 
    }
} 

