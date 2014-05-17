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

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.adapter.OrganizeAdapter;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.dslv.DragSortController;
import net.sourceforge.servestream.dslv.DragSortListView;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class OrganizeUrlsActivity extends ActionBarActivity {

	private static final int MENU_ID_ACCEPT = 2;
	
	private List<UriBean> mBaselineUris;
	
	private OrganizeAdapter mAdapter;
	private StreamDatabase mStreamdb;
	
	private ActionMode mActionMode;
	
	@SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (mActionMode == null) {
				startSupportActionMode(mActionModeCallback);
			}
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_organize_urls);
		
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		DragSortListView list = (DragSortListView) findViewById(android.R.id.list);
		list.setEmptyView(findViewById(android.R.id.empty));
		list.setDropListener(dropListener);
		list.setRemoveListener(removeListener);

		DragSortController controller = new DragSortController(list);
		controller.setDragInitMode(DragSortController.ON_DRAG);
		controller.setRemoveMode(DragSortController.FLING_REMOVE);
        controller.setRemoveEnabled(true);
		controller.setDragHandleId(R.id.drag_handle);
		list.setOnTouchListener(controller);
		
		mAdapter = new OrganizeAdapter(this, new ArrayList<UriBean>(), mHandler);
		list.setAdapter(mAdapter);
	}
	
	public void onStart() {
		super.onStart();
		
		mStreamdb = new StreamDatabase(this);
		updateList();
	}
	
	public void onStop() {
		super.onStop();
		
		mStreamdb.close();
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case android.R.id.home:
    			Intent intent = new Intent(this, MainActivity.class);
    			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
    			return true;
    		case MENU_ID_ACCEPT:
    			saveChanges();
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		TypedArray drawables = obtainStyledAttributes(new int[] { R.attr.ic_action_accept });
		MenuItem item = menu.add(Menu.NONE, MENU_ID_ACCEPT, Menu.NONE, R.string.confirm_label);
		item.setIcon(drawables.getDrawable(0));
	    MenuItemCompat.setShowAsAction(item,
	    		MenuItemCompat.SHOW_AS_ACTION_IF_ROOM
				| MenuItemCompat.SHOW_AS_ACTION_WITH_TEXT);
		return true;
	}
	
	private void updateList() {
		mAdapter.clear();
		mBaselineUris = mStreamdb.getUris();
		for (int i = 0; i < mBaselineUris.size(); i++) {
			mAdapter.add(mBaselineUris.get(i));
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private synchronized void saveChanges() {
		List<UriBean> uris = mAdapter.getItems();
		
		List<UriBean> urisToDelete = new ArrayList<UriBean>();
		
		for (int i = 0; i < mBaselineUris.size(); i++) {
			if (!uris.contains(mBaselineUris.get(i))) {
				mStreamdb.deleteUri(mBaselineUris.get(i));
				urisToDelete.add(mBaselineUris.get(i));
			}
		}
		
		for (int i = 0; i < urisToDelete.size(); i++) {
			mBaselineUris.remove(urisToDelete.get(i));
		}
		
		int listPosition = 1;
		
		ContentValues values = new ContentValues();
		
		for (int i = 0; i < uris.size(); i++) {
			values.clear();
			values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, listPosition);
			listPosition++;
			
			mStreamdb.updateUri(uris.get(i), values);
		}
		
		updateList();
	}
	
	private DragSortListView.DropListener dropListener = new DragSortListView.DropListener() {

		@Override
		public void drop(int from, int to) {
			UriBean item = mAdapter.getItem(from);
			
            mAdapter.remove(item);
            mAdapter.insert(item, to);
		}
	};

	private DragSortListView.RemoveListener removeListener = new DragSortListView.RemoveListener() {

		@Override
		public void remove(int which) {
			mAdapter.remove(mAdapter.getItem(which));
		}
	};

	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.menu_item_delete:
				mode.finish(); // Action picked, so close the CAB
				return true;
			default:
				return false;
			}
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.organize_urls_menu, menu);
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
		}

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			mActionMode = mode;
			return false;
		}
	};
}
