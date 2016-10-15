package com.nutomic.syncthingandroid.syncthing;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.activities.RestartActivity;
import com.nutomic.syncthingandroid.http.GetTask;
import com.nutomic.syncthingandroid.http.PostTask;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.Model;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.util.FolderObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public static final int USAGE_REPORTING_UNDECIDED = 0;
    public static final int USAGE_REPORTING_ACCEPTED  = 2;
    public static final int USAGE_REPORTING_DENIED    = -1;
    private static final List<Integer> USAGE_REPORTING_DECIDED =
            Arrays.asList(USAGE_REPORTING_ACCEPTED, USAGE_REPORTING_DENIED);

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }

    private final Context mContext;

    private String mVersion;

    private final URL mUrl;

    private final String mApiKey;

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
    private final HashMap<String, Model> mCachedModelInfo = new HashMap<>();

    /**
     * Stores a hash map to resolve folders to paths for events.
     */
    private final Map<String, String> mCacheFolderPathLookup = new HashMap<>();

    public RestApi(Context context, URL url, String apiKey, OnApiAvailableListener apiListener,
                   OnConfigChangedListener configListener) {
        mContext = context;
        mUrl = url;
        mApiKey = apiKey;
        mHttpsCertPath = mContext.getFilesDir() + "/" + SyncthingService.HTTPS_CERT_FILE;
        mOnApiAvailableListener = apiListener;
        mOnConfigChangedListener = configListener;
    }

    /**
     * Number of previous calls to {@link #tryIsAvailable()}.
     */
    private final AtomicInteger mAvailableCount = new AtomicInteger(0);

    /**
     * Number of asynchronous calls performed in {@link #onWebGuiAvailable()}.
     */
    private static final int TOTAL_STARTUP_CALLS = 3;

    public interface OnApiAvailableListener {
        public void onApiAvailable();
    }

    private final OnApiAvailableListener mOnApiAvailableListener;

    private final OnConfigChangedListener mOnConfigChangedListener;

    /**
     * Gets local device ID, syncthing version and config, then calls all OnApiAvailableListeners.
     */
    @Override
    public void onWebGuiAvailable() {
        mAvailableCount.set(0);
        new GetTask(mUrl, GetTask.URI_VERSION, mHttpsCertPath, mApiKey, result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            mVersion = json.get("version").getAsString();
            Log.i(TAG, "Syncthing version is " + mVersion);
            tryIsAvailable();
        }).execute();
        new GetTask(mUrl, GetTask.URI_CONFIG, mHttpsCertPath, mApiKey, result -> {
            try {
                mConfig = new JSONObject(result);
                tryIsAvailable();
            } catch (JSONException e) {
                Log.w(TAG, "Failed to parse config", e);
            }
        }).execute();
        getSystemInfo(info -> {
            mLocalDeviceId = info.myID;
            tryIsAvailable();
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
        nm.cancel(RestartActivity.NOTIFICATION_RESTART);
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
                    ? new JSONArray(Arrays.asList(((String) value).split(",")))
                    : value);
            requireRestart(activity);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to set value for " + key, e);
        }
    }

    private List<String> jsonToList(JSONArray array) throws JSONException {
        ArrayList<String> list = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }

    /**
     * Either shows a restart dialog, or only updates the config, depending on
     * {@link #mRestartPostponed}.
     */
    public void requireRestart(Activity activity) {
        if (mRestartPostponed) {
            new PostTask(mUrl, PostTask.URI_CONFIG, mHttpsCertPath, mApiKey, null)
                    .execute(mConfig.toString());
        } else {
            activity.startActivity(new Intent(mContext, RestartActivity.class));
        }
        mOnConfigChangedListener.onConfigChanged();
    }

    /**
     * Sends the current config to Syncthing and restarts it.
     *
     * This executes a restart immediately, and does not show a dialog.
     */
    public void updateConfig() {
        new PostTask(mUrl, PostTask.URI_CONFIG, mHttpsCertPath, mApiKey, result -> {
            mContext.startService(new Intent(mContext, SyncthingService.class)
                    .setAction(SyncthingService.ACTION_RESTART));
        }).execute(mConfig.toString());

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
            String json = mConfig.getJSONArray("devices").toString();
            List<Device> devices = new ArrayList<>();
            Collections.addAll(devices, new Gson().fromJson(json, Device[].class));

            Iterator<Device> it = devices.iterator();
            while (it.hasNext()) {
                Device device = it.next();
                boolean isLocalDevice = Objects.equal(mLocalDeviceId, device.deviceID);
                if (!includeLocal && isLocalDevice)
                    it.remove();
            }
            return devices;
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
     * Result listener for {@link #getSystemVersion(OnReceiveSystemVersionListener)}.
     */
    public interface OnReceiveSystemVersionListener {
        void onReceiveSystemVersion(SystemVersion version);
    }

    /**
     * Requests and parses information about current system status and resource usage.
     *
     * @param listener Callback invoked when the result is received.
     */
    public void getSystemInfo(final OnReceiveSystemInfoListener listener) {
        new GetTask(mUrl, GetTask.URI_SYSTEM, mHttpsCertPath, mApiKey, result -> {
                listener.onReceiveSystemInfo(new Gson().fromJson(result, SystemInfo.class));
        }).execute();
    }

    /**
     * Requests and parses system version information.
     *
     * @param listener Callback invoked when the result is received.
     */
    public void getSystemVersion(final OnReceiveSystemVersionListener listener) {
        new GetTask(mUrl, GetTask.URI_VERSION, mHttpsCertPath, mApiKey, result -> {
            try {
                SystemVersion systemVersion = new Gson().fromJson(result, SystemVersion.class);
                listener.onReceiveSystemVersion(systemVersion);
            } catch (JsonSyntaxException e) {
                Log.w(TAG, "Failed to read system info", e);
            }
        }).execute();
    }

    /**
     * Returns a list of all existing folders.
     */
    public List<Folder> getFolders() {
        if (mConfig == null)
            return new ArrayList<>();

        try {
            String foldersJson = mConfig.getJSONArray("folders").toString();
            return Arrays.asList(new Gson().fromJson(foldersJson, Folder[].class));
        } catch (JSONException e) {
            Log.w(TAG, "Failed to read devices", e);
            return new ArrayList<>();
        }
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
        new GetTask(mUrl, GetTask.URI_CONNECTIONS, mHttpsCertPath, mApiKey, result -> {
            Long now = System.currentTimeMillis();
            Long timeElapsed = (now - mPreviousConnectionTime) / 1000;
            if (timeElapsed < 1) {
                listener.onReceiveConnections(mPreviousConnections);
                return;
            }

            try {
                JSONObject json = new JSONObject(result);
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
                for (Map.Entry<String, JSONObject> jsonConnection : jsonConnections.entrySet()) {
                    String deviceId = jsonConnection.getKey();
                    Connection c = new Connection();
                    JSONObject conn = jsonConnection.getValue();
                    c.address = deviceId;
                    c.at = conn.getString("at");
                    c.inBytesTotal = conn.getLong("inBytesTotal");
                    c.outBytesTotal = conn.getLong("outBytesTotal");
                    c.address = conn.getString("address");
                    c.clientVersion = conn.getString("clientVersion");
                    c.completion = getDeviceCompletion(deviceId);
                    c.connected = conn.getBoolean("connected");

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
        }).execute();
    }

    /**
     * Calculates completion percentage for the given device using {@link #mCachedModelInfo}.
     */
    private int getDeviceCompletion(String deviceId) {
        int folderCount = 0;
        float percentageSum = 0;
        // Syncthing UI limits pending deletes to 95% completion of a device
        int maxPercentage = 100;
        for (Map.Entry<String, Model> modelInfo : mCachedModelInfo.entrySet()) {
            boolean isShared = false;
            outerloop:
            for (Folder r : getFolders()) {
                for (String n : r.getDevices()) {
                    if (n.equals(deviceId)) {
                        isShared = true;
                        break outerloop;
                    }
                }
            }
            if (isShared) {
                long global = modelInfo.getValue().globalBytes;
                long local = modelInfo.getValue().inSyncBytes;
                if (modelInfo.getValue().needFiles == 0 && modelInfo.getValue().needDeletes > 0)
                    maxPercentage = 95;
                percentageSum += (global != 0)
                        ? (local * 100f) / global
                        : 100f;
                folderCount++;
            }
        }
        return (folderCount != 0)
                ? Math.min(Math.round(percentageSum / folderCount), maxPercentage)
                : 100;
    }

    /**
     * Listener for {@link #getModel}.
     */
    public interface OnReceiveModelListener {
        public void onReceiveModel(String folderId, Model model);
    }

    /**
     * Listener for {@link #getEvents}.
     */
    public interface OnReceiveEventListener {
        /**
         * Called for each event.
         *
         * Events with a "folder" field in the data have an extra "folderpath" element added.
         *  @param eventType Name of the event. (See Syncthing documentation)
         * @param data Contains the data fields of the event.
         */
        void onEvent(String eventType, JsonObject data);

        /**
         * Called after all available events have been processed.
         * @param lastId The id of the last event processed. Should be used as a starting point for
         *               the next round of event processing.
         */
        void onDone(long lastId);
    }
    /**
     * Returns status information about the folder with the given id.
     */
    public void getModel(final String folderId, final OnReceiveModelListener listener) {
        new GetTask(mUrl, GetTask.URI_MODEL, mHttpsCertPath, mApiKey, result -> {
            Model m = new Gson().fromJson(result, Model.class);
            mCachedModelInfo.put(folderId, m);
            listener.onReceiveModel(folderId, m);
        }).execute("folder", folderId);
    }

    /**
     * Refreshes the lookup table to convert folder names to paths for events.
     */
    private String getPathForFolder(String folderName) {
        synchronized(mCacheFolderPathLookup) {
            if (!mCacheFolderPathLookup.containsKey(folderName)) {
                mCacheFolderPathLookup.clear();
                for (Folder folder : getFolders()) {
                    mCacheFolderPathLookup.put(folder.id, folder.path);
                }
            }

            return mCacheFolderPathLookup.get(folderName);
        }
    }

    private void clearFolderCache() {
        synchronized(mCacheFolderPathLookup) {
            mCacheFolderPathLookup.clear();
        }
    }

    /**
     * Retrieves the events that have accumulated since the given event id.
     *
     * The OnReceiveEventListeners onEvent method is called for each event.
     */
    public final void getEvents(final long sinceId, final long limit, final OnReceiveEventListener listener) {
        new GetTask(mUrl, GetTask.URI_EVENTS, mHttpsCertPath, mApiKey, result -> {
            JsonArray jsonEvents = new JsonParser().parse(result).getAsJsonArray();
            long lastId = 0;

            for (int i = 0; i < jsonEvents.size(); i++) {
                JsonObject json = jsonEvents.get(i).getAsJsonObject();
                String type     = json.get("type").getAsString();
                long id         = json.get("id").getAsLong();

                if (lastId < id)
                    lastId = id;

                JsonObject data = null;
                if (json.has("data"))
                    data = json.get("data").getAsJsonObject();

                // Add folder path to data.
                if (data != null && data.has("folder")) {
                    String folder = data.get("folder").getAsString();
                    String folderPath = getPathForFolder(folder);
                    data.addProperty("folderpath", folderPath);
                }

                listener.onEvent(type, data);
            }

            listener.onDone(lastId);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                "since", String.valueOf(sinceId), "limit", String.valueOf(limit));
    }

    /**
     * Updates or creates the given device, depending on whether it already exists.
     *
     * @param device Settings of the device to edit. To create a device, pass a non-existant device ID.
     * @param listener for the normalized device ID (may be null).
     */
    public void editDevice(@NonNull final Device device, final Activity activity,
                           final OnDeviceIdNormalizedListener listener) {
        normalizeDeviceId(device.deviceID,
                (normalizedId, error) -> {
                    if (listener != null) listener.onDeviceIdNormalized(normalizedId, error);
                    if (normalizedId == null)
                        return;

                    device.deviceID = normalizedId;
                    // If the device already exists, just update it.
                    boolean create = true;
                    for (Device n : getDevices(true)) {
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
                        n.put("addresses", new JSONArray(device.addresses));
                        n.put("compression", device.compression);
                        n.put("introducer", device.introducer);
                        requireRestart(activity);
                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to read devices", e);
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
    public void editFolder(Folder folder, boolean create, Activity activity) {
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
            r.put("label", folder.label);
            r.put("id", folder.id);
            r.put("ignorePerms", true);
            r.put("type", folder.type);

            JSONArray devices = new JSONArray();
            for (String n : folder.getDevices()) {
                JSONObject element = new JSONObject();
                element.put("deviceID", n);
                devices.put(element);
            }
            r.put("devices", devices);
            JSONObject versioning = new JSONObject();
            versioning.put("type", folder.versioning.type);
            JSONObject params = new JSONObject();
            versioning.put("params", params);
            for (String key : folder.versioning.params.keySet()) {
                params.put(key, folder.versioning.params.get(key));
            }
            r.put("rescanIntervalS", folder.rescanIntervalS);
            r.put("versioning", versioning);
            requireRestart(activity);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to edit folder " + folder.id + " at " + folder.path, e);
            return;
        }

        clearFolderCache();
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

        clearFolderCache();
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
    public void normalizeDeviceId(final String id, final OnDeviceIdNormalizedListener listener) {
        new GetTask(mUrl, GetTask.URI_DEVICEID, mHttpsCertPath, mApiKey, result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            JsonElement normalizedId = json.get("id");
            JsonElement error = json.get("error");
            if (normalizedId != null)
                listener.onDeviceIdNormalized(normalizedId.getAsString(), null);
            if (error != null)
                listener.onDeviceIdNormalized(null, error.getAsString());
        }).execute("id", id);
    }

    /**
     * Force a rescan of the given subdirectory in folder.
     */
    @Override
    public void onFolderFileChange(String folderId, String relativePath) {
        new PostTask(mUrl, PostTask.URI_SCAN, mHttpsCertPath, mApiKey, null)
                .execute(folderId, relativePath);
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

    /**
     * Returns value of usage reporting preference.
     */
    public int getUsageReportAccepted() {
        try {
            int value = mConfig.getJSONObject(TYPE_OPTIONS).getInt("urAccepted");
            if (value > USAGE_REPORTING_ACCEPTED)
                throw new RuntimeException("Inalid usage reporting value");
            if (!USAGE_REPORTING_DECIDED.contains(value))
                value = USAGE_REPORTING_UNDECIDED;

            return value;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to read usage report value", e);
            return USAGE_REPORTING_DENIED;
        }
    }

    /**
     * Sets new value for usage reporting preference.
     */
    public void setUsageReportAccepted(int value, Activity activity) {
        if (BuildConfig.DEBUG && !USAGE_REPORTING_DECIDED.contains(value))
            throw new IllegalArgumentException("Invalid value for usage report");

        try {
            mConfig.getJSONObject(TYPE_OPTIONS).put("urAccepted", value);
        } catch (JSONException e) {
            Log.w(TAG, "Failed to set usage report value", e);
        }
        requireRestart(activity);
    }

    /**
     * Callback for {@link #getUsageReport}.
     */
    public interface OnReceiveUsageReportListener {
        public void onReceiveUsageReport(String report);
    }

    /**
     * Returns prettyfied usage report.
     */
    public void getUsageReport(final OnReceiveUsageReportListener listener) {
        new GetTask(mUrl, GetTask.URI_REPORT, mHttpsCertPath, mApiKey, result -> {
            try {
                listener.onReceiveUsageReport(new JSONObject(result).toString(4));
            } catch (JSONException e) {
                throw new RuntimeException("Failed to prettify usage report", e);
            }
        }).execute();
    }

    /**
     * Sets {@link #mRestartPostponed} to true.
     */
    public void setRestartPostponed() {
        mRestartPostponed = true;
    }
}
