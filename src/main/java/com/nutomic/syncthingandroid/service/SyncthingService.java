package com.nutomic.syncthingandroid.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.WebGuiActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import com.nutomic.syncthingandroid.PostTask;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

	private static final String TAG = "SyncthingService";

	private static final int NOTIFICATION_ID = 1;

	/**
	 * Path to the native, integrated syncthing binary, relative to the data folder
	 */
	private static final String BINARY_NAME = "lib/libsyncthing.so";

	/**
	 * URL of the local syncthing web UI.
	 */
	public static final String SYNCTHING_URL = "http://127.0.0.1:8080";

	/**
	 * Path to call for shutdown (with POST).
	 */
	private static final String PATH_SHUTDOWN = "/rest/shutdown";

	/**
	 * Path to call to get version name (with GET).
	 */
	public static final String PATH_VERSION = "/rest/version";

	private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

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
		n.flags |= Notification.FLAG_ONGOING_EVENT;
		startForeground(NOTIFICATION_ID, n);

		new Thread(new Runnable() {
			@Override
			public void run() {
				DataOutputStream dos = null;
				InputStreamReader isr = null;
				try	{
					Process p = Runtime.getRuntime().exec("sh");
					dos = new DataOutputStream(p.getOutputStream());
					// Set home directory to data folder for syncthing to use.
					dos.writeBytes("HOME=" + getApplicationInfo().dataDir + "\n");
					// Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
					dos.writeBytes(getApplicationInfo().dataDir + "/" + BINARY_NAME + " " +
							"-home " + getApplicationInfo().dataDir + "\n");
					dos.writeBytes("exit\n");
					dos.flush();

					int ret = p.waitFor();
					Log.d(TAG, "Syncthing binary exited with code " + Integer.toString(ret));

					// Write syncthing binary output to log.
					// NOTE: This is only done on shutdown, not live.
					isr = new InputStreamReader(p.getInputStream());
					BufferedReader stdout = new BufferedReader(isr);
					String line;
					while((line = stdout.readLine()) != null) {
						Log.w(TAG, "stderr: " + line);
					}
					isr = new InputStreamReader(p.getErrorStream());
					BufferedReader stderr = new BufferedReader(isr);
					while((line = stderr.readLine()) != null) {
						Log.i(TAG, "stdout: " + line);
					}
				}
				catch(IOException e) {
					Log.e(TAG, "Failed to execute syncthing binary or read output", e);
				}
				catch(InterruptedException e) {
					Log.e(TAG, "Failed to execute syncthing binary or read output", e);
				}
				finally {
					try {
						dos.close();
						isr.close();
					}
					catch (IOException e) {
						Log.w(TAG, "Failed to close stream", e);
					}
				}
			}
		}).start();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	/**
	 * Stops the native binary.
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		new PostTask().execute(SYNCTHING_URL + PATH_SHUTDOWN);
	}

}
