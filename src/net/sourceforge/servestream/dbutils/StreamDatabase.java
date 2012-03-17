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
	public final static String TAG = StreamDatabase.class.getName();

	public static final Object[] dbLock = new Object[0];
	
	public static final String TABLE_STREAMS = "streams";
	public static final String FIELD_STREAM_ID = "_id";
	public static final String FIELD_STREAM_NICKNAME = "nickname";	
	public static final String FIELD_STREAM_PROTOCOL = "protocol";
	public static final String FIELD_STREAM_USERNAME = "username";
	public static final String FIELD_STREAM_PASSWORD = "password";
	public static final String FIELD_STREAM_HOSTNAME = "hostname";
	public static final String FIELD_STREAM_PORT = "port";
	public static final String FIELD_STREAM_PATH = "path";
	public static final String FIELD_STREAM_QUERY = "query";
	public static final String FIELD_STREAM_REFERENCE = "reference";
	public final static String FIELD_STREAM_LASTCONNECT = "lastconnect";
	public final static String FIELD_STREAM_COLOR = "color";
	public final static String FIELD_STREAM_FONTSIZE = "fontsize";
	
	public static final String	DATABASE_NAME = "servestream.db";
    private static final int DATABASE_VERSION = 4;
    
    private static final String STREAM_TABLE_CREATE =
                "CREATE TABLE " + TABLE_STREAMS + " (" +
				FIELD_STREAM_ID + " INTEGER PRIMARY KEY, " +
				FIELD_STREAM_NICKNAME + " TEXT, " +
				FIELD_STREAM_PROTOCOL + " TEXT, " +
				FIELD_STREAM_USERNAME + " TEXT, " +
				FIELD_STREAM_PASSWORD + " TEXT, " +
				FIELD_STREAM_HOSTNAME + " TEXT, " +
				FIELD_STREAM_PORT + " TEXT, " +
				FIELD_STREAM_PATH + " TEXT, " +
				FIELD_STREAM_QUERY + " TEXT, " +
				FIELD_STREAM_REFERENCE + " TEXT, " +
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
		
		if (oldVersion <= 2) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_STREAMS);
			onCreate(db);
		}
		
		switch (oldVersion) {
			case 3:
				db.execSQL("ALTER TABLE " + TABLE_STREAMS
						+ " ADD COLUMN " + FIELD_STREAM_REFERENCE + " TEXT DEFAULT ''");
				break;
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

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			db.update(TABLE_STREAMS, values, FIELD_STREAM_ID + " = ?", new String[] { String.valueOf(stream.getId()) });
		}
	}
	
	private ArrayList<Stream> createStreamList(Cursor c) {
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();

		final int COL_ID = c.getColumnIndexOrThrow(FIELD_STREAM_ID),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_STREAM_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_STREAM_PROTOCOL),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_STREAM_USERNAME),
			COL_PASSWORD = c.getColumnIndexOrThrow(FIELD_STREAM_PASSWORD),
			COL_HOST = c.getColumnIndexOrThrow(FIELD_STREAM_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_STREAM_PORT),
			COL_PATH = c.getColumnIndexOrThrow(FIELD_STREAM_PATH),
			COL_QUERY = c.getColumnIndexOrThrow(FIELD_STREAM_QUERY),
			COL_REFERENCE = c.getColumnIndexOrThrow(FIELD_STREAM_REFERENCE),
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
			stream.setReference(c.getString(COL_REFERENCE));
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));
			stream.setColor(c.getString(COL_COLOR));
			stream.setFontSize(c.getLong(COL_FONTSIZE));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
	
	public ArrayList<Stream> getStreams() {
		return getStreams(false);
	}
	
	
	public ArrayList<Stream> getStreams(boolean sortByName) {
		String sortField = sortByName ? FIELD_STREAM_NICKNAME : FIELD_STREAM_ID;
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();
		
		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

		    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, sortField + " ASC");

		    streamUrls = createStreamList(c);

		    c.close();
		}

		return streamUrls;
	}
	
	public void deleteStream(Stream stream) {
		if (stream.getId() < 0)
			return;

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();
			db.delete(TABLE_STREAMS, FIELD_STREAM_ID + " = ?", new String[] { String.valueOf(stream.getId()) });
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

		String selectionValues[] = new String[selectionValuesList.size()];
		selectionValuesList.toArray(selectionValues);
		selectionValuesList = null;

		Stream returnedStream;

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_STREAMS, null,
					selectionBuilder.toString(),
					selectionValues,
					null, null, null);

			returnedStream = getFirstStream(c);
		}

		return returnedStream;
	}
	
	public Stream findStream(int id) {
		Stream returnedStream;
		
		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_STREAMS, null,
					FIELD_STREAM_ID + " = ?",
					new String[] { String.valueOf(id) },
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

		final int COL_ID = c.getColumnIndexOrThrow(FIELD_STREAM_ID),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_STREAM_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_STREAM_PROTOCOL),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_STREAM_USERNAME),
			COL_PASSWORD = c.getColumnIndexOrThrow(FIELD_STREAM_PASSWORD),
			COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_STREAM_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_STREAM_PORT),
			COL_PATH = c.getColumnIndexOrThrow(FIELD_STREAM_PATH),
			COL_QUERY = c.getColumnIndexOrThrow(FIELD_STREAM_QUERY),
			COL_REFERENCE = c.getColumnIndexOrThrow(FIELD_STREAM_REFERENCE),
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
			stream.setReference(c.getString(COL_REFERENCE));
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));
			stream.setColor(c.getString(COL_COLOR));
			stream.setFontSize(c.getLong(COL_FONTSIZE));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
	
	public Stream saveStream(Stream stream) {
		long id;

		SQLiteDatabase db = null;
		ContentValues contentValues = null;
		
		synchronized (dbLock) {
			db = this.getWritableDatabase();

			contentValues = new ContentValues();
			contentValues.put(FIELD_STREAM_NICKNAME, stream.getNickname());
			contentValues.put(FIELD_STREAM_PROTOCOL, stream.getProtocol());
			contentValues.put(FIELD_STREAM_USERNAME, stream.getUsername());
			contentValues.put(FIELD_STREAM_PASSWORD, stream.getPassword());
			contentValues.put(FIELD_STREAM_HOSTNAME, stream.getHostname());
			contentValues.put(FIELD_STREAM_PORT, stream.getPort());
			contentValues.put(FIELD_STREAM_PATH, stream.getPath());
			contentValues.put(FIELD_STREAM_QUERY, stream.getQuery());
			contentValues.put(FIELD_STREAM_REFERENCE, stream.getReference());
			contentValues.put(FIELD_STREAM_LASTCONNECT, stream.getLastConnect());
			contentValues.put(FIELD_STREAM_COLOR, stream.getColor());
			contentValues.put(FIELD_STREAM_FONTSIZE, stream.getFontSize());
			id = db.insert(TABLE_STREAMS, null, contentValues);
		}

		stream.setID(id);
		
		synchronized (dbLock) {
			db = null;
			contentValues = new ContentValues();
			
			if (stream.getUsername() != null) {
			    if (stream.getUsername().equals(""))
				    contentValues.put(FIELD_STREAM_USERNAME, "");
			}
			
			if (stream.getPassword() != null) {
			    if (stream.getPassword().equals(""))
				    contentValues.put(FIELD_STREAM_PASSWORD, "");
			}
			
			if (stream.getQuery() != null) {
			    if (stream.getQuery().equals(""))
				    contentValues.put(FIELD_STREAM_QUERY, "");
			}

			if (stream.getReference() != null) {
			    if (stream.getReference().equals(""))
				    contentValues.put(FIELD_STREAM_REFERENCE, "");
			}
			
			if (contentValues.size() > 0) {
				Log.v(TAG, "Replacing null values");
			    db = this.getWritableDatabase();
			    db.update(TABLE_STREAMS, contentValues, FIELD_STREAM_ID + " = ?", new String[] { String.valueOf(stream.getId()) });
			}
		}
		
        Log.v("TAG", "Stream wrote to database");
		return stream;
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
		selection.put(FIELD_STREAM_REFERENCE, stream.getReference());
		
		return selection;
	}

}
