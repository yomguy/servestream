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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import net.sourceforge.servestream.transport.TransportFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;

public class BackupUtils {

	private static final String BACKUP_FILE = "backup.json";
	private static final String ROOT_JSON_ELEMENT = "backup";
	private static final String BACKUP_DIRECTORY_PATH = "/ServeStream/backup/";
	
	private static void showBackupDialog(final Context context, String message) {
    	AlertDialog.Builder builder;
    	AlertDialog alertDialog;
    	builder = new AlertDialog.Builder(context);
    	builder.setMessage(message)
    		.setCancelable(true)
    		.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int id) {
    				dialog.dismiss();
    			}
    		});
    	alertDialog = builder.create();
    	alertDialog.show();
	}
	
	public synchronized static void backup(Context context) {
		boolean success = true;
		String message = "Backup failed";
		
		File backupFile = getBackupFile();
		
		if (backupFile == null) {
			success = false;
		} else {
			BufferedWriter out = null;
			StreamDatabase streamdb = new StreamDatabase(context);
			List<UriBean> uris = streamdb.getUris();
			streamdb.close();
			
			if (uris.size() > 0) {
				try {
					String json = writeJSON(uris);
					out = new BufferedWriter(new FileWriter(backupFile));
					out.write(json);
				} catch (FileNotFoundException e) {
					success = false;
				} catch (IOException e) {
					success = false;
				} catch (JSONException e) {
					success = false;
				} finally {
					try {
						out.close();
					} catch (IOException e) {
					}
				}
			} else {
				success = false;
				message = "No data available to backup.";
			}
		}
		
		if (success) {
			message = "Backup was successful, file is: \"" + backupFile + "\"";
		}
		
		showBackupDialog(context, message);
	}
	
	public synchronized static void restore(Context context) {
		boolean success = true;
		String message = "Restore failed";
		
		StreamDatabase streamdb = new StreamDatabase(context);
		
		File backupFile = getBackupFile();
		
		if (backupFile == null || !backupFile.exists()) {
			success = false;
			message = "Restore failed, make sure \"" + Environment.getExternalStorageDirectory() + BACKUP_DIRECTORY_PATH + BACKUP_FILE + "\" exists.";
		} else {
			try {
				List<UriBean> uris = parseBackupFile(backupFile);
			
				for (int i = 0; i < uris.size(); i++) {
					if (TransportFactory.findUri(streamdb, uris.get(i).getUri()) == null) {
						streamdb.saveUri(uris.get(i));
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				success = false;
			} catch (JSONException e) {
				e.printStackTrace();
				success = false;
			}
		}
		
		if (success) {
			message = "Restore was successful";
		}
		
		streamdb.close();
		
		showBackupDialog(context, message);
	}
	
	private static File getBackupFile() {
		File file = new File(Environment.getExternalStorageDirectory() + BACKUP_DIRECTORY_PATH);

	    if (!file.exists() && !file.mkdirs()) {
	    	return null;
	    }
	    
		file = new File(Environment.getExternalStorageDirectory() + BACKUP_DIRECTORY_PATH, BACKUP_FILE);
		
	    return file;
	}
	
	private static String writeJSON(List<UriBean> uris) throws JSONException {		
		JSONObject root = new JSONObject();
		JSONArray array = new JSONArray();
		JSONObject js = new JSONObject();
		
		for (int i = 0; i < uris.size(); i++) {
			UriBean uriBean = uris.get(i);				
			
			js = new JSONObject();
			js.put(StreamDatabase.FIELD_STREAM_NICKNAME, uriBean.getNickname());
			js.put(StreamDatabase.FIELD_STREAM_PROTOCOL, uriBean.getProtocol());
			js.put(StreamDatabase.FIELD_STREAM_USERNAME, uriBean.getUsername());
			js.put(StreamDatabase.FIELD_STREAM_PASSWORD, uriBean.getPassword());
			js.put(StreamDatabase.FIELD_STREAM_HOSTNAME, uriBean.getHostname());
			js.put(StreamDatabase.FIELD_STREAM_PORT, uriBean.getPort());
			js.put(StreamDatabase.FIELD_STREAM_PATH, uriBean.getPath());
			js.put(StreamDatabase.FIELD_STREAM_QUERY, uriBean.getQuery());
			js.put(StreamDatabase.FIELD_STREAM_REFERENCE, uriBean.getReference());
			js.put(StreamDatabase.FIELD_STREAM_LASTCONNECT, uriBean.getLastConnect());
			array.put(js);
		}
			
		js = new JSONObject();
		js.put(UriBean.BEAN_NAME, array);
		root.put(ROOT_JSON_ELEMENT, js);
		
		return root.toString();
	}
	
	private static List<UriBean> parseBackupFile(File backupFile) throws IOException, JSONException {
		BufferedReader br = null;
		String line;
		StringBuffer buffer = new StringBuffer();
		List<UriBean> uris = new ArrayList<UriBean>();
		
		try {
			br = new BufferedReader(new FileReader(backupFile));
			
			while ((line = br.readLine()) != null) {
				buffer.append(line);
			}
		 
			JSONObject js = new JSONObject(buffer.toString());
			JSONArray tableRows = js.getJSONObject(ROOT_JSON_ELEMENT).getJSONArray(UriBean.BEAN_NAME);
			
			for (int i = 0; i < tableRows.length(); i++) {
				JSONObject row = tableRows.getJSONObject(i);
				UriBean uriBean = new UriBean();
				
	      		@SuppressWarnings("unchecked")
				Iterator<String> iterator = row.keys();
        		
	      		while (iterator.hasNext()) {
	      			String name = iterator.next();
	      		
	      			if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_NICKNAME)) {
	    				uriBean.setNickname(row.getString(StreamDatabase.FIELD_STREAM_NICKNAME));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PROTOCOL)) {
	      				uriBean.setProtocol(row.getString(StreamDatabase.FIELD_STREAM_PROTOCOL));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_USERNAME)) {
	    				uriBean.setUsername(row.getString(StreamDatabase.FIELD_STREAM_USERNAME));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PASSWORD)) {
	    				uriBean.setPassword(row.getString(StreamDatabase.FIELD_STREAM_PASSWORD));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_HOSTNAME)) {
	      				uriBean.setHostname(row.getString(StreamDatabase.FIELD_STREAM_HOSTNAME));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PORT)) {
	      				uriBean.setPort(row.getInt(StreamDatabase.FIELD_STREAM_PORT));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_PATH)) {
	      				uriBean.setPath(row.getString(StreamDatabase.FIELD_STREAM_PATH));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_QUERY)) {
	      				uriBean.setQuery(row.getString(StreamDatabase.FIELD_STREAM_QUERY));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_REFERENCE)) {
	      				uriBean.setReference(row.getString(StreamDatabase.FIELD_STREAM_REFERENCE));
	      			} else if (name.equalsIgnoreCase(StreamDatabase.FIELD_STREAM_LASTCONNECT)) {
	      				uriBean.setLastConnect(row.getLong(StreamDatabase.FIELD_STREAM_LASTCONNECT));
	      			}
	      		}
	      		
				uris.add(uriBean);
			}
		} finally {
			Utils.closeBufferedReader(br);
		}
		
		return uris;
	}
}
