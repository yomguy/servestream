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

package net.sourceforge.servestream.activity;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.dslv.DragSortController;
import net.sourceforge.servestream.dslv.DragSortListView;
import net.sourceforge.servestream.dslv.SimpleDragSortCursorAdapter;
import net.sourceforge.servestream.provider.Media;
import net.sourceforge.servestream.service.IMediaPlaybackService;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.PreferenceConstants;
import net.sourceforge.servestream.utils.Utils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;

import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.AbstractCursor;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import java.util.Arrays;

public class NowPlayingActivity extends ActionBarActivity implements
			View.OnCreateContextMenuListener,
			MusicUtils.Defs, ServiceConnection {
	
    private static final String TAG = NowPlayingActivity.class.getName();
    
    private DragSortListView mList;
    
    private boolean mIsDragging = false;
    private boolean mShouldRefresh = false;
    private static boolean mDeletedOneRow = false;
    private String mCurrentTrackName;
    private static Cursor mTrackCursor;
    private TrackListAdapter mAdapter;
    private boolean mAdapterSent = false;
    private int mSelectedPosition;
    private ServiceToken mToken;
    private SharedPreferences mPreferences;
    
    String[] mCursorCols = new String[] {
            Media.MediaColumns._ID,             // index must match IDCOLIDX below
            Media.MediaColumns.URI,
            Media.MediaColumns.TITLE,
            Media.MediaColumns.ALBUM,
            Media.MediaColumns.ARTIST,
            Media.MediaColumns.DURATION
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_now_playing);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
        
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        mList = (DragSortListView) findViewById(android.R.id.list);
        mList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position,
					long arg3) {
				if (mTrackCursor.getCount() == 0) {
		            return;
		        }
		        
		        // When selecting a track from the queue, just jump there instead of
		        // reloading the queue. This is both faster, and prevents accidentally
		        // dropping out of party shuffle.
		        if (MusicUtils.sService != null) {
		            try {
		                int queuePosition = MusicUtils.sService.getQueuePosition();
		                	
		                if (position == queuePosition) {
		                	if (MusicUtils.sService.isPlaying()) {
		                        MusicUtils.sService.pause();
		                    } else {
		                        MusicUtils.sService.play();
		                    }
		                } else {
		                	MusicUtils.sService.setQueuePosition(position);
		                }
		            } catch (RemoteException ex) {
		            }
		        }
				
			}
		});
		
	    DragSortController controller = new DragSortController(mList);
		controller.setDragInitMode(DragSortController.ON_DRAG);
		controller.setRemoveMode(DragSortController.FLING_REMOVE);
        controller.setRemoveEnabled(true);
		controller.setDragHandleId(R.id.drag);
		mList.setOnTouchListener(controller);
		mList.setOnCreateContextMenuListener(this);
		
        mAdapter = (TrackListAdapter) getLastCustomNonConfigurationInstance();
        
        if (mAdapter != null) {
            mAdapter.setActivity(this);
            mList.setAdapter(mAdapter);
        }
        
        mToken = MusicUtils.bindToService(this, this);
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        if (mAdapter == null) {
            //Log.i("@@@", "starting query");
            mAdapter = new TrackListAdapter(
                    getApplication(), // need to use application context to avoid leaks
                    this,
                    R.layout.now_playing_item,
                    null, // cursor
                    new String[] {},
                    new int[] {});
            mList.setAdapter(mAdapter);
            getTrackCursor(mAdapter.getQueryHandler(), null, true);
        } else {
            mTrackCursor = mAdapter.getCursor();
            // If mTrackCursor is null, this can be because it doesn't have
            // a cursor yet (because the initial query that sets its cursor
            // is still in progress), or because the query failed.
            // In order to not flash the error dialog at the user for the
            // first case, simply retry the query when the cursor is null.
            // Worst case, we end up doing the same query twice.
            if (mTrackCursor != null) {
                init(mTrackCursor, false);
            } else {
                getTrackCursor(mAdapter.getQueryHandler(), null, true);
            }
        }
    }
    
    public void onServiceDisconnected(ComponentName name) {
        finish();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        TrackListAdapter a = mAdapter;
        mAdapterSent = true;
        return a;
    }
    
    @Override
    public void onDestroy() {
        ListView lv = mList;
        
        if (lv != null) {
            // clear the listeners so we won't get any more callbacks
            ((DragSortListView) lv).setDropListener(null);
            ((DragSortListView) lv).setRemoveListener(null);
        }

        MusicUtils.unbindFromService(mToken);
        
        try {
        	unregisterReceiver(mNowPlayingListener);
        } catch (IllegalArgumentException ex) {
            // we end up here in case we never registered the listeners
        }
        
        // If we have an adapter and didn't send it off to another activity yet, we should
        // close its cursor, which we do by assigning a null cursor to it. Doing this
        // instead of closing the cursor directly keeps the framework from accessing
        // the closed cursor later.
        if (!mAdapterSent && mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        // Because we pass the adapter to the next activity, we need to make
        // sure it doesn't keep a reference to this activity. We can do this
        // by clearing its DatasetObservers, which setListAdapter(null) does.
        mList.setAdapter(null);
        mAdapter = null;
        super.onDestroy();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        if (mTrackCursor != null) {
        	mList.invalidateViews();
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case android.R.id.home:
        		finish();
        		return true;
        	default:
        		return super.onOptionsItemSelected(item);
        }
    }
    
    public void init(Cursor newCursor, boolean isLimited) {

        if (mAdapter == null) {
            return;
        }
        mAdapter.changeCursor(newCursor); // also sets mTrackCursor
        
        if (mTrackCursor == null) {
            closeContextMenu();
            return;
        }

        // When showing the queue, position the selection on the currently playing track
        // Otherwise, position the selection on the first matching artist, if any
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.META_RETRIEVED);
        f.addAction(MediaPlaybackService.QUEUE_CHANGED);
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        try {
            int cur = MusicUtils.sService.getQueuePosition();
            mList.setSelection(cur);
            registerReceiver(mNowPlayingListener, new IntentFilter(f));
            mNowPlayingListener.onReceive(this, new Intent(MediaPlaybackService.META_CHANGED));
        } catch (RemoteException ex) {
        }
    }
    
    private void removePlaylistItem(int which) {
        View v = mList.getChildAt(which - mList.getFirstVisiblePosition());
        if (v == null) {
            Log.d(TAG, "No view when removing playlist item " + which);
            return;
        }
        try {
            if (MusicUtils.sService != null
                    && which != MusicUtils.sService.getQueuePosition()) {
                mDeletedOneRow = true;
            }
        } catch (RemoteException e) {
            // Service died, so nothing playing.
            mDeletedOneRow = true;
        }
        v.setVisibility(View.GONE);
        mList.invalidateViews();
        ((NowPlayingCursor)mTrackCursor).removeItem(which);
        v.setVisibility(View.VISIBLE);
        mList.invalidateViews();
    }

    private BroadcastReceiver mNowPlayingListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MediaPlaybackService.META_CHANGED)) {
            	mList.invalidateViews();
            } else if (intent.getAction().equals(MediaPlaybackService.META_RETRIEVED)) {
            	if (isDragging()) {
            	    mShouldRefresh = true;
            	    System.out.println("dsdsdsssssssssssss=================>");
            	} else {
                	if (mAdapter != null) {
                		Cursor c = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
                		if (c.getCount() == 0) {
                			c.close();
                			finish();
                			return;
                		}
                		mAdapter.changeCursor(c);
                	}
            	}
            } else if (intent.getAction().equals(MediaPlaybackService.QUEUE_CHANGED)) {
                if (mDeletedOneRow) {
                    // This is the notification for a single row that was
                    // deleted previously, which is already reflected in
                    // the UI.
                    mDeletedOneRow = false;
                    return;
                }
                // The service could disappear while the broadcast was in flight,
                // so check to see if it's still valid
                if (MusicUtils.sService == null) {
                    finish();
                    return;
                }
                if (mAdapter != null) {
                    Cursor c = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
                    if (c.getCount() == 0) {
                        finish();
                        c.close();
                        return;
                    }
                    mAdapter.changeCursor(c);
                }
            } else if (intent.getAction().equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
            	mList.invalidateViews();
            }
        }
    };

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        mSelectedPosition =  info.position;
        mTrackCursor.moveToPosition(mSelectedPosition);
        mCurrentTrackName = mTrackCursor.getString(mTrackCursor.getColumnIndexOrThrow(
                Media.MediaColumns.TITLE));
        
        menu.setHeaderTitle(mCurrentTrackName);
        
        android.view.MenuItem remove = menu.add(R.string.remove_from_playlist);
        remove.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(android.view.MenuItem arg0) {
				removePlaylistItem(mSelectedPosition);
				return true;
			}
        });
    }

    // In order to use alt-up/down as a shortcut for moving the selected item
    // in the list, we need to override dispatchKeyEvent, not onKeyDown.
    // (onKeyDown never sees these events, since they are handled by the list)
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getMetaState() != 0 &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    moveItem(true);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    moveItem(false);
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    removeItem();
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void removeItem() {
        int curcount = mTrackCursor.getCount();
        int curpos = mList.getSelectedItemPosition();
        
        if (curcount == 0 || curpos < 0) {
            return;
        }
        
        // remove track from queue

        // Work around bug 902971. To get quick visual feedback
        // of the deletion of the item, hide the selected view.
        try {
            if (curpos != MusicUtils.sService.getQueuePosition()) {
                mDeletedOneRow = true;
            }
        } catch (RemoteException ex) {
        }
        
        View v = mList.getSelectedView();
        v.setVisibility(View.GONE);
        mList.invalidateViews();
        ((NowPlayingCursor)mTrackCursor).removeItem(curpos);
        v.setVisibility(View.VISIBLE);
        mList.invalidateViews();
    }
    
    private void moveItem(boolean up) {
        int curcount = mTrackCursor.getCount(); 
        int curpos = mList.getSelectedItemPosition();
       
        if ( (up && curpos < 1) || (!up  && curpos >= curcount - 1)) {
            return;
        }

        NowPlayingCursor c = (NowPlayingCursor) mTrackCursor;
        c.moveItem(curpos, up ? curpos - 1 : curpos + 1);
        ((TrackListAdapter)mList.getAdapter()).notifyDataSetChanged();
        mList.invalidateViews();
        mDeletedOneRow = true;
        
        if (up) {
        	mList.setSelection(curpos - 1);
        } else {
        	mList.setSelection(curpos + 1);
        }
    }
    
    private Cursor getTrackCursor(TrackListAdapter.TrackQueryHandler queryhandler, String filter,
            boolean async) {

        if (queryhandler == null) {
            throw new IllegalArgumentException();
        }

        Cursor ret = null;

        if (MusicUtils.sService != null) {
            ret = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
            if (ret.getCount() == 0) {
                finish();
            }
        } else {
            // Nothing is playing.
        }
        
        // This special case is for the "nowplaying" cursor, which cannot be handled
        // asynchronously using AsyncQueryHandler, so we do some extra initialization here.
        if (ret != null && async) {
            init(ret, false);
        }
        return ret;
    }

    private class NowPlayingCursor extends AbstractCursor
    {
        public NowPlayingCursor(IMediaPlaybackService service, String [] cols)
        {
            mCols = cols;
            mService  = service;
            makeNowPlayingCursor();
        }
        private void makeNowPlayingCursor() {
            mCurrentPlaylistCursor = null;
            try {
                mNowPlaying = mService.getQueue();
            } catch (RemoteException ex) {
                mNowPlaying = new long[0];
            }
            mSize = mNowPlaying.length;
            if (mSize == 0) {
                return;
            }

            StringBuilder where = new StringBuilder();
            where.append(Media.MediaColumns._ID + " IN (");
            for (int i = 0; i < mSize; i++) {
                where.append(mNowPlaying[i]);
                if (i < mSize - 1) {
                    where.append(",");
                }
            }
            where.append(")");

            mCurrentPlaylistCursor = MusicUtils.query(NowPlayingActivity.this,
            		Media.MediaColumns.CONTENT_URI,
                    mCols, where.toString(), null, Media.MediaColumns._ID);

            if (mCurrentPlaylistCursor == null) {
                mSize = 0;
                return;
            }
            
            int size = mCurrentPlaylistCursor.getCount();
            mCursorIdxs = new long[size];
            mCurrentPlaylistCursor.moveToFirst();
            int colidx = mCurrentPlaylistCursor.getColumnIndexOrThrow(Media.MediaColumns._ID);
            for (int i = 0; i < size; i++) {
                mCursorIdxs[i] = mCurrentPlaylistCursor.getLong(colidx);
                mCurrentPlaylistCursor.moveToNext();
            }
            mCurrentPlaylistCursor.moveToFirst();
            mCurPos = -1;
            
            // At this point we can verify the 'now playing' list we got
            // earlier to make sure that all the items in there still exist
            // in the database, and remove those that aren't. This way we
            // don't get any blank items in the list.
            try {
                int removed = 0;
                for (int i = mNowPlaying.length - 1; i >= 0; i--) {
                    long trackid = mNowPlaying[i];
                    int crsridx = Arrays.binarySearch(mCursorIdxs, trackid);
                    if (crsridx < 0) {
                        //Log.i("@@@@@", "item no longer exists in db: " + trackid);
                        removed += mService.removeTrack(trackid);
                    }
                }
                if (removed > 0) {
                    mNowPlaying = mService.getQueue();
                    mSize = mNowPlaying.length;
                    if (mSize == 0) {
                        mCursorIdxs = null;
                        return;
                    }
                }
            } catch (RemoteException ex) {
                mNowPlaying = new long[0];
            }
        }

        @Override
        public int getCount()
        {
            return mSize;
        }

        @Override
        public boolean onMove(int oldPosition, int newPosition)
        {
            if (oldPosition == newPosition)
                return true;
            
            if (mNowPlaying == null || mCursorIdxs == null || newPosition >= mNowPlaying.length) {
                return false;
            }

            // The cursor doesn't have any duplicates in it, and is not ordered
            // in queue-order, so we need to figure out where in the cursor we
            // should be.
           
            long newid = mNowPlaying[newPosition];
            int crsridx = Arrays.binarySearch(mCursorIdxs, newid);
            mCurrentPlaylistCursor.moveToPosition(crsridx);
            mCurPos = newPosition;
            
            return true;
        }

        public boolean removeItem(int which)
        {
            try {
                if (mService.removeTracks(which, which) == 0) {
                    return false; // delete failed
                }
                int i = (int) which;
                mSize--;
                while (i < mSize) {
                    mNowPlaying[i] = mNowPlaying[i+1];
                    i++;
                }
                onMove(-1, (int) mCurPos);
            } catch (RemoteException ex) {
            }
            return true;
        }
        
        public void moveItem(int from, int to) {
            try {
                mService.moveQueueItem(from, to);
                mNowPlaying = mService.getQueue();
                onMove(-1, mCurPos); // update the underlying cursor
            } catch (RemoteException ex) {
            }
        }

        @Override
        public String getString(int column)
        {
            try {
                return mCurrentPlaylistCursor.getString(column);
            } catch (Exception ex) {
                onChange(true);
                return "";
            }
        }

        @Override
        public short getShort(int column)
        {
            return mCurrentPlaylistCursor.getShort(column);
        }

        @Override
        public int getInt(int column)
        {
            try {
                return mCurrentPlaylistCursor.getInt(column);
            } catch (Exception ex) {
                onChange(true);
                return 0;
            }
        }

        @Override
        public long getLong(int column)
        {
            try {
                return mCurrentPlaylistCursor.getLong(column);
            } catch (Exception ex) {
                onChange(true);
                return 0;
            }
        }

        @Override
        public float getFloat(int column)
        {
            return mCurrentPlaylistCursor.getFloat(column);
        }

        @Override
        public double getDouble(int column)
        {
            return mCurrentPlaylistCursor.getDouble(column);
        }

        @Override
        public boolean isNull(int column)
        {
            return mCurrentPlaylistCursor.isNull(column);
        }

        @Override
        public String[] getColumnNames()
        {
            return mCols;
        }
        
        @Override
        public boolean requery()
        {
            makeNowPlayingCursor();
            return true;
        }

        @Override
        public void close() {
        	if (mCurrentPlaylistCursor != null) {
        		mCurrentPlaylistCursor.close();
        		mCurrentPlaylistCursor = null;
        	}
        }
        
        private String [] mCols;
        private Cursor mCurrentPlaylistCursor;     // updated in onMove
        private int mSize;          // size of the queue
        private long[] mNowPlaying;
        private long[] mCursorIdxs;
        private int mCurPos;
        private IMediaPlaybackService mService;
    }
    
    class TrackListAdapter extends SimpleDragSortCursorAdapter {

    	int mUriIdx;
        int mTitleIdx;
        int mArtistIdx;
        int mDurationIdx;
        int mAudioIdIdx;

        private final StringBuilder mBuilder = new StringBuilder();
        
        private NowPlayingActivity mActivity = null;
        private TrackQueryHandler mQueryHandler;
        private String mConstraint = null;
        private boolean mConstraintIsValid = false;
        
        class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            ImageView play_indicator;
            CharArrayBuffer buffer1;
            char [] buffer2;
            ImageView icon;
        }

        class TrackQueryHandler extends AsyncQueryHandler {

            class QueryArgs {
                public Uri uri;
                public String [] projection;
                public String selection;
                public String [] selectionArgs;
                public String orderBy;
            }

            TrackQueryHandler(ContentResolver res) {
                super(res);
            }
            
            public Cursor doQuery(Uri uri, String[] projection,
                    String selection, String[] selectionArgs,
                    String orderBy, boolean async) {
                if (async) {
                    // Get 100 results first, which is enough to allow the user to start scrolling,
                    // while still being very fast.
                    Uri limituri = uri.buildUpon().appendQueryParameter("limit", "100").build();
                    QueryArgs args = new QueryArgs();
                    args.uri = uri;
                    args.projection = projection;
                    args.selection = selection;
                    args.selectionArgs = selectionArgs;
                    args.orderBy = orderBy;

                    startQuery(0, args, limituri, projection, selection, selectionArgs, orderBy);
                    return null;
                }
                return MusicUtils.query(mActivity,
                        uri, projection, selection, selectionArgs, orderBy);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
                mActivity.init(cursor, cookie != null);
                if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
                    QueryArgs args = (QueryArgs) cookie;
                    startQuery(1, null, args.uri, args.projection, args.selection,
                            args.selectionArgs, args.orderBy);
                }
            }
        }
        
        TrackListAdapter(Context context, NowPlayingActivity currentactivity,
                int layout, Cursor cursor, String[] from, int[] to) {
            super(context, layout, cursor, from, to);
            mActivity = currentactivity;
            getColumnIndices(cursor);
            
            mQueryHandler = new TrackQueryHandler(context.getContentResolver());
        }
        
        public void setActivity(NowPlayingActivity newactivity) {
            mActivity = newactivity;
        }
        
        public TrackQueryHandler getQueryHandler() {
            return mQueryHandler;
        }
        
        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
            	mUriIdx = cursor.getColumnIndexOrThrow(Media.MediaColumns.URI);
                mTitleIdx = cursor.getColumnIndexOrThrow(Media.MediaColumns.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(Media.MediaColumns.ARTIST);
                mDurationIdx = cursor.getColumnIndexOrThrow(Media.MediaColumns.DURATION);
                try {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID);
                } catch (IllegalArgumentException ex) {
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(Media.MediaColumns._ID);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            
            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.play_indicator = (ImageView) v.findViewById(R.id.play_indicator);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            vh.icon = (ImageView) v.findViewById(R.id.icon);
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            
            ViewHolder vh = (ViewHolder) view.getTag();
            
            String trackName = cursor.getString(mTitleIdx);
            if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
            	vh.line1.setText(R.string.widget_one_track_info_unavailable);
            } else {           
            	cursor.copyStringToBuffer(mTitleIdx, vh.buffer1);
            	vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);
        	}
            
            int secs = cursor.getInt(mDurationIdx) / 1000;
            if (secs == 0) {
                vh.duration.setText("    ");
            } else {
                vh.duration.setText(MusicUtils.makeTimeString(context, secs));
            }
            
            final StringBuilder builder = mBuilder;
            builder.delete(0, builder.length());

            String artistName = cursor.getString(mArtistIdx); 
            if (artistName == null || artistName.equals(Media.UNKNOWN_STRING)) {
            	builder.append(cursor.getString(mUriIdx));
            } else {
            	builder.append(artistName);
            }
            int len = builder.length();
            if (vh.buffer2.length < len) {
                vh.buffer2 = new char[len];
            }
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);

            ImageView iv = vh.icon;
            iv.setVisibility(View.GONE);
            if (mPreferences.getBoolean(PreferenceConstants.RETRIEVE_ALBUM_ART, false)) {
            	long id = cursor.getInt(mAudioIdIdx);
        		Drawable d = MusicUtils.getCachedMediumArtwork(NowPlayingActivity.this, id);
        		if (d != null) {
        			iv.setImageDrawable(d);
        			iv.setVisibility(View.VISIBLE);
        		}
            }
            
            iv = vh.play_indicator;
            long id = -1;
            boolean isPlaying = false;
            if (MusicUtils.sService != null) {
                // TODO: IPC call on each bind??
                try {
                    id = MusicUtils.sService.getQueuePosition();
                    isPlaying = MusicUtils.sService.isPlaying();
                } catch (RemoteException ex) {
                }
            }
            
            // Determining whether and where to show the "now playing indicator
            // is tricky, because we don't actually keep track of where the songs
            // in the current playlist came from after they've started playing.
            //
            // If the "current playlists" is shown, then we can simply match by position,
            // otherwise, we need to match by id. Match-by-id gets a little weird if
            // a song appears in a playlist more than once, and you're in edit-playlist
            // mode. In that case, both items will have the "now playing" indicator.
            // For this reason, we don't show the play indicator at all when in edit
            // playlist mode (except when you're viewing the "current playlist",
            // which is not really a playlist)
            if ( (cursor.getPosition() == id)) {
            	if (isPlaying) {
            		iv.setImageResource(Utils.getThemedIcon(NowPlayingActivity.this, R.attr.ic_av_play));
            	} else {
            		iv.setImageResource(Utils.getThemedIcon(NowPlayingActivity.this, R.attr.ic_av_pause));
            	}
                iv.setVisibility(View.VISIBLE);
            } else {
                iv.setVisibility(View.GONE);
            }
        }
        
        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mTrackCursor) {
                mTrackCursor = cursor;
                super.changeCursor(cursor);
                getColumnIndices(cursor);
            }
        }
        
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            String s = constraint.toString();
            if (mConstraintIsValid && (
                    (s == null && mConstraint == null) ||
                    (s != null && s.equals(mConstraint)))) {
                return getCursor();
            }
            Cursor c = mActivity.getTrackCursor(mQueryHandler, s, false);
            mConstraint = s;
            mConstraintIsValid = true;
            return c;
        }
        
        @Override
        public void drop(int from, int to) {
        	setDragging(false);
        	
        	if (from == to) {
        		return;
        	}
        	
        	if (mShouldRefresh) {
        		if (mAdapter != null) {
        			Cursor c = new NowPlayingCursor(MusicUtils.sService, mCursorCols);
        			if (c.getCount() == 0) {
        				c.close();
        				finish();
        				return;
        			}
        			mAdapter.changeCursor(c);
        		}
        	}
        	
        	mShouldRefresh = false;
        	
        	// update the currently playing list
            NowPlayingCursor c = (NowPlayingCursor) mTrackCursor;
            c.moveItem(from, to);
            //((DragSortListView.AdapterWrapper)mList.getAdapter()).notifyDataSetChanged();
            NowPlayingActivity.this.mList.invalidateViews();
            mDeletedOneRow = true;
        }
        
        @Override
        public void remove(int which) {
			removePlaylistItem(which);
        }
        
        /**
         * Does nothing. Just completes DragSortListener interface.
         */
        @Override
        public void drag(int from, int to) {
        	setDragging(true);
        }
    }
    
    private synchronized void setDragging(boolean isDragging) {
    	mIsDragging = isDragging;
    }
    
    private synchronized boolean isDragging() {
    	return mIsDragging;
    }
}

