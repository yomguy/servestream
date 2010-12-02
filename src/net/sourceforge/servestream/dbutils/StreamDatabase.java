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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Contains information about various SSH hosts, include public hostkey if known
 * from previous sessions.
 *
 * @author jsharkey
 */
public class StreamDatabase extends SQLiteOpenHelper {

	public static final Object[] m_dbLock = new Object[0];
	
	private static final String	KEY_NICKNAME = "nickname";	
	private static final String	KEY_PROTOCOL = "protocol";
	private static final String	KEY_HOSTNAME = "hostname";
	private static final String	KEY_PORT = "port";
	private static final String	KEY_PATH = "path";
	private final static String KEY_LASTCONNECT = "lastconnect";
	
	private static final String	DATABASE_NAME = "servestream.db";
	public static final String	TABLE_STREAMS = "streams";
    private static final int DATABASE_VERSION = 1;
    
    private static final String STREAM_TABLE_CREATE =
                "CREATE TABLE " + TABLE_STREAMS + " (" +
				" _id INTEGER PRIMARY KEY, " +
                KEY_NICKNAME + " TEXT, " +
                KEY_PROTOCOL + " TEXT, " +
                KEY_HOSTNAME + " TEXT, " +
                KEY_PORT + " TEXT, " +
                KEY_PATH + " TEXT, " +
                KEY_LASTCONNECT + " INTEGER);";

    public StreamDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        
        this.getWritableDatabase().close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(STREAM_TABLE_CREATE);
    }

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub
		
	}
	
	private ArrayList<Stream> createStreamList(Cursor c) {
		ArrayList<Stream> streamUrls = new ArrayList<Stream>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(KEY_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(KEY_PROTOCOL),
			COL_HOST = c.getColumnIndexOrThrow(KEY_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(KEY_PORT),
			COL_PATH = c.getColumnIndexOrThrow(KEY_PATH),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(KEY_LASTCONNECT);

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
		
		selectionBuilder.append(KEY_PROTOCOL).append(" = ?");
		selectionValuesList.add(stream.getProtocol());
		selectionBuilder.append(" AND ").append(KEY_HOSTNAME).append(" = ?");
		selectionValuesList.add(stream.getHostname());
		selectionBuilder.append(" AND ").append(KEY_PORT).append(" = ?");
		selectionValuesList.add(stream.getPort());
		selectionBuilder.append(" AND ").append(KEY_PATH).append(" = ?");
		selectionValuesList.add(stream.getPath());

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
			COL_NICKNAME = c.getColumnIndexOrThrow(KEY_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(KEY_PROTOCOL),
			COL_HOSTNAME = c.getColumnIndexOrThrow(KEY_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(KEY_PORT),
			COL_PATH = c.getColumnIndexOrThrow(KEY_PATH),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(KEY_LASTCONNECT);

		while (c.moveToNext()) {
			Stream stream = new Stream();

			stream.setID(c.getLong(COL_ID));
			stream.setNickname(c.getString(COL_NICKNAME));
			stream.setProtocol(c.getString(COL_PROTOCOL));
			stream.setHostname(c.getString(COL_HOSTNAME));
			stream.setPort(c.getString(COL_PORT));
			stream.setPath(c.getString(COL_PATH));
			stream.setLastConnect(c.getLong(COL_LASTCONNECT));

			streamUrls.add(stream);
		}

		return streamUrls;
	}
	
	public Stream saveStream(Stream stream) {
		long id;

		synchronized (m_dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			ContentValues contentValues = new ContentValues();
			contentValues.put(KEY_NICKNAME, stream.getNickname());
			contentValues.put(KEY_PROTOCOL, stream.getProtocol());
			contentValues.put(KEY_HOSTNAME, stream.getHostname());
			contentValues.put(KEY_PORT, stream.getPort());
			contentValues.put(KEY_PATH, stream.getPath());
			contentValues.put(KEY_LASTCONNECT, stream.getLastConnect());
			id = db.insert(TABLE_STREAMS, null, contentValues);
		}

		stream.setID(id);
        Log.v("stream wrotee", "partydsdd");
		return stream;
	}
}
