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

package net.sourceforge.servestream.activity;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.button.VerticalTextSpinner;
import net.sourceforge.servestream.service.MediaPlaybackService;
import net.sourceforge.servestream.utils.MusicUtils;
import net.sourceforge.servestream.utils.MusicUtils.ServiceToken;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

public class MinuteSelector extends Activity implements ServiceConnection
{
	private static int MAX_ARRAY_VALUE = 121;

    private static final String STATE_SELECTED_POS = "net.sourceforge.servestream.selectedpos";
	
    private VerticalTextSpinner mMinutes;
    private int mPos = -1;
    private ServiceToken mToken;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.minute_picker);
        getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                                    WindowManager.LayoutParams.WRAP_CONTENT);
        
        mMinutes = (VerticalTextSpinner)findViewById(R.id.minutes);
        mMinutes.setItems(getMinuteArray());
        mMinutes.setWrapAround(false);
        mMinutes.setScrollInterval(200);
        
        ((Button) findViewById(R.id.set)).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                int pos = mMinutes.getCurrentSelectedPos();
                setSleepTimer(pos);
                setResult(RESULT_OK);
                finish();
			}
		});
        
        
        ((Button) findViewById(R.id.cancel)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        
        if (icicle != null) {
        	mPos = icicle.getInt(STATE_SELECTED_POS, 0);
        	mMinutes.setSelectedPos(mPos);
        }
        
        mToken = MusicUtils.bindToService(this, this);
    }
    
    @Override
    public void onSaveInstanceState(Bundle outcicle) {
        outcicle.putInt(STATE_SELECTED_POS, mMinutes.getCurrentSelectedPos());
    }
    
    @Override
    public void onStop() {
    	super.onStop();
        MusicUtils.unbindFromService(mToken);
    }
    
    private void setSleepTimer(int pos) {
    	try {
			MusicUtils.sService.setSleepTimerMode(pos);
			if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
				showToast(getString(R.string.sleep_timer_off_notif));
			} else {
                String minuteText = mMinutes.getCurrentSelectedText(mMinutes.getCurrentSelectedPos());
			    showToast(getString(R.string.sleep_timer_on_notif) + " " + minuteText);
			}
		} catch (RemoteException e) {
		}
    }
    
    private String [] getMinuteArray() {
    	String [] minutes = new String[MAX_ARRAY_VALUE];
    	
    	for (int i = 0; i < MAX_ARRAY_VALUE; i++) {
    		if (i == 0) {
    			minutes[i] = getResources().getString(R.string.minute_picker_cancel);
    		} else if (i == 0) {
    			minutes[i] = getResources().getString(R.string.minute);
    		} else if (i % 60 == 0 && i > 60) {
    			minutes[i] = getResources().getString(R.string.hours, String.valueOf(i / 60));
    		} else if (i % 60 == 0) {
    			minutes[i] = getResources().getString(R.string.hour);
    		} else {
    			minutes[i] = getResources().getString(R.string.minutes, String.valueOf(i));
    		}
    	}
    	
    	return minutes;
    }
    
    private void showToast(String message) {
        Toast toast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        toast.setText(message);
        toast.show();
    }

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		try {
			if (mPos != -1) {
				return;
			}
			
	        mMinutes.setSelectedPos(MusicUtils.sService.getSleepTimerMode());
		} catch (RemoteException e) {
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		finish();		
	}
}
