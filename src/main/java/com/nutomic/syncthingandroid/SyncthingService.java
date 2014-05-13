package com.nutomic.syncthingandroid;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

	private static final String TAG = "SyncthingService";

	private static final int NOTIFICATION_ID = 1;

	/**
	 * URL of the local syncthing web UI.
	 */
	public static final String SYNCTHING_URL = "http://127.0.0.1:8080";

	/**
	 * Path to call for shutdown (with POST).
	 */
	private static final String PATH_SHUTDOWN = "/rest/shutdown";

	private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

	/**
	 * Creates notification, starts native binary.
	 */
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
		return mBinder;
	}

	/**
	 * Stops the native binary.
	 *
	 * NOTE: This stops all Activities and Services.
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		new RestTask() {
			@Override
			protected void onPostExecute(Void aVoid) {
				// HACK: Android does not release the memory for the native binary, so we explicitly
				// stop the VM and thus also all Activities/Services.
				System.exit(0);
			}
		}.execute(SYNCTHING_URL + PATH_SHUTDOWN);
	}
}
