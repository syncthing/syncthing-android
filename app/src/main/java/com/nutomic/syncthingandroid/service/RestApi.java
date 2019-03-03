package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.ShareActivity;
import com.nutomic.syncthingandroid.http.GetRequest;
import com.nutomic.syncthingandroid.http.PostRequest;
import com.nutomic.syncthingandroid.model.Config;
import com.nutomic.syncthingandroid.model.Completion;
import com.nutomic.syncthingandroid.model.CompletionInfo;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.DiscoveredDevice;
import com.nutomic.syncthingandroid.model.DiskEvent;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.FolderIgnoreList;
import com.nutomic.syncthingandroid.model.FolderStatus;
import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.model.IgnoredFolder;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.model.PendingDevice;
import com.nutomic.syncthingandroid.model.PendingFolder;
import com.nutomic.syncthingandroid.model.RemoteIgnoredDevice;
import com.nutomic.syncthingandroid.model.SystemStatus;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.service.Constants.ENABLE_TEST_DATA;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi {

    private static final String TAG = "RestApi";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Compares folders by labels, uses the folder ID as fallback if the label is empty
     */
    private final static Comparator<Folder> FOLDERS_COMPARATOR = (lhs, rhs) -> {
        String lhsLabel = lhs.label != null && !lhs.label.isEmpty() ? lhs.label : lhs.id;
        String rhsLabel = rhs.label != null && !rhs.label.isEmpty() ? rhs.label : rhs.id;
        return lhsLabel.compareTo(rhsLabel);
    };

    public interface OnConfigChangedListener {
        void onConfigChanged();
    }

    public interface OnResultListener1<T> {
        void onResult(T t);
    }

    public interface OnResultListener2<T, R> {
        void onResult(T t, R r);
    }

    private final Context mContext;
    private final URL mUrl;
    private final String mApiKey;

    private String mVersion;
    private Config mConfig;

    /**
     * Results cached from systemInfo
     */
    private String mLocalDeviceId;
    private Integer mUrVersionMax;

    /**
     * Stores the result of the last successful request to {@link GetRequest#URI_CONNECTIONS},
     * or an empty Map.
     */
    private Optional<Connections> mPreviousConnections = Optional.absent();

    /**
     * Stores the timestamp of the last successful request to {@link GetRequest#URI_CONNECTIONS}.
     */
    private long mPreviousConnectionTime = 0;

    /**
     * In the last-finishing {@link #readConfigFromRestApi} callback, we have to call
     * {@link SyncthingService#onApiAvailable} to indicate that the RestApi class is fully initialized.
     * We do this to avoid getting stuck with our main thread due to synchronous REST queries.
     * The correct indication of full initialisation is crucial to stability as other listeners of
     * {@link ../activities/SettingsActivity#SettingsFragment#onServiceStateChange} needs cached config and system information available.
     * e.g. SettingsFragment need "mLocalDeviceId"
     */
    private Boolean asyncQueryConfigComplete = false;
    private Boolean asyncQueryVersionComplete = false;
    private Boolean asyncQuerySystemStatusComplete = false;

    /**
     * Object that must be locked upon accessing the following variables:
     * asyncQueryConfigComplete, asyncQueryVersionComplete, asyncQuerySystemStatusComplete
     */
    private final Object mAsyncQueryCompleteLock = new Object();

    /**
     * Object that must be locked upon accessing mConfig
     */
    private final Object mConfigLock = new Object();

    /**
     * Stores the latest result of {@link #getFolderStatus} for each folder
     */
    private HashMap<String, FolderStatus> mCachedFolderStatuses = new HashMap<>();

    /**
     * Stores the latest result of device and folder completion events.
     */
    private Completion mCompletion;

    private Gson mGson;

    public RestApi(Context context, URL url, String apiKey, OnApiAvailableListener apiListener,
                   OnConfigChangedListener configListener) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(context);
        mContext = context;
        mUrl = url;
        mApiKey = apiKey;
        mOnApiAvailableListener = apiListener;
        mOnConfigChangedListener = configListener;
        mCompletion = new Completion(ENABLE_VERBOSE_LOG);
        mGson = getGson();
    }

    public interface OnApiAvailableListener {
        void onApiAvailable();
    }

    private final OnApiAvailableListener mOnApiAvailableListener;

    private final OnConfigChangedListener mOnConfigChangedListener;

    /**
     * Gets local device ID, syncthing version and config, then calls all OnApiAvailableListeners.
     */
    public void readConfigFromRestApi() {
        LogV("Querying config from REST ...");
        synchronized (mAsyncQueryCompleteLock) {
            asyncQueryVersionComplete = false;
            asyncQueryConfigComplete = false;
            asyncQuerySystemStatusComplete = false;
        }
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            JsonObject json = new JsonParser().parse(result).getAsJsonObject();
            mVersion = json.get("version").getAsString();
            updateDebugFacilitiesCache();
            synchronized (mAsyncQueryCompleteLock) {
                asyncQueryVersionComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, result -> {
            onReloadConfigComplete(result);
            synchronized (mAsyncQueryCompleteLock) {
                asyncQueryConfigComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
        getSystemStatus(info -> {
            mLocalDeviceId = info.myID;
            mUrVersionMax = info.urVersionMax;
            synchronized (mAsyncQueryCompleteLock) {
                asyncQuerySystemStatusComplete = true;
                checkReadConfigFromRestApiCompleted();
            }
        });
    }

    private void checkReadConfigFromRestApiCompleted() {
        if (asyncQueryVersionComplete && asyncQueryConfigComplete && asyncQuerySystemStatusComplete) {
            LogV("Reading config from REST completed. Syncthing version is " + mVersion);
            // Tell SyncthingService it can transition to State.ACTIVE.
            mOnApiAvailableListener.onApiAvailable();
        }
    }

    public void reloadConfig() {
        new GetRequest(mContext, mUrl, GetRequest.URI_CONFIG, mApiKey, null, this::onReloadConfigComplete);
    }

    private void onReloadConfigComplete(String result) {
        Boolean configParseSuccess;
        synchronized(mConfigLock) {
            mConfig = mGson.fromJson(result, Config.class);
            configParseSuccess = mConfig != null;
        }
        if (!configParseSuccess) {
            throw new RuntimeException("config is null: " + result);
        }
        Log.d(TAG, "onReloadConfigComplete: Successfully parsed configuration.");
        LogV("mConfig.pendingDevices = " + mGson.toJson(mConfig.pendingDevices));
        LogV("mConfig.remoteIgnoredDevices = " + mGson.toJson(mConfig.remoteIgnoredDevices));

        // Update cached device and folder information stored in the mCompletion model.
        mCompletion.updateFromConfig(getDevices(true), getFolders());
    }

    /**
     * Queries debug facilities available from the currently running syncthing binary
     * if the syncthing binary version changed. First launch of the binary is also
     * considered as a version change.
     * Precondition: {@link #mVersion} read from REST
     */
    private void updateDebugFacilitiesCache() {
        if (!mVersion.equals(PreferenceManager.getDefaultSharedPreferences(mContext).getString(Constants.PREF_LAST_BINARY_VERSION, ""))) {
            // First binary launch or binary upgraded case.
            new GetRequest(mContext, mUrl, GetRequest.URI_DEBUG, mApiKey, null, result -> {
                try {
                    Set<String> facilitiesToStore = new HashSet<String>();
                    JsonObject json = new JsonParser().parse(result).getAsJsonObject();
                    JsonObject jsonFacilities = json.getAsJsonObject("facilities");
                    for (String facilityName : jsonFacilities.keySet()) {
                        facilitiesToStore.add(facilityName);
                    }
                    PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putStringSet(Constants.PREF_DEBUG_FACILITIES_AVAILABLE, facilitiesToStore)
                        .apply();

                    // Store current binary version so we will only store this information again
                    // after a binary update.
                    PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                        .putString(Constants.PREF_LAST_BINARY_VERSION, mVersion)
                        .apply();
                } catch (Exception e) {
                    Log.w(TAG, "updateDebugFacilitiesCache: Failed to get debug facilities. result=" + result);
                }
            });
        }
    }

    /**
     * Permanently ignore a device when it tries to connect.
     * Ignored devices will not trigger the "DeviceRejected" event
     * in {@link EventProcessor#onEvent}.
     */
    public void ignoreDevice(String deviceId) {
        synchronized (mConfigLock) {
            // Check if the device has already been ignored.
            for (RemoteIgnoredDevice remoteIgnoredDevice : mConfig.remoteIgnoredDevices) {
                if (deviceId.equals(remoteIgnoredDevice.deviceID)) {
                    // Device already ignored.
                    Log.d(TAG, "Device already ignored [" + deviceId + "]");
                    return;
                }
            }

            /**
             * Ignore device by moving its corresponding "pendingDevice" entry to
             * a newly created "remotePendingDevice" entry.
             */
            RemoteIgnoredDevice remoteIgnoredDevice = new RemoteIgnoredDevice();
            remoteIgnoredDevice.deviceID = deviceId;
            Iterator<PendingDevice> it = mConfig.pendingDevices.iterator();
            while (it.hasNext()) {
                PendingDevice pendingDevice = it.next();
                if (deviceId.equals(pendingDevice.deviceID)) {
                    // Move over information stored in the "pendingDevice" entry.
                    remoteIgnoredDevice.address = pendingDevice.address;
                    remoteIgnoredDevice.name = pendingDevice.name;
                    remoteIgnoredDevice.time = pendingDevice.time;
                    it.remove();
                    break;
                }
            }
            mConfig.remoteIgnoredDevices.add(remoteIgnoredDevice);
            sendConfig();
            Log.d(TAG, "Ignored device [" + deviceId + "]");
        }
    }

    /**
     * Permanently ignore a folder share request.
     * Ignored folders will not trigger the "FolderRejected" event
     * in {@link EventProcessor#onEvent}.
     */
    public void ignoreFolder(String deviceId, String folderId) {
        synchronized (mConfigLock) {
            for (Device device : mConfig.devices) {
                if (deviceId.equals(device.deviceID)) {
                    /**
                     * Check if the folder has already been ignored.
                     */
                    for (IgnoredFolder ignoredFolder : device.ignoredFolders) {
                        if (folderId.equals(ignoredFolder.id)) {
                            // Folder already ignored.
                            Log.d(TAG, "Folder [" + folderId + "] already ignored on device [" + deviceId + "]");
                            return;
                        }
                    }

                    /**
                     * Ignore folder by moving its corresponding "pendingFolder" entry to
                     * a newly created "ignoredFolder" entry.
                     */
                    IgnoredFolder ignoredFolder = new IgnoredFolder();
                    ignoredFolder.id = folderId;
                    Iterator<PendingFolder> it = device.pendingFolders.iterator();
                    while (it.hasNext()) {
                        PendingFolder pendingFolder = it.next();
                        if (folderId.equals(pendingFolder.id)) {
                            // Move over information stored in the "pendingFolder" entry.
                            ignoredFolder.label = pendingFolder.label;
                            ignoredFolder.time = pendingFolder.time;
                            it.remove();
                            break;
                        }
                    }
                    device.ignoredFolders.add(ignoredFolder);
                    LogV("device.pendingFolders = " + mGson.toJson(device.pendingFolders));
                    LogV("device.ignoredFolders = " + mGson.toJson(device.ignoredFolders));
                    sendConfig();
                    Log.d(TAG, "Ignored folder [" + folderId + "] announced by device [" + deviceId + "]");

                    // Given deviceId handled.
                    break;
                }
            }
        }
    }

    /**
     * Undo ignoring devices and folders.
     */
    public void undoIgnoredDevicesAndFolders() {
        Log.d(TAG, "Undo ignoring devices and folders ...");
        synchronized (mConfigLock) {
            mConfig.remoteIgnoredDevices.clear();
            for (Device device : mConfig.devices) {
                device.ignoredFolders.clear();
            }
        }
    }

    /**
     * Override folder changes. This is the same as hitting
     * the "override changes" button from the web UI.
     */
    public void overrideChanges(String folderId) {
        Log.d(TAG, "overrideChanges '" + folderId + "'");
        new PostRequest(mContext, mUrl, PostRequest.URI_DB_OVERRIDE, mApiKey,
            ImmutableMap.of("folder", folderId), null, null);
    }

    /**
     * Sends current config to Syncthing.
     * Will result in a "ConfigSaved" event.
     * EventProcessor will trigger this.reloadConfig().
     */
    private void sendConfig() {
        String jsonConfig;
        synchronized (mConfigLock) {
            jsonConfig = mGson.toJson(mConfig);
        }
        // Log.v(TAG, "sendConfig: config=" + jsonConfig);
        new PostRequest(mContext, mUrl, PostRequest.URI_SYSTEM_CONFIG, mApiKey,
            null, jsonConfig, null);
        mOnConfigChangedListener.onConfigChanged();
    }

    /**
     * Sends current config and restarts Syncthing.
     */
    public void saveConfigAndRestart() {
        String jsonConfig;
        synchronized (mConfigLock) {
            jsonConfig = mGson.toJson(mConfig);
        }
        new PostRequest(mContext, mUrl, PostRequest.URI_SYSTEM_CONFIG, mApiKey,
                null, jsonConfig, result -> {
            Intent intent = new Intent(mContext, SyncthingService.class)
                    .setAction(SyncthingService.ACTION_RESTART);
            mContext.startService(intent);
        });
        mOnConfigChangedListener.onConfigChanged();
    }

    /**
     * Posts shutdown request.
     * This will cause SyncthingNative to exit and not restart.
     */
    public void shutdown() {
        new PostRequest(mContext, mUrl, PostRequest.URI_SYSTEM_SHUTDOWN, mApiKey,
                null, null, null);
    }

    /**
     * Returns the version name, or a (text) error message on failure.
     */
    public String getVersion() {
        return mVersion;
    }

    public List<Folder> getFolders() {
        List<Folder> folders;
        synchronized (mConfigLock) {
            folders = deepCopy(mConfig.folders, new TypeToken<List<Folder>>(){}.getType());
        }
        Collections.sort(folders, FOLDERS_COMPARATOR);
        return folders;
    }

    public final Folder getFolderByID(String folderID) {
        if (ENABLE_TEST_DATA && folderID.equals("abcd-efgh")) {
            final Folder folder = new Folder();
            folder.id = "abcd-efgh";
            folder.label = "label_abcd-efgh";
            folder.path = "/storage/emulated/0/testdata";
            folder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;
            return folder;
        }

        final List<Folder> folders = getFolders();
        for (Folder folder : folders) {
            if (folder.id.equals(folderID)) {
                return folder;
            }
        }
        return null;
    }

    /**
     * This is only used for new folder creation, see {@link ../activities/FolderActivity}.
     */
    public void addFolder(Folder folder) {
        synchronized (mConfigLock) {
            // Add the new folder to the model.
            mConfig.folders.add(folder);
            // Send model changes to syncthing, does not require a restart.
            sendConfig();
        }
    }

    public void updateFolder(Folder newFolder) {
        synchronized (mConfigLock) {
            removeFolderInternal(newFolder.id);
            mConfig.folders.add(newFolder);
            sendConfig();
        }
    }

    public void removeFolder(String id) {
        synchronized (mConfigLock) {
            removeFolderInternal(id);
            // mCompletion will be updated after the ConfigSaved event.
            sendConfig();
            // Remove saved data from share activity for this folder.
        }
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(ShareActivity.PREF_FOLDER_SAVED_SUBDIRECTORY+id)
                .apply();
    }

    private void removeFolderInternal(String id) {
        synchronized (mConfigLock) {
            Iterator<Folder> it = mConfig.folders.iterator();
            while (it.hasNext()) {
                Folder f = it.next();
                if (f.id.equals(id)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    /**
     * Returns a list of all existing devices.
     *
     * @param includeLocal True if the local device should be included in the result.
     */
    public List<Device> getDevices(Boolean includeLocal) {
        List<Device> devices;
        synchronized (mConfigLock) {
            devices = deepCopy(mConfig.devices, new TypeToken<List<Device>>(){}.getType());
        }

        Iterator<Device> it = devices.iterator();
        while (it.hasNext()) {
            Device device = it.next();
            boolean isLocalDevice = Objects.equal(mLocalDeviceId, device.deviceID);
            if (!includeLocal && isLocalDevice) {
                it.remove();
                break;
            }
        }
        return devices;
    }

    public Device getLocalDevice() {
        List<Device> devices = getDevices(true);
        if (devices.isEmpty()) {
            throw new RuntimeException("RestApi.getLocalDevice: devices is empty.");
        }
        LogV("getLocalDevice: Looking for local device ID " + mLocalDeviceId);
        for (Device d : devices) {
            if (d.deviceID.equals(mLocalDeviceId)) {
                return deepCopy(d, Device.class);
            }
        }
        throw new RuntimeException("RestApi.getLocalDevice: Failed to get the local device crucial to continuing execution.");
    }

    public void addDevice(Device device) {
        synchronized (mConfigLock) {
            mConfig.devices.add(device);
            sendConfig();
        }
    }

    public void updateDevice(Device newDevice) {
        synchronized (mConfigLock) {
            removeDeviceInternal(newDevice.deviceID);
            mConfig.devices.add(newDevice);
            sendConfig();
        }
    }

    public void removeDevice(String deviceId) {
        synchronized (mConfigLock) {
            removeDeviceInternal(deviceId);
            // mCompletion will be updated after the ConfigSaved event.
            sendConfig();
        }
    }

    private void removeDeviceInternal(String deviceId) {
        synchronized (mConfigLock) {
            Iterator<Device> it = mConfig.devices.iterator();
            while (it.hasNext()) {
                Device d = it.next();
                if (d.deviceID.equals(deviceId)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    public Options getOptions() {
        synchronized (mConfigLock) {
            return deepCopy(mConfig.options, Options.class);
        }
    }

    public Gui getGui() {
        synchronized (mConfigLock) {
            return deepCopy(mConfig.gui, Gui.class);
        }
    }

    public void editSettings(Gui newGui, Options newOptions) {
        synchronized (mConfigLock) {
            mConfig.gui = newGui;
            mConfig.options = newOptions;
        }
    }

    /**
     * Returns a deep copy of object.
     *
     * This method uses Gson and only works with objects that can be converted with Gson.
     */
    private <T> T deepCopy(T object, Type type) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(object, type), type);
    }

    /**
     * Requests and parses information about current system status and resource usage.
     */
    public void getSystemStatus(OnResultListener1<SystemStatus> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_SYSTEM_STATUS, mApiKey, null, result -> {
            SystemStatus systemStatus;
            try {
                systemStatus = mGson.fromJson(result, SystemStatus.class);
                listener.onResult(systemStatus);
            } catch (Exception e) {
                Log.e(TAG, "getSystemStatus: Parsing REST API result failed. result=" + result);
            }
        });
    }

    public boolean isConfigLoaded() {
        synchronized(mConfigLock) {
            return mConfig != null;
        }
    }

    /**
     * Requests locally discovered devices.
     */
    public void getDiscoveredDevices(OnResultListener1<Map<String, DiscoveredDevice>> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_SYSTEM_DISCOVERY, mApiKey,
                null, result -> {
            Map<String, DiscoveredDevice> discoveredDevices = mGson.fromJson(result, new TypeToken<Map<String, DiscoveredDevice>>(){}.getType());
            if (ENABLE_TEST_DATA) {
                DiscoveredDevice fakeDiscoveredDevice = new DiscoveredDevice();
                fakeDiscoveredDevice.addresses = new String[]{"tcp4://192.168.178.10:40004"};
                discoveredDevices.put("ZOK75WR-W3XWWUZ-NNLXV7V-DUYKVWA-SSPD7OH-3QYOZBY-SBH3N2Y-IAVJ4QH", fakeDiscoveredDevice);
                discoveredDevices.put("ZPUZOWC-SUCJILE-ITNLBLL-MHBWJG5-46QM47Y-CDTQT3M-IA4RSJV-7BYA7QA", fakeDiscoveredDevice);
            }
            listener.onResult(discoveredDevices);
        });
    }

    /**
     * Requests ignore list for given folder.
     */
    public void getFolderIgnoreList(String folderId, OnResultListener1<FolderIgnoreList> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_DB_IGNORES, mApiKey,
                ImmutableMap.of("folder", folderId), result -> {
            FolderIgnoreList folderIgnoreList = mGson.fromJson(result, FolderIgnoreList.class);
            listener.onResult(folderIgnoreList);
        });
    }

    /**
     * Posts ignore list for given folder.
     */
    public void postFolderIgnoreList(String folderId, String[] ignore) {
        FolderIgnoreList folderIgnoreList = new FolderIgnoreList();
        folderIgnoreList.ignore = ignore;
        new PostRequest(mContext, mUrl, PostRequest.URI_DB_IGNORES, mApiKey,
            ImmutableMap.of("folder", folderId), mGson.toJson(folderIgnoreList), null);
    }

    /**
     * Requests and parses system version information.
     */
    public void getSystemVersion(OnResultListener1<SystemVersion> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_VERSION, mApiKey, null, result -> {
            SystemVersion systemVersion = mGson.fromJson(result, SystemVersion.class);
            listener.onResult(systemVersion);
        });
    }

    /**
     * Returns connection info for the local device and all connected devices.
     */
    public void getConnections(final OnResultListener1<Connections> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_CONNECTIONS, mApiKey, null, result -> {
            Long now = System.currentTimeMillis();
            Long msElapsed = now - mPreviousConnectionTime;
            if (msElapsed < Constants.GUI_UPDATE_INTERVAL) {
                listener.onResult(deepCopy(mPreviousConnections.get(), Connections.class));
                return;
            }

            mPreviousConnectionTime = now;
            Connections connections = mGson.fromJson(result, Connections.class);
            for (Map.Entry<String, Connections.Connection> e : connections.connections.entrySet()) {
                e.getValue().completion = mCompletion.getDeviceCompletion(e.getKey());

                Connections.Connection prev =
                        (mPreviousConnections.isPresent() && mPreviousConnections.get().connections.containsKey(e.getKey()))
                                ? mPreviousConnections.get().connections.get(e.getKey())
                                : new Connections.Connection();
                e.getValue().setTransferRate(prev, msElapsed);
            }
            Connections.Connection prev =
                    mPreviousConnections.transform(c -> c.total).or(new Connections.Connection());
            connections.total.setTransferRate(prev, msElapsed);
            mPreviousConnections = Optional.of(connections);
            listener.onResult(deepCopy(connections, Connections.class));
        });
    }

    /**
     * Returns status information about the folder with the given id.
     */
    public void getFolderStatus(final String folderId, final OnResultListener2<String, FolderStatus> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_DB_STATUS, mApiKey,
                    ImmutableMap.of("folder", folderId), result -> {
            FolderStatus m = mGson.fromJson(result, FolderStatus.class);
            mCachedFolderStatuses.put(folderId, m);
            listener.onResult(folderId, m);
        });
    }

    /**
     * Requests and parses information about recent changes.
     */
    public void getDiskEvents(int limit, OnResultListener1<List<DiskEvent>> listener) {
        new GetRequest(
                mContext, mUrl,
                GetRequest.URI_EVENTS_DISK, mApiKey,
                ImmutableMap.of("limit", Integer.toString(limit)),
                result -> {
                    List<DiskEvent> diskEvents = new ArrayList<>();
                    try {
                        JsonArray jsonDiskEvents = new JsonParser().parse(result).getAsJsonArray();
                        for (int i = jsonDiskEvents.size()-1; i >= 0; i--) {
                            JsonElement jsonDiskEvent = jsonDiskEvents.get(i);
                            diskEvents.add(mGson.fromJson(jsonDiskEvent, DiskEvent.class));
                        }
                        listener.onResult(diskEvents);
                    } catch (Exception e) {
                        Log.e(TAG, "getDiskEvents: Parsing REST API result failed. result=" + result);
                    }
                }
        );
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
     * Retrieves the events that have accumulated since the given event id.
     *
     * The OnReceiveEventListeners onEvent method is called for each event.
     */
    public final void getEvents(final long sinceId, final long limit, final OnReceiveEventListener listener) {
        Map<String, String> params =
                ImmutableMap.of("since", String.valueOf(sinceId), "limit", String.valueOf(limit));
        new GetRequest(mContext, mUrl, GetRequest.URI_EVENTS, mApiKey, params, result -> {
            JsonArray jsonEvents = new JsonParser().parse(result).getAsJsonArray();
            long lastId = 0;

            for (int i = 0; i < jsonEvents.size(); i++) {
                JsonElement json = jsonEvents.get(i);
                Event event = mGson.fromJson(json, Event.class);

                if (lastId < event.id)
                    lastId = event.id;

                listener.onEvent(event);
            }

            listener.onDone(lastId);
        });
    }

    /**
     * Updates cached folder and device completion info according to event data.
     */
    public void setCompletionInfo(String deviceId, String folderId, CompletionInfo completionInfo) {
        mCompletion.setCompletionInfo(deviceId, folderId, completionInfo);
    }

    /**
     * Returns prettyfied usage report.
     */
    public void getUsageReport(final OnResultListener1<String> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_REPORT, mApiKey, null, result -> {
            JsonElement json = new JsonParser().parse(result);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            listener.onResult(gson.toJson(json));
        });
    }

    public String getApiKey() {
        return mApiKey;
    }

    public URL getUrl() {
        return mUrl;
    }

    public Boolean isUsageReportingAccepted() {
        Options options = getOptions();
        if (options == null) {
            Log.e(TAG, "isUsageReportingAccepted called while options == null");
            return false;
        }
        return options.isUsageReportingAccepted(mUrVersionMax);
    }

    public Boolean isUsageReportingDecided() {
        Options options = getOptions();
        if (options == null) {
            Log.e(TAG, "isUsageReportingDecided called while options == null");
            return true;
        }
        return options.isUsageReportingDecided(mUrVersionMax);
    }

    public void setUsageReporting(Boolean acceptUsageReporting) {
        Options options = getOptions();
        if (options == null) {
            Log.e(TAG, "setUsageReporting called while options == null");
            return;
        }
        options.urAccepted = acceptUsageReporting ? mUrVersionMax : Options.USAGE_REPORTING_DENIED;
        synchronized (mConfigLock) {
            mConfig.options = options;
        }
    }

    public void downloadSupportBundle(File targetFile, final OnResultListener1<Boolean> listener) {
        new GetRequest(mContext, mUrl, GetRequest.URI_DEBUG_SUPPORT, mApiKey, null, result -> {
            Boolean failSuccess = true;
            LogV("downloadSupportBundle: Writing '" + targetFile.getPath() + "' ...");
            FileOutputStream fileOutputStream = null;
            try {
                if (!targetFile.exists()) {
                    targetFile.createNewFile();
                }
                fileOutputStream = new FileOutputStream(targetFile);
                fileOutputStream.write(result.getBytes("ISO-8859-1"));  // Do not use UTF-8 here because the ZIP would be corrupted.
                fileOutputStream.flush();
            } catch (IOException e) {
                Log.w(TAG, "downloadSupportBundle: Failed to write '" + targetFile.getPath() + "' #1", e);
                failSuccess = false;
            } finally {
                try {
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "downloadSupportBundle: Failed to write '" + targetFile.getPath() + "' #2", e);
                    failSuccess = false;
                }
            }
            if (listener != null) {
                listener.onResult(failSuccess);
            }
        });
    }

    /**
     * Event triggered by {@link RunConditionMonitor} routed here through {@link SyncthingService}.
     */
    public void applyCustomRunConditions(RunConditionMonitor runConditionMonitor) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        synchronized (mConfigLock) {
            Boolean configChanged = false;

            // Check if the config has been loaded.
            if (mConfig == null) {
                Log.w(TAG, "applyCustomRunConditions: mConfig is not ready yet.");
                return;
            }

            // Check if the folders are available from config.
            if (mConfig.folders != null) {
                for (Folder folder : mConfig.folders) {
                    // LogV("applyCustomRunConditions: Processing config of folder(" + folder.label + ")");
                    Boolean folderCustomSyncConditionsEnabled = sharedPreferences.getBoolean(
                        Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id), false
                    );
                    if (folderCustomSyncConditionsEnabled) {
                        Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                            Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id
                        );
                        LogV("applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? "1" : "0"));
                        if (folder.paused != !syncConditionsMet) {
                            folder.paused = !syncConditionsMet;
                            Log.d(TAG, "applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? ">1" : ">0"));
                            configChanged = true;
                        }
                    }
                }
            } else {
                Log.d(TAG, "applyCustomRunConditions: mConfig.folders is not ready yet.");
                return;
            }

            // Check if the devices are available from config.
            if (mConfig.devices != null) {
                for (Device device : mConfig.devices) {
                    // LogV("applyCustomRunConditions: Processing config of device(" + device.name + ")");
                    Boolean deviceCustomSyncConditionsEnabled = sharedPreferences.getBoolean(
                        Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID), false
                    );
                    if (deviceCustomSyncConditionsEnabled) {
                        Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                            Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID
                        );
                        LogV("applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? "1" : "0"));
                        if (device.paused != !syncConditionsMet) {
                            device.paused = !syncConditionsMet;
                            Log.d(TAG, "applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? ">1" : ">0"));
                            configChanged = true;
                        }
                    }
                }
            } else {
                Log.d(TAG, "applyCustomRunConditions: mConfig.devices is not ready yet.");
                return;
            }

            if (configChanged) {
                LogV("applyCustomRunConditions: Sending changed config ...");
                sendConfig();
            } else {
                LogV("applyCustomRunConditions: No action was necessary.");
            }
        }
    }

    private Gson getGson() {
        Gson gson = new GsonBuilder()
                .create();
        return gson;
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
