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

package net.sourceforge.servestream.utils;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.service.MediaPlaybackService;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class SleepTimerDialog extends DialogFragment implements DialogInterface.OnCancelListener {
	
	private int mSleepTimerMode;
	
    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface SleepTimerDialogListener {
        public void onSleepTimerSet(DialogFragment dialog, int pos);
    }
	
    // Use this instance of the interface to deliver action events
    static SleepTimerDialogListener mListener;
    
    /* Call this to instantiate a new SleepTimerDialog.
     * @param activity  The activity hosting the dialog, which must implement the
     *                  SleepTimerDialogListener to receive event callbacks.
     * @returns A new instance of SleepTimerDialog.
     * @throws  ClassCastException if the host activity does not
     *          implement SleepTimerDialogListener
     */
    public static SleepTimerDialog newInstance(Activity activity, int sleepTimerMode) {
    	// Verify that the host activity implements the callback interface
        try {
            // Instantiate the SleepTimerDialogListener so we can send events with it
            mListener = (SleepTimerDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement SleepTimerDialogListener");
        }
    	SleepTimerDialog frag = new SleepTimerDialog();
    	
   	 	// Supply dialog text as an argument.
        Bundle args = new Bundle();
        args.putInt("sleep_timer_mode", sleepTimerMode);
        frag.setArguments(args);
    	
        return frag;
    }
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSleepTimerMode = getArguments().getInt("sleep_timer_mode");
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int MAX_SLEEP_TIMER_MINUTES = 120;
    	LayoutInflater factory = LayoutInflater.from(getActivity());
    	final View sleepTimerView = factory.inflate(R.layout.alert_dialog_sleep_timer, null);
    	final TextView sleepTimerText = (TextView) sleepTimerView.findViewById(R.id.sleep_timer_text);

    	final SeekBar seekbar = (SeekBar) sleepTimerView.findViewById(R.id.seekbar);
    	sleepTimerText.setText(makeTimeString(mSleepTimerMode));
    	seekbar.setProgress(mSleepTimerMode);
    	seekbar.setMax(MAX_SLEEP_TIMER_MINUTES);
    	seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

    		@Override
    		public void onProgressChanged(SeekBar seekBar, int progress,
    				boolean fromUser) {
    			sleepTimerText.setText(makeTimeString(progress));
    		}

    		@Override
    		public void onStartTrackingTouch(SeekBar seekBar) {
    			
    		}

    		@Override
    		public void onStopTrackingTouch(SeekBar seekBar) {
    			
    		}
    	
    	});
    	
        // Use the Builder class for convenient dialog construction
	    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
	    builder = new AlertDialog.Builder(getActivity());
	    builder.setTitle(R.string.sleep_timer_label);
	    builder.setCancelable(true);
	    builder.setView(sleepTimerView);
	    builder.setOnCancelListener(this);
    	builder.setPositiveButton(R.string.enable_sleeptimer_label, new DialogInterface.OnClickListener() {
    		public void onClick(DialogInterface dialog, int whichButton) {
    			mListener.onSleepTimerSet(SleepTimerDialog.this, seekbar.getProgress());
    		}
    	});
        return builder.create();
    }
    
    public void onCancel(DialogInterface dialog) {
    	
    }
    
    private String makeTimeString(int pos) {
    	String minuteText;
    	
    	if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
	    	minuteText = getResources().getString(R.string.disable_sleeptimer_label);
	    } else if (pos == 1) {
	    	minuteText = getResources().getString(R.string.minute);
	    } else if (pos % 60 == 0 && pos > 60) {
	    	minuteText = getResources().getString(R.string.hours, String.valueOf(pos / 60));
	    } else if (pos % 60 == 0) {
	    	minuteText = getResources().getString(R.string.hour);
	    } else {
	    	minuteText = getResources().getString(R.string.minutes, String.valueOf(pos));
	    }
    	
    	return minuteText;
    }
}