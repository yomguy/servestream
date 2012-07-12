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

package net.sourceforge.servestream.utils;

import android.util.Base64;

public class XmlBuilder {
	private StringBuilder sb;

	public XmlBuilder() {
		sb = new StringBuilder();
	}

	public XmlBuilder append(String data) {
		sb.append(data);

		return this;
	}

	public XmlBuilder append(String field, Object data) {
		if (data == null) {
			sb.append(String.format("<%s/>", field));
		} else if (data instanceof String) {
			String input = (String) data;
			boolean binary = false;

			for (byte b : input.getBytes()) {
				if (b < 0x20 || b > 0x7e) {
					binary = true;
					break;
				}
			}

			sb.append(String.format("<%s>%s</%s>", field,
					binary ? new String(Base64.encode(input.getBytes(), Base64.DEFAULT)) : input, field));
		} else if (data instanceof Integer) {
			sb.append(String.format("<%s>%d</%s>", field, (Integer) data, field));
		} else if (data instanceof Long) {
			sb.append(String.format("<%s>%d</%s>", field, (Long) data, field));
		} else if (data instanceof byte[]) {
			sb.append(String.format("<%s>%s</%s>", field, new String(Base64.encode((byte[]) data, Base64.DEFAULT)), field));
		} else if (data instanceof Boolean) {
			sb.append(String.format("<%s>%s</%s>", field, (Boolean) data, field));
		}

		return this;
	}

	public String toString() {
		return sb.toString();
	}
}