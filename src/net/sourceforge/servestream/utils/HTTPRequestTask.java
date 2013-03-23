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
import net.sourceforge.servestream.transport.AbsTransport;
import net.sourceforge.servestream.transport.TransportFactory;
import android.net.Uri;

public class HTTPRequestTask implements Runnable {
    
	private String mUri;
	private boolean mUseFFmpegPlayer;
	private HTTPRequestListener mListener;
	
    public HTTPRequestTask(String uri,
    		boolean useFFmpegPlayer,
    		HTTPRequestListener listener) {
		mUri = uri;
		mUseFFmpegPlayer = useFFmpegPlayer;
        mListener = listener;
    }

	@Override
    public void run() {
		onPostExecute(processUri());
	}

	private String processUri() {
		UriBean uriBean = null;
		AbsTransport transport = null;
		String contentType = null;
		
		try {
			Uri uri = TransportFactory.getUri(mUri);
		
			if (uri != null) {
				uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
			}
		
			if (uriBean != null) {
				transport = TransportFactory.getTransport(uriBean.getProtocol());
				transport.setUri(uriBean);
				transport.connect();
				contentType = transport.getContentType();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			transport.close();
		}
		
		return contentType;
	}
	
	public synchronized void execute() {
		new Thread(this, "").start();
	}
	
    private void onPostExecute(String result) {
    	if (result == null) {
    		mListener.onHTTPRequestError(mUri, mUseFFmpegPlayer);
    	} else {
    		mListener.onContentTypeObtained(mUri, mUseFFmpegPlayer, result);
    	}
    }
	
	public interface HTTPRequestListener {
        public void onContentTypeObtained(String path, boolean useFFmpegPlayer, String contentType);
        public void onHTTPRequestError(String path, boolean useFFmpegPlayer);
    }
}