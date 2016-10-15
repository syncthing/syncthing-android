package com.nutomic.syncthingandroid.syncthing;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.http.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.FolderObserver;
import com.android.PRNGFixes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SyncthingService";

    /**
     * Intent action to perform a Syncthing restart.
     */
    public static final String ACTION_RESTART =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESTART";

    /**
     * Intent action to reset Syncthing's database.
     */
    public static final String ACTION_RESET =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESET";

    /**
     * Interval in ms at which the GUI is updated (eg {@link com.nutomic.syncthingandroid.fragments.DrawerFragment}).
     */
    public static final long GUI_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    /**
     * name of the public key file in the data directory.
     */
    public static final String PUBLIC_KEY_FILE = "cert.pem";

    /**
     * name of the private key file in the data directory.
     */
    public static final String PRIVATE_KEY_FILE = "key.pem";

    /**
     * name of the public HTTPS CA file in the data directory.
     */
    public static final String HTTPS_CERT_FILE = "https-cert.pem";

    /**
     * Directory where config is exported to and imported from.
     */
    public static final File EXPORT_PATH =
            new File(Environment.getExternalStorageDirectory(), "backups/syncthing");

    /**
     * path to the native, integrated syncthing binary, relative to the data folder
     */
    public static final String BINARY_NAME = "lib/libsyncthing.so";

    public static final String PREF_ALWAYS_RUN_IN_BACKGROUND = "always_run_in_background";
    public static final String PREF_SYNC_ONLY_WIFI           = "sync_only_wifi";
    public static final String PREF_SYNC_ONLY_WIFI_SSIDS     = "sync_only_wifi_ssids_set";
    public static final String PREF_SYNC_ONLY_CHARGING       = "sync_only_charging";
    public static final String PREF_USE_ROOT                 = "use_root";
    private static final String PREF_NOTIFICATION_TYPE       = "notification_type";
    public static final String PREF_USE_WAKE_LOCK            = "wakelock_while_binary_running";
    public static final String PREF_FOREGROUND_SERVICE       = "run_as_foreground_service";

    private static final int NOTIFICATION_ACTIVE = 1;

    private ConfigXml mConfig;

    private RestApi mApi;

    private EventProcessor mEventProcessor;

    private final LinkedList<FolderObserver> mObservers = new LinkedList<>();

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

    private final HashSet<OnApiChangeListener> mOnApiChangeListeners =
            new HashSet<>();

    private final BroadcastReceiver mPowerSaveModeChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateState();
        }
    };

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
        DISABLED,
        ERROR
    }

    private State mCurrentState = State.INIT;

    /**
     * Object that can be locked upon when accessing mCurrentState
     * Currently used to male onDestroy() and PollWebGuiAvailableTaskImpl.onPostExcecute() tread-safe
     */
    private final Object stateLock = new Object();

    /**
     * True if a stop was requested while syncthing is starting, in that case, perform stop in
     * {@link PollWebGuiAvailableTaskImpl}.
     */
    private boolean mStopScheduled = false;

    private DeviceStateHolder mDeviceStateHolder;

    private SyncthingRunnable mRunnable;

    /**
     * Handles intents, either {@link #ACTION_RESTART}, or intents having
     * {@link DeviceStateHolder#EXTRA_HAS_WIFI} or {@link DeviceStateHolder#EXTRA_IS_CHARGING}
     * (which are handled by {@link DeviceStateHolder}.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown();
            mCurrentState = State.INIT;
            updateState();
        } else if (ACTION_RESET.equals(intent.getAction())) {
            shutdown();
            new SyncthingRunnable(this, SyncthingRunnable.Command.reset).run();
            mCurrentState = State.INIT;
            updateState();
        } else if (mCurrentState != State.INIT) {
            mDeviceStateHolder.update(intent);
            updateState();
        }
        return START_STICKY;
    }

    /**
     * Checks according to preferences and charging/wifi state, whether syncthing should be enabled
     * or not.
     *
     * Depending on the result, syncthing is started or stopped, and {@link #onApiChange()} is
     * called.
     */
    public void updateState() {
        // Start syncthing.
        if (mDeviceStateHolder.shouldRun()) {
            if (mCurrentState == State.ACTIVE || mCurrentState == State.STARTING) {
                mStopScheduled = false;
                return;
            }

            // HACK: Make sure there is no syncthing binary left running from an improper
            // shutdown (eg Play Store update).
            // NOTE: This will log an exception if syncthing is not actually running.
            shutdown();

            Log.i(TAG, "Starting syncthing according to current state and preferences");
            mConfig = null;
            try {
                mConfig = new ConfigXml(SyncthingService.this);
            } catch (ConfigXml.OpenConfigException e) {
                mCurrentState = State.ERROR;
                Toast.makeText(this, R.string.config_create_failed, Toast.LENGTH_LONG).show();
            }

            if (mConfig != null) {
                mCurrentState = State.STARTING;

                if (mApi != null)
                    registerOnWebGuiAvailableListener(mApi);
                if (mEventProcessor != null)
                    registerOnWebGuiAvailableListener(mEventProcessor);
                new PollWebGuiAvailableTaskImpl(getWebGuiUrl(), getFilesDir() + "/" + HTTPS_CERT_FILE, mConfig.getApiKey())
                        .execute();
                mRunnable = new SyncthingRunnable(this, SyncthingRunnable.Command.main);
                new Thread(mRunnable).start();
                updateNotification();
            }
        }
        // Stop syncthing.
        else {
            if (mCurrentState == State.DISABLED)
                return;

            Log.i(TAG, "Stopping syncthing according to current state and preferences");
            mCurrentState = State.DISABLED;

            shutdown();
        }
        onApiChange();
    }

    /**
     * Shows or hides the persistent notification based on running state and
     * {@link #PREF_NOTIFICATION_TYPE}.
     */
    private void updateNotification() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String type = sp.getString(PREF_NOTIFICATION_TYPE, "low_priority");
        boolean foreground = sp.getBoolean(PREF_FOREGROUND_SERVICE, false);
        if ("none".equals(type) && foreground) {
            // foreground priority requires any notification
            // so this ensures that we either have a "default" or "low_priority" notification,
            // but not "none".
            type = "low_priority";
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if ((mCurrentState == State.ACTIVE || mCurrentState == State.STARTING) &&
                !type.equals("none")) {
            Context appContext = getApplicationContext();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext)
                    .setContentTitle(getString(R.string.syncthing_active))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setOngoing(true)
                    .setContentIntent(PendingIntent.getActivity(appContext, 0,
                            new Intent(appContext, MainActivity.class), 0));
            if (type.equals("low_priority"))
                builder.setPriority(NotificationCompat.PRIORITY_MIN);

            if (foreground) {
                builder.setContentText(getString(R.string.syncthing_active_foreground));
                startForeground(NOTIFICATION_ACTIVE, builder.build());
            } else {
                stopForeground(false); // ensure no longer running with foreground priority
                nm.notify(NOTIFICATION_ACTIVE, builder.build());
            }
        } else {
            // ensure no longer running with foreground priority
            stopForeground(false);
            nm.cancel(NOTIFICATION_ACTIVE);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_NOTIFICATION_TYPE) || key.equals(PREF_FOREGROUND_SERVICE))
            updateNotification();
        else if (key.equals(PREF_SYNC_ONLY_CHARGING) || key.equals(PREF_SYNC_ONLY_WIFI)
                || key.equals(PREF_SYNC_ONLY_WIFI_SSIDS))
            updateState();
    }

    /**
     * Starts the native binary.
     */
    @Override
    @TargetApi(21)
    public void onCreate() {
        super.onCreate();
        PRNGFixes.apply();

        mDeviceStateHolder = new DeviceStateHolder(SyncthingService.this);
        registerReceiver(mDeviceStateHolder, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            registerReceiver(mPowerSaveModeChangedReceiver,
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        }
        new StartupTask().execute();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Sets up the initial configuration, updates the config when coming from an old
     * version, and reads syncthing URL and API key (these are passed internally as
     * {@code Pair<String, String>}.
     */
    private class StartupTask extends AsyncTask<Void, Void, Pair<URL, String>> {

        @Override
        protected Pair<URL, String> doInBackground(Void... voids) {
            try {
                mConfig = new ConfigXml(SyncthingService.this);
                return new Pair<>(mConfig.getWebGuiUrl(), mConfig.getApiKey());
            } catch (ConfigXml.OpenConfigException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Pair<URL, String> urlAndKey) {
            if (urlAndKey == null) {
                Toast.makeText(SyncthingService.this, R.string.config_create_failed,
                        Toast.LENGTH_LONG).show();
                mCurrentState = State.ERROR;
                onApiChange();
                return;
            }

            mApi = new RestApi(SyncthingService.this, urlAndKey.first, urlAndKey.second,
                    () -> {
                        mCurrentState = State.ACTIVE;
                        onApiChange();
                        new Thread(() -> {
                            for (Folder r : mApi.getFolders()) {
                                try {
                                    mObservers.add(new FolderObserver(mApi, r));
                                } catch (FolderObserver.FolderNotExistingException e) {
                                    Log.w(TAG, "Failed to add observer for folder", e);
                                } catch (StackOverflowError e) {
                                    Log.w(TAG, "Failed to add folder observer", e);
                                    Toast.makeText(SyncthingService.this,
                                            R.string.toast_folder_observer_stack_overflow,
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        }).start();
                    }, SyncthingService.this::onApiChange);

            mEventProcessor = new EventProcessor(SyncthingService.this, mApi);

            registerOnWebGuiAvailableListener(mApi);
            registerOnWebGuiAvailableListener(mEventProcessor);
            Log.i(TAG, "Web GUI will be available at " + mConfig.getWebGuiUrl());
            updateState();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Stops the native binary.
     *
     * The native binary crashes if stopped before it is fully active. In that case signal the
     * stop request to PollWebGuiAvailableTaskImpl that is active in that situation and terminate
     * the service there.
     */
    @Override
    public void onDestroy() {

        synchronized (stateLock) {
            if (mCurrentState == State.INIT || mCurrentState == State.STARTING) {
                Log.i(TAG, "Delay shutting down service until initialisation of Syncthing finished");
                mStopScheduled = true;

            } else {
                Log.i(TAG, "Shutting down service immediately");
                shutdown();
            }
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB)
            unregisterReceiver(mPowerSaveModeChangedReceiver);
    }

    private void shutdown() {
        if (mEventProcessor != null)
            mEventProcessor.shutdown();

        if (mRunnable != null)
            mRunnable.killSyncthing();

        if (mApi != null)
            mApi.shutdown();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        stopForeground(false);
        nm.cancel(NOTIFICATION_ACTIVE);

        for (FolderObserver ro : mObservers) {
            ro.stopWatching();
        }
        mObservers.clear();
    }

    /**
     * Register a listener for the web gui becoming available..
     *
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
     *
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
     *
     * The listener is called immediately with the current state, and again whenever the state
     * changes. The call is always from the GUI thread.
     *
     * @see #unregisterOnApiChangeListener
     */
    public void registerOnApiChangeListener(OnApiChangeListener listener) {
        // Make sure we don't send an invalid state or syncthing might show a "disabled" message
        // when it's just starting up.
        listener.onApiChange(mCurrentState);
        mOnApiChangeListeners.add(listener);
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @see #registerOnApiChangeListener
     */
    public void unregisterOnApiChangeListener(OnApiChangeListener listener) {
        mOnApiChangeListeners.remove(listener);
    }

    private class PollWebGuiAvailableTaskImpl extends PollWebGuiAvailableTask {

        public PollWebGuiAvailableTaskImpl(URL url, String httpsCertPath, String apiKey) {
            super(url, httpsCertPath, apiKey);
        }

        /**
         * Wait for the web-gui of the native syncthing binary to come online.
         *
         * In case the binary is to be stopped, also be aware that another thread could request
         * to stop the binary in the time while waiting for the GUI to become active. See the comment
         * for SyncthingService.onDestroy for details.
         */
        @Override
        protected void onPostExecute(Void aVoid) {
            synchronized (stateLock) {
                if (mStopScheduled) {
                    mCurrentState = State.DISABLED;
                    onApiChange();
                    shutdown();
                    mStopScheduled = false;
                    stopSelf();
                    return;
                }
            }
            Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
            mCurrentState = State.STARTING;
            onApiChange();
            for (OnWebGuiAvailableListener listener : mOnWebGuiAvailableListeners) {
                listener.onWebGuiAvailable();
            }
            mOnWebGuiAvailableListeners.clear();
        }
    }

    /**
     * Called to notifiy listeners of an API change.
     *
     * Must only be called from SyncthingService or {@link RestApi} on the main thread.
     */
    private void onApiChange() {
        for (Iterator<OnApiChangeListener> i = mOnApiChangeListeners.iterator();
             i.hasNext(); ) {
            OnApiChangeListener listener = i.next();
            if (listener != null) {
                listener.onApiChange(mCurrentState);
            } else {
                i.remove();
            }
        }
    }

    public URL getWebGuiUrl() {
        return mConfig.getWebGuiUrl();
    }

    /**
     * Returns the value of "always_run_in_background" preference.
     */
    public static boolean alwaysRunInBackground(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(PREF_ALWAYS_RUN_IN_BACKGROUND, false);
    }

    /**
     * Exports the local config and keys to {@link #EXPORT_PATH}.
     */
    public void exportConfig() {
        EXPORT_PATH.mkdirs();
        try {
            Files.copy(new File(getFilesDir(), ConfigXml.CONFIG_FILE),
                    new File(EXPORT_PATH, ConfigXml.CONFIG_FILE));
            Files.copy(new File(getFilesDir(), PRIVATE_KEY_FILE),
                    new File(EXPORT_PATH, PRIVATE_KEY_FILE));
            Files.copy(new File(getFilesDir(), PUBLIC_KEY_FILE),
                    new File(EXPORT_PATH, PUBLIC_KEY_FILE));
        } catch (IOException e) {
            Log.w(TAG, "Failed to export config", e);
        }
    }

    /**
     * Imports config and keys from {@link #EXPORT_PATH}.
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        mCurrentState = State.DISABLED;
        shutdown();
        File config = new File(EXPORT_PATH, ConfigXml.CONFIG_FILE);
        File privateKey = new File(EXPORT_PATH, PRIVATE_KEY_FILE);
        File publicKey = new File(EXPORT_PATH, PUBLIC_KEY_FILE);
        if (!config.exists() || !privateKey.exists() || !publicKey.exists())
            return false;

        try {
            Files.copy(config, new File(getFilesDir(), ConfigXml.CONFIG_FILE));
            Files.copy(privateKey, new File(getFilesDir(), PRIVATE_KEY_FILE));
            Files.copy(publicKey, new File(getFilesDir(), PUBLIC_KEY_FILE));
        } catch (IOException e) {
            Log.w(TAG, "Failed to import config", e);
        }
        mCurrentState = State.INIT;
        updateState();
        return true;
    }
}
