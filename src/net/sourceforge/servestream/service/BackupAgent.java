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

package net.sourceforge.servestream.service;

import java.io.IOException;

import net.sourceforge.servestream.alarm.AlarmProvider.DatabaseHelper;
import net.sourceforge.servestream.dbutils.StreamDatabase;
import net.sourceforge.servestream.preference.PreferenceConstants;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class BackupAgent extends BackupAgentHelper {

	public final static String TAG = BackupAgent.class.getName();
	
    @Override
    public void onCreate() {
    	
    	Log.v(TAG, "onCreate called");
    	
        SharedPreferencesBackupHelper prefs = new SharedPreferencesBackupHelper(this, getPackageName() +
        		"_preferences");
        addHelper(PreferenceConstants.BACKUP_PREF_KEY, prefs);
        
		FileBackupHelper streams = new FileBackupHelper(this, "../databases/" + StreamDatabase.DATABASE_NAME);
		addHelper(StreamDatabase.DATABASE_NAME, streams);
		
		FileBackupHelper alarms = new FileBackupHelper(this, "../databases/" + DatabaseHelper.DATABASE_NAME);
		addHelper(DatabaseHelper.DATABASE_NAME, alarms);
    }
    
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
              ParcelFileDescriptor newState) throws IOException {
    	
    	Log.v(TAG, "onBackup called");
    	
        // Hold the lock while the FileBackupHelper performs backup
        synchronized (StreamDatabase.dbLock) {
            super.onBackup(oldState, data, newState);
        }
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
            ParcelFileDescriptor newState) throws IOException {
    	
    	Log.v(TAG, "onRestore called");
    	
        // Hold the lock while the FileBackupHelper restores the file
        synchronized (StreamDatabase.dbLock) {
            super.onRestore(data, appVersionCode, newState);
        }
    }
}