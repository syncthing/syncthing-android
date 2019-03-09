package com.nutomic.syncthingandroid.service;

import android.app.PendingIntent;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.annimon.stream.Stream;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.DeviceActivity;
import com.nutomic.syncthingandroid.activities.FolderActivity;
import com.nutomic.syncthingandroid.model.CompletionInfo;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Event;
import com.nutomic.syncthingandroid.model.Folder;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Run by the syncthing service to convert syncthing events into local broadcasts.
 *
 * It uses {@link RestApi#getEvents} to read the pending events and wait for new events.
 */
public class EventProcessor implements  Runnable, RestApi.OnReceiveEventListener {

    private static final String TAG = "EventProcessor";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Minimum interval in seconds at which the events are polled from syncthing and processed.
     * This interval will not wake up the device to save battery power.
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
    private final RestApi mRestApi;
    @Inject SharedPreferences mPreferences;
    @Inject NotificationHandler mNotificationHandler;

    public EventProcessor(Context context, RestApi restApi) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        mContext = context;
        mRestApi = restApi;
    }

    @Override
    public void run() {
        // Restore the last event id if the event processor may have been restarted.
        if (mLastEventId == 0) {
            mLastEventId = mPreferences.getLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, 0);
        }

        // First check if the event number ran backwards.
        // If that's the case we've to start at zero because syncthing was restarted.
        mRestApi.getEvents(0, 1, new RestApi.OnReceiveEventListener() {
            @Override
            public void onEvent(Event event) {
            }

            @Override
            public void onDone(long lastId) {
                if (lastId < mLastEventId) mLastEventId = 0;

                LogV("Reading events starting with id " + mLastEventId);

                mRestApi.getEvents(mLastEventId, 0, EventProcessor.this);
            }
        });
    }

    /**
     * Performs the actual event handling.
     */
    @Override
    public void onEvent(Event event) {
        switch (event.type) {
            case "ConfigSaved":
                if (mRestApi != null) {
                    LogV("Forwarding ConfigSaved event to RestApi to get the updated config.");
                    mRestApi.reloadConfig();
                }
                break;
            case "DeviceRejected":
                onDeviceRejected(
                    (String) event.data.get("device"),          // deviceId
                    (String) event.data.get("name")             // deviceName
                );
                break;
            case "FolderCompletion":
                CompletionInfo completionInfo = new CompletionInfo();
                completionInfo.completion = (Double) event.data.get("completion");
                mRestApi.setCompletionInfo(
                    (String) event.data.get("device"),          // deviceId
                    (String) event.data.get("folder"),          // folderId
                    completionInfo
                );
                break;
            case "FolderRejected":
                onFolderRejected(
                    (String) event.data.get("device"),          // deviceId
                    (String) event.data.get("folder"),          // folderId
                    (String) event.data.get("folderLabel")      // folderLabel
                );
                break;
            case "ItemFinished":
                String action               = (String) event.data.get("action");
                String error                = (String) event.data.get("error");
                String folderId             = (String) event.data.get("folder");
                String relativeFilePath     = (String) event.data.get("item");

                // Lookup folder.path for the given folder.id if all fields were contained in the event.data.
                String folderPath = null;
                if (!TextUtils.isEmpty(action) &&
                        !TextUtils.isEmpty(folderId) &&
                        !TextUtils.isEmpty(relativeFilePath)) {
                    for (Folder folder : mRestApi.getFolders()) {
                        if (folder.id.equals(folderId)) {
                            folderPath = folder.path;
                            break;
                        }
                    }
                }
                if (!TextUtils.isEmpty(folderPath)) {
                    onItemFinished(action, error, new File(folderPath, relativeFilePath));
                } else {
                    Log.w(TAG, "ItemFinished: Failed to determine folder.path for folder.id=\"" + (TextUtils.isEmpty(folderId) ? "" : folderId) + "\"");
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
            case "FolderResumed":
            case "FolderScanProgress":
            case "FolderSummary":
            case "FolderWatchStateChanged":
            case "ItemStarted":
            case "ListenAddressesChanged":
            case "LocalIndexUpdated":
            case "LoginAttempt":
            case "RemoteDownloadProgress":
            case "RemoteIndexUpdated":
            case "Starting":
            case "StartupComplete":
            case "StateChanged":
                if (ENABLE_VERBOSE_LOG) {
                    LogV("Ignored event " + event.type + ", data " + event.data);
                }
                break;
            default:
                Log.d(TAG, "Unhandled event " + event.type);
        }
    }

    @Override
    public void onDone(long id) {
        if (mLastEventId < id) {
            mLastEventId = id;

            // Store the last EventId in case we get killed
            mPreferences.edit().putLong(Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID, mLastEventId).apply();
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

    private void onDeviceRejected(String deviceId, String deviceName) {
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
            intentAccept, PendingIntent.FLAG_UPDATE_CURRENT);

        // Prepare "ignore" action.
        Intent intentIgnore = new Intent(mContext, SyncthingService.class)
                .putExtra(SyncthingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SyncthingService.EXTRA_DEVICE_ID, deviceId);
        intentIgnore.setAction(SyncthingService.ACTION_IGNORE_DEVICE);
        PendingIntent piIgnore = PendingIntent.getService(mContext, 0,
            intentIgnore, PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        mNotificationHandler.showConsentNotification(notificationId, title, piAccept, piIgnore);
    }

    private void onFolderRejected(String deviceId, String folderId,
                                    String folderLabel) {
        if (deviceId == null || folderId == null) {
            return;
        }
        Log.d(TAG, "Device " + deviceId + " wants to share folder " +
            folderLabel + " (" + folderId + ")");

        // Find the deviceName corresponding to the deviceId
        String deviceName = null;
        for (Device d : mRestApi.getDevices(false)) {
            if (d.deviceID.equals(deviceId)) {
                deviceName = d.getDisplayName();
                break;
            }
        }
        String title = mContext.getString(R.string.folder_rejected, deviceName,
                folderLabel.isEmpty() ? folderId : folderLabel + " (" + folderId + ")");
        int notificationId = mNotificationHandler.getNotificationIdFromText(title);

        // Prepare "accept" action.
        boolean isNewFolder = Stream.of(mRestApi.getFolders())
                .noneMatch(f -> f.id.equals(folderId));
        Intent intentAccept = new Intent(mContext, FolderActivity.class)
                .putExtra(FolderActivity.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(FolderActivity.EXTRA_IS_CREATE, isNewFolder)
                .putExtra(FolderActivity.EXTRA_DEVICE_ID, deviceId)
                .putExtra(FolderActivity.EXTRA_FOLDER_ID, folderId)
                .putExtra(FolderActivity.EXTRA_FOLDER_LABEL, folderLabel);
        PendingIntent piAccept = PendingIntent.getActivity(mContext, notificationId,
            intentAccept, PendingIntent.FLAG_UPDATE_CURRENT);

        // Prepare "ignore" action.
        Intent intentIgnore = new Intent(mContext, SyncthingService.class)
                .putExtra(SyncthingService.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(SyncthingService.EXTRA_DEVICE_ID, deviceId)
                .putExtra(SyncthingService.EXTRA_FOLDER_ID, folderId);
        intentIgnore.setAction(SyncthingService.ACTION_IGNORE_FOLDER);
        PendingIntent piIgnore = PendingIntent.getService(mContext, 0,
            intentIgnore, PendingIntent.FLAG_UPDATE_CURRENT);

        // Show notification.
        mNotificationHandler.showConsentNotification(notificationId, title, piAccept, piIgnore);
    }

    /**
     * Precondition: action != null
     */
    private void onItemFinished(String action, String error, File updatedFile) {
        String relativeFilePath = updatedFile.toString();
        if (!TextUtils.isEmpty(error)) {
            Log.e(TAG, "onItemFinished: Error \"" + error + "\" reported on file: " + relativeFilePath);
            return;
        }

        switch (action) {
            case "delete":          // file deleted
                Log.i(TAG, "Deleting file from MediaStore: " + relativeFilePath);
                Uri contentUri = MediaStore.Files.getContentUri("external");
                ContentResolver resolver = mContext.getContentResolver();
                LoggingAsyncQueryHandler asyncQueryHandler = new LoggingAsyncQueryHandler(resolver);
                asyncQueryHandler.startDelete(
                    0,                          // this will be passed to "onUpdatedComplete#token"
                    relativeFilePath,           // this will be passed to "onUpdatedComplete#cookie"
                    contentUri,
                    MediaStore.Images.ImageColumns.DATA + " LIKE ?",
                    new String[]{updatedFile.getPath()}
                );
                break;
            case "update":          // file contents changed
            case "metadata":        // file metadata changed but not contents
                Log.i(TAG, "Rescanning file via MediaScanner: " + relativeFilePath);
                MediaScannerConnection.scanFile(mContext, new String[]{updatedFile.getPath()},
                        null, null);
                break;
            default:
                Log.w(TAG, "onItemFinished: Unhandled action \"" + action + "\"");
        }
    }

    private static class LoggingAsyncQueryHandler extends AsyncQueryHandler {

        public LoggingAsyncQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onUpdateComplete(token, cookie, result);
            if (result == 1 && cookie != null) {
                // Log.v(TAG, "onItemFinished: onDeleteComplete: [ok] file=" + cookie.toString() + ", token=" + Integer.toString(token));
            }
        }
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
