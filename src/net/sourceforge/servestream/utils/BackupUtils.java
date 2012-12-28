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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.transport.TransportFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlSerializer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Xml;
import android.widget.Toast;

public class BackupUtils {

	private static final String BACKUP_FILE = "backup.xml";
	
	private static final String ROOT_ELEMENT_TAG_NAME = "backup";
	private static final String BACKUP_ENCODING = "UTF-8";

	private static final int BACKUP_OPERATION = 0;
	private static final int RESTORE_OPERATION = 1;
	
	public static void showBackupDialog(final Context context) {
    	AlertDialog.Builder builder;
    	AlertDialog alertDialog;
    	
    	File backupFile = getBackupFile(context, BACKUP_FILE);

    	if (backupFile == null) {
    		return;
    	}
    	
    	CharSequence [] items;
    	
    	if (backupFile.exists()) {
    		items = new CharSequence[2];
    	    items[0] = context.getString(R.string.list_menu_backup);
    		items[1] = context.getString(R.string.restore);
    	} else {
    		items = new CharSequence[1];
    		items[0] = context.getString(R.string.list_menu_backup);
    	}

    	builder = new AlertDialog.Builder(context);
    	builder.setTitle(R.string.recovery_options)
    		.setItems(items, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int item) {
    				if (item == BACKUP_OPERATION) {
    					backup(context, BACKUP_FILE);
    				} else if (item == RESTORE_OPERATION) {
    					restore(context, BACKUP_FILE);
    					((MainActivity) context).updateList();
    				}
    			}
    		});
    	alertDialog = builder.create();
    	alertDialog.show();
	}
	
	private static void backup(Context context, String filename) {
		BufferedWriter out = null;
		
		StreamDatabase streamdb = new StreamDatabase(context);
		
		String xml = writeXml(streamdb.getUris());
		
		streamdb.close();
		
		File backupFile = getBackupFile(context, filename);
		try {
			out = new BufferedWriter(new FileWriter(backupFile));
			out.write(xml);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
			} catch (IOException e) {
			}
		}
		
		Toast.makeText(context, context.getString(R.string.backup_message), Toast.LENGTH_SHORT).show();
	}
	
	private static void restore(Context context, String fileName) {
		StreamDatabase streamdb = new StreamDatabase(context);
		
		File backupFile = new File(context.getCacheDir(), fileName);
		
		BackupFileParser bfp = new BackupFileParser(backupFile);
		
		List<UriBean> uris = bfp.parse();
		
		for (int i = 0; i < uris.size(); i++) {
			if (TransportFactory.findUri(streamdb, uris.get(i).getUri()) == null) {
				streamdb.saveUri(uris.get(i));
			}
		}
		
		streamdb.close();
		
		Toast.makeText(context, R.string.restore_message, Toast.LENGTH_SHORT).show();
	}
	
	private static File getBackupFile(Context context, String filename) {
		//File cacheDir = context.getCacheDir();
		File cacheDir = context.getExternalCacheDir();
		
		if (cacheDir == null) {
			return null;
		}
		
		return new File(cacheDir, filename);
	}
	
	private static String writeXml(List<UriBean> uris) {
	    XmlSerializer serializer = Xml.newSerializer();
	    StringWriter writer = new StringWriter();
	    
	    try {
	        serializer.setOutput(writer);
	        serializer.startDocument(BACKUP_ENCODING, true);
	        serializer.startTag("", ROOT_ELEMENT_TAG_NAME);
	        
	        for (UriBean uriBean: uris) {
	            serializer.startTag("", UriBean.BEAN_NAME);
	        	
        		Set<Entry<String, Object>> selection = uriBean.getValues().valueSet();
	        	
        		Iterator<Entry<String, Object>> i = selection.iterator();
        		
        		while (i.hasNext()) {
        			Entry<String, Object> entry = i.next();
        		
        			String key = entry.getKey();
        			
        			// don't save password and username in plain text
        			if (key.equals(StreamDatabase.FIELD_STREAM_USERNAME) ||
        					key.equals(StreamDatabase.FIELD_STREAM_PASSWORD)) {
        				continue;
        			}
        			
        			serializer.startTag("", key);
            		if (entry.getValue() != null) {
            			serializer.text(entry.getValue().toString());
            		} else {
            			serializer.text("");
            		}
        			serializer.endTag("", key);
        		}
        		
                serializer.endTag("", UriBean.BEAN_NAME);
	        }
	        
	        serializer.endTag("", ROOT_ELEMENT_TAG_NAME);
	        serializer.endDocument();
	        return writer.toString();
	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    } 
	}
	
	private static class BackupFileParser {

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
}
