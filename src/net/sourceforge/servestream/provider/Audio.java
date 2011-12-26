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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Convenience definitions for AudioProvider
 */
public final class Audio {
    public static final String AUTHORITY = "net.sourceforge.servestream.provider.Audio";

    // This class cannot be instantiated
    private Audio() {}
    
    /**
     * Audio table
     */
    public static final class AudioColumns implements BaseColumns {
        // This class cannot be instantiated
        private AudioColumns() {}

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/uris");

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of uris.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/com.sourceforge.servestream.uri";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single uri.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/com.sourceforge.servestream.uri";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";
        
        /**
         * The URI of the file
         * <P>Type: TEXT</P>
         */
        public static final String URI = "uri";
        
        /**
         * The title of the content 
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";
        
        /**
         * The album the audio file is from, if any 
         * <P>Type: TEXT</P>
         */
        public static final String ALBUM = "album";

        /**
         * The artist who created the audio file, if any 
         * <P>Type: TEXT</P>
         */
        public static final String ARTIST = "artist";

        /**
         * The duration of the audio file, in ms 
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DURATION = "duration";

        /**
         * The track number of this song on the album, if any. This number encodes both the track number and the disc number. For multi-disc sets, this number will be 1xxx for tracks on the first disc, 2xxx for tracks on the second disc, etc.
         * <P>Type: INTEGER</P>
         */
        public static final String TRACK = "track";

        /**
         * The year the audio file was recorded, if any 
         * <P>Type: INTEGER</P>
         */
        public static final String YEAR = "year";
    }
}
