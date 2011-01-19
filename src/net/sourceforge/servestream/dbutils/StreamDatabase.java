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
/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

package net.sourceforge.servestream.dbutils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Contains information about various SSH hosts, include public hostkey if known
 * from previous sessions.
 *
 * @author jsharkey
 */
public class StreamDatabase extends SQLiteOpenHelper {
	public final static String TAG = "ServeStream.StreamDatabase";

	public static final Object[] m_dbLock = new Object[0];
	
	public static final String TABLE_STREAMS = "streams";
	public static final String FIELD_STREAM_NICKNAME = "nickname";	
	public static final String FIELD_STREAM_PROTOCOL = "protocol";
	public static final String FIELD_STREAM_USERNAME = "username";
	public static final String FIELD_STREAM_PASSWORD = "password";
	public static final String FIELD_STREAM_HOSTNAME = "hostname";
	public static final String FIELD_STREAM_PORT = "port";
	public static final String FIELD_STREAM_PATH = "path";
	public static final String FIELD_STREAM_QUERY = "query";
	public final static String FIELD_STREAM_LASTCONNECT = "lastconnect";
	public final static String FIELD_STREAM_COLOR = "color";
	public final static String FIELD_STREAM_FONTSIZE = "fontsize";
	
	private static final String	DATABASE_NAME = "servestream.db";
    private static final int DATABASE_VERSION = 3;
    
    private static final String STREAM_TABLE_CREATE =
                "CREATE TABLE " + TABLE_STREAMS + " (" +
				" _id INTEGER PRIMARY KEY, " +
				FIELD_STREAM_NICKNAME + " TEXT, " +
				FIELD_STREAM_PROTOCOL + " TEXT, " +
				FIELD_STREAM_USERNAME + " TEXT, " +
				FIELD_STREAM_PASSWORD + " TEXT, " +
				FIELD_STREAM_HOSTNAME + " TEXT, " +
				FIELD_STREAM_PORT + " TEXT, " +
				FIELD_STREAM_PATH + " TEXT, " +
				FIELD_STREAM_QUERY + " TEXT, " +
				FIELD_STREAM_LASTCONNECT + " INTEGER, " +
                FIELD_STREAM_COLOR + " TEXT, " +
	            FIELD_STREAM_FONTSIZE + " INTEGER);";

    public StreamDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        
        this.getWritableDatabase().close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(STREAM_TABLE_CREATE);
        Log.v(TAG, "new table created");
    }

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		try {
			onRobustUpgrade(db, oldVersion, newVersion);
		} catch (SQLiteException e) {
			// The database has entered an unknown state. Try to recover.
			try {
				//regenerateTables(db);
			} catch (SQLiteException e2) {
				//dropAndCreateTables(db);
			}
		}
	}
	
	public void onRobustUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) throws SQLiteException {
		
		if (oldVersion == 1) {
			
		    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, null);

			ArrayList<Stream> streams = createOldStreamList(c);

		    c.close();
			
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_STREAMS);
			onCreate(db);
			
			for (int i = 0; i < streams.size(); i++) {
				Log.v(TAG, "writing: " + streams.get(i).getNickname());
				saveOldStream(streams.get(i), db);
			}
			
			return;
		}
		
		if (oldVersion == 2) {
			
		    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, null);

			ArrayList<Stream> streams = createOldStreamList2(c);

		    c.close();
			
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_STREAMS);
			onCreate(db);
			
			for (int i = 0; i < streams.size(); i++) {
				Log.v(TAG, "writing: " + streams.get(i).getNickname());
				saveOldStream2(streams.get(i), db);
			}
			
			return;
		}
	}
	
	/**
	 * Touch a specific stream to update its "last connected" field.
	 * 
	 * @param stream Nickname field of stream to update
	 */
	public void touchHost(Stream stream) {
		long now = System.currentTimeMillis() / 1000;

		ContentValues values = new ContentValues();
		values.put(FIELD_STREAM_LASTCONNECT, now);

		synchronized (m_dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			db.update(TABLE_STREAMS, values, "_id = ?", new String[] { String.valueOf(stream.getId()) });
		}
	}
	
	private ArrayList<Stream> createStreamList(Cursor c) {
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_STREAM_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_STREAM_PROTOCOL),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_STREAM_USERNAME),
			COL_PASSWORD = c.getColumnIndexOrThrow(FIELD_STREAM_PASSWORD),
			COL_HOST = c.getColumnIndexOrThrow(FIELD_STREAM_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_STREAM_PORT),
			COL_PATH = c.getColumnIndexOrThrow(FIELD_STREAM_PATH),
			COL_QUERY = c.getColumnIndexOrThrow(FIELD_STREAM_QUERY),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_STREAM_LASTCONNECT),
		    COL_COLOR = c.getColumnIndexOrThrow(FIELD_STREAM_COLOR),
		    COL_FONTSIZE = c.getColumnIndexOrThrow(FIELD_STREAM_FONTSIZE);

		while (c.moveToNext()) {
			Stream stream = new Stream();

			stream.setID(c.getLong(COL_ID));
			stream.setNickname(c.getString(COL_NICKNAME));
			stream.setProtocol(c.getString(COL_PROTOCOL));
			stream.setUsername(c.getString(COL_USERNAME));
			stream.setPassword(c.getString(COL_PASSWORD));
			stream.setHostname(c.getString(COL_HOST));
			stream.setPort(c.getString(COL_PORT));
			stream.setPath(c.getString(COL_PATH));
			stream.setQuery(c.getString(COL_QUERY));
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));
			stream.setColor(c.getString(COL_COLOR));
			stream.setFontSize(c.getLong(COL_FONTSIZE));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
	
	public ArrayList<Stream> getStreams() {
		
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();
		
		synchronized (m_dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

		    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, null);

		    streamUrls = createStreamList(c);

		    c.close();
		}

		return streamUrls;
	}
	
	public void deleteStream(Stream stream) {
		if (stream.getId() < 0)
			return;

		synchronized (m_dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();
			db.delete(TABLE_STREAMS, "_id = ?", new String[] { String.valueOf(stream.getId()) });
		}
	}
	
	public Stream findStream(Stream stream) {
		StringBuilder selectionBuilder = new StringBuilder();

		ArrayList<String> selectionValuesList = new ArrayList<String>();
		
		HashMap<String, String> selection = getSelectionArgs(stream);
		
		Iterator<Entry<String, String>> i = selection.entrySet().iterator();
		int n = 0;
		
		while(i.hasNext()) {
			Entry<String, String> entry = i.next();
			
			if (n++ > 0)
				selectionBuilder.append(" AND ");

			selectionBuilder.append(entry.getKey())
				.append(" = ?");

			selectionValuesList.add(entry.getValue());
		}
		
		/*selectionBuilder.append(FIELD_STREAM_PROTOCOL).append(" = ?");
		selectionValuesList.add(stream.getProtocol());
		selectionBuilder.append(" AND ").append(FIELD_STREAM_HOSTNAME).append(" = ?");
		selectionValuesList.add(stream.getHostname());
		selectionBuilder.append(" AND ").append(FIELD_STREAM_PORT).append(" = ?");
		selectionValuesList.add(stream.getPort());
		selectionBuilder.append(" AND ").append(FIELD_STREAM_PATH).append(" = ?");
		selectionValuesList.add(stream.getPath());*/

		String selectionValues[] = new String[selectionValuesList.size()];
		selectionValuesList.toArray(selectionValues);
		selectionValuesList = null;

		Stream returnedStream;

		synchronized (m_dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_STREAMS, null,
					selectionBuilder.toString(),
					selectionValues,
					null, null, null);

			returnedStream = getFirstStream(c);
		}

		return returnedStream;
	}
	
	private Stream getFirstStream(Cursor c) {
		Stream stream = null;

		ArrayList<Stream> streamUrls = createStreams(c);
		if (streamUrls.size() > 0)
			stream = streamUrls.get(0);

		c.close();

		return stream;
	}
	
	private ArrayList<Stream> createStreams(Cursor c) {
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_STREAM_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_STREAM_PROTOCOL),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_STREAM_USERNAME),
			COL_PASSWORD = c.getColumnIndexOrThrow(FIELD_STREAM_PASSWORD),
			COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_STREAM_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_STREAM_PORT),
			COL_PATH = c.getColumnIndexOrThrow(FIELD_STREAM_PATH),
			COL_QUERY = c.getColumnIndexOrThrow(FIELD_STREAM_QUERY),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_STREAM_LASTCONNECT),
		    COL_COLOR = c.getColumnIndexOrThrow(FIELD_STREAM_COLOR),
		    COL_FONTSIZE = c.getColumnIndexOrThrow(FIELD_STREAM_FONTSIZE);

		while (c.moveToNext()) {
			Stream stream = new Stream();

			stream.setID(c.getLong(COL_ID));
			stream.setNickname(c.getString(COL_NICKNAME));
			stream.setProtocol(c.getString(COL_PROTOCOL));
			stream.setUsername(c.getString(COL_USERNAME));
			stream.setPassword(c.getString(COL_PASSWORD));
			stream.setHostname(c.getString(COL_HOSTNAME));
			stream.setPort(c.getString(COL_PORT));
			stream.setPath(c.getString(COL_PATH));
			stream.setQuery(c.getString(COL_QUERY));
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));
			stream.setColor(c.getString(COL_COLOR));
			stream.setFontSize(c.getLong(COL_FONTSIZE));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
	
	public Stream saveStream(Stream stream) {
		long id;

		synchronized (m_dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			ContentValues contentValues = new ContentValues();
			contentValues.put(FIELD_STREAM_NICKNAME, stream.getNickname());
			contentValues.put(FIELD_STREAM_PROTOCOL, stream.getProtocol());
			contentValues.put(FIELD_STREAM_USERNAME, stream.getUsername());
			contentValues.put(FIELD_STREAM_PASSWORD, stream.getPassword());
			contentValues.put(FIELD_STREAM_HOSTNAME, stream.getHostname());
			contentValues.put(FIELD_STREAM_PORT, stream.getPort());
			contentValues.put(FIELD_STREAM_PATH, stream.getPath());
			contentValues.put(FIELD_STREAM_QUERY, stream.getQuery());
			contentValues.put(FIELD_STREAM_LASTCONNECT, stream.getLastConnect());
			contentValues.put(FIELD_STREAM_COLOR, stream.getColor());
			contentValues.put(FIELD_STREAM_FONTSIZE, stream.getFontSize());
			id = db.insert(TABLE_STREAMS, null, contentValues);
		}

		stream.setID(id);
		
        replaceNullFields(stream);
		
        Log.v("TAG", "Stream wrote to database");
		return stream;
	}
	
	private void replaceNullFields(Stream stream) {
		synchronized (m_dbLock) {
			ContentValues contentValues = new ContentValues();
				
			if (stream.getUsername().equals(""))
				contentValues.put(FIELD_STREAM_USERNAME, "");
			
			if (stream.getPassword().equals(""))
				contentValues.put(FIELD_STREAM_PASSWORD, "");
			
			if (stream.getQuery().equals(""))
				contentValues.put(FIELD_STREAM_QUERY, "");
			
			if (contentValues.size() > 0) {
				Log.v(TAG, "Replacing null values");
			    SQLiteDatabase db = this.getWritableDatabase();
			    db.update(TABLE_STREAMS, contentValues, "_id = ?", new String[] { String.valueOf(stream.getId()) });
			}
		}
	}
	
	public HashMap<String, String> getSelectionArgs(Stream stream) {
		HashMap<String, String> selection = new HashMap<String, String>();
		
		//selection.put(FIELD_STREAM_NICKNAME, stream.getNickname());
		selection.put(FIELD_STREAM_PROTOCOL, stream.getProtocol());
		selection.put(FIELD_STREAM_USERNAME, stream.getUsername());
		selection.put(FIELD_STREAM_PASSWORD, stream.getPassword());
		selection.put(FIELD_STREAM_HOSTNAME, stream.getHostname());
		selection.put(FIELD_STREAM_PORT, stream.getPort());
		selection.put(FIELD_STREAM_PATH, stream.getPath());
		selection.put(FIELD_STREAM_QUERY, stream.getQuery());
		
		return selection;
	}
	
    public ArrayList<Stream> getOldStreams() {
		
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();
		
		synchronized (m_dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

		    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, null);

		    streamUrls = createOldStreamList(c);

		    c.close();
		}

		return streamUrls;
	}
    
    private ArrayList<Stream> createOldStreamList(Cursor c) {
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_STREAM_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_STREAM_PROTOCOL),
			COL_HOST = c.getColumnIndexOrThrow(FIELD_STREAM_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_STREAM_PORT),
			COL_PATH = c.getColumnIndexOrThrow(FIELD_STREAM_PATH),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_STREAM_LASTCONNECT);

		while (c.moveToNext()) {
			Stream stream = new Stream();

			stream.setID(c.getLong(COL_ID));
			stream.setNickname(c.getString(COL_NICKNAME));
			stream.setProtocol(c.getString(COL_PROTOCOL));
			stream.setHostname(c.getString(COL_HOST));
			stream.setPort(c.getString(COL_PORT));
			stream.setPath(c.getString(COL_PATH));
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
    
    public Stream saveOldStream(Stream stream, SQLiteDatabase db) {
		long id;

			ContentValues contentValues = new ContentValues();
			contentValues.put(FIELD_STREAM_NICKNAME, stream.getNickname());
			contentValues.put(FIELD_STREAM_PROTOCOL, stream.getProtocol());
			contentValues.put(FIELD_STREAM_HOSTNAME, stream.getHostname());
			contentValues.put(FIELD_STREAM_PORT, stream.getPort());
			contentValues.put(FIELD_STREAM_PATH, stream.getPath());
			contentValues.put(FIELD_STREAM_QUERY, stream.getQuery());
			contentValues.put(FIELD_STREAM_LASTCONNECT, stream.getLastConnect());
			contentValues.put(FIELD_STREAM_COLOR, stream.getColor());
			contentValues.put(FIELD_STREAM_FONTSIZE, stream.getFontSize());
			id = db.insert(TABLE_STREAMS, null, contentValues);

		stream.setID(id);
		
        Log.v("TAG", "Stream wrote to database");
		return stream;
	}
    
    private ArrayList<Stream> createOldStreamList2(Cursor c) {
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_STREAM_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_STREAM_PROTOCOL),
			COL_HOST = c.getColumnIndexOrThrow(FIELD_STREAM_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_STREAM_PORT),
			COL_PATH = c.getColumnIndexOrThrow(FIELD_STREAM_PATH),
			COL_QUERY = c.getColumnIndexOrThrow(FIELD_STREAM_QUERY),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_STREAM_LASTCONNECT),
			COL_COLOR = c.getColumnIndexOrThrow(FIELD_STREAM_COLOR),
			COL_FONTSIZE = c.getColumnIndexOrThrow(FIELD_STREAM_FONTSIZE);

		while (c.moveToNext()) {
			Stream stream = new Stream();

			stream.setID(c.getLong(COL_ID));
			stream.setNickname(c.getString(COL_NICKNAME));
			stream.setProtocol(c.getString(COL_PROTOCOL));
			stream.setHostname(c.getString(COL_HOST));
			stream.setPort(c.getString(COL_PORT));
			stream.setPath(c.getString(COL_PATH));				
			stream.setQuery(c.getString(COL_QUERY));
			
			if (stream.getQuery() != null && stream.getQuery().equals(""))
				stream.setQuery(null);
			
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));
			stream.setColor(c.getString(COL_COLOR));
			stream.setFontSize(c.getLong(COL_FONTSIZE));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
    
    public Stream saveOldStream2(Stream stream, SQLiteDatabase db) {
		long id;

			ContentValues contentValues = new ContentValues();
			contentValues.put(FIELD_STREAM_NICKNAME, stream.getNickname());
			contentValues.put(FIELD_STREAM_PROTOCOL, stream.getProtocol());
			contentValues.put(FIELD_STREAM_USERNAME, stream.getUsername());
			contentValues.put(FIELD_STREAM_PASSWORD, stream.getPassword());
			contentValues.put(FIELD_STREAM_HOSTNAME, stream.getHostname());
			contentValues.put(FIELD_STREAM_PORT, stream.getPort());
			contentValues.put(FIELD_STREAM_PATH, stream.getPath());
			contentValues.put(FIELD_STREAM_QUERY, stream.getQuery());
			contentValues.put(FIELD_STREAM_LASTCONNECT, stream.getLastConnect());
			contentValues.put(FIELD_STREAM_COLOR, stream.getColor());
			contentValues.put(FIELD_STREAM_FONTSIZE, stream.getFontSize());
			id = db.insert(TABLE_STREAMS, null, contentValues);

		stream.setID(id);
		
		replaceNullFields(stream);
		
        Log.v("TAG", "Stream wrote to database");
		return stream;
	}
}
