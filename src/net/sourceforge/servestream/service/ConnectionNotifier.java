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
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.StreamMediaActivity;
import net.sourceforge.servestream.utils.PreferenceConstants;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

/**
 * @author Kenny Root
 *
 * Based on the concept from jasta's blog post.
 */
public abstract class ConnectionNotifier {
	private static final int ONLINE_NOTIFICATION = 1;

	public static ConnectionNotifier getInstance() {
		if (PreferenceConstants.PRE_ECLAIR)
			return PreEclair.Holder.sInstance;
		else
			return EclairAndBeyond.Holder.sInstance;
	}

	protected NotificationManager getNotificationManager(Context context) {
		return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	protected Notification newNotification(Context context) {
		Notification notification = new Notification();
		notification.icon = R.drawable.notification_icon;
		notification.when = System.currentTimeMillis();

		return notification;
	}

	protected Notification newRunningNotification(Context context) {
		Notification notification = newNotification(context);

		notification.flags = Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_NO_CLEAR;
		notification.when = 0;

		notification.contentIntent = PendingIntent.getActivity(context,
				ONLINE_NOTIFICATION,
				new Intent(context, StreamMediaActivity.class), 0);

		Resources res = context.getResources();

		notification.setLatestEventInfo(context,
				res.getString(R.string.app_name),
				res.getString(R.string.app_is_playing),
				notification.contentIntent);

		return notification;
	}

	public abstract void showRunningNotification(Service context);
	public abstract void hideRunningNotification(Service context);

	private static class PreEclair extends ConnectionNotifier {
		private static class Holder {
			private static final PreEclair sInstance = new PreEclair();
		}

		@Override
		public void showRunningNotification(Service context) {
			getNotificationManager(context).notify(ONLINE_NOTIFICATION, newRunningNotification(context));
		}

		@Override
		public void hideRunningNotification(Service context) {
			getNotificationManager(context).cancel(ONLINE_NOTIFICATION);
		}
	}

	private static class EclairAndBeyond extends ConnectionNotifier {
		private static class Holder {
			private static final EclairAndBeyond sInstance = new EclairAndBeyond();
		}

		@Override
		public void showRunningNotification(Service context) {
			context.startForeground(ONLINE_NOTIFICATION, newRunningNotification(context));
		}

		@Override
		public void hideRunningNotification(Service context) {
			context.stopForeground(true);
		}
	}
}
