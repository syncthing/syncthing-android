package com.nutomic.syncthingandroid.syncthing;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

	/**
	 * Key of the map element containing connection info for the local node, in the return
	 * value of {@link #getConnections}
	 */
	public static final String LOCAL_NODE_CONNECTIONS = "total";

	public static class Node {
		public String Addresses;
		public String Name;
		public String NodeID;
	}

	public static class SystemInfo {
		public long alloc;
		public double cpuPercent;
		public boolean extAnnounceOK;
		public int goroutines;
		public String myID;
		public long sys;
	}

	public static class Repo {
		public String Directory;
		public String ID;
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

	public static class Connection {
		public String At;
		public long InBytesTotal;
		public long OutBytesTotal;
		public long InBits;
		public long OutBits;
		public String Address;
		public String ClientVersion;
		public int Completion;
	}

	public static class Model {
		public long globalBytes;
		public long globalDeleted;
		public long globalFiles;
		public long localBytes;
		public long localDeleted;
		public long localFiles;
		public long inSyncBytes;
		public long inSyncFiles;
		public long needBytes;
		public long needFiles;
		public String state;
		public String invalid;
	}

	private static final int NOTIFICATION_RESTART = 2;

	private final SyncthingService mSyncthingService;

	private String mVersion;

	private final String mUrl;

	private String mApiKey;

	private JSONObject mConfig;

	private String mLocalNodeId;

	private final NotificationManager mNotificationManager;

	private boolean mRestartPostponed = false;

	/**
	 * Stores the result of the last successful request to {@link GetTask#URI_CONNECTIONS},
	 * or an empty HashMap.
	 */
	private HashMap<String, Connection> mPreviousConnections = new HashMap<String, Connection>();

	/**
	 * Stores the timestamp of the last successful request to {@link GetTask#URI_CONNECTIONS}.
	 */
	private long mPreviousConnectionTime = 0;

	/**
	 * Stores the latest result of {@link #getModel(String, OnReceiveModelListener)} for each repo,
	 * for calculating node percentage in {@link #getConnections(OnReceiveConnectionsListener)}.
	 */
	private HashMap<String, Model> mCachedModelInfo = new HashMap<String, Model>();

	public RestApi(SyncthingService syncthingService, String url, String apiKey) {
		mSyncthingService = syncthingService;
		mUrl = url;
		mApiKey = apiKey;
		mNotificationManager = (NotificationManager)
				mSyncthingService.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
	 * Returns the full URL of the web gui.
	 */
	public String getUrl() {
		return mUrl;
	}

	/**
	 * Returns the API key needed to access the Rest API.
	 */
	public String getApiKey() {
		return mApiKey;
	}

	/**
	 * Number of previous calls to {@link #tryIsAvailable()}.
	 */
	private AtomicInteger mAvailableCount = new AtomicInteger(0);

	/**
	 * Number of asynchronous calls performed in {@link #onWebGuiAvailable()}.
	 */
	private static final int TOTAL_STARTUP_CALLS = 3;

	/**
	 * Gets local node id, syncthing version and config, then calls all OnApiAvailableListeners.
	 */
	@Override
	public void onWebGuiAvailable() {
		new GetTask() {
			@Override
			protected void onPostExecute(String version) {
				mVersion = version;
				Log.i(TAG, "Syncthing version is " + mVersion);
				tryIsAvailable();
			}
		}.execute(mUrl, GetTask.URI_VERSION, mApiKey);
		new GetTask() {
			@Override
			protected void onPostExecute(String config) {
				try {
					mConfig = new JSONObject(config);
					tryIsAvailable();
				}
				catch (JSONException e) {
					Log.w(TAG, "Failed to parse config", e);
				}
			}
		}.execute(mUrl, GetTask.URI_CONFIG, mApiKey);
		getSystemInfo(new OnReceiveSystemInfoListener() {
			@Override
			public void onReceiveSystemInfo(SystemInfo info) {
				mLocalNodeId = info.myID;
				tryIsAvailable();
			}
		});
	}

	/**
	 * Increments mAvailableCount by one, and, if it reached TOTAL_STARTUP_CALLS,
	 * calls {@link SyncthingService#onApiChange(boolean)}.
	 */
	private void tryIsAvailable() {
		int value = mAvailableCount.incrementAndGet();
		if (value == TOTAL_STARTUP_CALLS) {
			mSyncthingService.onApiChange(true);
		}
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
		new PostTask().execute(mUrl, PostTask.URI_SHUTDOWN, mApiKey);
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
	public <T> void setValue(String name, String key, T value, boolean isArray, Activity activity) {
		try {
			mConfig.getJSONObject(name).put(key, (isArray)
					? listToJson(((String) value).split(" "))
					: value);
			configUpdated(activity);
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
	@TargetApi(11)
	private void configUpdated(Context context) {
		new PostTask().execute(mUrl, PostTask.URI_CONFIG, mApiKey, mConfig.toString());

		if (mRestartPostponed)
			return;

		final Intent intent = new Intent(mSyncthingService, SyncthingService.class)
				.setAction(SyncthingService.ACTION_RESTART);

		AlertDialog.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
				? new AlertDialog.Builder(context.getApplicationContext(), AlertDialog.THEME_HOLO_LIGHT)
				: new AlertDialog.Builder(context.getApplicationContext());
		AlertDialog dialog = builder.setMessage(R.string.restart_title)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						mSyncthingService.startService(intent);
					}
				})
				.setNegativeButton(R.string.restart_later, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						createRestartNotification();
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						createRestartNotification();
					}
				})
				.create();
		dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
		dialog.show();
	}

	/**
	 * Creates a notification prompting the user to restart the app.
	 */
	private void createRestartNotification() {
		Intent intent = new Intent(mSyncthingService, SyncthingService.class)
				.setAction(SyncthingService.ACTION_RESTART);
		PendingIntent pi = PendingIntent.getService(mSyncthingService, 0, intent, 0);

		Notification n = new NotificationCompat.Builder(mSyncthingService)
				.setContentTitle(mSyncthingService.getString(R.string.restart_title))
				.setContentText(mSyncthingService.getString(R.string.restart_notification_text))
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pi)
				.build();
		n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
		mNotificationManager.notify(NOTIFICATION_RESTART, n);
		mRestartPostponed = true;
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
	 * Result listener for {@link #getSystemInfo(OnReceiveSystemInfoListener)}.
	 */
	public interface OnReceiveSystemInfoListener {
		public void onReceiveSystemInfo(SystemInfo info);
	}

	/**
	 * Requests and parses information about current system status and resource usage.
	 *
	 * @param listener Callback invoked when the result is received.
	 */
	public void getSystemInfo(final OnReceiveSystemInfoListener listener) {
		new GetTask() {
			@Override
			protected void onPostExecute(String s) {
				if (s == null)
					return;

				try {
					JSONObject system = new JSONObject(s);
					SystemInfo si = new SystemInfo();
					si.alloc = system.getLong("alloc");
					si.cpuPercent = system.getDouble("cpuPercent");
					si.extAnnounceOK = system.optBoolean("extAnnounceOK", false);
					si.goroutines = system.getInt("goroutines");
					si.myID = system.getString("myID");
					si.sys = system.getLong("sys");
					listener.onReceiveSystemInfo(si);
				}
				catch (JSONException e) {
					Log.w(TAG, "Failed to read system info", e);
				}
			}
		}.execute(mUrl, GetTask.URI_SYSTEM, mApiKey);
	}

	/**
	 * Returns a list of all nodes in the array nodes, excluding the local node.
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
			if (!n.NodeID.equals(mLocalNodeId)) {
				ret.add(n);
			}
		}
		return ret;
	}

	/**
	 * Returns a list of all existing repositores.
	 */
	public List<Repo> getRepos() {
		if (mConfig == null)
			return new ArrayList<Repo>();

		List<Repo> ret = null;
		try {
			JSONArray repos = mConfig.getJSONArray("Repositories");
			ret = new ArrayList<Repo>(repos.length());
			for (int i = 0; i < repos.length(); i++) {
				JSONObject json = repos.getJSONObject(i);
				Repo r = new Repo();
				r.Directory = json.getString("Directory");
				r.ID = json.getString("ID");
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
	 * Converts a number of bytes to a human readable file size (eg 3.5 GB).
	 */
	public static String readableFileSize(Context context, long bytes) {
		final String[] units = context.getResources().getStringArray(R.array.file_size_units);
		if (bytes <= 0) return "0 " + units[0];
		int digitGroups = (int) (Math.log10(bytes)/Math.log10(1024));
		return new DecimalFormat("#,##0.#")
				.format(bytes/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

	/**
	 * Converts a number of bytes to a human readable transfer rate in bits (eg 100 Kb/s).
	 */
	public static String readableTransferRate(Context context, long bits) {
		final String[] units = context.getResources().getStringArray(R.array.transfer_rate_units);
		if (bits <= 0) return "0 " + units[0];
		int digitGroups = (int) (Math.log10(bits)/Math.log10(1024));
		return new DecimalFormat("#,##0.#")
				.format(bits/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
	}

	/**
	 * Listener for {@link #getConnections}.
	 */
	public interface OnReceiveConnectionsListener {

		/**
		 * @param connections Map from Node ID to {@link Connection}.
		 *
		 * NOTE: The parameter connections is cached internally. Do not modify it or
		 *       any of its contents.
		 */
		public void onReceiveConnections(Map<String, Connection> connections);
	}

	/**
	 * Returns connection info for the local node and all connected nodes.
	 *
	 * Use the key {@link #LOCAL_NODE_CONNECTIONS} to get connection info for the local node.
	 */
	public void getConnections(final OnReceiveConnectionsListener listener) {
		new GetTask() {
			@Override
			protected void onPostExecute(String s) {
				if (s == null)
					return;

				Long now = System.currentTimeMillis();
				Long difference = (now - mPreviousConnectionTime) / 1000;
				if (difference < 1) {
					listener.onReceiveConnections(mPreviousConnections);
					return;
				}

				try {
					JSONObject json = new JSONObject(s);
					String[] names = json.names().join(" ").replace("\"", "").split(" ");
					HashMap<String, Connection> connections = new HashMap<String, Connection>();
					for (String nodeId : names) {
						Connection c = new Connection();
						JSONObject conn = json.getJSONObject(nodeId);
						c.Address = nodeId;
						c.At = conn.getString("At");
						c.InBytesTotal = conn.getLong("InBytesTotal");
						c.OutBytesTotal = conn.getLong("OutBytesTotal");
						c.Address = conn.getString("Address");
						c.ClientVersion = conn.getString("ClientVersion");
						c.Completion = getNodeCompletion(nodeId);

						Connection prev = (mPreviousConnections.containsKey(nodeId))
								? mPreviousConnections.get(nodeId)
								: new Connection();
						mPreviousConnectionTime = now;
						if (difference != 0) {
							c.InBits = Math.max(0, 8 *
									(conn.getLong("InBytesTotal") - prev.InBytesTotal) / difference);
							c.OutBits = Math.max(0, 8 *
									(conn.getLong("OutBytesTotal") - prev.OutBytesTotal) / difference);
						}

						connections.put(nodeId, c);

					}
					mPreviousConnections = connections;
					listener.onReceiveConnections(mPreviousConnections);
				}
				catch (JSONException e) {
					Log.w(TAG, "Failed to parse connections", e);
				}
			}
		}.execute(mUrl, GetTask.URI_CONNECTIONS, mApiKey);
	}

	/**
	 * Calculates completion percentage for the given node using {@link #mCachedModelInfo}.
	 */
	private int getNodeCompletion(String nodeId) {
		int repoCount = 0;
		float percentageSum = 0;
		for (String id : mCachedModelInfo.keySet()) {
			boolean isShared = false;
			outerloop:
			for (Repo r : getRepos()) {
				for (Node n : r.Nodes) {
					if (n.NodeID.equals(nodeId)) {
						isShared = true;
						break outerloop;
					}

				}
			}
			if (isShared) {
				long global = mCachedModelInfo.get(id).globalBytes;
				long local = mCachedModelInfo.get(id).localBytes;
				percentageSum += (global != 0)
						? (local * 100f) / global
						: 100f;
				repoCount++;
			}
		}
		return (repoCount != 0)
				? (int) percentageSum / repoCount
				: 100;
	}


	/**
	 * Listener for {@link #getModel}.
	 */
	public interface OnReceiveModelListener {
		public void onReceiveModel(String repoId, Model model);
	}

	/**
	 * Returns status information about the repo with the given ID.
	 */
	public void getModel(final String repoId, final OnReceiveModelListener listener) {
		new GetTask() {
			@Override
			protected void onPostExecute(String s) {
				if (s == null)
					return;

				try {
					JSONObject json = new JSONObject(s);
					Model m = new Model();
					m.globalBytes = json.getLong("globalBytes");
					m.globalDeleted = json.getLong("globalDeleted");
					m.globalFiles = json.getLong("globalFiles");
					m.localBytes = json.getLong("localBytes");
					m.localDeleted = json.getLong("localDeleted");
					m.localFiles = json.getLong("localFiles");
					m.inSyncBytes = json.getLong("inSyncBytes");
					m.inSyncFiles = json.getLong("inSyncFiles");
					m.needBytes = json.getLong("needBytes");
					m.needFiles = json.getLong("needFiles");
					m.state = json.getString("state");
					m.invalid = json.optString("invalid");
					mCachedModelInfo.put(repoId, m);
					listener.onReceiveModel(repoId, m);
				}
				catch (JSONException e) {
					Log.w(TAG, "Failed to read repository info", e);
				}
			}
		}.execute(mUrl, GetTask.URI_MODEL, mApiKey, "repo", repoId);
	}

	/**
	 * Updates or creates the given node, depending on whether it already exists.
	 *
	 * @param node Settings of the node to edit. To create a node, pass a non-existant node ID.
	 * @param listener {@link OnNodeIdNormalizedListener} for the normalized node ID.
	 */
	public void editNode(final Node node,
			final OnNodeIdNormalizedListener listener) {
		mSyncthingService.getApi().normalizeNodeId(node.NodeID,
				new RestApi.OnNodeIdNormalizedListener() {
					@Override
					public void onNodeIdNormalized(String normalizedId, String error) {
						listener.onNodeIdNormalized(normalizedId, error);
						if (normalizedId == null)
							return;

						node.NodeID = normalizedId;
						// If the node already exists, just update it.
						boolean create = true;
						for (RestApi.Node n : getNodes()) {
							if (n.NodeID.equals(node.NodeID)) {
								create = false;
							}
						}

						try {
							JSONArray nodes = mConfig.getJSONArray("Nodes");
							JSONObject n = null;
							if (create) {
								n = new JSONObject();
								nodes.put(n);
							}
							else {
								for (int i = 0; i < nodes.length(); i++) {
									JSONObject json = nodes.getJSONObject(i);
									if (node.NodeID.equals(json.getString("NodeID"))) {
										n = nodes.getJSONObject(i);
										break;
									}
								}
							}
							n.put("NodeID", node.NodeID);
							n.put("Name", node.Name);
							n.put("Addresses", listToJson(node.Addresses.split(" ")));
							configUpdated(mSyncthingService);
						}
						catch (JSONException e) {
							Log.w(TAG, "Failed to read nodes", e);
						}
					}
				});
	}

	/**
	 * Deletes the given node from syncthing.
	 */
	public void deleteNode(Node node, Activity activity) {
		try {
			JSONArray nodes = mConfig.getJSONArray("Nodes");

			for (int i = 0; i < nodes.length(); i++) {
				JSONObject json = nodes.getJSONObject(i);
				if (node.NodeID.equals(json.getString("NodeID"))) {
					mConfig.remove("Nodes");
					mConfig.put("Nodes", delete(nodes, nodes.getJSONObject(i)));
					break;
				}
			}
			configUpdated(activity);
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to edit repo", e);
		}
	}

	/**
	 * Updates or creates the given node.
	 */
	public void editRepo(Repo repo, boolean create, Activity activity) {
		try {
			JSONArray repos = mConfig.getJSONArray("Repositories");
			JSONObject r = null;
			if (create) {
				r = new JSONObject();
				repos.put(r);
			}
			else {
				for (int i = 0; i < repos.length(); i++) {
					JSONObject json = repos.getJSONObject(i);
					if (repo.ID.equals(json.getString("ID"))) {
						r = repos.getJSONObject(i);
						break;
					}
				}
			}
			r.put("Directory", repo.Directory);
			r.put("ID", repo.ID);
			r.put("IgnorePerms", true);
			r.put("ReadOnly", repo.ReadOnly);
			JSONArray nodes = new JSONArray();
			for (Node n : repo.Nodes) {
				JSONObject element = new JSONObject();
				element.put("Addresses", listToJson(n.Addresses.split(" ")));
				element.put("Name", n.Name);
				element.put("NodeID", n.NodeID);
				nodes.put(element);
			}
			r.put("Nodes", nodes);
			JSONObject versioning = new JSONObject();
			versioning.put("Type", repo.Versioning.getType());
			JSONObject params = new JSONObject();
			versioning.put("Params", params);
			for (String key : repo.Versioning.getParams().keySet()) {
				params.put(key, repo.Versioning.getParams().get(key));
			}
			r.put("Versioning", versioning);
			configUpdated(activity);
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to edit repo " + repo.ID + " at " + repo.Directory, e);
		}
	}

	/**
	 * Deletes the given repository from syncthing.
	 */
	public void deleteRepo(Repo repo, Activity activity) {
		try {
			JSONArray repos = mConfig.getJSONArray("Repositories");

			for (int i = 0; i < repos.length(); i++) {
				JSONObject json = repos.getJSONObject(i);
				if (repo.ID.equals(json.getString("ID"))) {
					mConfig.remove("Repositories");
					mConfig.put("Repositories", delete(repos, repos.getJSONObject(i)));
					break;
				}
			}
			configUpdated(activity);
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to edit repo", e);
		}
	}

	/**
	 * Replacement for {@link org.json.JSONArray#remove(int)}, which is not available on older APIs.
	 */
	private JSONArray delete(JSONArray array, JSONObject delete) throws JSONException {
		JSONArray newArray = new JSONArray();
		for (int i = 0; i < array.length(); i++) {
			if (!array.getJSONObject(i).equals(delete)) {
				newArray.put(array.get(i));
			}
		}
		return newArray;
	}

	/**
	 * Result listener for {@link #normalizeNodeId(String, OnNodeIdNormalizedListener)}.
	 */
	public interface OnNodeIdNormalizedListener {
		/**
		 * On any call, exactly one parameter will be null.
		 *
		 * @param normalizedId The normalized node ID, or null on error.
		 * @param error An error message, or null on success.
		 */
		public void onNodeIdNormalized(String normalizedId, String error);
	}

	/**
	 * Normalizes a given node ID.
	 */
	public void normalizeNodeId(String id, final OnNodeIdNormalizedListener listener) {
		new GetTask() {
			@Override
			protected void onPostExecute(String s) {
				super.onPostExecute(s);
				String normalized = null;
				String error = null;
				try {
					JSONObject json = new JSONObject(s);
					normalized = json.optString("id", null);
					error = json.optString("error", null);
				}
				catch (JSONException e) {
					Log.d(TAG, "Failed to parse normalized node ID JSON", e);
				}
				listener.onNodeIdNormalized(normalized, error);
			}
		}.execute(mUrl, GetTask.URI_NODEID, mApiKey, "id", id);
	}

	public boolean isApiAvailable() {
		return mAvailableCount.get() == TOTAL_STARTUP_CALLS;
	}

	/**
	 * Shares the given node id via Intent. Must be called from an Activity.
	 */
	public static void shareNodeId(Activity activity, String id) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, id);
		activity.startActivity(Intent.createChooser(
				shareIntent, activity.getString(R.string.send_node_id_to)));
	}

	/**
	 * Copies the given node ID to the clipboard (and shows a Toast telling about it).
	 *
	 * @param id The node ID to copy.
	 */
	@TargetApi(11)
	public void copyNodeId(String id) {
		int sdk = android.os.Build.VERSION.SDK_INT;
		if(sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
			android.text.ClipboardManager clipboard = (android.text.ClipboardManager)
					mSyncthingService.getSystemService(Context.CLIPBOARD_SERVICE);
			clipboard.setText(id);
		} else {
			ClipboardManager clipboard = (ClipboardManager)
					mSyncthingService.getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip =	ClipData.newPlainText(mSyncthingService.getString(R.string.node_id), id);
			clipboard.setPrimaryClip(clip);
		}
		Toast.makeText(mSyncthingService, R.string.node_id_copied_to_clipboard, Toast.LENGTH_SHORT)
				.show();
	}

}
