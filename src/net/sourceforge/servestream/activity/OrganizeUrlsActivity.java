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

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.adapter.OrganizeAdapter;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;

public class OrganizeUrlsActivity extends SherlockListActivity {

	private OrganizeAdapter mAdapter;
	private StreamDatabase mStreamdb;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_organize_urls);
		
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		DragSortListView list = (DragSortListView) getListView();
		list.setDropListener(dropListener);
		list.setRemoveListener(removeListener);

		DragSortController controller = new DragSortController(list);
		controller.setDragInitMode(DragSortController.ON_DRAG);
		controller.setRemoveMode(DragSortController.FLING_REMOVE);
        controller.setRemoveEnabled(true);
		//controller.setDragHandleId(R.id.icon);
		list.setOnTouchListener(controller);
		
		mAdapter = new OrganizeAdapter(this, new ArrayList<UriBean>());
		setListAdapter(mAdapter);
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
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
	
	private void updateList() {
		mAdapter.clear();
		List<UriBean> uris = mStreamdb.getUris();
		for (int i = 0; i < uris.size(); i++) {
			mAdapter.add(uris.get(i));
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private DragSortListView.DropListener dropListener = new DragSortListView.DropListener() {

		@Override
		public void drop(int from, int to) {
			UriBean fromUri = (UriBean) getListView().getAdapter().getItem(from);
			UriBean toUri = (UriBean) getListView().getAdapter().getItem(to);
			updateUris(fromUri, toUri);
		}
	};

	private DragSortListView.RemoveListener removeListener = new DragSortListView.RemoveListener() {

		@Override
		public void remove(int which) {
			UriBean uri = (UriBean) getListView().getAdapter().getItem(which);
			removeUri(uri);
		}
	};
	
	private synchronized void updateUris(UriBean fromUri, UriBean toUri) {
		int fromPosition = fromUri.getListPosition();
		int toPosition = toUri.getListPosition();
		
		ContentValues values = new ContentValues();
		values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, toPosition);
		
		mStreamdb.updateUri(fromUri, values);
		
		values.clear();
		values.put(StreamDatabase.FIELD_STREAM_LIST_POSITION, fromPosition);

		mStreamdb.updateUri(toUri, values);
		
		updateList();
	}
	
	private synchronized void removeUri(UriBean uri) {
		mStreamdb.deleteUri(uri);
		updateList();
	}
}
