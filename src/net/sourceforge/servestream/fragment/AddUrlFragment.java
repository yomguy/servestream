/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
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

package net.sourceforge.servestream.fragment;

import java.io.IOException;

import org.xml.sax.SAXException;

import net.sourceforge.jplaylistparser.exception.JPlaylistParserException;
import net.sourceforge.jplaylistparser.parser.AutoDetectParser;
import net.sourceforge.jplaylistparser.playlist.Playlist;
import net.sourceforge.jplaylistparser.playlist.PlaylistEntry;
import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;
import net.sourceforge.servestream.utils.LoadingDialog;
import net.sourceforge.servestream.utils.LoadingDialog.LoadingDialogListener;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

public class AddUrlFragment extends Fragment implements LoadingDialogListener {
	
	public static final String URI_EXTRA = "uri_extra";
	
    private final static String LOADING_DIALOG = "loading_dialog";
	
	private EditText mUrlEditText;
	private EditText mNicknameEditText;
	private Button mConfirmButton;
	private Button mCancelButton;
	private CheckBox mSavePlaylistEntriesCheckbox;

	private String mUri;
	
	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Intent intent = new Intent(getActivity(), MainActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			getActivity().finish();
		}
	};
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		if (getArguments() != null && getArguments().getString(URI_EXTRA) != null) {
			mUri = getArguments().getString(URI_EXTRA);
		}
		
        setRetainInstance(true);
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_add_uri, container, false);
		mUrlEditText = (EditText) view.findViewById(R.id.url_edittext);
		mCancelButton = (Button) view.findViewById(R.id.cancel_button);
		mConfirmButton = (Button) view.findViewById(R.id.confirm_button);
		mNicknameEditText = (EditText) view.findViewById(R.id.nickname_edittext);
		mSavePlaylistEntriesCheckbox = (CheckBox) view.findViewById(R.id.save_playlist_entries_checkbox);
		
		if (mUri != null) {
			mUrlEditText.setText(mUri);
			mUri = null;
		}
		
		return view;
	}
	
	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		
		mUrlEditText.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {

				if(event.getAction() == KeyEvent.ACTION_UP)
					return false;
				
				if(keyCode != KeyEvent.KEYCODE_ENTER)
					return false;
			    
				processUri();
				
			    return true;
			}
			
		});
		
		mCancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
    			getActivity().finish();
			}
			
		});
		
		mConfirmButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				processUri();
			}
		});
		
		mSavePlaylistEntriesCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					mNicknameEditText.setEnabled(false);
				} else {
					mNicknameEditText.setEnabled(true);
				}
			}
		});
	}

    private void processUri() {
		String input = mUrlEditText.getText().toString();
		
		Uri uri = TransportFactory.getUri(input);

		if (uri == null) {
			mUrlEditText.setError(getString(R.string.error_url_label));
			return;
		}

		StreamDatabase streamdb = new StreamDatabase(getActivity());
		UriBean uriBean = TransportFactory.findUri(streamdb, uri);
		if (uriBean == null) {
			uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
			
			if (mSavePlaylistEntriesCheckbox.isChecked()) {
				streamdb.close();
				new ParseEntriesTask(getActivity(), mHandler, uriBean).execute();
				return;
			} else {
				String nickname = mNicknameEditText.getText().toString();
			
				if (!nickname.equals("")) {
					uriBean.setNickname(nickname);
				}
			
				AbsTransport transport = TransportFactory.getTransport(uriBean.getProtocol());
				transport.setUri(uriBean);
				streamdb.saveUri(uriBean);
			}
		}
		
		streamdb.close();
		
		Intent intent = new Intent(getActivity(), MainActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		getActivity().finish();
	}
    
    private void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction.  We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		Fragment prev = getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		newFragment = LoadingDialog.newInstance(this, getString(R.string.opening_url_message));

		ft.add(0, newFragment, tag);
		ft.commitAllowingStateLoss();
	}

	private void dismissDialog(String tag) {
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		DialogFragment prev = (DialogFragment) getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		ft.commitAllowingStateLoss();
	}
    
    private class ParseEntriesTask extends AsyncTask<Void, Void, Void> {

    	private Context mContext;
    	private Handler mHandler;
    	private UriBean mUri;
    	
    	public ParseEntriesTask(Context context, Handler handler, UriBean uri) {
    		mContext = context;
    		mHandler = handler;
    		mUri = uri;
    	}
    	
    	@Override
    	protected void onPreExecute() {
    		showDialog(LOADING_DIALOG);
    	}
    	
    	@Override
    	protected Void doInBackground(Void... params) {
			StreamDatabase streamdb = new StreamDatabase(mContext);
    		
    		AutoDetectParser parser = new AutoDetectParser(); // Should auto-detect!
    	    Playlist playlist = new Playlist();
    	    
			AbsTransport transport = TransportFactory.getTransport(mUri.getProtocol());
			transport.setUri(mUri);
			
			try { 
				transport.connect();
				
				parser.parse(mUri.getScrubbedUri().toString(), transport.getContentType(), transport.getConnection(), playlist);
				
				for (int i = 0; i < playlist.getPlaylistEntries().size(); i++) {
					PlaylistEntry entry = playlist.getPlaylistEntries().get(i);
					Uri uri = TransportFactory.getUri(entry.get(PlaylistEntry.URI));
					UriBean uriBean = TransportFactory.findUri(streamdb, uri);
					if (uriBean == null) {
						uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
						
						AbsTransport transport2 = TransportFactory.getTransport(uriBean.getProtocol());
						transport2.setUri(uriBean);
						streamdb.saveUri(uriBean);
					}
				}
    		} catch (IOException e) {
    			playlist = null;
    		} catch (SAXException e) {
    			playlist = null;
    		} catch (JPlaylistParserException e) {
    			playlist = null;
    		} finally {
    			transport.close();
    		}

			if (playlist == null) {
				processUri(streamdb, mUri.getScrubbedUri().toString());
			}
			
			streamdb.close();
			
    		return null;
    	}

    	@Override
        protected void onPostExecute(Void v) {
    		dismissDialog(LOADING_DIALOG);
    	    mHandler.sendEmptyMessage(0);
        }
    	
    	private void processUri(StreamDatabase streamdb, String input) {
    		Uri uri = TransportFactory.getUri(input);

    		if (uri == null) {
    			return;
    		}
    		
    		UriBean uriBean = TransportFactory.findUri(streamdb, uri);
    		if (uriBean == null) {
    			uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
    			
    			AbsTransport transport = TransportFactory.getTransport(uriBean.getProtocol());
    			transport.setUri(uriBean);
    			streamdb.saveUri(uriBean);
    		}
    	}
    }

	@Override
	public void onLoadingDialogCancelled(DialogFragment dialog) {
		
	}
}
