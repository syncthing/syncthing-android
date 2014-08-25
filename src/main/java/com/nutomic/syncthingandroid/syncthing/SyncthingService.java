package com.nutomic.syncthingandroid.syncthing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.util.ConfigXml;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

    private static final String TAG = "SyncthingService";

    private static final int NOTIFICATION_RUNNING = 1;

    /**
     * Intent action to perform a syncthing restart.
     */
    public static final String ACTION_RESTART = "restart";

    /**
     * Interval in ms at which the GUI is updated (eg {@link }LocalNodeInfoFragment}).
     */
    public static final int GUI_UPDATE_INTERVAL = 1000;

    /**
     * Name of the public key file in the data directory.
     */
    private static final String PUBLIC_KEY_FILE = "cert.pem";

    /**
     * Name of the private key file in the data directory.
     */
    private static final String PRIVATE_KEY_FILE = "key.pem";

    /**
     * Path to the native, integrated syncthing binary, relative to the data folder
     */
    public static final String BINARY_NAME = "lib/libsyncthing.so";

    private ConfigXml mConfig;

    private RestApi mApi;

    private SyncthingRunnable mSyncthingRunnable;

    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    /**
     * Callback for when the Syncthing web interface becomes first available after service start.
     */
    public interface OnWebGuiAvailableListener {
        public void onWebGuiAvailable();
    }

    private final HashSet<OnWebGuiAvailableListener> mOnWebGuiAvailableListeners =
            new HashSet<>();

    public interface OnApiChangeListener {
        public void onApiChange(State currentState);
    }

    private final HashSet<WeakReference<OnApiChangeListener>> mOnApiChangeListeners =
            new HashSet<>();

    /**
     * INIT: Service is starting up and initializing.
     * STARTING: Syncthing binary is starting (but the API is not yet ready).
     * ACTIVE: Syncthing binary is up and running.
     * DISABLED: Syncthing binary is stopped according to user preferences.
     */
    public enum State {
        INIT,
        STARTING,
        ACTIVE,
        DISABLED
    }

    private State mCurrentState = State.INIT;

    /**
     * True if a stop was requested while syncthing is starting, in that case, perform stop in
     * {@link PollWebGuiAvailableTaskImpl}.
     */
    private boolean mStopScheduled = false;

    private DeviceStateHolder mDeviceStateHolder;

    /**
     * Handles intents, either {@link #ACTION_RESTART}, or intents having
     * {@link DeviceStateHolder#EXTRA_HAS_WIFI} or {@link DeviceStateHolder#EXTRA_IS_CHARGING}
     * (which are handled by {@link DeviceStateHolder}.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Just catch the empty intent and return.
        if (intent == null) {
        } else if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            new PostTask() {
                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    new StartupTask().execute();
                }
            }.execute(mConfig.getWebGuiUrl(), PostTask.URI_RESTART, mSyncthingRunnable.getApiKey());
        } else if (mCurrentState != State.INIT) {
            mDeviceStateHolder.update(intent);
            updateState();
        }
        return START_STICKY;
    }

    /**
     * Checks according to preferences and charging/wifi state, whether syncthing should be enabled
     * or not.
     * <p/>
     * Depending on the result, syncthing is started or stopped, and {@link #onApiChange()} is
     * called.
     */
    public void updateState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean prefStopMobileData = prefs.getBoolean("stop_sync_on_mobile_data", true);
        boolean prefStopNotCharging = prefs.getBoolean("stop_sync_while_not_charging", true);

        // Start syncthing.
        if ((mDeviceStateHolder.isCharging() || !prefStopNotCharging) &&
                (mDeviceStateHolder.isWifiConnected() || !prefStopMobileData)) {
            if (mCurrentState == State.ACTIVE || mCurrentState == State.STARTING) {
                mStopScheduled = false;
                return;
            }

            mCurrentState = State.STARTING;
            registerOnWebGuiAvailableListener(mApi);
            new PollWebGuiAvailableTaskImpl().execute(mConfig.getWebGuiUrl());
            mSyncthingRunnable =
                    new SyncthingRunnable(this, getApplicationInfo().dataDir + "/" + BINARY_NAME);
            mApi.setApiKey(mSyncthingRunnable.getApiKey());
            new Thread(mSyncthingRunnable).start();
        }
        // Stop syncthing.
        else {
            if (mCurrentState == State.DISABLED)
                return;

            mCurrentState = State.DISABLED;

            // Syncthing is currently started, perform the stop later.
            if (mCurrentState == State.STARTING) {
                mStopScheduled = true;
            } else if (mApi != null) {
                mApi.shutdown();
            }
        }
        onApiChange();
    }

    /**
     * Move config file, keys, and index files to "official" folder
     * <p/>
     * Intended to bring the file locations in older installs in line with
     * newer versions.
     */
    private void moveConfigFiles() {
        FilenameFilter idxFilter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".idx.gz");
            }
        };

        if (new File(getApplicationInfo().dataDir, PUBLIC_KEY_FILE).exists()) {
            File publicKey = new File(getApplicationInfo().dataDir, PUBLIC_KEY_FILE);
            publicKey.renameTo(new File(getFilesDir(), PUBLIC_KEY_FILE));
            File privateKey = new File(getApplicationInfo().dataDir, PRIVATE_KEY_FILE);
            privateKey.renameTo(new File(getFilesDir(), PRIVATE_KEY_FILE));
            File config = new File(getApplicationInfo().dataDir, ConfigXml.CONFIG_FILE);
            config.renameTo(new File(getFilesDir(), ConfigXml.CONFIG_FILE));

            File oldStorageDir = new File(getApplicationInfo().dataDir);
            File[] files = oldStorageDir.listFiles(idxFilter);
            for (File file : files) {
                if (file.isFile()) {
                    file.renameTo(new File(getFilesDir(), file.getName()));
                }
            }
        }
    }

    /**
     * Creates notification, starts native binary.
     */
    @Override
    public void onCreate() {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        n.flags |= Notification.FLAG_ONGOING_EVENT;
        startForeground(NOTIFICATION_RUNNING, n);

        mDeviceStateHolder = new DeviceStateHolder(SyncthingService.this);
        registerReceiver(mDeviceStateHolder, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        new StartupTask().execute();
    }

    /**
     * Sets up the initial configuration, updates the config when coming from an old
     * version, and reads syncthing URL and API key (these are passed internally as
     * {@code Pair<String, String>}. TODO
     */
    private class StartupTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            moveConfigFiles();
            mConfig = new ConfigXml(SyncthingService.this);
            mConfig.updateIfNeeded();

            if (isFirstStart()) {
                Log.i(TAG, "App started for the first time. " +
                        "Copying default config, keys will be generated automatically");
                mConfig.createCameraRepo();
            }

            return mConfig.getWebGuiUrl();
        }

        @Override
        protected void onPostExecute(String webGuiUrl) {
            mApi = new RestApi(SyncthingService.this, webGuiUrl,
                    new RestApi.OnApiAvailableListener() {
                @Override
                public void onApiAvailable() {
                    onApiChange();
                }
            });
            registerOnWebGuiAvailableListener(mApi);
            Log.i(TAG, "Web GUI will be available at " + mConfig.getWebGuiUrl());

            // HACK: Make sure there is no syncthing binary left running from an improper
            // shutdown (eg Play Store updateIfNeeded).
            // NOTE: This will log an exception if syncthing is not actually running.
            mApi.shutdown();
            updateState();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Stops the native binary.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Shutting down service");
        mApi.shutdown();
    }

    /**
     * Register a listener for the web gui becoming available..
     * <p/>
     * If the web gui is already available, listener will be called immediately.
     * Listeners are unregistered automatically after being called.
     */
    public void registerOnWebGuiAvailableListener(OnWebGuiAvailableListener listener) {
        if (mCurrentState == State.ACTIVE) {
            listener.onWebGuiAvailable();
        } else {
            mOnWebGuiAvailableListeners.add(listener);
        }
    }

    /**
     * Returns true if this service has not been started before (ie config.xml does not exist).
     * <p/>
     * This will return true until the public key file has been generated.
     */
    public boolean isFirstStart() {
        return !new File(getFilesDir(), PUBLIC_KEY_FILE).exists();
    }

    public RestApi getApi() {
        return mApi;
    }

    /**
     * Register a listener for the syncthing API state changing.
     * <p/>
     * The listener is called immediately with the current state, and again whenever the state
     * changes.
     */
    public void registerOnApiChangeListener(OnApiChangeListener listener) {
        // Make sure we don't send an invalid state or syncthing might shwow a "disabled" message
        // when it's just starting up.
        listener.onApiChange(mCurrentState);
        mOnApiChangeListeners.add(new WeakReference<OnApiChangeListener>(listener));
    }

    private class PollWebGuiAvailableTaskImpl extends PollWebGuiAvailableTask {
        @Override
        protected void onPostExecute(Void aVoid) {
            if (mStopScheduled) {
                mCurrentState = State.DISABLED;
                onApiChange();
                mApi.shutdown();
                mStopScheduled = false;
                return;
            }
            Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
            mCurrentState = State.ACTIVE;
            for (OnWebGuiAvailableListener listener : mOnWebGuiAvailableListeners) {
                listener.onWebGuiAvailable();
            }
            mOnWebGuiAvailableListeners.clear();
        }
    }

    /**
     * Called to notifiy listeners of an API change.
     *
     * Must only be called from SyncthingService or {@link RestApi}.
     */
    private void onApiChange() {
        for (Iterator<WeakReference<OnApiChangeListener>> i = mOnApiChangeListeners.iterator();
             i.hasNext(); ) {
            WeakReference<OnApiChangeListener> listener = i.next();
            if (listener.get() != null) {
                listener.get().onApiChange(mCurrentState);
            } else {
                i.remove();
            }
        }
    }

    /**
     * Dialog to be shown when attempting to start syncthing while it is disabled according
     * to settings (because the device is not charging or wifi is disconnected).
     */
    public static void showDisabledDialog(final Activity activity) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.syncthing_disabled_title)
                .setMessage(R.string.syncthing_disabled_message)
                .setPositiveButton(R.string.syncthing_disabled_change_settings,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                activity.finish();
                                Intent intent = new Intent(activity, SettingsActivity.class)
                                        .setAction(SettingsActivity.ACTION_APP_SETTINGS_FRAGMENT);
                                activity.startActivity(intent);
                            }
                        }
                )
                .setNegativeButton(R.string.exit,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                activity.finish();
                            }
                        }
                )
                .show()
                .setCancelable(false);
    }

    public String getWebGuiUrl() {
        return mConfig.getWebGuiUrl();
    }

}
