package net.sourceforge.servestream.fragment;

import java.util.HashMap;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.bean.UriBean;
import net.sourceforge.servestream.database.StreamDatabase;
import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;

public class UriEditorFragment extends net.sourceforge.servestream.preference.PreferenceFragment
    	implements Preference.OnPreferenceChangeListener {
	
	private HashMap<String, String> mValues = new HashMap<String, String>();
	
	private EditTextPreference mNickname;
	private EditTextPreference mProtocol;
	private EditTextPreference mUsername;
	private EditTextPreference mPassword;
	private EditTextPreference mHostname;
	private EditTextPreference mPort;
	private EditTextPreference mPath;
	private EditTextPreference mQuery;
	private EditTextPreference mReference;

	private UriBean mBean;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
        addPreferencesFromResource(R.xml.stream_prefs);
        
        long streamId = getActivity().getIntent().getLongExtra(Intent.EXTRA_TITLE, -1);

        mNickname = (EditTextPreference) findPreference("nickname");
        mNickname.setOnPreferenceChangeListener(mListener);
        mValues.put("nickname", StreamDatabase.FIELD_STREAM_NICKNAME);
        
        mProtocol = (EditTextPreference) findPreference("protocol");
        mProtocol.setOnPreferenceChangeListener(mListener);
        mValues.put("protocol", StreamDatabase.FIELD_STREAM_PROTOCOL);
        
        mUsername = (EditTextPreference) findPreference("username");
        mUsername.setOnPreferenceChangeListener(mListener);
        mValues.put("username", StreamDatabase.FIELD_STREAM_USERNAME);
        
        mPassword = (EditTextPreference) findPreference("password");
        mPassword.setOnPreferenceChangeListener(mListener);
        mValues.put("password", StreamDatabase.FIELD_STREAM_PASSWORD);
        
        mHostname = (EditTextPreference) findPreference("hostname");
        mHostname.setOnPreferenceChangeListener(mListener);
        mValues.put("hostname", StreamDatabase.FIELD_STREAM_HOSTNAME);

        mPort = (EditTextPreference) findPreference("port");
        mPort.setOnPreferenceChangeListener(mListener);
        mValues.put("port", StreamDatabase.FIELD_STREAM_PORT);
        
        mPath = (EditTextPreference) findPreference("path");
        mPath.setOnPreferenceChangeListener(mListener);
        mValues.put("path", StreamDatabase.FIELD_STREAM_PATH);
        
        mQuery = (EditTextPreference) findPreference("query");
        mQuery.setOnPreferenceChangeListener(mListener);
        mValues.put("query", StreamDatabase.FIELD_STREAM_QUERY);
        
        mReference = (EditTextPreference) findPreference("reference");
        mReference.setOnPreferenceChangeListener(mListener);
        mValues.put("reference", StreamDatabase.FIELD_STREAM_REFERENCE);
        
        StreamDatabase database = new StreamDatabase(getActivity());
        mBean = database.findUri((int) streamId);
        database.close();
        
        updatePrefs(mBean);
	}
	
	private OnPreferenceChangeListener mListener = new OnPreferenceChangeListener() {

		@Override
		public boolean onPreferenceChange(Preference preference, Object newValue) {
	    	if (preference.getKey().equals("port") && newValue != null && newValue.equals("")) {
	    		newValue = "-1";
	    	}
			
	    	if (newValue != null && newValue.equals("")) {
	    		newValue = null;
	    	}
	    	
	        String val = (String) newValue;
            // Set the summary based on the new label.
			preference.setSummary(val);
            //if (val != null && !val.equals(((EditTextPreference) preference).getText())) {
            	// Call through to the generic listener.
                return UriEditorFragment.this.onPreferenceChange(preference,
                    newValue);
            //}
            //return true;
		}
		
	};
	
    // Used to post runnables asynchronously.
    private static final Handler sHandler = new Handler();
	
    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        // Asynchronously save the alarm since this method is called _before_
        // the value of the preference has changed.
        sHandler.post(new Runnable() {
            public void run() {
            	saveUri(preference, newValue);
            }
        });
        return true;
    }
	
    private void updatePrefs(UriBean bean) {
    	mNickname.setSummary(bean.getNickname());
    	mNickname.setText(bean.getNickname());
    	mProtocol.setSummary(bean.getProtocol());
    	mProtocol.setText(bean.getProtocol());
    	mUsername.setSummary(bean.getUsername());
    	mUsername.setText(bean.getUsername());
    	mPassword.setSummary(bean.getPassword());
    	mPassword.setText(bean.getPassword());
		//if (key.equals("password") && value != null) {
		//	value = new String(new char[value.length()]).replace("\0", "*");
		//}
    	mHostname.setSummary(bean.getHostname());
    	mHostname.setText(bean.getHostname());
    	mPort.setSummary(String.valueOf(bean.getPort()));
    	mPort.setText(String.valueOf(bean.getPort()));
    	mPath.setSummary(bean.getPath());
    	mPath.setText(bean.getPath());
    	mQuery.setSummary(bean.getQuery());
    	mQuery.setText(bean.getQuery());
    	mReference.setSummary(bean.getReference());
    	mReference.setText(bean.getReference());
    }

    private void saveUri(final Preference preference, Object newValue) {
    	String value = (String) newValue;
    	
    	ContentValues values = new ContentValues();
    	values.put(mValues.get(preference.getKey()), value);
    	
    	StreamDatabase database = new StreamDatabase(getActivity());
    	SQLiteDatabase db = database.getWritableDatabase();
    	db.update(StreamDatabase.TABLE_STREAMS, values, "_id = ?", new String[] { String.valueOf(mBean.getId()) });
    	database.close();
    	db.close();
    }
}
