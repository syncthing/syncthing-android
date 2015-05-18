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
import android.widget.Toast;

import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.util.FolderObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener,
        FolderObserver.OnFolderFileChangeListener {

    private static final String TAG = "RestApi";

    /**
     * Parameter for {@link #getValue} or {@link #setValue} referring to "options" config item.
     */
    public static final String TYPE_OPTIONS = "options";

    /**
     * Parameter for {@link #getValue} or {@link #setValue} referring to "gui" config item.
     */
    public static final String TYPE_GUI = "gui";

    /**
     * The name of the HTTP header used for the syncthing API key.
     */
    public static final String HEADER_API_KEY = "X-API-Key";

    /**
     * Key of the map element containing connection info for the local device, in the return
     * value of {@link #getConnections}
     */
    public static final String TOTAL_STATS = "total";

    public static class Device implements Serializable {
        public String addresses;
        public String name;
        public String deviceID;
        public String compression;
        public boolean introducer;
    }

    public static class SystemInfo {
        public long alloc;
        public double cpuPercent;
        public int extAnnounceConnected; // Number of connected announce servers.
        public int extAnnounceTotal; // Total number of configured announce servers.
        public int goroutines;
        public String myID;
        public long sys;
    }

    public static class Folder implements Serializable {
        public String path;
        public String id;
        public String invalid;
        public List<String> deviceIds;
        public boolean readOnly;
        public int rescanIntervalS;
        public Versioning versioning;
    }

    public static class Versioning implements Serializable {
        protected final Map<String, String> mParams = new HashMap<>();

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
        public String at;
        public long inBytesTotal;
        public long outBytesTotal;
        public long inBits;
        public long outBits;
        public String address;
        public String clientVersion;
        public int completion;
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

    private final Context mContext;

    private String mVersion;

    private final String mUrl;

    private final String mApiKey;

    private final String mGuiUser;

    private final String mGuiPassword;

    private final String mHttpsCertPath;

    private JSONObject mConfig;

    private String mLocalDeviceId;

    private boolean mRestartPostponed = false;

    /**
     * Stores the result of the last successful request to {@link GetTask#URI_CONNECTIONS},
     * or an empty Map.
     */
    private Map<String, Connection> mPreviousConnections = new HashMap<>();

    /**
     * Stores the timestamp of the last successful request to {@link GetTask#URI_CONNECTIONS}.
     */
    private long mPreviousConnectionTime = 0;

    /**
     * Stores the latest result of {@link #getModel(String, OnReceiveModelListener)} for each folder,
     * for calculating device percentage in {@link #getConnections(OnReceiveConnectionsListener)}.
     */
    private HashMap<String, Model> mCachedModelInfo = new HashMap<>();

    public RestApi(Context context, String url, String apiKey, String guiUser, String guiPassword,
                   OnApiAvailableListener listener) {
        mContext = context;
        mUrl = url;
        mApiKey = apiKey;
        mGuiUser = guiUser;
        mGuiPassword = guiPassword;
        mHttpsCertPath = mContext.getFilesDir() + "/" + SyncthingService.HTTPS_CERT_FILE;
        mOnApiAvailableListener = listener;
    }

    /**
     * Number of previous calls to {@link #tryIsAvailable()}.
     */
    private AtomicInteger mAvailableCount = new AtomicInteger(0);

    /**
     * Number of asynchronous calls performed in {@link #onWebGuiAvailable()}.
     */
    private static final int TOTAL_STARTUP_CALLS = 3;

    public interface OnApiAvailableListener {
        public void onApiAvailable();
    }

    private final OnApiAvailableListener mOnApiAvailableListener;

    /**
     * Gets local device ID, syncthing version and config, then calls all OnApiAvailableListeners.
     */
    @Override
    public void onWebGuiAvailable() {
        mAvailableCount.set(0);
        new GetTask(mHttpsCertPath) {
            @Override
            protected void onPostExecute(String s) {
                try {
                    JSONObject json = new JSONObject(s);
                    mVersion = json.getString("version");
                    Log.i(TAG, "Syncthing version is " + mVersion);
                    tryIsAvailable();
                }
                catch (JSONException e) {
                    Log.w(TAG, "Failed to parse config", e);
                }
            }
        }.execute(mUrl, GetTask.URI_VERSION, mApiKey);
        new GetTask(mHttpsCertPath) {
            @Override
            protected void onPostExecute(String config) {
                try {
                    mConfig = new JSONObject(config);
                    tryIsAvailable();
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse config", e);
                }
            }
        }.execute(mUrl, GetTask.URI_CONFIG, mApiKey);
        getSystemInfo(new OnReceiveSystemInfoListener() {
            @Override
            public void onReceiveSystemInfo(SystemInfo info) {
                mLocalDeviceId = info.myID;
                tryIsAvailable();
            }
        });
    }

    /**
     * Increments mAvailableCount by one, and, if it reached TOTAL_STARTUP_CALLS,
     * calls {@link SyncthingService#onApiChange()}.
     */
    private void tryIsAvailable() {
        int value = mAvailableCount.incrementAndGet();
        if (BuildConfig.DEBUG && value > TOTAL_STARTUP_CALLS) {
            throw new AssertionError("Too many startup calls");
        }
        if (value == TOTAL_STARTUP_CALLS) {
            mOnApiAvailableListener.onApiAvailable();
        }
    }

    /**
     * Returns the version name, or a (text) error message on failure.
     */
    public String getVersion() {
        return mVersion;
    }

    /**
     * Stops syncthing and cancels notification. For use by {@link SyncthingService}.
     */
    public void shutdown() {
        // Happens in unit tests.
        if (mContext == null)
            return;

        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_RESTART);
        mRestartPostponed = false;
    }

    /**
     * Gets a value from config,
     *
     * Booleans are returned as {@link }Boolean#toString}, arrays as space seperated string.
     *
     * @param name {@link #TYPE_OPTIONS} or {@link #TYPE_GUI}
     * @param key  The key to read from.
     * @return The value as a String, or null on failure.
     */
    public String getValue(String name, String key) {
        // Happens if this functions is called before class is fully initialized.
        if (mConfig == null)
            return "";

        try {
            Object value = mConfig.getJSONObject(name).get(key);
            return (value instanceof JSONArray)
                    ? ((JSONArray) value).join(", ").replace("\"", "").replace("\\", "")
                    : value.toString();
        } catch (JSONException e) {
            Log.w(TAG, "Failed to get value for " + key, e);
            return "";
        }
    }

    /**
     * Sets a value to config and sends it via Rest API.
     * <p/>
     * Booleans must be passed as {@link Boolean}, arrays as space seperated string
     * with isArray true.
     *
     * @param name    {@link #TYPE_OPTIONS} or {@link #TYPE_GUI}
     * @param key     The key to write to.
     * @param value   The new value to set, either String, Boolean or Integer.
     * @param isArray True if value is a space seperated String that should be converted to array.
     */
    public <T> void setValue(String name, String key, T value, boolean isArray, Activity activity) {
        try {
            mConfig.getJSONObject(name).put(key, (isArray)
                    ? listToJson(((String) value).split(","))
                    : value);
            requireRestart(activity);
        } catch (JSONException e) {
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
            json.put(s.trim());
        }
        return json;
    }

    public void requireRestart(Activity activity) {
        requireRestart(activity, true);
    }

    /**
     * Sends the updated mConfig via Rest API to syncthing and displays a "restart"
     * dialog or notification.
     *
     * @param activity The calling activity.
     * @param updateConfig If true, {@link #mConfig} will be sent to `/rest/system/config`.
     */
    @TargetApi(11)
    public void requireRestart(Activity activity, boolean updateConfig) {
        if (updateConfig) {
            new PostTask(mHttpsCertPath)
                    .execute(mUrl, PostTask.URI_CONFIG, mApiKey, mConfig.toString());
        }
        // TODO Should wait for completion...

        if (mRestartPostponed)
            return;

        final Intent intent = new Intent(mContext, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESTART);

        AlertDialog.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                ? new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_LIGHT)
                : new AlertDialog.Builder(activity);
        builder.setMessage(R.string.restart_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mContext.startService(intent);
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
                .show();
    }

    /**
     * Reset Syncthing's indexes when confirmed by a dialog.
     */
    @TargetApi(11)
    public void resetSyncthing(final Activity activity) {
        final Intent intent = new Intent(mContext, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESET);

        AlertDialog.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                ? new AlertDialog.Builder(activity, AlertDialog.THEME_HOLO_LIGHT)
                : new AlertDialog.Builder(activity);
        builder.setTitle(R.string.streset_title);
        builder.setMessage(R.string.streset_question)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mContext.startService(intent);
                        Toast.makeText(activity, R.string.streset_done, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) { }
                })
                .show();
    }

    /**
     * Creates a notification prompting the user to restart the app.
     */
    private void createRestartNotification() {
        Intent intent = new Intent(mContext, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESTART);
        PendingIntent pi = PendingIntent.getService(mContext, 0, intent, 0);

        Notification n = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.restart_title))
                .setContentText(mContext.getString(R.string.restart_notification_text))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(pi)
                .build();
        n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_RESTART, n);
        mRestartPostponed = true;
    }

    /**
     * Returns a list of all existing devices.
     *
     * @param includeLocal True if the local device should be included in the result.
     */
    public List<Device> getDevices(boolean includeLocal) {
        if (mConfig == null)
            return new ArrayList<>();

        try {
            JSONArray devices = mConfig.getJSONArray("devices");
            List<Device> ret = new ArrayList<>(devices.length());
            for (int i = 0; i < devices.length(); i++) {
                JSONObject json = devices.getJSONObject(i);
                Device n = new Device();
                n.addresses = json.optJSONArray("addresses").join(" ").replace("\"", "");
                n.name = json.getString("name");
                n.deviceID = json.getString("deviceID");
                n.compression = json.getString("compression");
                n.introducer = json.getBoolean("introducer");
                if (includeLocal || !mLocalDeviceId.equals(n.deviceID)) {
                    ret.add(n);
                }
            }
            return ret;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to read devices", e);
            return new ArrayList<>();
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
        new GetTask(mHttpsCertPath) {
            @Override
            protected void onPostExecute(String s) {
                if (s == null)
                    return;

                try {
                    JSONObject system = new JSONObject(s);
                    SystemInfo si = new SystemInfo();
                    si.alloc = system.getLong("alloc");
                    si.cpuPercent = system.getDouble("cpuPercent");
                    if (system.has("extAnnounceOK")) {
                        JSONObject announce = system.getJSONObject("extAnnounceOK");
                        si.extAnnounceTotal = announce.length();
                        for (Iterator<String> it = announce.keys(); it.hasNext(); ) {
                            String key = it.next();
                            if (announce.getBoolean(key)) {
                                si.extAnnounceConnected++;
                            }
                        }
                    }
                    si.goroutines = system.getInt("goroutines");
                    si.myID = system.getString("myID");
                    si.sys = system.getLong("sys");
                    listener.onReceiveSystemInfo(si);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to read system info", e);
                }
            }
        }.execute(mUrl, GetTask.URI_SYSTEM, mApiKey);
    }

    /**
     * Returns a list of all existing folders.
     */
    public List<Folder> getFolders() {
        if (mConfig == null)
            return new ArrayList<>();

        List<Folder> ret;
        try {
            JSONArray folders = mConfig.getJSONArray("folders");
            ret = new ArrayList<>(folders.length());
            for (int i = 0; i < folders.length(); i++) {
                JSONObject json = folders.getJSONObject(i);
                Folder r = new Folder();
                r.path = json.getString("path");
                r.id = json.getString("id");
                if (json.has("invalid")) {
                    r.invalid = json.getString("invalid"); // TODO Upstream bug
                } else {
                    r.invalid = "";
                }
                r.deviceIds = new ArrayList<>();
                JSONArray devices = json.getJSONArray("devices");
                for (int j = 0; j < devices.length(); j++) {
                    JSONObject n = devices.getJSONObject(j);
                    r.deviceIds.add(n.getString("deviceID"));
                }

                r.readOnly = json.getBoolean("readOnly");
                r.rescanIntervalS = json.getInt("rescanIntervalS");
                JSONObject versioning = json.getJSONObject("versioning");
                if (versioning.getString("type").equals("simple")) {
                    SimpleVersioning sv = new SimpleVersioning();
                    JSONObject params = versioning.getJSONObject("params");
                    sv.setParams(params.getInt("keep"));
                    r.versioning = sv;
                } else {
                    r.versioning = new Versioning();
                }

                ret.add(r);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to read devices", e);
            return new ArrayList<>();
        }
        return ret;
    }

    /**
     * Converts a number of bytes to a human readable file size (eg 3.5 GiB).
     */
    public static String readableFileSize(Context context, long bytes) {
        final String[] units = context.getResources().getStringArray(R.array.file_size_units);
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Converts a number of bytes to a human readable transfer rate in bits (eg 100 Kb/s).
     */
    public static String readableTransferRate(Context context, long bits) {
        final String[] units = context.getResources().getStringArray(R.array.transfer_rate_units);
        if (bits <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bits) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bits / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Listener for {@link #getConnections}.
     */
    public interface OnReceiveConnectionsListener {

        /**
         * @param connections Map from Device id to {@link Connection}.
         *                    <p/>
         *                    NOTE: The parameter connections is cached internally. Do not modify it or
         *                    any of its contents.
         */
        public void onReceiveConnections(Map<String, Connection> connections);
    }

    /**
     * Returns connection info for the local device and all connected devices.
     * <p/>
     * Use the key {@link #TOTAL_STATS} to get connection info for the local device.
     */
    public void getConnections(final OnReceiveConnectionsListener listener) {
        new GetTask(mHttpsCertPath) {
            @Override
            protected void onPostExecute(String s) {
                if (s == null)
                    return;

                Long now = System.currentTimeMillis();
                Long timeElapsed = (now - mPreviousConnectionTime) / 1000;
                if (timeElapsed < 1) {
                    listener.onReceiveConnections(mPreviousConnections);
                    return;
                }

                try {
                    JSONObject json = new JSONObject(s);
                    Map<String, JSONObject> jsonConnections = new HashMap<>();
                    jsonConnections.put(TOTAL_STATS, json.getJSONObject(TOTAL_STATS));
                    JSONArray extConnections = json.getJSONObject("connections").names();
                    if (extConnections != null) {
                        for (int i = 0; i < extConnections.length(); i++) {
                            String deviceId = extConnections.get(i).toString();
                            jsonConnections.put(deviceId, json.getJSONObject("connections").getJSONObject(deviceId));
                        }
                    }
                    Map<String, Connection> connections = new HashMap<>();
                    for (String deviceId : jsonConnections.keySet()) {
                        Connection c = new Connection();
                        JSONObject conn = jsonConnections.get(deviceId);
                        c.address = deviceId;
                        c.at = conn.getString("at");
                        c.inBytesTotal = conn.getLong("inBytesTotal");
                        c.outBytesTotal = conn.getLong("outBytesTotal");
                        c.address = conn.getString("address");
                        c.clientVersion = conn.getString("clientVersion");
                        c.completion = getDeviceCompletion(deviceId);

                        Connection prev = (mPreviousConnections.containsKey(deviceId))
                                ? mPreviousConnections.get(deviceId)
                                : new Connection();
                        mPreviousConnectionTime = now;
                        c.inBits = Math.max(0, 8 *
                                (conn.getLong("inBytesTotal") - prev.inBytesTotal) / timeElapsed);
                        c.outBits = Math.max(0, 8 *
                                (conn.getLong("outBytesTotal") - prev.outBytesTotal) / timeElapsed);

                        connections.put(deviceId, c);

                    }
                    mPreviousConnections = connections;
                    listener.onReceiveConnections(mPreviousConnections);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse connections", e);
                }
            }
        }.execute(mUrl, GetTask.URI_CONNECTIONS, mApiKey);
    }

    /**
     * Calculates completion percentage for the given device using {@link #mCachedModelInfo}.
     */
    private int getDeviceCompletion(String deviceId) {
        int folderCount = 0;
        float percentageSum = 0;
        for (String id : mCachedModelInfo.keySet()) {
            boolean isShared = false;
            outerloop:
            for (Folder r : getFolders()) {
                for (String n : r.deviceIds) {
                    if (n.equals(deviceId)) {
                        isShared = true;
                        break outerloop;
                    }

                }
            }
            if (isShared) {
                long global = mCachedModelInfo.get(id).globalBytes;
                long local = mCachedModelInfo.get(id).inSyncBytes;
                percentageSum += (global != 0)
                        ? (local * 100f) / global
                        : 100f;
                folderCount++;
            }
        }
        return (folderCount != 0)
                ? (int) percentageSum / folderCount
                : 100;
    }


    /**
     * Listener for {@link #getModel}.
     */
    public interface OnReceiveModelListener {
        public void onReceiveModel(String folderId, Model model);
    }

    /**
     * Returns status information about the folder with the given id.
     */
    public void getModel(final String folderId, final OnReceiveModelListener listener) {
        new GetTask(mHttpsCertPath) {
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
                    mCachedModelInfo.put(folderId, m);
                    listener.onReceiveModel(folderId, m);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to read folder info", e);
                }
            }
        }.execute(mUrl, GetTask.URI_MODEL, mApiKey, "folder", folderId);
    }

    /**
     * Returns the folder's state as a localized string.
     *
     * @param state One of idle, scanning, cleaning or syncing.
     */
    public static String getLocalizedState(Context c, String state) {
        switch (state) {
            case "idle":     return c.getString(R.string.state_idle);
            case "scanning": return c.getString(R.string.state_scanning);
            case "cleaning": return c.getString(R.string.state_cleaning);
            case "syncing":  return c.getString(R.string.state_syncing);
            case "unknown":  // Fallthrough
            case "":         return c.getString(R.string.state_unknown);
        }
        if (BuildConfig.DEBUG) {
            throw new AssertionError("Unexpected folder state " + state);
        }
        return "";
    }

    /**
     * Updates or creates the given device, depending on whether it already exists.
     *
     * @param device Settings of the device to edit. To create a device, pass a non-existant device ID.
     * @param listener for the normalized device ID (may be null).
     */
    public void editDevice(final Device device, final Activity activity,
            final OnDeviceIdNormalizedListener listener) {
        normalizeDeviceId(device.deviceID,
                new RestApi.OnDeviceIdNormalizedListener() {
                    @Override
                    public void onDeviceIdNormalized(String normalizedId, String error) {
                        if (listener != null) listener.onDeviceIdNormalized(normalizedId, error);
                        if (normalizedId == null)
                            return;

                        device.deviceID = normalizedId;
                        // If the device already exists, just update it.
                        boolean create = true;
                        for (RestApi.Device n : getDevices(true)) {
                            if (n.deviceID.equals(device.deviceID)) {
                                create = false;
                            }
                        }

                        try {
                            JSONArray devices = mConfig.getJSONArray("devices");
                            JSONObject n = null;
                            if (create) {
                                n = new JSONObject();
                                devices.put(n);
                            } else {
                                for (int i = 0; i < devices.length(); i++) {
                                    JSONObject json = devices.getJSONObject(i);
                                    if (device.deviceID.equals(json.getString("deviceID"))) {
                                        n = devices.getJSONObject(i);
                                        break;
                                    }
                                }
                            }
                            n.put("deviceID", device.deviceID);
                            n.put("name", device.name);
                            n.put("addresses", listToJson(device.addresses.split(" ")));
                            n.put("compression", device.compression);
                            n.put("introducer", device.introducer);
                            requireRestart(activity);
                        } catch (JSONException e) {
                            Log.w(TAG, "Failed to read devices", e);
                        }
                    }
                }
        );
    }

    /**
     * Deletes the given device from syncthing.
     */
    public boolean deleteDevice(Device device, Activity activity) {
        try {
            JSONArray devices = mConfig.getJSONArray("devices");

            for (int i = 0; i < devices.length(); i++) {
                JSONObject json = devices.getJSONObject(i);
                if (device.deviceID.equals(json.getString("deviceID"))) {
                    mConfig.remove("devices");
                    mConfig.put("devices", delete(devices, devices.getJSONObject(i)));
                    break;
                }
            }
            requireRestart(activity);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to edit folder", e);
            return false;
        }
        return true;
    }

    /**
     * Updates or creates the given device.
     */
    public boolean editFolder(Folder folder, boolean create, Activity activity) {
        try {
            JSONArray folders = mConfig.getJSONArray("folders");
            JSONObject r = null;
            if (create) {
                r = new JSONObject();
                folders.put(r);
            } else {
                for (int i = 0; i < folders.length(); i++) {
                    JSONObject json = folders.getJSONObject(i);
                    if (folder.id.equals(json.getString("id"))) {
                        r = folders.getJSONObject(i);
                        break;
                    }
                }
            }
            r.put("path", folder.path);
            r.put("id", folder.id);
            r.put("ignorePerms", true);
            r.put("readOnly", folder.readOnly);
            JSONArray devices = new JSONArray();
            for (String n : folder.deviceIds) {
                JSONObject element = new JSONObject();
                element.put("deviceID", n);
                devices.put(element);
            }
            r.put("devices", devices);
            JSONObject versioning = new JSONObject();
            versioning.put("type", folder.versioning.getType());
            JSONObject params = new JSONObject();
            versioning.put("params", params);
            for (String key : folder.versioning.getParams().keySet()) {
                params.put(key, folder.versioning.getParams().get(key));
            }
            r.put("rescanIntervalS", folder.rescanIntervalS);
            r.put("versioning", versioning);
            requireRestart(activity);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to edit folder " + folder.id + " at " + folder.path, e);
            return false;
        }
        return true;
    }

    /**
     * Deletes the given folder from syncthing.
     */
    public boolean deleteFolder(Folder folder, Activity activity) {
        try {
            JSONArray folders = mConfig.getJSONArray("folders");

            for (int i = 0; i < folders.length(); i++) {
                JSONObject json = folders.getJSONObject(i);
                if (folder.id.equals(json.getString("id"))) {
                    mConfig.remove("folders");
                    mConfig.put("folders", delete(folders, folders.getJSONObject(i)));
                    break;
                }
            }
            requireRestart(activity);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to edit folder", e);
            return false;
        }
        return true;
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
     * Result listener for {@link #normalizeDeviceId(String, OnDeviceIdNormalizedListener)}.
     */
    public interface OnDeviceIdNormalizedListener {
        /**
         * On any call, exactly one parameter will be null.
         *
         * @param normalizedId The normalized device ID, or null on error.
         * @param error        An error message, or null on success.
         */
        public void onDeviceIdNormalized(String normalizedId, String error);
    }

    /**
     * Normalizes a given device ID.
     */
    public void normalizeDeviceId(String id, final OnDeviceIdNormalizedListener listener) {
        new GetTask(mHttpsCertPath) {
            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                String normalized = null;
                String error = null;
                try {
                    JSONObject json = new JSONObject(s);
                    normalized = json.optString("id", null);
                    error = json.optString("error", null);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to parse normalized device ID JSON", e);
                }
                listener.onDeviceIdNormalized(normalized, error);
            }
        }.execute(mUrl, GetTask.URI_DEVICEID, mApiKey, "id", id);
    }

    /**
     * Shares the given device ID via Intent. Must be called from an Activity.
     */
    public static void shareDeviceId(Context context, String id) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, id);
        context.startActivity(Intent.createChooser(
                shareIntent, context.getString(R.string.send_device_id_to)));
    }

    /**
     * Copies the given device ID to the clipboard (and shows a Toast telling about it).
     *
     * @param id The device ID to copy.
     */
    @TargetApi(11)
    public void copyDeviceId(String id) {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk < android.os.Build.VERSION_CODES.HONEYCOMB) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager)
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(id);
        } else {
            ClipboardManager clipboard = (ClipboardManager)
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(mContext.getString(R.string.device_id), id);
            clipboard.setPrimaryClip(clip);
        }
        Toast.makeText(mContext, R.string.device_id_copied_to_clipboard, Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * Force a rescan of the given subdirectory in folder.
     */
    @Override
    public void onFolderFileChange(String folderId, String relativePath) {
        new PostTask(mHttpsCertPath).execute(mUrl, PostTask.URI_SCAN, mApiKey, "folder", folderId, "sub",
                relativePath);
    }

    /**
     * Returns the object representing the local device.
     */
    public Device getLocalDevice() {
        for (Device d : getDevices(true)) {
            if (d.deviceID.equals(mLocalDeviceId)) {
                return d;
            }
        }
        return new Device();
    }

    public String getApiKey() {
        return mApiKey;
    }

    public String getGuiUser() {
        return mGuiUser;
    }

    public String getGuiPassword() {
        return mGuiPassword;
    }

}
