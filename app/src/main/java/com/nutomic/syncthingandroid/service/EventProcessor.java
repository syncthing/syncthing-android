package com.nutomic.syncthingandroid.service;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.util.Consumer;

import com.annimon.stream.Stream;
import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.DeviceActivity;
import com.nutomic.syncthingandroid.activities.FolderActivity;
import com.nutomic.syncthingandroid.model.CompletionInfo;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Run by the syncthing service to convert syncthing events into local broadcasts.
 *
 * It uses {@link RestApi#getEvents} to read the pending events and wait for new events.
 */
public class EventProcessor implements  Runnable, RestApi.OnReceiveEventListener {

    private static final String TAG = "EventProcessor";
    private static final String PREF_LAST_SYNC_ID = "last_sync_id";

    /**
     * Minimum interval in seconds at which the events are polled from syncthing and processed.
     * This intervall will not wake up the device to save battery power.
     */
    private static final long EVENT_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(15);

    /**
     * Use the MainThread for all callbacks and message handling
     * or we have to track down nasty threading problems.
     */
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private volatile long mLastEventId = 0;
    private volatile boolean mShutdown = true;

    private final Context mContext;
    private final RestApi mApi;
    @Inject SharedPreferences mPreferences;
    @Inject NotificationHandler mNotificationHandler;

    public EventProcessor(
            Context context,
            RestApi api,
            SharedPreferences preferences,
            NotificationHandler notificationHandler
    ) {
        mContext = context;
        mApi = api;
        mPreferences = preferences;
        mNotificationHandler = notificationHandler;
    }

    @Override
    public void run() {
        // Restore the last event id if the event processor may have been restarted.
        if (mLastEventId == 0) {
            mLastEventId = mPreferences.getLong(PREF_LAST_SYNC_ID, 0);
        }

        // First check if the event number ran backwards.
        // If that's the case we've to start at zero because syncthing was restarted.
        mApi.getEvents(0, 1, new RestApi.OnReceiveEventListener() {
            @Override
            public void onEvent(Event event) {
            }

            @Override
            public void onDone(long lastId) {
                if (lastId < mLastEventId) mLastEventId = 0;

                Log.d(TAG, "Reading events starting with id " + mLastEventId);

                mApi.getEvents(mLastEventId, 0, EventProcessor.this);
            }
        });
    }

    /**
     * Performs the actual event handling.
     */
    @Override
    public void onEvent(Event event) {
        Map<String,Object> mapData = null;
        try {
            mapData = (Map<String,Object>) event.data;
        } catch (ClassCastException e) { }
        switch (event.type) {
            case "ConfigSaved":
                if (mApi != null) {
                    Log.v(TAG, "Forwarding ConfigSaved event to RestApi to get the updated config.");
                    mApi.reloadConfig();
                }
                break;
            case "PendingDevicesChanged":
                mapNullable((List<Map<String,String>>) mapData.get("added"), this::onPendingDevicesChanged);
                break;
            case "FolderCompletion":
                CompletionInfo completionInfo = new CompletionInfo();
                completionInfo.completion = (Double) mapData.get("completion");
                mApi.setCompletionInfo(
                    (String) mapData.get("device"),          // deviceId
                    (String) mapData.get("folder"),          // folderId
                    completionInfo
                );
                break;
            case "PendingFoldersChanged":
                mapNullable((List<Map<String,String>>) mapData.get("added"), this::onPendingFoldersChanged);
                break;
            case "ItemFinished":
                String folder = (String) mapData.get("folder");
                String folderPath = null;
                for (Folder f : mApi.getFolders()) {
                    if (f.id.equals(folder)) {
                        folderPath = f.path;
                    }
                }
                File updatedFile = new File(folderPath, (String) mapData.get("item"));
                if (!"delete".equals(mapData.get("action"))) {
                    Log.i(TAG, "Rescanned file via MediaScanner: " + updatedFile.toString());
                    MediaScannerConnection.scanFile(mContext, new String[]{updatedFile.getPath()},
                            null, null);
                } else {
                    // Starting with Android 10/Q and targeting API level 29/removing legacy storage flag,
                    // reports of files being spuriously deleted came up.
                    // Best guess is that Syncthing directly interacted with the filesystem before,
                    // and there's a virtualisation layer there now. Also there's reports this API
                    // changed behaviour with scoped storage. In any case it now does not only
                    // update the media db, but actually delete the file on disk. Which is bad,
                    // as it can race with the creation of the same file and thus delete it. See:
                    // https://github.com/syncthing/syncthing-android/issues/1801
                    // https://github.com/syncthing/syncthing/issues/7974
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        break;
                    }
                    // https://stackoverflow.com/a/29881556/1837158
                    Log.i(TAG, "Deleted file from MediaStore: " + updatedFile.toString());
                    Uri contentUri = MediaStore.Files.getContentUri("external");
                    ContentResolver resolver = mContext.getContentResolver();
                    resolver.delete(contentUri, MediaStore.Images.ImageColumns.DATA + " = ?",
                            new String[]{updatedFile.getPath()});
                }
                break;
            case "Ping":
                // Ignored.
                break;
            case "DeviceConnected":
            case "DeviceDisconnected":
            case "DeviceDiscovered":
            case "DownloadProgress":
            case "FolderPaused":
            case "FolderScanProgress":
            case "FolderSummary":
            case "ItemStarted":
            case "LocalIndexUpdated":
            case "LoginAttempt":
            case "RemoteDownloadProgress":
            case "RemoteIndexUpdated":
            case "Starting":
            case "StartupComplete":
            case "StateChanged":
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Ignored event " + event.type + ", data " + event.data);
                }
                break;
            default:
                Log.v(TAG, "Unhandled event " + event.type);
        }
    }

    @Override
    public void onDone(long id) {
        if (mLastEventId < id) {
            mLastEventId = id;

            // Store the last EventId in case we get killed
            mPreferences.edit().putLong(PREF_LAST_SYNC_ID, mLastEventId).apply();
        }

        synchronized (mMainThreadHandler) {
            if (!mShutdown) {
                mMainThreadHandler.removeCallbacks(this);
                mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
            }
        }
    }

    public void start() {
        Log.d(TAG, "Starting event processor.");

        // Remove all pending callbacks and add a new one. This makes sure that only one
        // event poller is running at any given time.
        synchronized (mMainThreadHandler) {
            mShutdown = false;
            mMainThreadHandler.removeCallbacks(this);
            mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
        }
    }

    public void stop() {
        Log.d(TAG, "Stopping event processor.");
        synchronized (mMainThreadHandler) {
            mShutdown = true;
            mMainThreadHandler.removeCallbacks(this);
        }
    }

    private void onPendingDevicesChanged(Map<String, String> added) {
        String deviceId = added.get("deviceID");
        String deviceName = added.get("name");
        String deviceAddress = added.get("address");
        if (deviceId == null) {
            return;
        }
        Log.d(TAG, "Unknown device " + deviceName + "(" + deviceId + ") wants to connect");

        String title = mContext.getString(R.string.device_rejected,
                deviceName.isEmpty() ? deviceId.substring(0, 7) : deviceName);
        int notificationId = mNotificationHandler.getNotificationIdFromText(title);

        // Prepare "accept" action.
        Intent intentAccept = new Intent(mContext, DeviceActivity.class)
                .putExtra(DeviceActivity.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(DeviceActivity.EXTRA_IS_CREATE, true)
                .putExtra(DeviceActivity.EXTRA_DEVICE_ID, deviceId)
                .putExtra(DeviceActivity.EXTRA_DEVICE_NAME, deviceName);
        PendingIntent piAccept = PendingIntent.getActivity(mContext, notificationId,
            intentAccept, Constants.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Prepare "ignore" action.
        Intent intentIgnore = new Intent(mContext, SyncthingService.class)
                .putExtra(SyncthingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SyncthingService.EXTRA_DEVICE_ID, deviceId)
                .putExtra(SyncthingService.EXTRA_DEVICE_NAME, deviceName)
                .putExtra(SyncthingService.EXTRA_DEVICE_ADDRESS, deviceAddress);
        intentIgnore.setAction(SyncthingService.ACTION_IGNORE_DEVICE);
        PendingIntent piIgnore = PendingIntent.getService(mContext, 0,
            intentIgnore, Constants.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        mNotificationHandler.showConsentNotification(notificationId, title, piAccept, piIgnore);
    }

    private void onPendingFoldersChanged(Map<String, String> added) {
        String deviceId = added.get("deviceID");
        String folderId = added.get("folderID");
        String folderLabel = added.get("folderLabel");
        if (deviceId == null || folderId == null) {
            return;
        }
        Log.d(TAG, "Device " + deviceId + " wants to share folder " +
            folderLabel + " (" + folderId + ")");

        // Find the deviceName corresponding to the deviceId
        String deviceName = null;
        for (Device d : mApi.getDevices(false)) {
            if (d.deviceID.equals(deviceId)) {
                deviceName = d.getDisplayName();
                break;
            }
        }
        String title = mContext.getString(R.string.folder_rejected, deviceName,
                folderLabel.isEmpty() ? folderId : folderLabel + " (" + folderId + ")");
        int notificationId = mNotificationHandler.getNotificationIdFromText(title);

        // Prepare "accept" action.
        boolean isNewFolder = Stream.of(mApi.getFolders())
                .noneMatch(f -> f.id.equals(folderId));
        Intent intentAccept = new Intent(mContext, FolderActivity.class)
                .putExtra(FolderActivity.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(FolderActivity.EXTRA_IS_CREATE, isNewFolder)
                .putExtra(FolderActivity.EXTRA_DEVICE_ID, deviceId)
                .putExtra(FolderActivity.EXTRA_FOLDER_ID, folderId)
                .putExtra(FolderActivity.EXTRA_FOLDER_LABEL, folderLabel);
        PendingIntent piAccept = PendingIntent.getActivity(mContext, notificationId,
            intentAccept, Constants.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Prepare "ignore" action.
        Intent intentIgnore = new Intent(mContext, SyncthingService.class)
                .putExtra(SyncthingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SyncthingService.EXTRA_DEVICE_ID, deviceId)
                .putExtra(SyncthingService.EXTRA_FOLDER_ID, folderId)
                .putExtra(SyncthingService.EXTRA_FOLDER_LABEL, folderLabel);
        intentIgnore.setAction(SyncthingService.ACTION_IGNORE_FOLDER);
        PendingIntent piIgnore = PendingIntent.getService(mContext, 0,
            intentIgnore, Constants.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        mNotificationHandler.showConsentNotification(notificationId, title, piAccept, piIgnore);
    }

    private <T> void mapNullable(List<T> l, Consumer<T> c) {
        if (l != null) {
            for (T m : l) {
                c.accept(m);
            }
        }
    }

}
