package com.nutomic.syncthingandroid.service;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.Manifest;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.android.PRNGFixes;
import com.annimon.stream.Stream;
import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.http.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import javax.inject.Inject;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

    private static final String TAG = "SyncthingService";

    /**
     * Intent action to perform a Syncthing restart.
     */
    public static final String ACTION_RESTART =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESTART";

    /**
     * Intent action to reset Syncthing's database.
     */
    public static final String ACTION_RESET_DATABASE =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESET_DATABASE";

    /**
     * Intent action to reset Syncthing's delta indexes.
     */
    public static final String ACTION_RESET_DELTAS =
            "com.nutomic.syncthingandroid.service.SyncthingService.RESET_DELTAS";

    public static final String ACTION_REFRESH_NETWORK_INFO =
            "com.nutomic.syncthingandroid.service.SyncthingService.REFRESH_NETWORK_INFO";

    /**
     * Callback for when the Syncthing web interface becomes first available after service start.
     */
    public interface OnWebGuiAvailableListener {
        void onWebGuiAvailable();
    }

    private final HashSet<OnWebGuiAvailableListener> mOnWebGuiAvailableListeners =
            new HashSet<>();

    public interface OnApiChangeListener {
        void onApiChange(State currentState);
    }

    /**
     * Indicates the current state of SyncthingService and of Syncthing itself.
     */
    public enum State {
        /** Service is initializing, Syncthing was not started yet. */
        INIT,
        /** Syncthing binary is starting. */
        STARTING,
        /** Syncthing binary is running, API is available. */
        ACTIVE,
        /** Syncthing is stopped according to user preferences. */
        DISABLED,
        /** There is some problem that prevents Syncthing from running. */
        ERROR,
    }

    /**
     * Initialize the service with State.DISABLED as {@link DeviceStateHolder} will
     * send an update if we should run the binary after it got instantiated in
     * {@link onStartCommand}.
     */
    private State mCurrentState = State.DISABLED;

    private ConfigXml mConfig;
    private RestApi mApi;
    private EventProcessor mEventProcessor;
    private @Nullable DeviceStateHolder mDeviceStateHolder = null;
    private SyncthingRunnable mSyncthingRunnable;
    private Handler mHandler;

    private final HashSet<OnApiChangeListener> mOnApiChangeListeners = new HashSet<>();
    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    @Inject NotificationHandler mNotificationHandler;
    @Inject SharedPreferences mPreferences;

    /**
     * Object that can be locked upon when accessing mCurrentState
     * Currently used to male onDestroy() and PollWebGuiAvailableTaskImpl.onPostExcecute() tread-safe
     */
    private final Object mStateLock = new Object();

    /**
     * Stores the result of the last should run decision received by OnDeviceStateChangedListener.
     */
    private boolean mLastDeterminedShouldRun = false;

    /**
     * True if a stop was requested while syncthing is starting, in that case, perform stop in
     * {@link #pollWebGui}.
     */
    private boolean mStopScheduled = false;

    /**
     * True if the user granted the storage permission.
     */
    private boolean mStoragePermissionGranted = false;

    /**
     * Starts the native binary.
     */
    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");
        super.onCreate();
        PRNGFixes.apply();
        ((SyncthingApp) getApplication()).component().inject(this);
        mHandler = new Handler();

        /**
         * If runtime permissions are revoked, android kills and restarts the service.
         * see issue: https://github.com/syncthing/syncthing-android/issues/871
         * We need to recheck if we still have the storage permission.
         */
        mStoragePermissionGranted = (ContextCompat.checkSelfPermission(this,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                                        PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Handles intents, either {@link #ACTION_RESTART}, or intents having
     * {@link DeviceStateHolder#EXTRA_IS_ALLOWED_NETWORK_CONNECTION} or
     * {@link DeviceStateHolder#EXTRA_IS_CHARGING} (which are handled by {@link DeviceStateHolder}.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");
        if (!mStoragePermissionGranted) {
            Log.e(TAG, "User revoked storage permission. Stopping service.");
            if (mNotificationHandler != null) {
                mNotificationHandler.showStoragePermissionRevokedNotification();
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        /**
         * Send current service state to listening endpoints.
         * This is required that components know about the service State.DISABLED
         * if DeviceStateHolder does not send a "shouldRun = true" callback
         * to start the binary according to preferences shortly after its creation.
         * See {@link mLastDeterminedShouldRun} defaulting to "false".
         */
        if (mCurrentState == State.DISABLED) {
            onApiChange(mCurrentState);
        }
        if (mDeviceStateHolder == null) {
            /**
             * Instantiate the run condition monitor on first onStartCommand and
             * enable callback on run condition change affecting the final decision to
             * run/terminate syncthing. After initial run conditions are collected
             * the first decision is sent to {@link onUpdatedShouldRunDecision}.
             */
            mDeviceStateHolder = new DeviceStateHolder(SyncthingService.this, this::onUpdatedShouldRunDecision);
        }
        mNotificationHandler.updatePersistentNotification(this);

        if (intent == null)
            return START_STICKY;

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown(State.INIT, () -> new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));
        } else if (ACTION_RESET_DATABASE.equals(intent.getAction())) {
            shutdown(State.INIT, () -> {
                new SyncthingRunnable(this, SyncthingRunnable.Command.resetdatabase).run();
                new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            });
        } else if (ACTION_RESET_DELTAS.equals(intent.getAction())) {
            shutdown(State.INIT, () -> {
                new SyncthingRunnable(this, SyncthingRunnable.Command.resetdeltas).run();
                new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            });
        } else if (ACTION_REFRESH_NETWORK_INFO.equals(intent.getAction())) {
            mDeviceStateHolder.updateShouldRunDecision();
        }
        return START_STICKY;
    }

    /**
     * After run conditions monitored by {@link DeviceStateHolder} changed and
     * it had an influence on the decision to run/terminate syncthing, this
     * function is called to notify this class to run/terminate the syncthing binary.
     * {@link #onApiChange} is called while applying the decision change.
     */
    private void onUpdatedShouldRunDecision(boolean newShouldRunDecision) {
        if (newShouldRunDecision != mLastDeterminedShouldRun) {
            Log.i(TAG, "shouldRun decision changed to " + newShouldRunDecision + " according to configured run conditions.");
            mLastDeterminedShouldRun = newShouldRunDecision;

            // React to the shouldRun condition change.
            if (newShouldRunDecision) {
                // Start syncthing.
                switch (mCurrentState) {
                    case DISABLED:
                    case INIT:
                        // HACK: Make sure there is no syncthing binary left running from an improper
                        // shutdown (eg Play Store update).
                        shutdown(State.INIT, () -> {
                            Log.v(TAG, "Starting syncthing");
                            new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        });
                        break;
                    case STARTING:
                    case ACTIVE:
                        mStopScheduled = false;
                        break;
                    case ERROR:
                    default:
                        break;
                }
            } else {
                // Stop syncthing.
                if (mCurrentState == State.DISABLED) {
                    return;
                }
                Log.v(TAG, "Stopping syncthing");
                shutdown(State.DISABLED, () -> {});
            }
        }
    }

    /**
     * Sets up the initial configuration, and updates the config when coming from an old
     * version.
     */
    private class StartupTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                mConfig = new ConfigXml(SyncthingService.this);
                mConfig.updateIfNeeded();
            } catch (ConfigXml.OpenConfigException e) {
                mNotificationHandler.showCrashedNotification(R.string.config_create_failed, true);
                synchronized (mStateLock) {
                    onApiChange(State.ERROR);
                }
                cancel(true);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mApi == null) {
                mApi = new RestApi(SyncthingService.this, mConfig.getWebGuiUrl(), mConfig.getApiKey(),
                                    SyncthingService.this::onApiAvailable, () -> onApiChange(mCurrentState));
                registerOnWebGuiAvailableListener(mApi);
                Log.i(TAG, "Web GUI will be available at " + mConfig.getWebGuiUrl());
            }
            synchronized (mStateLock) {
                onApiChange(State.STARTING);
                pollWebGui();
                mSyncthingRunnable = new SyncthingRunnable(SyncthingService.this, SyncthingRunnable.Command.main);
                new Thread(mSyncthingRunnable).start();
            }
        }
    }

    /**
     * Called when {@link pollWebGui} confirmed the REST API is available.
     * We can assume mApi being available under normal conditions.
     * UI stressing results in mApi getting null on simultaneous shutdown, so
     * we check it for safety.
     */
    private void onApiAvailable() {
        synchronized (mStateLock) {
            onApiChange(State.ACTIVE);
        }
        if (mApi == null) {
            Log.e(TAG, "onApiAvailable: Did we stop the binary during startup? mApi == null");
            return;
        }
        if (mEventProcessor == null) {
            mEventProcessor = new EventProcessor(SyncthingService.this, mApi);
        }
        mEventProcessor.start();
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
        Log.v(TAG, "onDestroy");
        if (mDeviceStateHolder != null) {
            /**
             * Shut down the OnDeviceStateChangedListener so we won't get interrupted by run
             * condition events that occur during shutdown.
             */
            mDeviceStateHolder.shutdown();
        }
        if (mStoragePermissionGranted) {
            synchronized (mStateLock) {
                if (mCurrentState == State.INIT || mCurrentState == State.STARTING) {
                    Log.i(TAG, "Delay shutting down synchting binary until initialisation finished");
                    mStopScheduled = true;
                } else {
                    Log.i(TAG, "Shutting down syncthing binary immediately");
                    shutdown(State.DISABLED, () -> {});
                }
            }
        } else {
            // If the storage permission got revoked, we did not start the binary and
            // are in State.INIT requiring an immediate shutdown of this service class.
            Log.i(TAG, "Shutting down syncthing binary due to missing storage permission.");
            shutdown(State.DISABLED, () -> {});
        }
        super.onDestroy();
    }

    /**
     * Stop Syncthing and all helpers like event processor and api handler.
     *
     * Sets {@link #mCurrentState} to newState, and calls onKilledListener once Syncthing is killed.
     */
    private void shutdown(State newState, SyncthingRunnable.OnSyncthingKilled onKilledListener) {
        Log.i(TAG, "Shutting down background service");
        onApiChange(newState);

        if (mEventProcessor != null) {
            mEventProcessor.stop();
            mEventProcessor = null;
        }

        if (mApi != null) {
            mApi.shutdown();
            mApi = null;
        }

        if (mNotificationHandler != null)
            mNotificationHandler.cancelPersistentNotification(this);

        if (mSyncthingRunnable != null) {
            mSyncthingRunnable.killSyncthing(onKilledListener);
            mSyncthingRunnable = null;
        } else {
            onKilledListener.onKilled();
        }
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

    public @Nullable RestApi getApi() {
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

    /**
     * Wait for the web-gui of the native syncthing binary to come online.
     *
     * In case the binary is to be stopped, also be aware that another thread could request
     * to stop the binary in the time while waiting for the GUI to become active. See the comment
     * for SyncthingService.onDestroy for details.
     */
    private void pollWebGui() {
        new PollWebGuiAvailableTask(this, getWebGuiUrl(), mConfig.getApiKey(), result -> {
            synchronized (mStateLock) {
                if (mStopScheduled) {
                    shutdown(State.DISABLED, () -> {});
                    mStopScheduled = false;
                    stopSelf();
                    return;
                }
            }
            Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
            Stream.of(mOnWebGuiAvailableListeners).forEach(OnWebGuiAvailableListener::onWebGuiAvailable);
            mOnWebGuiAvailableListeners.clear();
        });
    }

    /**
     * Called to notifiy listeners of an API change.
     */
    private void onApiChange(State newState) {
        Log.v(TAG, "onApiChange(" + newState + ") called.");
        mHandler.post(() -> {
            mCurrentState = newState;
            mNotificationHandler.updatePersistentNotification(this);
            for (Iterator<OnApiChangeListener> i = mOnApiChangeListeners.iterator();
                 i.hasNext(); ) {
                OnApiChangeListener listener = i.next();
                if (listener != null) {
                    listener.onApiChange(mCurrentState);
                } else {
                    i.remove();
                }
            }
        });
    }

    public URL getWebGuiUrl() {
        return mConfig.getWebGuiUrl();
    }

    public State getCurrentState() {
        return mCurrentState;
    }

    /**
     * Exports the local config and keys to {@link Constants#EXPORT_PATH}.
     */
    public void exportConfig() {
        Constants.EXPORT_PATH.mkdirs();
        try {
            Files.copy(Constants.getConfigFile(this),
                    new File(Constants.EXPORT_PATH, Constants.CONFIG_FILE));
            Files.copy(Constants.getPrivateKeyFile(this),
                    new File(Constants.EXPORT_PATH, Constants.PRIVATE_KEY_FILE));
            Files.copy(Constants.getPublicKeyFile(this),
                    new File(Constants.EXPORT_PATH, Constants.PUBLIC_KEY_FILE));
        } catch (IOException e) {
            Log.w(TAG, "Failed to export config", e);
        }
    }

    /**
     * Imports config and keys from {@link Constants#EXPORT_PATH}.
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        File config = new File(Constants.EXPORT_PATH, Constants.CONFIG_FILE);
        File privateKey = new File(Constants.EXPORT_PATH, Constants.PRIVATE_KEY_FILE);
        File publicKey = new File(Constants.EXPORT_PATH, Constants.PUBLIC_KEY_FILE);
        if (!config.exists() || !privateKey.exists() || !publicKey.exists())
            return false;
        shutdown(State.INIT, () -> {
            try {
                Files.copy(config, Constants.getConfigFile(this));
                Files.copy(privateKey, Constants.getPrivateKeyFile(this));
                Files.copy(publicKey, Constants.getPublicKeyFile(this));
            } catch (IOException e) {
                Log.w(TAG, "Failed to import config", e);
            }
            new StartupTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });
        return true;
    }
}
