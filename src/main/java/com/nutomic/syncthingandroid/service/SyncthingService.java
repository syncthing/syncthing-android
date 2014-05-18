package com.nutomic.syncthingandroid.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.WebGuiActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;

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
	 * Interval in ms, at which connections to the web gui are performed on first start
	 * to find out if it's online.
	 */
	private static final long WEB_GUI_POLL_INTERVAL = 100;

	private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

	/**
	 * Callback for when the Syncthing web interface becomes first available after service start.
	 */
	public interface OnWebGuiAvailableListener {
		public void onWebGuiAvailable();
	}

	private LinkedList<OnWebGuiAvailableListener> mOnWebGuiAvailableListeners =
			new LinkedList<OnWebGuiAvailableListener>();

	private boolean mIsWebGuiAvailable = false;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	/**
	 * Thrown when execution of the native syncthing binary returns an error.
	 * Prints the syncthing log.
	 */
	public static class NativeExecutionException extends RuntimeException {

		private final String mLog;

		public NativeExecutionException(String message, String log) {
			super(message);
			mLog = log;
		}

		@Override
		public String getMessage() {
			return super.getMessage() + "\n" + mLog;
		}
	}
	/**
	 * Runs the syncthing binary from command line, and prints its output to logcat.
	 */
	private class NativeSyncthingRunnable implements Runnable {
		@Override
		public void run() throws NativeExecutionException {
			DataOutputStream dos = null;
			InputStreamReader isr = null;
			int ret = 1;
			String log = "";
			try	{
				Process p = Runtime.getRuntime().exec("sh");
				dos = new DataOutputStream(p.getOutputStream());
				// Set home directory to sdcard (so the "create repo" hint makes sense)
				dos.writeBytes("HOME=" +
						Environment.getExternalStorageDirectory().toString() + "\n");
				// Set syncthing config folder to app data folder.
				dos.writeBytes(getApplicationInfo().dataDir + "/" + BINARY_NAME + " " +
						"-home " + getApplicationInfo().dataDir + "\n");
				dos.writeBytes("exit\n");
				dos.flush();

				ret = p.waitFor();

				// Write syncthing binary output to log.
				// NOTE: This is only done on shutdown, not in real time.
				isr = new InputStreamReader(p.getInputStream());
				BufferedReader stdout = new BufferedReader(isr);
				String line;
				while((line = stdout.readLine()) != null) {
					log += "stderr: " + line + "\n";
					Log.w(TAG, "stderr: " + line);
				}
				isr = new InputStreamReader(p.getErrorStream());
				BufferedReader stderr = new BufferedReader(isr);
				while((line = stderr.readLine()) != null) {
					log += "stdout: " + line + "\n";
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
				if (ret != 1) {
					stopSelf();
					// Include the log for Play Store crash reports.
					throw new NativeExecutionException("Syncthing binary returned error code " +
							Integer.toString(ret), log);
				}
				try {
					dos.close();
					isr.close();
				}
				catch (IOException e) {
					Log.w(TAG, "Failed to close stream", e);
				}
			}
		}
	}

	/**
	 * Polls SYNCTHING_URL until it returns HTTP status OK, then calls all listeners
	 * in mOnWebGuiAvailableListeners and clears it.
	 */
	private class PollWebGuiAvailableTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... voids) {
			int status = 0;
			HttpClient httpclient = new DefaultHttpClient();
			HttpHead head = new HttpHead(SYNCTHING_URL);
			do {
				try {
					Thread.sleep(WEB_GUI_POLL_INTERVAL);
					HttpResponse response = httpclient.execute(head);
					// NOTE: status is not really needed, as HttpHostConnectException is thrown
					// earlier.
					status = response.getStatusLine().getStatusCode();
				}
				catch (HttpHostConnectException e) {
					// We catch this in every call, as long as the service is not online,
					// so we ignore and continue.
				}
				catch (IOException e) {
					Log.d(TAG, "Failed to poll for web interface", e);
				}
				catch (InterruptedException e) {
					Log.d(TAG, "Failed to poll for web interface", e);
				}
			} while(status != HttpStatus.SC_OK);
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			mIsWebGuiAvailable = true;
			for (OnWebGuiAvailableListener listener : mOnWebGuiAvailableListeners) {
				listener.onWebGuiAvailable();
			}
			mOnWebGuiAvailableListeners.clear();
		}
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

		new Thread(new NativeSyncthingRunnable()).start();

		new PollWebGuiAvailableTask().execute();
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
		new PostTask().execute(PostTask.URI_SHUTDOWN);
	}

	/**
	 * Register a listener for the web gui becoming available..
	 *
	 * If the web gui is already available, listener will be called immediately.
	 * Listeners are unregistered automatically after being called.
	 */
	public void registerOnWebGuiAvailableListener(OnWebGuiAvailableListener listener) {
		if (mIsWebGuiAvailable) {
			listener.onWebGuiAvailable();
		}
		else {
			mOnWebGuiAvailableListeners.addLast(listener);
		}
	}

}
