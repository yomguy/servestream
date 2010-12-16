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

public class ProtocolFactory {
	
	public static final String HTTP_HINT = "hostname/path";
	public static final String HTTPS_HINT = "hostname/path";
	
	/**
	 * Default constructor
	 */
	public ProtocolFactory() {
		
	}
	
	/**
	 * Returns a hint for the protocol passed in 
	 * 
	 * @param protocol The protocol
	 * @return String The hint for the protocol
	 */
	public static String getProtocolHint(String protocol) {
		if (protocol.equals("http")) {
			return HTTP_HINT;
		} else if (protocol.equals("https")) {
			return HTTPS_HINT;
		} else {
			return null;
		}
	}
	
	/**
	 * 
	 * 
	 * @param scheme 
	 * @return String Protocol
	 */
	public static String getProtocol(String scheme) {
		if (scheme.equals("http")) {
			return "http://";
		} else if (scheme.equals("https")) {
			return "https://";
		} else {
			return null;
		}
	}
	
}