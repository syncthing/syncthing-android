package com.nutomic.syncthingandroid.syncthing;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.activities.RestartActivity;
import com.nutomic.syncthingandroid.http.GetTask;
import com.nutomic.syncthingandroid.http.PostTask;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.Model;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.util.FolderObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.net.URL;
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
     * Key of the map element containing connection info for the local device, in the return
     * value of {@link #getConnections}
     */
    public static final String TOTAL_STATS = "total";

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }

    private final Context mContext;
    private final URL mUrl;
    private final String mApiKey;
    private final String mHttpsCertPath;

    private String mVersion;
    private Config mConfig;
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
            mConfig = new Gson().fromJson(result, Config.class);
            tryIsAvailable();
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
        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(RestartActivity.NOTIFICATION_RESTART);
        mRestartPostponed = false;
    }

    /**
     * Either shows a restart dialog, or only updates the config, depending on
     * {@link #mRestartPostponed}.
     */
    public void requireRestart(Activity activity) {
        if (mRestartPostponed) {
            sendConfig();
        } else {
            activity.startActivity(new Intent(mContext, RestartActivity.class));
        }
        mOnConfigChangedListener.onConfigChanged();
    }

    private void sendConfig() {
        new PostTask(mUrl, PostTask.URI_CONFIG, mHttpsCertPath, mApiKey, null)
                .execute(new Gson().toJson(mConfig));
    }

    /**
     * Immediately restarts Syncthing, without confirmation.
     */
    public void restart() {
        new PostTask(mUrl, PostTask.URI_CONFIG, mHttpsCertPath, mApiKey, result -> {
            Intent intent = new Intent(mContext, SyncthingService.class)
                    .setAction(SyncthingService.ACTION_RESTART);
            mContext.startService(intent);
        }).execute(new Gson().toJson(mConfig));
    }

    /**
     * Returns a list of all existing devices.
     *
     * @param includeLocal True if the local device should be included in the result.
     */
    public List<Device> getDevices(boolean includeLocal) {
        List<Device> devices = deepCopy(mConfig.devices, new TypeToken<List<Device>>(){}.getType());

        Iterator<Device> it = devices.iterator();
        while (it.hasNext()) {
            Device device = it.next();
            boolean isLocalDevice = Objects.equal(mLocalDeviceId, device.deviceID);
            if (!includeLocal && isLocalDevice)
                it.remove();
        }
        return devices;
    }

    public Options getOptions() {
        return deepCopy(mConfig.options, Options.class);
    }

    public Config.Gui getGui() {
        return deepCopy(mConfig.gui, Config.Gui.class);
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    public <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    public void removeDevice(String deviceId) {
        removeDeviceInternal(deviceId);
        sendConfig();
    }

    private void removeDeviceInternal(String deviceId) {
        Iterator<Device> it = mConfig.devices.iterator();
        while (it.hasNext()) {
            Device d = it.next();
            if (d.deviceID.equals(deviceId)) {
                it.remove();
            }
        }
    }

    public void removeFolder(String id) {
        removeFolderInternal(id);
        sendConfig();
    }

    private void removeFolderInternal(String id) {
        Iterator<Folder> it = mConfig.folders.iterator();
        while (it.hasNext()) {
            Folder f = it.next();
            if (f.id.equals(id)) {
                it.remove();
            }
        }
    }

    public void addDevice(Device device, OnDeviceIdNormalizedListener listener) {
        normalizeDeviceId(device.deviceID, ((normalizedId, error) -> {
            if (error == null) {
                mConfig.devices.add(device);
                sendConfig();
            }
            else {
                listener.onDeviceIdNormalized(normalizedId, error);
            }
        }));
    }

    public void addFolder(Folder folder) {
        mConfig.folders.add(folder);
        sendConfig();
    }

    public void editDevice(Device newDevice) {
        removeDeviceInternal(newDevice.deviceID);
        addDevice(newDevice, null);
    }

    public void editFolder(Folder newFolder) {
        removeFolderInternal(newFolder.id);
        addFolder(newFolder);
    }

    public void editSettings(Config.Gui newGui, Options newOptions, Activity activity) {
        mConfig.gui = newGui;
        mConfig.options = newOptions;
        requireRestart(activity);
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
            SystemVersion systemVersion = new Gson().fromJson(result, SystemVersion.class);
            listener.onReceiveSystemVersion(systemVersion);
        }).execute();
    }

    /**
     * Returns a list of all existing folders.
     */
    public List<Folder> getFolders() {
        return deepCopy(mConfig.folders, new TypeToken<List<Folder>>(){}.getType());
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
         */
        void onEvent(Event event);

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
     * Retrieves the events that have accumulated since the given event id.
     *
     * The OnReceiveEventListeners onEvent method is called for each event.
     */
    public final void getEvents(final long sinceId, final long limit, final OnReceiveEventListener listener) {
        new GetTask(mUrl, GetTask.URI_EVENTS, mHttpsCertPath, mApiKey, result -> {
            JsonArray jsonEvents = new JsonParser().parse(result).getAsJsonArray();
            long lastId = 0;

            for (int i = 0; i < jsonEvents.size(); i++) {
                JsonElement json = jsonEvents.get(i);
                Event event = new Gson().fromJson(json, Event.class);

                if (lastId < event.id)
                    lastId = event.id;

                listener.onEvent(event);
            }

            listener.onDone(lastId);
        }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                "since", String.valueOf(sinceId), "limit", String.valueOf(limit));
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
                return deepCopy(d, Device.class);
            }
        }
        throw new RuntimeException();
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
            JsonElement json = new JsonParser().parse(result);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            listener.onReceiveUsageReport(gson.toJson(json));
        }).execute();
    }

    /**
     * Sets {@link #mRestartPostponed} to true.
     */
    public void setRestartPostponed() {
        mRestartPostponed = true;
    }
}
