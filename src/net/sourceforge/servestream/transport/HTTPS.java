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

package net.sourceforge.servestream.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLDecoder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.utils.Utils;

import android.net.Uri;

public class HTTPS extends AbsTransport {

	private static final String PROTOCOL = "https";
	private static final int DEFAULT_PORT = 443;
	
	private HttpsURLConnection conn = null;
	private InputStream is = null;
	private int mResponseCode = -1;
	private String mContentType = null;
	
	public HTTPS() {
		super();
	}
	
	public HTTPS(UriBean uri) {
		super(uri);
	}
	
	public static String getProtocolName() {
		return PROTOCOL;
	}

	protected String getPrivateProtocolName() {
		return PROTOCOL;
	}
	
	/**
	 * Encode the current transport into a URI that can be passed via intent calls.
	 * @return URI to host
	 */
	public static Uri getUri(String input) {
		return getUri(input, false);
	}
	
	/**
	 * Encode the current transport into a URI that can be passed via intent calls.
	 * @return URI to host
	 */
	private static Uri getUri(String input, boolean scrubUri) {
		if (input == null) {
			return null;
		}
		
		String hostname = null;
		int port = -1;
		
		try {
			input = URLDecoder.decode(input, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			return null;
		}
		
		URL url = null;
		
		try {
			url = new URL(input);
		} catch (MalformedURLException e) {
			return null;
		}
		
		// the following code is used as a temporary fix to deal with Android's
		// handling of URL's that contain "special characters" such as "[" (Issue 12724)
		String [] split = url.getHost().split("\\:");
		
		if (split.length == 2) {
			hostname = split[0];
			port = Integer.valueOf(split[1]);
		}
		
		StringBuilder sb = new StringBuilder();

		sb.append(PROTOCOL)
			.append("://");
		
		if (!scrubUri) {
			if (url.getUserInfo() != null) {
				String [] authInfo = url.getUserInfo().split("\\:");
			
				if (authInfo.length == 2) {
					sb.append(authInfo[0])
						.append(":")
						.append(authInfo[1])
						.append("@");
				}
			}
		}
		
		if (hostname != null) {
			sb.append(hostname)
				.append(":");
		} else {
			sb.append(url.getHost())
				.append(":");
		}
		
		if (port != -1) {
			sb.append(port);
		} else {
			if (url.getPort() == -1) {
				sb.append(DEFAULT_PORT);
			} else {
				sb.append(url.getPort());	
			}
		}
		
		sb.append(url.getPath());
		
		if (url.getQuery() != null) {
		    sb.append("?")
				.append(url.getQuery());
		}
		
		if (url.getRef() != null) {
		    sb.append("#")
				.append(url.getRef());
		}
		
		Uri uri = Uri.parse(sb.toString());

		return uri;
	}
	
	@Override
	public void connect() throws IOException {
		URL url = null;

    	final String username = uri.getUsername();
    	final String password = uri.getPassword();
    	
    	if (username != null && password != null) {
    		Authenticator.setDefault(new Authenticator() {
    			protected PasswordAuthentication getPasswordAuthentication() {
    				return new PasswordAuthentication(username, password.toCharArray()); 
    			};
    		});
        	
    		url = uri.getScrubbedURL();
    	} else {
    		url = uri.getURL();
    	}

    	trustAllHosts();
    	conn = (HttpsURLConnection) url.openConnection();   
    	conn.setHostnameVerifier(DO_NOT_VERIFY);
    	conn.setConnectTimeout(6000);
    	conn.setReadTimeout(6000);
	    conn.setRequestMethod("GET");
    	conn.setRequestProperty("User-Agent", "ServeStream");
    	
	    mResponseCode = conn.getResponseCode();
		    
	    if (mResponseCode == -1) {
	        mResponseCode = HttpURLConnection.HTTP_OK;
	    }
	        
	    mContentType = conn.getContentType();
	    is = conn.getInputStream();
	}

	@Override
	public void close() {
		Utils.closeInputStream(is);
		Utils.closeHttpConnection(conn);		
	}

	@Override
	public boolean exists() {
		return true;
	}
	
	@Override
	public boolean isConnected() {
		return is != null;
	}

	@Override
	public int getDefaultPort() {
		return DEFAULT_PORT;
	}

	@Override
	public void getSelectionArgs(Uri uri, Map<String, String> selection) {
		selection.put(StreamDatabase.FIELD_STREAM_PROTOCOL, PROTOCOL);
		
		if (uri.getUserInfo() != null) {
			String [] authInfo = uri.getUserInfo().split("\\:");
			
			if (authInfo.length == 2) {
				selection.put(StreamDatabase.FIELD_STREAM_USERNAME, authInfo[0]);
				selection.put(StreamDatabase.FIELD_STREAM_PASSWORD, authInfo[1]);
			}
		} else {
			selection.put(StreamDatabase.FIELD_STREAM_USERNAME, null);
			selection.put(StreamDatabase.FIELD_STREAM_PASSWORD, null);
		}
		
		selection.put(StreamDatabase.FIELD_STREAM_HOSTNAME, uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		selection.put(StreamDatabase.FIELD_STREAM_PORT, Integer.toString(port));
		
		if (uri.getPath() != null) {
			selection.put(StreamDatabase.FIELD_STREAM_PATH, uri.getPath());
		}
		selection.put(StreamDatabase.FIELD_STREAM_QUERY, uri.getQuery());
		selection.put(StreamDatabase.FIELD_STREAM_REFERENCE, uri.getFragment());		
	}

	@Override
	public UriBean createUri(Uri uri) {
		UriBean host = new UriBean();

		host.setProtocol(PROTOCOL);

		if (uri.getUserInfo() != null) {
			String [] authInfo = uri.getUserInfo().split("\\:");
			
			if (authInfo.length == 2) {
				host.setUsername(authInfo[0]);
				host.setPassword(authInfo[1]);
			}
		}
		
		host.setHostname(uri.getHost());

		int port = uri.getPort();
		if (port < 0)
			port = DEFAULT_PORT;
		host.setPort(port);

		host.setPath(uri.getPath());
		host.setQuery(uri.getQuery());
		host.setReference(uri.getFragment());

		String nickname = getUri(uri.toString(), true).toString();
		host.setNickname(nickname);

		return host;
	}

	@Override
	public String getContentType() {
		return mContentType;
	}
	
	@Override
	public boolean usesNetwork() {
	    return true;
	}
	
	@Override
	public InputStream getConnection() {
		return is;
	}
	
	@Override
	public boolean isPotentialPlaylist() {
		return true;
	}
	
	// always verify the host - dont check for certificate
	private final HostnameVerifier DO_NOT_VERIFY = new HostnameVerifier() {
	        public boolean verify(String hostname, SSLSession session) {
	                return true;
	        }
	};

	/**
	 * Trust every server - dont check for any certificate
	 */
	private void trustAllHosts() {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new java.security.cert.X509Certificate[] {};
			}

			public void checkClientTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}

			public void checkServerTrusted(X509Certificate[] chain,
					String authType) throws CertificateException {
			}
		} };

		// Install the all-trusting trust manager
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection
			.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
