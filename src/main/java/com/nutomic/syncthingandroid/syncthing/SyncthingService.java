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

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.WebGuiActivity;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
	 * Runs the syncthing binary from command line, and prints its output to logcat (on exit).
	 */
	private void runNative() {
		DataOutputStream dos = null;
		int ret = 1;
		Process process = null;
		try	{
			process = Runtime.getRuntime().exec("sh");
			dos = new DataOutputStream(process.getOutputStream());
			// Set home directory to data folder for syncthing to use.
			dos.writeBytes("HOME=" + getFilesDir() + "\n");
			// Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
			dos.writeBytes(getApplicationInfo().dataDir + "/" + BINARY_NAME + " " +
					"-home " + getFilesDir() + "\n");
			dos.writeBytes("exit\n");
			dos.flush();

			log(process.getInputStream(), Log.INFO);
			log(process.getErrorStream(), Log.WARN);

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
				Looper.prepare();
				if (isFirstStart(SyncthingService.this)) {
					Log.i(TAG, "App started for the first time. " +
							"Copying default config, keys will be generated automatically");
					copyDefaultConfig();
				}
				moveConfigFiles();
				updateConfig();

				String syncthingUrl = null;
				try {
					DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
					Document d = db.parse(getConfigFile());
					Element options = (Element)
							d.getDocumentElement().getElementsByTagName("gui").item(0);
					syncthingUrl = options.getElementsByTagName("address").item(0).getTextContent();
				}
				catch (SAXException e) {
					throw new RuntimeException("Failed to read gui url, aborting", e);
				}
				catch (ParserConfigurationException e) {
					throw new RuntimeException("Failed to read gui url, aborting", e);
				}
				catch (IOException e) {
					throw new RuntimeException("Failed to read gui url, aborting", e);
				}
				finally {
					mApi = new RestApi("http://" + syncthingUrl);
					Log.i(TAG, "Web GUI will be available at " + mApi.getUrl());
					registerOnWebGuiAvailableListener(mApi);
				}
				new PollWebGuiAvailableTask().execute();
				runNative();
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
			mOnWebGuiAvailableListeners.addLast(listener);
		}
	}

	private File getConfigFile() {
		return new File(getFilesDir(), CONFIG_FILE);
	}

	/**
	 * Updates the config file.
	 *
	 * Coming from 0.2.0 and earlier, globalAnnounceServer value "announce.syncthing.net:22025" is
	 * replaced with "194.126.249.5:22025" (as domain resolve is broken).
	 *
	 * Coming from 0.3.0 and earlier, the ignorePerms flag is set to true on every repository.
	 */
	private void updateConfig() {
		try {
			Log.i(TAG, "Checking for needed config updates");
			boolean changed = false;
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document d = db.parse(getConfigFile());


			// Hardcode default globalAnnounceServer ip.
			Element options = (Element)
					d.getDocumentElement().getElementsByTagName("options").item(0);
			Element globalAnnounceServer = (Element)
					options.getElementsByTagName("globalAnnounceServer").item(0);
			if (globalAnnounceServer.getTextContent().equals("announce.syncthing.net:22025")) {
				Log.i(TAG, "Replacing globalAnnounceServer host with ip");
				globalAnnounceServer.setTextContent("194.126.249.5:22025");
				changed = true;
			}

			// Set ignorePerms attribute.
			NodeList repos = d.getDocumentElement().getElementsByTagName("repository");
			for (int i = 0; i < repos.getLength(); i++) {
				Element r = (Element) repos.item(i);
				if (!r.hasAttribute("ignorePerms") ||
						!Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
					Log.i(TAG, "Set 'ignorePerms' on repository " + r.getAttribute("id"));
					r.setAttribute("ignorePerms", Boolean.toString(true));
					changed = true;
				}
			}

			// Write the changes back to file.
			if (changed) {
				Log.i(TAG, "Writing updated config back to file");
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				DOMSource domSource = new DOMSource(d);
				StreamResult streamResult = new StreamResult(getConfigFile());
				transformer.transform(domSource, streamResult);
			}
		}
		catch (ParserConfigurationException e) {
			Log.w(TAG, "Failed to parse config", e);
		}
		catch (IOException e) {
			Log.w(TAG, "Failed to parse config", e);
		}
		catch (SAXException e) {
			Log.w(TAG, "Failed to parse config", e);
		}
		catch (TransformerException e) {
			Log.w(TAG, "Failed to save updated config", e);
		}
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
			int read = 0;

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

}
