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

package net.sourceforge.servestream.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

public class CustomVideoView extends VideoView {

	private int width = -1;
	private int height = -1;
	
    public CustomVideoView(Context context) {
        super(context);
    }

    public CustomVideoView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }
        
    public CustomVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
        
    @Override
    protected void onMeasure (int width, int height)
    {
         setMeasuredDimension(this.width,this.height);
    }
        
    /**
     * Sets the width and height for the VideoView to use
     * 
     * @param width The desired width of the VideoView
     * @param height The desired height of the VideoView
     */
    public void setDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
