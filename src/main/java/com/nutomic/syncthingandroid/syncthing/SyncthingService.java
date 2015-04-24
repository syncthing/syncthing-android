package com.nutomic.syncthingandroid.syncthing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.FolderObserver;
import com.nutomic.syncthingandroid.util.PRNGFixes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

    private static final String TAG = "SyncthingService";

    /**
     * Intent action to perform a syncthing restart.
     */
    public static final String ACTION_RESTART = "restart";

    /**
     * Interval in ms at which the GUI is updated (eg {@link com.nutomic.syncthingandroid.fragments.DrawerFragment}).
     */
    public static final int GUI_UPDATE_INTERVAL = 1000;

    /**
     * Name of the public key file in the data directory.
     */
    public static final String PUBLIC_KEY_FILE = "cert.pem";

    /**
     * Name of the private key file in the data directory.
     */
    public static final String PRIVATE_KEY_FILE = "key.pem";

    /**
     * Directory where config is exported to and imported from.
     */
    public static final File EXPORT_PATH =
            new File(Environment.getExternalStorageDirectory(), "backups/syncthing");

    /**
     * Path to the native, integrated syncthing binary, relative to the data folder
     */
    public static final String BINARY_NAME = "lib/libsyncthing.so";

    public static final String PREF_ALWAYS_RUN_IN_BACKGROUND = "always_run_in_background";

    public static final String PREF_SYNC_ONLY_WIFI = "sync_only_wifi";

    public static final String PREF_SYNC_ONLY_CHARGING = "sync_only_charging";

    private static final int NOTIFICATION_ACTIVE = 1;

    private ConfigXml mConfig;

    private RestApi mApi;

    private LinkedList<FolderObserver> mObservers = new LinkedList<>();

    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    private Handler mMainHandler;

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
        if (intent == null)
            return START_STICKY;

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            mApi.shutdown();
            mCurrentState = State.INIT;
            updateState();
        }
        else if (mCurrentState != State.INIT) {
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
        boolean shouldRun;
        if (!alwaysRunInBackground(this)) {
            // Always run, ignoring wifi/charging state.
            shouldRun = true;
        }
        else {
            // Check wifi/charging state against preferences and start if ok.
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean prefStopMobileData = prefs.getBoolean(PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = prefs.getBoolean(PREF_SYNC_ONLY_CHARGING, false);

            shouldRun = (mDeviceStateHolder.isCharging() || !prefStopNotCharging) &&
                    (mDeviceStateHolder.isWifiConnected() || !prefStopMobileData);
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Start syncthing.
        if (shouldRun) {
            if (mCurrentState == State.ACTIVE || mCurrentState == State.STARTING) {
                mStopScheduled = false;
                return;
            }

            // HACK: Make sure there is no syncthing binary left running from an improper
            // shutdown (eg Play Store update).
            // NOTE: This will log an exception if syncthing is not actually running.
            mApi.shutdown();

            Log.i(TAG, "Starting syncthing according to current state and preferences");
            mConfig = new ConfigXml(SyncthingService.this);
            mCurrentState = State.STARTING;
            registerOnWebGuiAvailableListener(mApi);
            new PollWebGuiAvailableTaskImpl().execute(mConfig.getWebGuiUrl());
            new Thread(new SyncthingRunnable(
                    this, getApplicationInfo().dataDir + "/" + BINARY_NAME)).start();
            Notification n = new NotificationCompat.Builder(this)
                    .setContentTitle(getString(R.string.syncthing_active))
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setContentIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class), 0))
                    .build();
            nm.notify(NOTIFICATION_ACTIVE, n);
        }
        // Stop syncthing.
        else {
            if (mCurrentState == State.DISABLED)
                return;

            Log.i(TAG, "Stopping syncthing according to current state and preferences");
            mCurrentState = State.DISABLED;

            if (mApi != null) {
                mApi.shutdown();
                for (FolderObserver ro : mObservers) {
                    ro.stopWatching();
                }
                mObservers.clear();
            }
            nm.cancel(NOTIFICATION_ACTIVE);
        }
        onApiChange();
    }

    /**
     * Starts the native binary.
     */
    @Override
    public void onCreate() {
        PRNGFixes.apply();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);

        // Make sure this is also done for existing installs. We can replace this check with
        // {@link #isFirstStart()} after a while.
        if (!sp.getBoolean("default_user_pw_set", false)) {
            sp.edit().putBoolean("default_user_pw_set", true).commit();
            char[] chars =
                    "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
            StringBuilder sb = new StringBuilder();
            SecureRandom random = new SecureRandom();
            for (int i = 0; i < 20; i++)
                sb.append(chars[random.nextInt(chars.length)]);

            String user = Build.MODEL.replaceAll("[^a-zA-Z0-9 ]", "");
            Log.i(TAG, "Generated GUI username and password (username is " + user + ")");
            sp.edit().putString("gui_user", user)
                     .putString("gui_password", sb.toString()).commit();
        }

        mMainHandler = new Handler();
        mDeviceStateHolder = new DeviceStateHolder(SyncthingService.this);
        registerReceiver(mDeviceStateHolder, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        new StartupTask().execute();
    }

    /**
     * Sets up the initial configuration, updates the config when coming from an old
     * version, and reads syncthing URL and API key (these are passed internally as
     * {@code Pair<String, String>}.
     */
    private class StartupTask extends AsyncTask<Void, Void, Pair<String, String>> {
        @Override
        protected Pair<String, String> doInBackground(Void... voids) {
            mConfig = new ConfigXml(SyncthingService.this);
            return new Pair<>(mConfig.getWebGuiUrl(), mConfig.getApiKey());
        }

        @Override
        protected void onPostExecute(Pair<String, String> urlAndKey) {
            mApi = new RestApi(SyncthingService.this, urlAndKey.first, urlAndKey.second,
                    new RestApi.OnApiAvailableListener() {
                @Override
                public void onApiAvailable() {
                    onApiChange();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            for (RestApi.Folder r : mApi.getFolders()) {
                                try {
                                    mObservers.add(new FolderObserver(mApi, r));
                                } catch (FolderObserver.FolderNotExistingException e) {
                                    Log.w(TAG, "Failed to add observer for folder", e);
                                }
                            }
                        }
                    }).start();
                }
            });
            registerOnWebGuiAvailableListener(mApi);
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
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Shutting down service");
        if (mApi != null) {
            mApi.shutdown();
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ACTIVE);
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
     * @see {@link #unregisterOnApiChangeListener}
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
     * @see {@link #registerOnApiChangeListener}
     */
    public void unregisterOnApiChangeListener(OnApiChangeListener listener) {
        mOnApiChangeListeners.remove(listener);
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
     * Must only be called from SyncthingService or {@link RestApi}.
     */
    private void onApiChange() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
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
        });
    }

    /**
     * Dialog to be shown when attempting to start syncthing while it is disabled according
     * to settings (because the device is not charging or wifi is disconnected).
     */
    public static AlertDialog showDisabledDialog(final Activity activity) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
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
                .show();
        dialog.setCancelable(false);
        return dialog;
    }

    public String getWebGuiUrl() {
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
        copyFile(new File(getFilesDir(), ConfigXml.CONFIG_FILE),
                new File(EXPORT_PATH, ConfigXml.CONFIG_FILE));
        copyFile(new File(getFilesDir(), PRIVATE_KEY_FILE),
                new File(EXPORT_PATH, PRIVATE_KEY_FILE));
        copyFile(new File(getFilesDir(), PUBLIC_KEY_FILE),
                new File(EXPORT_PATH, PUBLIC_KEY_FILE));
    }

    /**
     * Imports config and keys from {@link #EXPORT_PATH}.
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        File config = new File(EXPORT_PATH, ConfigXml.CONFIG_FILE);
        File privateKey = new File(EXPORT_PATH, PRIVATE_KEY_FILE);
        File publicKey = new File(EXPORT_PATH, PUBLIC_KEY_FILE);
        if (!config.exists() || !privateKey.exists() || !publicKey.exists())
            return false;

        copyFile(config, new File(getFilesDir(), ConfigXml.CONFIG_FILE));
        copyFile(privateKey, new File(getFilesDir(), PRIVATE_KEY_FILE));
        copyFile(publicKey, new File(getFilesDir(), PUBLIC_KEY_FILE));

        startService(new Intent(this, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESTART));
        return true;
    }

    /**
     * Copies files between different storage devices.
     */
    private void copyFile(File source, File dest) {
        FileChannel is = null;
        FileChannel os = null;
        try {
            is = new FileInputStream(source).getChannel();
            os = new FileOutputStream(dest).getChannel();
            is.transferTo(0, is.size(), os);
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy file", e);
        } finally {
            try {
                if (is != null)
                  is.close();
                if (os != null)
                  os.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close stream", e);
            }
        }
    }


}
