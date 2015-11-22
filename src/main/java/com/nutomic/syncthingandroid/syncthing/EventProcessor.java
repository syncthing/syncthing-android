package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Run by the syncthing service to convert syncthing events into local broadcasts.
 * It uses SyncthingService.GetEvents to read the pending events and wait for new events.
 */
public class EventProcessor implements SyncthingService.OnWebGuiAvailableListener, Runnable,
        RestApi.OnReceiveEventListener {

    private static final String TAG = "EventProcessor";
    private static final String PREF_LAST_SYNC_ID = "last_sync_id";

    private static final String EVENT_BASE_ACTION = "com.nutomic.syncthingandroid.event";

    /**
     * Minimum interval in seconds at which the events are polled from syncthing and processed.
     * This intervall will not wake up the device to save battery power.
     */
    public static final long EVENT_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(15);

    // Use the MainThread for all callbacks and message handling
    // or we have to track down nasty threading problems.
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private volatile long mLastEventId = 0;
    private volatile boolean mShutdown = true;

    private final Context mContext;
    private final RestApi mApi;
    private final LocalBroadcastManager mLocalBM;
    private final Map<String, String> mFolderToPath = new HashMap<>();

    /**
     * Returns the action used by notification Intents fired for the given Syncthing event.
     * @param eventName Name of the Syncthing event.
     * @return Returns the full intent action used for local broadcasts.
     */
    public static String getEventIntentAction(String eventName) {
        return EVENT_BASE_ACTION + "." + eventName.toUpperCase(Locale.US);
    }

    /**
     * C'tor
     * @param context Context of the service using this event processor.
     * @param api Reference to the RestApi-Instance used for all API calls by this instance of the
     *            Event processor.
     */
    public EventProcessor(Context context, RestApi api) {
        mContext = context;
        mApi = api;
        mLocalBM = LocalBroadcastManager.getInstance(mContext);
    }

    private void updateFolderMap()
    {
        synchronized(mFolderToPath) {
            mFolderToPath.clear();
            for (RestApi.Folder folder: mApi.getFolders()) {
                mFolderToPath.put(folder.id, folder.path);
            }
        }
    }

    /**
     * @see Runnable
     */
    @Override
    public void run() {
        // Restore the last event id if the event processor may have been restartet.
        if (mLastEventId == 0) {
            mLastEventId = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getLong(PREF_LAST_SYNC_ID, 0);
        }

        // First check if the event number ran backwards.
        // If that's the case we've to start at zero because syncthing was restartet.
        mApi.getEvents(0, 1, new RestApi.OnReceiveEventListener() {
            @Override
            public void onEvent(long id, String eventType, Bundle eventData) {
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
     * @see RestApi.OnReceiveEventListener
     */
    @Override
    public void onEvent(final long id, final String eventType, final Bundle eventData) {
        // If a folder item is contained within the event. Resolve the local path.
        if (eventData.containsKey("folder")) {
            String folderPath = null;
            synchronized (mFolderToPath) {
                if (mFolderToPath.size() == 0) updateFolderMap();
                folderPath = mFolderToPath.get(eventData.getString("folder"));
            }

            if (folderPath != null) {
                eventData.putString("_localFolderPath",folderPath);

                if (eventData.containsKey("item")) {
                    final File file = new File(new File(folderPath), eventData.getString("item"));

                    eventData.putString("_localItemPath", file.getPath());
                }
            }
        }

        Intent broadcastIntent =
                new Intent(EVENT_BASE_ACTION + "." + eventType.toUpperCase(Locale.US));
        broadcastIntent.putExtras(eventData);
        mLocalBM.sendBroadcast(broadcastIntent);

        Log.d(TAG, "Sent local event broadcast " + broadcastIntent.getAction() +
                " including " + eventType.length() + " extra data items.");
    }

    /**
     * @see RestApi.OnReceiveEventListener
     */
    @Override
    public void onDone(long id) {
        if (mLastEventId < id) {
            mLastEventId = id;

            // Store the last EventId in case we get killed
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

            //noinspection CommitPrefEdits
            sp.edit().putLong(PREF_LAST_SYNC_ID, mLastEventId).commit();
        }

        synchronized (mMainThreadHandler) {
            if (!mShutdown) {
                mMainThreadHandler.removeCallbacks(this);
                mMainThreadHandler.postDelayed(this, EVENT_UPDATE_INTERVAL);
            }
        }
    }

    /**
     * @see SyncthingService.OnWebGuiAvailableListener
     */
    @Override
    public void onWebGuiAvailable() {
        Log.d(TAG, "WebGUI available. Starting event processor.");

        updateFolderMap();

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
}
