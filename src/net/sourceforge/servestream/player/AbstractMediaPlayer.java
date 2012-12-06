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
 * Provides a unified interface for dealing with audio files and
 * other media files.
 */
public abstract class AbstractMediaPlayer implements Parcelable {

    /**
     * Default constructor.
     * <p>When done with the MediaPlayer, you should call  {@link #release()},
     * to free the resources. If not released, too many MediaPlayer instances may
     * result in an exception.</p>
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
    
    /**
     * Sets the data source (file-path or http/rtsp URL) to use.
     *
     * @param path the path of the file, or the http/rtsp URL of the stream you want to play
     * @param isLocalFile an indicator if the file is local, or a stream
     *
     * <p>When <code>path</code> refers to a local file, the file may actually be opened by a
     * process other than the calling application.  This implies that the pathname
     * should be an absolute path (as any other process runs with unspecified current working
     * directory), and that the pathname should reference a world-readable file.
     * As an alternative, the application could first open the file for reading,
     * and then use the file descriptor form {@link #setDataSource(FileDescriptor)}.
     */
    public abstract void setDataSource(String path, boolean isLocalFile);
    
    /**
     * 
     * @return
     */
    public abstract boolean isInitialized();

    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * been stopped, or never started before, playback will start at the
     * beginning.
     */
    public abstract void start();

    /**
     * Stops playback after playback has been stopped or paused.
     */
    public abstract void stop();

    /**
     * Releases resources associated with this MediaPlayer object.
     * It is considered good practice to call this method when you're
     * done using the MediaPlayer. In particular, whenever an Activity
     * of an application is paused (its onPause() method is called),
     * or stopped (its onStop() method is called), this method should be
     * invoked to release the MediaPlayer object, unless the application
     * has a special need to keep the object around. In addition to
     * unnecessary resources (such as memory and instances of codecs)
     * being held, failure to call this method immediately if a
     * MediaPlayer object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and playback
     * failure for other applications if no multiple instances of the
     * same codec are supported on a device. Even if multiple instances
     * of the same codec are supported, some performance degradation
     * may be expected when unnecessary multiple instances are used
     * at the same time.
     */
    public abstract void release();
        
    /**
     * Pauses playback. Call start() to resume.
     */
    public abstract void pause();
    
    // TODO replace this with a callback in the constructor
    public abstract void setHandler(Handler handler);

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds
     */
    public abstract long duration();

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    public abstract long position();

    /**
     * Seeks to specified time position.
     *
     * @param msec the offset in milliseconds from the start to seek to
     */
    public abstract long seek(long msec);

    /**
     * Sets the volume on this player.
     * This API is recommended for balancing the output of audio streams
     * within an application. Unless you are writing an application to
     * control user settings, this API should be used in preference to
     * {@link AudioManager#setStreamVolume(int, int, int)} which sets the volume of ALL streams of
     * a particular type. Note that the passed volume values are raw scalars.
     * UI controls should be scaled logarithmically.
     *
     * @param leftVolume left volume scalar
     * @param rightVolume right volume scalar
     */
    public abstract void setVolume(float vol);

    /**
     * Sets the {@link SurfaceHolder} to use for displaying the video
     * portion of the media.
     *
     * Either a surface holder or surface must be set if a display or video sink
     * is needed.  Not calling this method or {@link #setSurface(Surface)}
     * when playing back a video will result in only the audio track being played.
     * A null surface holder or surface will result in only the audio track being
     * played.
     *
     * @param sh the SurfaceHolder to use for video display
     */
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