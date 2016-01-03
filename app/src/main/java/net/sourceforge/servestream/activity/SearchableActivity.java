package net.sourceforge.servestream.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.adapter.SearchAdapter;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.preference.UserPreferences;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class SearchableActivity extends ActionBarActivity {

	private SearchAdapter mAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.activity_searchable);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
	    
		ListView list = (ListView) findViewById(android.R.id.list);
		list.setEmptyView(findViewById(android.R.id.empty));
		list.setFastScrollEnabled(true);
	    list.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				UriBean uri = (UriBean) parent.getItemAtPosition(position);
				
				Intent intent = new Intent(SearchableActivity.this, MainActivity.class);
				intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
				intent.setData(uri.getUri());

				SearchableActivity.this.startActivity(intent);
			}
	    });

	    mAdapter = new SearchAdapter(this, new ArrayList<UriBean>());
		list.setAdapter(mAdapter);
		
	    // Get the intent, verify the action and get the query
	    Intent intent = getIntent();
	    if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
	      String query = intent.getStringExtra(SearchManager.QUERY);
	      ArrayList<UriBean> uris = intent.getParcelableArrayListExtra("uris");
	      performSearch(query, uris);
	    }
	}
	
	private void performSearch(String query, List<UriBean> uris) {
		mAdapter.clear();
		
		query = query.toLowerCase(Locale.getDefault());
		
		for (int i = 0; i < uris.size(); i++) {
			UriBean uri = uris.get(i);
			
			if (uri.getNickname().toLowerCase(Locale.getDefault()).contains(query)
					|| uri.getUri().toString().toLowerCase(Locale.getDefault()).contains(query)) {
				mAdapter.add(uri);
			}
		}
		
		mAdapter.notifyDataSetChanged();
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
    		case android.R.id.home:
    			finish();
    			return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
