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
/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream.service;

import android.graphics.Bitmap;
import net.sourceforge.servestream.player.MultiPlayer;

interface IMediaService
{
    void openFile(String path);
    void queueFirstFile();
    int getQueuePosition();
    boolean isPlaying();
    void stop();
    void pause();
    void play();
    void prev();
    void next();
    String getTrackNumber();
    String getTrackName();
    String getMediaURL();
    long duration();
    long position();
    long seek(long pos);
    long [] getQueue();
    int getPlayListLength();
    void setQueuePosition(int index);
    String getPath();
    String getPlayListPath();
    long getMediaId();
    void setShuffleMode(int shufflemode);
    int getShuffleMode();
    void setRepeatMode(int repeatmode);
    int getRepeatMode();
    void setSleepTimerMode(int sleepmode);
    int getSleepTimerMode();
    boolean loadQueue(String filename);
    MultiPlayer getMediaPlayer();
}
