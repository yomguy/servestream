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

package net.sourceforge.servestream.provider;

import net.sourceforge.servestream.provider.Audio.AudioColumns;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * Provides access to a database of notes. Each note has a title, the note
 * itself, a creation date and a modified data.
 */
public class AudioProvider extends ContentProvider {

    private static final String TAG = "AudioProvider";

    private static final String DATABASE_NAME = "audio.db";
    private static final int DATABASE_VERSION = 2;
    private static final String AUDIO_TABLE_NAME = "uris";

    private static HashMap<String, String> sNotesProjectionMap;

    private static final int AUDIO = 1;
    private static final int AUDIO_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + AUDIO_TABLE_NAME + " ("
                    + AudioColumns._ID + " INTEGER PRIMARY KEY,"
                    + AudioColumns.URI + " TEXT,"
                    + AudioColumns.TITLE + " TEXT,"
                    + AudioColumns.ALBUM + " TEXT,"
                    + AudioColumns.ARTIST + " TEXT,"
                    + AudioColumns.DURATION + " INTEGER,"
                    + AudioColumns.TRACK + " INTEGER,"
                    + AudioColumns.YEAR + " INTEGER"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS notes");
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(AUDIO_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
        case AUDIO:
            qb.setProjectionMap(sNotesProjectionMap);
            break;

        case AUDIO_ID:
            qb.setProjectionMap(sNotesProjectionMap);
            qb.appendWhere(AudioColumns._ID + "=" + uri.getPathSegments().get(1));
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = AudioColumns.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case AUDIO:
            return AudioColumns.CONTENT_TYPE;

        case AUDIO_ID:
            return AudioColumns.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != AUDIO) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        // Make sure that the fields are all set
        if (values.containsKey(AudioColumns.URI) == false) {
            values.put(AudioColumns.URI, "Unknown");
        }
        
        if (values.containsKey(AudioColumns.TITLE) == false) {
            values.put(AudioColumns.TITLE, "Unknown");
        }
        
        if (values.containsKey(AudioColumns.ALBUM) == false) {
            values.put(AudioColumns.ALBUM, "Unknown");
        }
        
        if (values.containsKey(AudioColumns.ARTIST) == false) {
            values.put(AudioColumns.ARTIST, "Unknown");
        }
        
        if (values.containsKey(AudioColumns.DURATION) == false) {
            values.put(AudioColumns.DURATION, -1);
        }
        
        if (values.containsKey(AudioColumns.TRACK) == false) {
            values.put(AudioColumns.TRACK, -1);
        }
        
        if (values.containsKey(AudioColumns.YEAR) == false) {
            values.put(AudioColumns.YEAR, -1);
        }

        if (values.containsKey(AudioColumns._ID) == false) {
            values.put(AudioColumns._ID, 99);
        }
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(AUDIO_TABLE_NAME, AudioColumns.URI, values);
        if (rowId > 0) {
            Uri audioUri = ContentUris.withAppendedId(AudioColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(audioUri, null);
            return audioUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case AUDIO:
            count = db.delete(AUDIO_TABLE_NAME, where, whereArgs);
            break;

        case AUDIO_ID:
            String noteId = uri.getPathSegments().get(1);
            count = db.delete(AUDIO_TABLE_NAME, AudioColumns._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case AUDIO:
            count = db.update(AUDIO_TABLE_NAME, values, where, whereArgs);
            break;

        case AUDIO_ID:
            String noteId = uri.getPathSegments().get(1);
            count = db.update(AUDIO_TABLE_NAME, values, AudioColumns._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Audio.AUTHORITY, "uris", AUDIO);
        sUriMatcher.addURI(Audio.AUTHORITY, "uris/#", AUDIO_ID);

        sNotesProjectionMap = new HashMap<String, String>();
        sNotesProjectionMap.put(AudioColumns._ID, AudioColumns._ID);
        sNotesProjectionMap.put(AudioColumns.URI, AudioColumns.URI);
        sNotesProjectionMap.put(AudioColumns.TITLE, AudioColumns.TITLE);
        sNotesProjectionMap.put(AudioColumns.ALBUM, AudioColumns.ALBUM);
        sNotesProjectionMap.put(AudioColumns.ARTIST, AudioColumns.ARTIST);
        sNotesProjectionMap.put(AudioColumns.DURATION, AudioColumns.DURATION);
        sNotesProjectionMap.put(AudioColumns.TRACK, AudioColumns.TRACK);
        sNotesProjectionMap.put(AudioColumns.YEAR, AudioColumns.YEAR);
    }
}
