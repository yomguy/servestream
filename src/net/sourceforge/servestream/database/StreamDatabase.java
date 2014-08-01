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

package net.sourceforge.servestream.database;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sourceforge.servestream.bean.UriBean;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

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
	public final static String FIELD_STREAM_LIST_POSITION = "listposition";
	
	public static final String	DATABASE_NAME = "servestream.db";
    private static final int DATABASE_VERSION = 5;
    
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
	            FIELD_STREAM_FONTSIZE + " INTEGER, " +
                FIELD_STREAM_LIST_POSITION + " INTEGER);";

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
			case 4:
				db.execSQL("ALTER TABLE " + TABLE_STREAMS
						+ " ADD COLUMN " + FIELD_STREAM_LIST_POSITION + " INTEGER");
				
			    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, FIELD_STREAM_ID + " ASC");

			    List<UriBean> uris = createUriBeans(c);

			    c.close();
				
				ContentValues values = new ContentValues();
				
				for (int i = 0; i < uris.size(); i++) {
					UriBean uri = uris.get(i);
					values.clear();
					values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, uri.getId());
					db.update(TABLE_STREAMS, values, FIELD_STREAM_ID + " = ?", new String [] { String.valueOf(uri.getId()) });
				}
				break;
		}
	}
	
	/**
	 * Touch a specific stream to update its "last connected" field.
	 * 
	 * @param stream Nickname field of stream to update
	 */
	public void touchUri(UriBean uri) {
		long now = System.currentTimeMillis() / 1000;

		ContentValues values = new ContentValues();
		values.put(FIELD_STREAM_LASTCONNECT, now);

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			db.update(TABLE_STREAMS, values, FIELD_STREAM_ID + " = ?", new String[] { String.valueOf(uri.getId()) });
		}
	}
	
	/**
	 * Touch a specific stream to update its "last connected" field.
	 * 
	 * @param stream Nickname field of stream to update
	 */
	public void updateUri(UriBean uri, ContentValues values) {
		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			db.update(TABLE_STREAMS, values, FIELD_STREAM_ID + " = ?", new String[] { String.valueOf(uri.getId()) });
		}
	}
	
	private List<UriBean> createUriBeans(Cursor c) {
		List<UriBean> uris = new ArrayList<UriBean>();

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
			COL_LISTPOSITION = c.getColumnIndexOrThrow(FIELD_STREAM_LIST_POSITION);

		while (c.moveToNext()) {
			UriBean uri = new UriBean();

			uri.setId(c.getLong(COL_ID));
			uri.setNickname(c.getString(COL_NICKNAME));
			uri.setProtocol(c.getString(COL_PROTOCOL));
			uri.setUsername(c.getString(COL_USERNAME));
			uri.setPassword(c.getString(COL_PASSWORD));
			uri.setHostname(c.getString(COL_HOST));
			uri.setPort(Integer.valueOf(c.getString(COL_PORT)));
			uri.setPath(c.getString(COL_PATH));
			uri.setQuery(c.getString(COL_QUERY));
			uri.setReference(c.getString(COL_REFERENCE));
			uri.setLastConnect(c.getLong(COL_LASTCONNECT));
			uri.setListPosition(c.getInt(COL_LISTPOSITION));

			uris.add(uri);
		}

		return uris;
	}
	
	public List<UriBean> getUris() {
		List<UriBean> uris = new ArrayList<UriBean>();
		
		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

		    Cursor c = db.query(TABLE_STREAMS, null, null, null, null, null, FIELD_STREAM_LIST_POSITION + " ASC");

		    uris = createUriBeans(c);

		    c.close();
		}

		return uris;
	}
	
	public void deleteUri(UriBean uri) {
		if (uri.getId() < 0) {
			return;
		}

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();
			db.delete(TABLE_STREAMS, FIELD_STREAM_ID + " = ?", new String[] { String.valueOf(uri.getId()) });
		}
	}
	
	public UriBean findUri(Map<String, String> selection) {
		StringBuffer sb = new StringBuffer();
		sb.append("SELECT * from ")
		.append(TABLE_STREAMS)
		.append(" where ");
		
		ArrayList<String> selectionValuesList = new ArrayList<String>();
		
		Iterator<Entry<String, String>> i = selection.entrySet().iterator();
		int n = 0;
		
		while(i.hasNext()) {
			Entry<String, String> entry = i.next();
			
			if (n++ > 0)
				sb.append(" AND ");

			sb.append(entry.getKey());

			if (entry.getValue() == null) {
				sb.append(" IS NULL");
			} else {
				selectionValuesList.add(entry.getValue());
				sb.append(" = ?");
			}
		}

		String selectionValues[] = new String[selectionValuesList.size()];
		selectionValuesList.toArray(selectionValues);
		selectionValuesList = null;

		UriBean uri;

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

	    	Cursor c = db.rawQuery(sb.toString(), selectionValues);
			
			uri = getFirstUriBean(c);
		}

		return uri;
	}
	
	public UriBean findUri(int id) {
		UriBean uri;
		
		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_STREAMS, null,
					FIELD_STREAM_ID + " = ?",
					new String[] { String.valueOf(id) },
					null, null, null);

			uri = getFirstUriBean(c);
		}

		return uri;
	}
	
	private UriBean getFirstUriBean(Cursor c) {
		UriBean uri = null;

		List<UriBean> uris = createUriBeans(c);
		if (uris.size() > 0)
			uri = uris.get(0);

		c.close();

		return uri;
	}
	
	public UriBean saveUri(UriBean uri) {
		long id;

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			id = db.insert(TABLE_STREAMS, null, uri.getValues());
			
			ContentValues values = new ContentValues();
			values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, id);
			db.update(TABLE_STREAMS, values, FIELD_STREAM_ID + " = ?", new String [] { String.valueOf(id) });
		}

		uri.setId(id);
		uri.setListPosition((int) id);
		
        Log.v("TAG", "Uri wrote to database");
		return uri;
	}
}
