package com.nutomic.syncthingandroid.syncthing;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;

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

	private RestApi mApi;

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
			if (isFirstStart(SyncthingService.this)) {
				copyDefaultConfig();
			}
			updateConfig();

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
				if (ret != 0) {
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
			mApi = new RestApi(this, "http://" + syncthingUrl);
			registerOnWebGuiAvailableListener(mApi);
		}

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
		mApi.shutdown();
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
		return new File(getApplicationInfo().dataDir, CONFIG_FILE);
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
					r.setAttribute("ignorePerms", Boolean.toString(true));
					changed = true;
				}
			}

			// Write the changes back to file.
			if (changed) {
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
			Log.d(TAG, "Failed to save updated config", e);
		}
	}

	/**
	 * Returns true if this service has not been started before (ie config.xml does not exist).
	 */
	public static boolean isFirstStart(Context context) {
		return !new File(context.getApplicationInfo().dataDir, CONFIG_FILE).exists();
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
