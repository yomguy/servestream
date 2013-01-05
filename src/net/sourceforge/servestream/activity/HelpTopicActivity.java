/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.utils.HelpTopicView;

import android.content.Intent;
import android.os.Bundle;

public class HelpTopicActivity extends SherlockActivity {
	public final static String TAG = HelpActivity.class.getName();

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		setContentView(R.layout.activity_help_topic);

		ActionBar actionBar = getSupportActionBar();
		
		String topic = getIntent().getStringExtra(Intent.EXTRA_TITLE);

		actionBar.setTitle(topic);

		HelpTopicView helpTopic = (HelpTopicView) findViewById(R.id.topic_text);
		helpTopic.setTopic(topic);
	}
}
