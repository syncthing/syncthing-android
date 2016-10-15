package com.nutomic.syncthingandroid.syncthing;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.gson.JsonObject;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.fragments.DeviceFragment;
import com.nutomic.syncthingandroid.fragments.FolderFragment;
import com.nutomic.syncthingandroid.model.Device;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Run by the syncthing service to convert syncthing events into local broadcasts.
 *
 * It uses {@link RestApi#getEvents} to read the pending events and wait for new events.
 */
public class EventProcessor implements SyncthingService.OnWebGuiAvailableListener, Runnable,
        RestApi.OnReceiveEventListener {

    private static final String TAG = "EventProcessor";
    private static final String PREF_LAST_SYNC_ID = "last_sync_id";

    /**
     * Minimum interval in seconds at which the events are polled from syncthing and processed.
     * This intervall will not wake up the device to save battery power.
     */
    public static final long EVENT_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(15);

    /**
     * Use the MainThread for all callbacks and message handling
     * or we have to track down nasty threading problems.
     */
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private volatile long mLastEventId = 0;
    private volatile boolean mShutdown = true;

    private final Context mContext;
    private final RestApi mApi;

    public EventProcessor(Context context, RestApi api) {
        mContext = context;
        mApi = api;
    }

    @Override
    public void run() {
        // Restore the last event id if the event processor may have been restartet.
        if (mLastEventId == 0) {
            mLastEventId = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getLong(PREF_LAST_SYNC_ID, 0);
        }

        // First check if the event number ran backwards.
        // If that's the case we've to start at zero because syncthing was restarted.
        mApi.getEvents(0, 1, new RestApi.OnReceiveEventListener() {
            @Override
            public void onEvent(String eventType, JsonObject data) {

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
    public void onEvent(String type, JsonObject data) {
        switch (type) {
            case "DeviceRejected":
                String deviceId = data.get("device").getAsString();
                Log.d(TAG, "Unknwon device " + deviceId + " wants to connect");

                Intent intent = new Intent(mContext, SettingsActivity.class)
                        .setAction(SettingsActivity.ACTION_DEVICE_SETTINGS)
                        .putExtra(SettingsActivity.EXTRA_IS_CREATE, true)
                        .putExtra(DeviceFragment.EXTRA_DEVICE_ID, deviceId);
                // HACK: Use a random, deterministic ID to make multiple PendingIntents
                //       distinguishable
                int requestCode = deviceId.hashCode();
                PendingIntent pi = PendingIntent.getActivity(mContext, requestCode, intent, 0);

                String title = mContext.getString(R.string.device_rejected,
                        deviceId.substring(0, 7));

                notify(title, pi);
                break;
            case "FolderRejected":
                deviceId = data.get("device").getAsString();
                String folderId = data.get("folder").getAsString();
                String folderLabel = data.get("folderLabel").getAsString();
                Log.d(TAG, "Device " + deviceId + " wants to share folder " + folderId);

                intent = new Intent(mContext, SettingsActivity.class)
                        .setAction(SettingsActivity.ACTION_FOLDER_SETTINGS)
                        .putExtra(SettingsActivity.EXTRA_IS_CREATE, true)
                        .putExtra(FolderFragment.EXTRA_DEVICE_ID, deviceId)
                        .putExtra(FolderFragment.EXTRA_FOLDER_ID, folderId)
                        .putExtra(FolderFragment.EXTRA_FOLDER_LABEL, folderLabel);
                // HACK: Use a random, deterministic ID to make multiple PendingIntents
                //       distinguishable
                requestCode = (deviceId + folderId + folderLabel).hashCode();
                pi = PendingIntent.getActivity(mContext, requestCode, intent, 0);

                String deviceName = null;
                for (Device d : mApi.getDevices(false)) {
                    if (d.deviceID.equals(deviceId))
                        deviceName = RestApi.getDeviceDisplayName(d);
                }
                title = mContext.getString(R.string.folder_rejected, deviceName,
                        folderLabel.isEmpty() ? folderId : folderLabel + " (" + folderId + ")");

                notify(title, pi);
                break;
            case "ItemFinished":
                File updatedFile = new File(data.get("folderpath").getAsString(),
                                            data.get("item").getAsString());
                Log.i(TAG, "Notified media scanner about " + updatedFile.toString());
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(updatedFile)));
                break;
            case "Ping":
                // Ignored.
                break;
            default:
                Log.i(TAG, "Unhandled event " + type);
        }
    }

    @Override
    public void onDone(long id) {
        if (mLastEventId < id) {
            mLastEventId = id;

            // Store the last EventId in case we get killed
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

            //noinspection CommitPrefEdits
            sp.edit().putLong(PREF_LAST_SYNC_ID, mLastEventId).apply();
        }

        synchronized (mMainThreadHandler) {
            if (!mShutdown) {
                mMainThreadHandler.removeCallbacks(this);
                mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
            }
        }
    }

    @Override
    public void onWebGuiAvailable() {
        Log.d(TAG, "WebGUI available. Starting event processor.");

        // Remove all pending callbacks and add a new one. This makes sure that only one
        // event poller is running at any given time.
        synchronized (mMainThreadHandler) {
            mShutdown = false;
            mMainThreadHandler.removeCallbacks(this);
            mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
        }
    }

    public void shutdown() {
        Log.d(TAG, "Shutdown event processor.");
        synchronized (mMainThreadHandler) {
            mShutdown = true;
            mMainThreadHandler.removeCallbacks(this);
        }
    }

    private void notify(String text, PendingIntent pi) {
        NotificationManager nm = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(text))
                .setContentIntent(pi)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setAutoCancel(true)
                .build();
        // HACK: Use a random, deterministic ID between 1000 and 2000 to avoid duplicate
        //       notifications.
        int notificationId = 1000 + text.hashCode() % 1000;
        nm.notify(notificationId, n);
    }
}
