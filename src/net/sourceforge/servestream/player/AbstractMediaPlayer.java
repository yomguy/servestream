/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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

package net.sourceforge.servestream.player;

import net.sourceforge.servestream.transport.File;
import net.sourceforge.servestream.transport.HTTP;
import net.sourceforge.servestream.transport.HTTPS;
import net.sourceforge.servestream.transport.MMS;
import net.sourceforge.servestream.transport.MMSH;
import net.sourceforge.servestream.transport.MMST;
import net.sourceforge.servestream.transport.RTSP;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.SurfaceHolder;

/**
 * Provides a unified interface for dealing with midi files and
 * other media files.
 */
public abstract class AbstractMediaPlayer implements Parcelable {

	/**
	 * Ensure the class cannot be instantiated
	 */
    protected AbstractMediaPlayer() {
        
    }

    /**
     * Detects the appropriate media player depending on the URI of 
     * a file.
     * @param uri path to a file.
     * @return a media player.
     */
	public static final AbstractMediaPlayer getMediaPlayer(String uri) {
		if (uri.startsWith(HTTP.getProtocolName())) {
			return new NativeMediaPlayer();
		} else if (uri.startsWith(HTTPS.getProtocolName())) {
			return new NativeMediaPlayer();
		} else if (uri.startsWith(File.getProtocolName())) {
			return new NativeMediaPlayer();
		} else if (uri.startsWith(RTSP.getProtocolName())) {
			return new NativeMediaPlayer();
		} else if (uri.startsWith(MMS.getProtocolName())) {
			return new FFmpegMediaPlayer();
		} else if (uri.startsWith(MMSH.getProtocolName())) {
			return new FFmpegMediaPlayer();
		} else if (uri.startsWith(MMST.getProtocolName())) {
			return new FFmpegMediaPlayer();
		} else {
			return null;
		}
	}
    
    public abstract void setDataSource(String path, boolean isLocalFile);
        
    public abstract boolean isInitialized();

    public abstract void start();

    public abstract void stop();

    public abstract void release();
        
    public abstract void pause();
        
    public abstract void setHandler(Handler handler);

    public abstract long duration();

    public abstract long position();

    public abstract long seek(long whereto);

    public abstract void setVolume(float vol);

    public abstract void setDisplay(SurfaceHolder holder);
    
	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {

	}
	
	public static final Parcelable.Creator<AbstractMediaPlayer> CREATOR = new
	Parcelable.Creator<AbstractMediaPlayer>() {
	    public AbstractMediaPlayer createFromParcel(Parcel in) {
	    	return null;
	    }

	    public AbstractMediaPlayer[] newArray(int size) {
	    	return null;
	    }
	};
}