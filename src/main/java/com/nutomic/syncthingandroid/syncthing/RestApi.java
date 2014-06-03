package com.nutomic.syncthingandroid.syncthing;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener {

	private static final String TAG = "RestApi";

	/**
	 * Parameter for {@link #getValue} or {@link #setValue} referring to "options" config item.
	 */
	public static final String TYPE_OPTIONS = "Options";

	/**
	 * Parameter for {@link #getValue} or {@link #setValue} referring to "gui" config item.
	 */
	public static final String TYPE_GUI = "GUI";

	public static class Node {
		public String Addresses;
		public String Name;
		public String NodeID;
	}

	public static class Repository {
		public String Directory;
		public String ID;
		public final boolean IgnorePerms = true;
		public String Invalid;
		public List<Node> Nodes;
		public boolean ReadOnly;
		public Versioning Versioning;
	}

	public static class Versioning {
		protected final Map<String, String> mParams = new HashMap<String, String>();
		public String getType() {
			return "";
		}
		public Map<String, String> getParams() {
			return mParams;
		}
	}

	public static class SimpleVersioning extends Versioning {
		@Override
		public String getType() {
			return "simple";
		}
		public void setParams(int keep) {
			mParams.put("keep", Integer.toString(keep));
		}
	}

	public interface OnApiAvailableListener {
		public void onApiAvailable();
	}

	private final LinkedList<OnApiAvailableListener> mOnApiAvailableListeners =
			new LinkedList<OnApiAvailableListener>();

	private static final int NOTIFICATION_RESTART = 2;

	private final Context mContext;

	private String mVersion;

	private final String mUrl;

	private String mApiKey;

	private JSONObject mConfig;

	private final NotificationManager mNotificationManager;

	public RestApi(Context context, String url, String apiKey) {
		mContext = context;
		mUrl = url;
		mApiKey = apiKey;
		mNotificationManager = (NotificationManager)
				mContext.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
	 * Returns the full URL of the web gui.
	 */
	public String getUrl() {
		return mUrl;
	}

	/**
	 * Gets version and config, then calls any OnApiAvailableListeners.
	 */
	@Override
	public void onWebGuiAvailable() {
		new GetTask() {
			@Override
			protected void onPostExecute(String version) {
				mVersion = version;
				Log.i(TAG, "Syncthing version is " + mVersion);
			}
		}.execute(mUrl, GetTask.URI_VERSION, mApiKey);
		new GetTask() {
			@Override
			protected void onPostExecute(String config) {
				try {
					mConfig = new JSONObject(config);
					for (OnApiAvailableListener listener : mOnApiAvailableListeners) {
						listener.onApiAvailable();
					}
					mOnApiAvailableListeners.clear();
				}
				catch (JSONException e) {
					Log.w(TAG, "Failed to parse config", e);
				}
			}
		}.execute(mUrl, GetTask.URI_CONFIG, mApiKey);
	}

	/**
	 * Returns the version name, or a (text) error message on failure.
	 */
	public String getVersion() {
		return mVersion;
	}

	/**
	 * Stops syncthing. You should probably use SyncthingService.stopService() instead.
	 */
	public void shutdown() {
		mNotificationManager.cancel(NOTIFICATION_RESTART);
		new PostTask().execute(mUrl, PostTask.URI_SHUTDOWN, mApiKey, "");
	}

	/**
	 * Restarts the syncthing binary.
	 */
	public void restart() {
		new PostTask().execute(mUrl, PostTask.URI_RESTART);
	}

	/**
	 * Gets a value from config,
	 *
	 * Booleans are returned as {@link }Boolean#toString}, arrays as space seperated string.
	 *
	 * @param name {@link #TYPE_OPTIONS} or {@link #TYPE_GUI}
	 * @param key The key to read from.
	 * @return The value as a String, or null on failure.
	 */
	public String getValue(String name, String key) {
		try {
			Object value = mConfig.getJSONObject(name).get(key);
			return (value instanceof JSONArray)
					? ((JSONArray) value).join(" ").replace("\"", "")
					: String.valueOf(value);
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to get value for " + key, e);
			return null;
		}
	}

	/**
	 * Sets a value to config and sends it via Rest API.
	 *
	 * Booleans must be passed as {@link Boolean}, arrays as space seperated string
	 * with isArray true.
	 *
	 * @param name {@link #TYPE_OPTIONS} or {@link #TYPE_GUI}
	 * @param key The key to write to.
	 * @param value The new value to set, either String, Boolean or Integer.
	 * @param isArray True iff value is a space seperated String that should be converted to array.
	 */
	public <T> void setValue(String name, String key, T value, boolean isArray) {
		try {
			mConfig.getJSONObject(name).put(key, (isArray)
					? listToJson(((String) value).split(" "))
					: value);
			configUpdated();
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to set value for " + key, e);
		}
	}

	/**
	 * Converts an array of strings to JSON array. Like JSONArray#JSONArray(Object array), but
	 * works on all API levels.
	 */
	private JSONArray listToJson(String[] list) {
		JSONArray json = new JSONArray();
		for (String s : list) {
			json.put(s);
		}
		return json;
	}

	/**
	 * Sends the updated mConfig via Rest API to syncthing and displays a "restart" notification.
	 */
	private void configUpdated() {
		new PostTask().execute(mUrl, PostTask.URI_CONFIG, mConfig.toString());

		Intent i = new Intent(mContext, SyncthingService.class)
				.setAction(SyncthingService.ACTION_RESTART);
		PendingIntent pi = PendingIntent.getService(mContext, 0, i, 0);

		Notification n = new NotificationCompat.Builder(mContext)
				.setContentTitle(mContext.getString(R.string.restart_notif_title))
				.setContentText(mContext.getString(R.string.restart_notif_text))
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pi)
				.build();
		n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
		mNotificationManager.notify(NOTIFICATION_RESTART, n);
	}

	/**
	 * Returns a list of all existing nodes.
	 */
	public List<Node> getNodes() {
		try {
			return getNodes(mConfig.getJSONArray("Nodes"));
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to read nodes", e);
			return null;
		}
	}

	/**
	 * Returns a list of all nodes in the array nodes.
	 */
	private List<Node> getNodes(JSONArray nodes) throws JSONException {
		List<Node> ret;
		ret = new ArrayList<Node>(nodes.length());
		for (int i = 0; i < nodes.length(); i++) {
			JSONObject json = nodes.getJSONObject(i);
			Node n = new Node();
			if (!json.isNull("Addresses")) {
				n.Addresses = json.getJSONArray("Addresses").join(" ").replace("\"", "");
			}
			n.Name = json.getString("Name");
			n.NodeID = json.getString("NodeID");
			ret.add(n);
		}
		return ret;
	}

	/**
	 * Returns a list of all existing repositores.
	 */
	public List<Repository> getRepositories() {
		List<Repository> ret = null;
		try {
			JSONArray repos = mConfig.getJSONArray("Repositories");
			ret = new ArrayList<Repository>(repos.length());
			for (int i = 0; i < repos.length(); i++) {
				JSONObject json = repos.getJSONObject(i);
				Repository r = new Repository();
				r.Directory = json.getString("Directory");
				r.ID = json.getString("ID");
				// Hardcoded to true because missing permissions support.
				// r.IgnorePerms = json.getBoolean("IgnorePerms");
				r.Invalid = json.getString("Invalid");
				r.Nodes = getNodes(json.getJSONArray("Nodes"));

				r.ReadOnly = json.getBoolean("ReadOnly");
				JSONObject versioning = json.getJSONObject("Versioning");
				if (versioning.getString("Type").equals("simple")) {
					SimpleVersioning sv = new SimpleVersioning();
					JSONObject params = versioning.getJSONObject("Params");
					sv.setParams(params.getInt("keep"));
					r.Versioning = sv;
				}
				else {
					r.Versioning = new Versioning();
				}

				ret.add(r);
			}
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to read nodes", e);
		}
		return ret;
	}

	/**
	 * Register a listener for the web gui becoming available..
	 *
	 * If the web gui is already available, listener will be called immediately.
	 * Listeners are unregistered automatically after being called.
	 */
	public void registerOnApiAvailableListener(OnApiAvailableListener listener) {
		if (mConfig != null) {
			listener.onApiAvailable();
		}
		else {
			mOnApiAvailableListeners.addLast(listener);
		}
	}

}
