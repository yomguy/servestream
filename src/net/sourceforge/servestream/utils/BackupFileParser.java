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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class BackupFileParser {

	private File mBackupFile = null;

	/**
	 * Default constructor
	 * @param backupFile
	 */
	public BackupFileParser(File backupFile) {
		mBackupFile = backupFile;
	}

	public List<UriBean> parse() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		List<UriBean> uris = new ArrayList<UriBean>();
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document dom = builder.parse(new FileInputStream(mBackupFile));
			Element root = dom.getDocumentElement();
			NodeList items = root.getElementsByTagName(UriBean.BEAN_NAME);

			for (int i = 0; i < items.getLength(); i++) {
				UriBean uriBean = new UriBean();
				Node item = items.item(i);
				NodeList properties = item.getChildNodes();
				for (int j = 0; j < properties.getLength(); j++) {
					Node property = properties.item(j);
					String name = property.getNodeName();

					if (property.getFirstChild() == null) {
						continue;
					}

					if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_NICKNAME)) {
						uriBean.setNickname(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PROTOCOL)) {
						uriBean.setProtocol(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_USERNAME)) {
						uriBean.setUsername(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PASSWORD)) {
						uriBean.setPassword(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_HOSTNAME)) {
						uriBean.setHostname(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PORT)) {
						uriBean.setPort(Integer.valueOf(property.getFirstChild().getNodeValue()));
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PATH)) {
						uriBean.setPath(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_QUERY)) {
						uriBean.setQuery(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_REFERENCE)) {
						uriBean.setReference(property.getFirstChild().getNodeValue());
					} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_LASTCONNECT)) {
						uriBean.setLastConnect(Long.valueOf(property.getFirstChild().getNodeValue()));
					}
				}

				uris.add(uriBean);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return uris;
	}
}