package com.nutomic.syncthingandroid;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

public class SyncthingService extends Service {

	private static final String TAG = "SyncthingService";

	private static final int NOTIFICATION_ID = 1;

	@Override
	public void onCreate() {
		PendingIntent pi = PendingIntent.getActivity(
				this, 0, new Intent(this, WebGuiActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		Notification n = new NotificationCompat.Builder(this)
				.setContentTitle(getString(R.string.app_name))
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pi)
				.build();
		startForeground(NOTIFICATION_ID, n);

		new Thread(new Runnable() {
			@Override
			public void run() {
				System.loadLibrary("syncthing");
			}
		}).start();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
