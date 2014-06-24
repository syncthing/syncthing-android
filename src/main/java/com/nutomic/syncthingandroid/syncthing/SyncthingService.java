package com.nutomic.syncthingandroid.syncthing;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.gui.MainActivity;
import com.nutomic.syncthingandroid.util.ConfigXml;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

	private static final String TAG = "SyncthingService";

	private static final int NOTIFICATION_RUNNING = 1;

	/**
	 * Intent action to perform a syncthing restart.
	 */
	public static final String ACTION_RESTART = "restart";

	/**
	 * Interval in ms at which the GUI is updated (eg {@link }LocalNodeInfoFragment}).
	 */
	public static final int GUI_UPDATE_INTERVAL = 1000;

	/**
	 * Path to the native, integrated syncthing binary, relative to the data folder
	 */
	private static final String BINARY_NAME = "lib/libsyncthing.so";

	/**
	 * Interval in ms, at which connections to the web gui are performed on first start
	 * to find out if it's online.
	 */
	private static final long WEB_GUI_POLL_INTERVAL = 100;

	/**
	 * File in the config folder that contains configuration.
	 */
	private static final String CONFIG_FILE = "config.xml";

	/**
	 * Name of the public key file in the data directory.
	 */
	private static final String PUBLIC_KEY_FILE = "cert.pem";

	/**
	 * Name of the private key file in the data directory.
	 */
	private static final String PRIVATE_KEY_FILE = "key.pem";

	private RestApi mApi;

	private final ReentrantLock mNativeLogLock = new ReentrantLock();

	private String mNativeLog = "";

	private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

	/**
	 * Callback for when the Syncthing web interface becomes first available after service start.
	 */
	public interface OnWebGuiAvailableListener {
		public void onWebGuiAvailable();
	}

	private final ReentrantLock mOnWebGuiAvailableListenersLock = new ReentrantLock();

	private final HashSet<OnWebGuiAvailableListener> mOnWebGuiAvailableListeners =
			new HashSet<OnWebGuiAvailableListener>();

	private boolean mIsWebGuiAvailable = false;

	public interface OnApiChangeListener {
		public void onApiChange(boolean isAvailable);
	}

	private final HashSet<WeakReference<OnApiChangeListener>> mOnApiAvailableListeners =
			new HashSet<WeakReference<OnApiChangeListener>>();

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
			mIsWebGuiAvailable = false;
			onApiChange(false);
			new PostTask() {
				@Override
				protected void onPostExecute(Void aVoid) {
					ConfigXml config = new ConfigXml(getConfigFile());
					mApi = new RestApi(SyncthingService.this,
							config.getWebGuiUrl(), config.getApiKey());
					registerOnWebGuiAvailableListener(mApi);
					new PollWebGuiAvailableTask().execute();
				}
			}.execute(mApi.getUrl(), PostTask.URI_RESTART, mApi.getApiKey());
		}
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
	 * Runs the syncthing binary from command line, and prints its output to logcat (on exit).
	 */
	private class SyncthingRunnable implements Runnable {
		@Override
		public void run() {
			DataOutputStream dos = null;
			int ret = 1;
			Process process = null;
			try	{
				process = Runtime.getRuntime().exec("sh");
				dos = new DataOutputStream(process.getOutputStream());
				// Set home directory to data folder for syncthing to use.
				dos.writeBytes("HOME=" + getFilesDir() + " ");
				// Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
				dos.writeBytes(getApplicationInfo().dataDir + "/" + BINARY_NAME + " " +
						"-home " + getFilesDir() + "\n");
				dos.writeBytes("exit\n");
				dos.flush();

				log(process.getErrorStream(), Log.INFO);

				ret = process.waitFor();
			}
			catch(IOException e) {
				Log.e(TAG, "Failed to execute syncthing binary or read output", e);
			}
			catch(InterruptedException e) {
				Log.e(TAG, "Failed to execute syncthing binary or read output", e);
			}
			finally {
				process.destroy();
				if (ret != 0) {
					stopSelf();
					// Include the log for Play Store crash reports.
					throw new NativeExecutionException("Syncthing binary returned error code " +
							Integer.toString(ret), mNativeLog);
				}
			}
		}
	}

	/**
	 * Logs the outputs of a stream to logcat and mNativeLog.
	 *
	 * @param is The stream to log.
	 * @param priority The log level, eg Log.INFO or Log.WARN.
	 */
	private void log(final InputStream is, final int priority) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line;
				try {
					while ((line = br.readLine()) != null) {
						mNativeLogLock.lock();
						Log.println(priority, TAG, ": " + line);
						mNativeLog += line + "\n";
						mNativeLogLock.unlock();
					}
				}
				catch (IOException e) {
					// NOTE: This is sometimes called on shutdown, as
					// Process.destroy() closes the stream.
					Log.w(TAG, "Failed to read syncthing command line output", e);
				}
			}
		}).start();
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
			HttpHead head = new HttpHead(mApi.getUrl());
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
					Log.w(TAG, "Failed to poll for web interface", e);
				}
				catch (InterruptedException e) {
					Log.w(TAG, "Failed to poll for web interface", e);
				}
			} while(status != HttpStatus.SC_OK);
			return null;
		}

		@Override
		protected void onPostExecute(Void aVoid) {
			Log.i(TAG, "Web GUI has come online at " + mApi.getUrl());
			mIsWebGuiAvailable = true;
			for (OnWebGuiAvailableListener listener : mOnWebGuiAvailableListeners) {
				listener.onWebGuiAvailable();
			}
			mOnWebGuiAvailableListeners.clear();
		}
	}

	/**
	 * Move config file, keys, and index files to "official" folder
	 *
	 * Intended to bring the file locations in older installs in line with
	 * newer versions.
	 */
	private void moveConfigFiles() {
		FilenameFilter idxFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".idx.gz");
			}
		};

		if (new File(getApplicationInfo().dataDir, PUBLIC_KEY_FILE).exists()) {
			try {
				File publicKey = new File(getApplicationInfo().dataDir, PUBLIC_KEY_FILE);
				publicKey.renameTo(new File(getFilesDir(), PUBLIC_KEY_FILE));
				File privateKey = new File(getApplicationInfo().dataDir, PRIVATE_KEY_FILE);
				privateKey.renameTo(new File(getFilesDir(), PRIVATE_KEY_FILE));
				File config = new File(getApplicationInfo().dataDir, CONFIG_FILE);
				config.renameTo(new File(getFilesDir(), CONFIG_FILE));

				File oldStorageDir = new File(getApplicationInfo().dataDir);
				File[] files = oldStorageDir.listFiles(idxFilter);
				for (File file : files) {
					if (file.isFile()) {
						file.renameTo(new File(getFilesDir(), file.getName()));
					}
				}
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to move config files", e);
			}
		}
	}

	/**
	 * Creates notification, starts native binary.
	 */
	@Override
	public void onCreate() {
		PendingIntent pi = PendingIntent.getActivity(
				this, 0, new Intent(this, MainActivity.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		Notification n = new NotificationCompat.Builder(this)
				.setContentTitle(getString(R.string.app_name))
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pi)
				.build();
		n.flags |= Notification.FLAG_ONGOING_EVENT;
		startForeground(NOTIFICATION_RUNNING, n);

		new StartupTask().execute();
	}

	/**
	 * Sets up the initial configuration, updates the config when coming from an old
	 * version, and reads syncthing URL and API key (these are passed internally as
	 * {@code Pair<String, String>}.
	 */
	private class StartupTask extends AsyncTask<Void, Void, Pair<String, String>> {
		@Override
		protected Pair<String, String> doInBackground(Void... voids) {Looper.prepare();
			if (isFirstStart(SyncthingService.this)) {
				Log.i(TAG, "App started for the first time. " +
						"Copying default config, keys will be generated automatically");
				copyDefaultConfig();
			}
			moveConfigFiles();
			ConfigXml config = new ConfigXml(getConfigFile());
			config.update();
			return new Pair<String, String>(config.getWebGuiUrl(), config.getApiKey());
		}

		@Override
		protected void onPostExecute(Pair<String, String> urlAndKey) {
			mApi = new RestApi(SyncthingService.this, urlAndKey.first, urlAndKey.second);
			Log.i(TAG, "Web GUI will be available at " + mApi.getUrl());
			// HACK: Make sure there is no syncthing binary left running from an improper
			// shutdown (eg Play Store update).
			// NOTE: This will log an exception if syncthing is not actually running.
			new PostTask().execute(mApi.getUrl(), PostTask.URI_SHUTDOWN, urlAndKey.second, "");
			registerOnWebGuiAvailableListener(mApi);
			new PollWebGuiAvailableTask().execute();
			new Thread(new SyncthingRunnable()).start();
		}
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
		Log.i(TAG, "Shutting down service");
		mApi.shutdown();
	}

	public boolean isWebGuiAvailable() {
		return mIsWebGuiAvailable;
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
			mOnWebGuiAvailableListeners.add(listener);
		}
	}

	private File getConfigFile() {
		return new File(getFilesDir(), CONFIG_FILE);
	}

	/**
	 * Returns true if this service has not been started before (ie config.xml does not exist).
	 *
	 * This will return true until the public key file has been generated.
	 */
	public static boolean isFirstStart(Context context) {
		return !new File(context.getFilesDir(), PUBLIC_KEY_FILE).exists();
	}

	/**
	 * Copies the default config file from res/raw/config_default.xml to (data folder)/config.xml.
	 */
	private void copyDefaultConfig() {
		InputStream in = null;
		FileOutputStream out = null;
		try {
			in = getResources().openRawResource(R.raw.config_default);
			out = new FileOutputStream(getConfigFile());
			byte[] buff = new byte[1024];
			int read;

			while ((read = in.read(buff)) > 0) {
				out.write(buff, 0, read);
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to write config file", e);
		}
		finally {
			try {
				in.close();
				out.close();
			}
			catch (IOException e) {
				Log.w(TAG, "Failed to close stream while copying config", e);
			}
		}
	}

	public RestApi getApi() {
		return mApi;
	}

	/**
	 * Register a listener for the syncthing API state changing.
	 *
	 * The listener is called immediately with the current state, and again whenever the state
	 * changes.
	 */
	public void registerOnApiChangeListener(OnApiChangeListener listener) {
		listener.onApiChange((mApi != null) ? mApi.isApiAvailable() : false);
		mOnApiAvailableListeners.add(new WeakReference<OnApiChangeListener>(listener));
	}

	/**
	 * Called when the state of the API changes.
	 *
	 * Must only be called from SyncthingService or {@link RestApi}.
	 */
	public void onApiChange(boolean isAvailable) {
		for (Iterator<WeakReference<OnApiChangeListener>> i = mOnApiAvailableListeners.iterator(); i.hasNext();) {
			WeakReference<OnApiChangeListener> listener = i.next();
			if (listener.get() != null) {
				listener.get().onApiChange(isAvailable);
			}
			else {
				i.remove();
			}
		}
	}

}
