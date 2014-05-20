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

package net.sourceforge.servestream.fragment;

import android.app.AlertDialog;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.app.LoaderManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.SetAlarmActivity;
import net.sourceforge.servestream.alarm.Alarm;
import net.sourceforge.servestream.alarm.Alarms;
import net.sourceforge.servestream.alarm.DigitalClock;
import net.sourceforge.servestream.alarm.ToastMaster;

/**
 * AlarmClock application.
 */
public class AlarmClockFragment extends ListFragment implements
	OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {

	public static final String PREFERENCES = "AlarmClock";
	
    /** This must be false for production.  If true, turns on logging,
        test code, etc. */
    public static final boolean DEBUG = false;

    private static final int URL_LOADER = 0;
    
    //private SharedPreferences mPrefs;
    private LayoutInflater mFactory;
    private AlarmTimeAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }
    
	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	    
		setListShownNoAnimation(true);
		
        mFactory = LayoutInflater.from(getActivity());
        
        mAdapter = new AlarmTimeAdapter(getActivity(), null, false);
        ListView list = getListView();
        list.setAdapter(mAdapter);
        list.setVerticalScrollBarEnabled(true);
        list.setOnItemClickListener(this);
        list.setOnCreateContextMenuListener(this);
        
        getLoaderManager().initLoader(URL_LOADER, null, this);
	}
	
    private void updateIndicatorAndAlarm(boolean enabled, ImageView bar,
            Alarm alarm) {
        bar.setImageResource(enabled ? R.drawable.ic_indicator_on
                : R.drawable.ic_indicator_off);
        Alarms.enableAlarm(getActivity(), alarm.id, enabled);
        if (enabled) {
        	SetAlarmActivity.popAlarmSetToast(getActivity(), alarm.hour, alarm.minutes,
                    alarm.daysOfWeek);
        }
    }

    private class AlarmTimeAdapter extends CursorAdapter {
    	
        public AlarmTimeAdapter(Context context, Cursor cursor, boolean autoRequery) {
            super(context, cursor, autoRequery);
        }

        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View ret = mFactory.inflate(R.layout.alarm_time_item, parent, false);

            DigitalClock digitalClock =
                    (DigitalClock) ret.findViewById(R.id.digitalClock);
            digitalClock.setLive(false);
            return ret;
        }

        public void bindView(View view, Context context, Cursor cursor) {
            final Alarm alarm = new Alarm(cursor);

            View indicator = view.findViewById(R.id.indicator);

            // Set the initial resource for the bar image.
            final ImageView barOnOff =
                    (ImageView) indicator.findViewById(R.id.bar_onoff);
            barOnOff.setImageResource(alarm.enabled ?
                    R.drawable.ic_indicator_on : R.drawable.ic_indicator_off);

            // Set the initial state of the clock "checkbox"
            final CheckBox clockOnOff =
                    (CheckBox) indicator.findViewById(R.id.clock_onoff);
            clockOnOff.setChecked(alarm.enabled);

            // Clicking outside the "checkbox" should also change the state.
            indicator.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        clockOnOff.toggle();
                        updateIndicatorAndAlarm(clockOnOff.isChecked(),
                                barOnOff, alarm);
                    }
            });

            DigitalClock digitalClock =
                    (DigitalClock) view.findViewById(R.id.digitalClock);

            // set the alarm text
            final Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, alarm.hour);
            c.set(Calendar.MINUTE, alarm.minutes);
            digitalClock.updateTime(c);
            digitalClock.setTypeface(Typeface.DEFAULT);

            // Set the repeat text or leave it blank if it does not repeat.
            TextView daysOfWeekView =
                    (TextView) digitalClock.findViewById(R.id.daysOfWeek);
            final String daysOfWeekStr =
                    alarm.daysOfWeek.toString(getActivity(), false);
            if (daysOfWeekStr != null && daysOfWeekStr.length() != 0) {
                daysOfWeekView.setText(daysOfWeekStr);
                daysOfWeekView.setVisibility(View.VISIBLE);
            } else {
                daysOfWeekView.setVisibility(View.GONE);
            }

            // Display the label
            TextView labelView =
                    (TextView) view.findViewById(R.id.label);
            if (alarm.label != null && alarm.label.length() != 0) {
                labelView.setText(alarm.label);
                labelView.setVisibility(View.VISIBLE);
            } else {
                labelView.setVisibility(View.GONE);
            }
        }
    };

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        final AdapterContextMenuInfo info =
                (AdapterContextMenuInfo) item.getMenuInfo();
        final int id = (int) info.id;
        // Error check just in case.
        if (id == -1) {
            return super.onContextItemSelected(item);
        }
        switch (item.getItemId()) {
            case R.id.delete_alarm:
                // Confirm that the alarm will be deleted.
                new AlertDialog.Builder(getActivity())
                        .setTitle(getString(R.string.delete_alarm))
                        .setMessage(getString(R.string.alarm_delete_confirmation_msg))
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d,
                                            int w) {
                                        Alarms.deleteAlarm(getActivity(), id);
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;

            case R.id.enable_alarm:
                final Cursor c = (Cursor) getListAdapter()
                        .getItem(info.position);
                final Alarm alarm = new Alarm(c);
                Alarms.enableAlarm(getActivity(), alarm.id, !alarm.enabled);
                if (!alarm.enabled) {
                	SetAlarmActivity.popAlarmSetToast(getActivity(), alarm.hour, alarm.minutes,
                            alarm.daysOfWeek);
                }
                return true;

            case R.id.edit_alarm:
                Intent intent = new Intent(getActivity(), SetAlarmActivity.class);
                intent.putExtra(Alarms.ALARM_ID, id);
                startActivity(intent);
                return true;

            default:
                break;
        }
        return super.onContextItemSelected(item);
    }

    private void addNewAlarm() {
        startActivity(new Intent(getActivity(), SetAlarmActivity.class));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ToastMaster.cancelToast();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfo) {
        // Inflate the menu from xml.
        getActivity().getMenuInflater().inflate(R.menu.alarm_clock_context_menu, menu);

        // Use the current item to create a custom view for the header.
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final Cursor c =
                (Cursor) getListAdapter().getItem((int) info.position);
        final Alarm alarm = new Alarm(c);

        // Construct the Calendar to compute the time.
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour);
        cal.set(Calendar.MINUTE, alarm.minutes);
        final String time = Alarms.formatTime(getActivity(), cal);

        // Inflate the custom view and set each TextView's text.
        final View v = mFactory.inflate(R.layout.context_menu_header, null);
        TextView textView = (TextView) v.findViewById(R.id.header_time);
        textView.setText(time);
        textView = (TextView) v.findViewById(R.id.header_label);
        textView.setText(alarm.label);

        // Set the custom view on the menu.
        menu.setHeaderView(v);
        // Change the text based on the state of the alarm.
        if (alarm.enabled) {
            menu.findItem(R.id.enable_alarm).setTitle(R.string.disable_alarm);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_add_alarm:
                addNewAlarm();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.alarm_clock_menu, menu);
	    super.onCreateOptionsMenu(menu, inflater);
	}
    
    public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
        Intent intent = new Intent(getActivity(), SetAlarmActivity.class);
        intent.putExtra(Alarms.ALARM_ID, (int) id);
        startActivity(intent);
    }

	@Override
	public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
	    switch (loaderID) {
	        case URL_LOADER:
	            // Returns a new CursorLoader
	            return new CursorLoader(
	                        getActivity(),
	                        Alarm.Columns.CONTENT_URI,
	                        Alarm.Columns.ALARM_QUERY_COLUMNS,
	                        null,
	                        null,
	                        Alarm.Columns.DEFAULT_SORT_ORDER
	        );
	        default:
	            // An invalid id was passed in
	            return null;
	    }

	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
		mAdapter.changeCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.changeCursor(null);
	}
}
