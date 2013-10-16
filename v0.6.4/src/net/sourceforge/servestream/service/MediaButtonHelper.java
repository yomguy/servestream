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

import android.content.ComponentName;
import android.media.AudioManager;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Class that assists with handling new media button APIs available in API level 8.
 */
public class MediaButtonHelper {
    // Backwards compatibility code (methods available as of API Level 8)
    private static final String TAG = "MediaButtonHelper";

    static {
        initializeStaticCompatMethods();
    }

    static Method sMethodRegisterMediaButtonEventReceiver;
    static Method sMethodUnregisterMediaButtonEventReceiver;

    static void initializeStaticCompatMethods() {
        try {
            sMethodRegisterMediaButtonEventReceiver = AudioManager.class.getMethod(
                    "registerMediaButtonEventReceiver",
                    new Class[] { ComponentName.class });
            sMethodUnregisterMediaButtonEventReceiver = AudioManager.class.getMethod(
                    "unregisterMediaButtonEventReceiver",
                    new Class[] { ComponentName.class });
        } catch (NoSuchMethodException e) {
            // Silently fail when running on an OS before API level 8.
        }
    }

    public static void registerMediaButtonEventReceiverCompat(AudioManager audioManager,
            ComponentName receiver) {
        if (sMethodRegisterMediaButtonEventReceiver == null)
            return;

        try {
            sMethodRegisterMediaButtonEventReceiver.invoke(audioManager, receiver);
        } catch (InvocationTargetException e) {
            // Unpack original exception when possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                // Unexpected checked exception; wrap and re-throw
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException invoking registerMediaButtonEventReceiver.");
            e.printStackTrace();
        }
    }

    public static void unregisterMediaButtonEventReceiverCompat(AudioManager audioManager,
            ComponentName receiver) {
        if (sMethodUnregisterMediaButtonEventReceiver == null)
            return;

        try {
            sMethodUnregisterMediaButtonEventReceiver.invoke(audioManager, receiver);
        } catch (InvocationTargetException e) {
            // Unpack original exception when possible
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            } else {
                // Unexpected checked exception; wrap and re-throw
                throw new RuntimeException(e);
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException invoking unregisterMediaButtonEventReceiver.");
            e.printStackTrace();
        }
    }
}
