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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;

import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class AddUrlActivity extends SherlockActivity {
	
	private EditText mUrlEditText;
	private Button mImportButton;
	private Button mConfirmButton;
	private Button mCancelButton;

	private ProgressDialog progDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//setTheme(UserPreferences.getTheme());
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_uri);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);		

		mUrlEditText = (EditText) findViewById(R.id.url_edittext);
		mImportButton = (Button) findViewById(R.id.import_button);
		mCancelButton = (Button) findViewById(R.id.cancel_button);
		mCancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
    			finish();
			}
			
		});
		
		mConfirmButton = (Button) findViewById(R.id.confirm_button);
		mConfirmButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				processUri(mUrlEditText.getText().toString());
			}
			
		});
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case android.R.id.home:
    			finish();
    			return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private boolean processUri(String input) {
		//hideKeyboard();
		
		StreamDatabase streamdb = new StreamDatabase(this);
		
		Uri uri = TransportFactory.getUri(input);

		if (uri == null) {
			mUrlEditText.setError(getString(R.string.invalid_url_message));
			return false;
		}

		UriBean uriBean = TransportFactory.findUri(streamdb, uri);
		if (uriBean == null) {
			uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
			
			AbsTransport transport = TransportFactory.getTransport(uriBean.getProtocol());
			transport.setUri(uriBean);
			streamdb.saveUri(uriBean);
		}
		
		streamdb.close();
		
		return true;
	}
}
