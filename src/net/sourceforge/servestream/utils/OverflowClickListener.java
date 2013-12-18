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

import net.sourceforge.servestream.bean.UriBean;
import android.view.View;

public interface OverflowClickListener {
	/**
	 * Register a callback to be invoked when the overflow menu button is clicked.
	 * 
	 * @param view Anchor view for this popup. The popup will appear below the
	 * anchor if there is room, or above it if there is not.
	 */
    public void onClick(View view, UriBean uri);
}
