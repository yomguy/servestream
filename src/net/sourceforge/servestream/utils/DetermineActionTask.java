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

package net.sourceforge.servestream.utils;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;
import android.content.Context;
import android.os.AsyncTask;

/**
 * Asynchronous task that prepares a MusicRetriever. This asynchronous task essentially calls
 * {@link MusicRetriever#prepare()} on a {@link MusicRetriever}, which may take some time to
 * run. Upon finishing, it notifies the indicated {@MusicRetrieverPreparedListener}.
 */
public class DetermineActionTask extends AsyncTask<Void, Void, Void> {
    
	public static final String URL_ACTION_UNDETERMINED = "undetermined";
	public static final String URL_ACTION_BROWSE = "browse";
	public static final String URL_ACTION_PLAY = "play";
	
	private Context mContext;
	private UriBean mUri;
	private MusicRetrieverPreparedListener mListener;
	
	private String mAction;
	private long[] mList;

    public DetermineActionTask(Context context,
    		UriBean uri,
            MusicRetrieverPreparedListener listener) {
    	mContext = context;
		mUri = uri;
        mListener = listener;
    }

	@Override
    protected Void doInBackground(Void... arg0) {
	    processUri();
	    return null;
	}

	private void processUri() {
		AbsTransport transport = TransportFactory.getTransport(getUri().getProtocol());
		transport.setUri(getUri());
		
		try {
			transport.connect();
		
			if (transport.getContentType() == null) {
				mAction = URL_ACTION_UNDETERMINED;
			} else if (transport.getContentType().contains("text/html")) {
				mAction = URL_ACTION_BROWSE;
			} else {
				mAction = URL_ACTION_PLAY;
				if (transport.isPotentialPlaylist()) {
					mList = MusicUtils.getFilesInPlaylist(mContext, getUri().getScrubbedUri().toString(), transport.getContentType(), transport.getConnection());
				} else {
					mList = MusicUtils.storeFile(mContext, getUri().getScrubbedUri().toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			mAction = URL_ACTION_UNDETERMINED;
		} finally {
			transport.close();
		}
	}
	
    @Override
    protected void onPostExecute(Void result) {
        mListener.onMusicRetrieverPrepared(mAction, mUri, mList);
    }
	
    /**
	 * @return the mUri
	 */
	public UriBean getUri() {
		return mUri;
	}

	public interface MusicRetrieverPreparedListener {
        public void onMusicRetrieverPrepared(String action, UriBean uri, long [] list);
    }
}