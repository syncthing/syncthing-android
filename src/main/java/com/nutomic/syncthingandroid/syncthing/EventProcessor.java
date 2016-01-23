package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

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
            public void onEvent(long id, String eventType, JSONObject data) throws JSONException {

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
    public void onEvent(long id, String type, JSONObject data) throws JSONException {
        switch (type) {
            case "ItemFinished":
                File updatedFile = new File(data.getString("folderpath"), data.getString("item"));
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
            sp.edit().putLong(PREF_LAST_SYNC_ID, mLastEventId).commit();
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
}
