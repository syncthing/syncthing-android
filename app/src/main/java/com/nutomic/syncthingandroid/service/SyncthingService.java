package com.nutomic.syncthingandroid.service;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.Manifest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.android.PRNGFixes;
import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.http.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.model.RunConditionCheckResult;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

/**
 * Holds the native syncthing instance and provides an API to access it.
 */
public class SyncthingService extends Service {

    private static final String TAG = "SyncthingService";

    private Boolean ENABLE_VERBOSE_LOG = false;

    /**
     * Intent action to perform a Syncthing restart.
     */
    public static final String ACTION_RESTART =
            "com.github.catfriend1.syncthingandroid.SyncthingService.RESTART";

    /**
     * Intent action to perform a Syncthing stop.
     */
    public static final String ACTION_STOP =
            "com.github.catfriend1.syncthingandroid.SyncthingService.STOP";

    /**
     * Intent action to reset Syncthing's database.
     */
    public static final String ACTION_RESET_DATABASE =
            "com.github.catfriend1.syncthingandroid.SyncthingService.RESET_DATABASE";

    /**
     * Intent action to reset Syncthing's delta indexes.
     */
    public static final String ACTION_RESET_DELTAS =
            "com.github.catfriend1.syncthingandroid.SyncthingService.RESET_DELTAS";

    public static final String ACTION_REFRESH_NETWORK_INFO =
            "com.github.catfriend1.syncthingandroid.SyncthingService.REFRESH_NETWORK_INFO";

    /**
     * Intent action to permanently ignore a device connection request.
     */
    public static final String ACTION_IGNORE_DEVICE =
            "com.github.catfriend1.syncthingandroid.SyncthingService.IGNORE_DEVICE";

    /**
     * Intent action to permanently ignore a folder share request.
     */
    public static final String ACTION_IGNORE_FOLDER =
            "com.github.catfriend1.syncthingandroid.SyncthingService.IGNORE_FOLDER";

    /**
     * Intent action to override folder changes.
     */
    public static final String ACTION_OVERRIDE_CHANGES =
            "com.github.catfriend1.syncthingandroid.SyncthingService.OVERRIDE_CHANGES";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE, ACTION_IGNORE_FOLDER.
     */
    public static final String EXTRA_NOTIFICATION_ID =
            "com.github.catfriend1.syncthingandroid.SyncthingService.EXTRA_NOTIFICATION_ID";

    /**
     * Extra used together with ACTION_IGNORE_DEVICE
     */
    public static final String EXTRA_DEVICE_ID =
            "com.github.catfriend1.syncthingandroid.SyncthingService.EXTRA_DEVICE_ID";

    /**
     * Extra used together with ACTION_IGNORE_FOLDER
     */
    public static final String EXTRA_FOLDER_ID =
            "com.github.catfriend1.syncthingandroid.SyncthingService.EXTRA_FOLDER_ID";

    /**
     * Extra used together with ACTION_STOP.
     */
    public static final String EXTRA_STOP_AFTER_CRASHED_NATIVE =
            "com.github.catfriend1.syncthingandroid.SyncthingService.EXTRA_STOP_AFTER_CRASHED_NATIVE";

    public interface OnServiceStateChangeListener {
        void onServiceStateChange(State currentState);
    }

    public interface OnRunConditionCheckResultListener {
        void onRunConditionCheckResultChanged(RunConditionCheckResult result);
    }

    /**
     * Indicates the current state of SyncthingService and of Syncthing itself.
     */
    public enum State {
        /**
         * Service is initializing, Syncthing was not started yet.
         */
        INIT,
        /**
         * Syncthing binary is starting.
         */
        STARTING,
        /**
         * Syncthing binary is running,
         * Rest API is available,
         * RestApi class read the config and is fully initialized.
         */
        ACTIVE,
        /**
         * Syncthing binary is shutting down.
         */
        DISABLED,
        /**
         * There is some problem that prevents Syncthing from running.
         */
        ERROR,
    }

    /**
     * Initialize the service with State.DISABLED as {@link RunConditionMonitor} will
     * send an update if we should run the binary after it got instantiated in
     * {@link #onStartCommand}.
     */
    private State mCurrentState = State.DISABLED;
    private AtomicReference<RunConditionCheckResult> mCurrentCheckResult = new AtomicReference<>(RunConditionCheckResult.SHOULD_RUN);
    private ConfigXml mConfig;
    private Thread mSyncthingRunnableThread = null;
    private Handler mHandler;

    private final HashSet<OnServiceStateChangeListener> mOnServiceStateChangeListeners = new HashSet<>();
    private final HashSet<OnRunConditionCheckResultListener> mOnRunConditionCheckResultListeners = new HashSet<>();
    private final SyncthingServiceBinder mBinder = new SyncthingServiceBinder(this);

    private @Nullable
    PollWebGuiAvailableTask mPollWebGuiAvailableTask = null;

    private @Nullable
    RestApi mRestApi = null;

    private @Nullable
    EventProcessor mEventProcessor = null;

    private @Nullable
    RunConditionMonitor mRunConditionMonitor = null;

    private @Nullable
    SyncthingRunnable mSyncthingRunnable = null;

    @Inject
    NotificationHandler mNotificationHandler;

    @Inject
    SharedPreferences mPreferences;

    /**
     * Object that must be locked upon accessing mCurrentState
     */
    private final Object mStateLock = new Object();

    /**
     * Stores the result of the last should run decision received by OnShouldRunChangedListener.
     */
    private boolean mLastDeterminedShouldRun = false;

    /**
     * True if the user granted the storage permission.
     */
    private boolean mStoragePermissionGranted = false;

    /**
     * True if experimental option PREF_BROADCAST_SERVICE_CONTROL is set.
     * Disables run condition monitor completely because the user chose to
     * control the service by sending broadcasts, e.g. from third-party
     * automation apps.
     */
    private boolean mPrefBroadcastServiceControl = false;

    /**
     * Starts the native binary.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        PRNGFixes.apply();
        ((SyncthingApp) getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        LogV("onCreate");
        mHandler = new Handler();

        /**
         * If runtime permissions are revoked, android kills and restarts the service.
         * see issue: https://github.com/syncthing/syncthing-android/issues/871
         * We need to recheck if we still have the storage permission.
         */
        mStoragePermissionGranted = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED);

        if (mNotificationHandler != null) {
            mNotificationHandler.setAppShutdownInProgress(false);
        }

        // Read pref.
        mPrefBroadcastServiceControl = mPreferences.getBoolean(Constants.PREF_BROADCAST_SERVICE_CONTROL, false);
    }

    /**
     * Handles intent actions, e.g. {@link #ACTION_RESTART}
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
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
         * if RunConditionMonitor does not send a "shouldRun = true" callback
         * to start the binary according to preferences shortly after its creation.
         * See {@link mLastDeterminedShouldRun} defaulting to "false".
         */
        if (mCurrentState == State.DISABLED) {
            synchronized (mStateLock) {
                onServiceStateChange(mCurrentState);
            }
        }

        if (mPrefBroadcastServiceControl) {
            Log.i(TAG, "onStartCommand: mPrefBroadcastServiceControl == true, RunConditionMonitor is disabled.");
            /**
             * Directly use the callback which normally is invoked by RunConditionMonitor to start the
             * syncthing native unconditionally.
             */
            onUpdatedShouldRunDecision(RunConditionCheckResult.SHOULD_RUN);
        } else {
            // Run condition monitor is enabled.
            if (mRunConditionMonitor == null) {
                /**
                 * Instantiate the run condition monitor on first onStartCommand and
                 * enable callback on run condition change affecting the final decision to
                 * run/terminate syncthing. After initial run conditions are collected
                 * the first decision is sent to {@link onShouldRunDecisionChanged}.
                 */
                mRunConditionMonitor = new RunConditionMonitor(SyncthingService.this,
                    this::onUpdatedShouldRunDecision,
                    this::applyCustomRunConditions
                );
            }
        }
        mNotificationHandler.updatePersistentNotification(this);

        if (intent == null) {
            return START_STICKY;
        }

        if (ACTION_RESTART.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            shutdown(State.INIT);
            launchStartupTask(SyncthingRunnable.Command.main);
        } else if (ACTION_STOP.equals(intent.getAction())) {
            if (intent.getBooleanExtra(EXTRA_STOP_AFTER_CRASHED_NATIVE, false)) {
                /**
                 * We were requested to stop the service because the syncthing native binary crashed.
                 * Changing mCurrentState prevents the "defer until syncthing is started" routine we normally
                 * use for clean shutdown to take place. Instead, we will immediately shutdown the crashed
                 * instance forcefully.
                 */
                mCurrentState = State.ERROR;
                shutdown(State.DISABLED);
            } else {
                // Graceful shutdown.
                if (mCurrentState == State.STARTING ||
                        mCurrentState == State.ACTIVE) {
                    shutdown(State.DISABLED);
                }
            }
        } else if (ACTION_RESET_DATABASE.equals(intent.getAction())) {
            /**
             * 1. Stop syncthing native if it's running.
             * 2. Reset the database, syncthing native will exit after performing the reset.
             * 3. Relaunch syncthing native if it was previously running.
             */
            Log.i(TAG, "Invoking reset of database");
            if (mCurrentState != State.DISABLED) {
                // Shutdown synchronously.
                shutdown(State.DISABLED);
            }
            new SyncthingRunnable(this, SyncthingRunnable.Command.resetdatabase).run();
            if (mLastDeterminedShouldRun) {
                launchStartupTask(SyncthingRunnable.Command.main);
            }
        } else if (ACTION_RESET_DELTAS.equals(intent.getAction())) {
            /**
             * 1. Stop syncthing native if it's running.
             * 2. Reset delta index, syncthing native will NOT exit after performing the reset.
             * 3. If syncthing was previously NOT running:
             * 3.1  Schedule a shutdown of the native binary after it left State.STARTING (to State.ACTIVE).
             *      This is the moment, when the reset delta index work was completed and Web UI came up.
             * 3.2  The shutdown gets deferred until State.ACTIVE was reached and then syncthing native will
             *      be shutdown synchronously.
             */
            Log.i(TAG, "Invoking reset of delta indexes");
            if (mCurrentState != State.DISABLED) {
                // Shutdown synchronously.
                shutdown(State.DISABLED);
            }
            launchStartupTask(SyncthingRunnable.Command.resetdeltas);
            if (!mLastDeterminedShouldRun) {
                // Shutdown if syncthing was not running before the UI action was raised.
                shutdown(State.DISABLED);
            }
        } else if (ACTION_REFRESH_NETWORK_INFO.equals(intent.getAction())) {
            if (mRunConditionMonitor != null) {
                mRunConditionMonitor.updateShouldRunDecision();
            }
        } else if (ACTION_IGNORE_DEVICE.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            // mRestApi is not null due to State.ACTIVE
            mRestApi.ignoreDevice(intent.getStringExtra(EXTRA_DEVICE_ID));
            mNotificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        } else if (ACTION_IGNORE_FOLDER.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            // mRestApi is not null due to State.ACTIVE
            mRestApi.ignoreFolder(intent.getStringExtra(EXTRA_DEVICE_ID), intent.getStringExtra(EXTRA_FOLDER_ID));
            mNotificationHandler.cancelConsentNotification(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        } else if (ACTION_OVERRIDE_CHANGES.equals(intent.getAction()) && mCurrentState == State.ACTIVE) {
            mRestApi.overrideChanges(intent.getStringExtra(EXTRA_FOLDER_ID));
        }
        return START_STICKY;
    }

    /**
     * After run conditions monitored by {@link RunConditionMonitor} changed and
     * it had an influence on the decision to run/terminate syncthing, this
     * function is called to notify this class to run/terminate the syncthing binary.
     * {@link #onServiceStateChange} is called while applying the decision change.
     */
    private void onUpdatedShouldRunDecision(RunConditionCheckResult result) {
        boolean newShouldRunDecision = result.isShouldRun();
        boolean reasonsChanged = !mCurrentCheckResult.getAndSet(result).equals(result);
        if (reasonsChanged) {
            onRunConditionCheckResultChange(result);
        }

        if (newShouldRunDecision != mLastDeterminedShouldRun) {
            Log.i(TAG, "shouldRun decision changed to " + newShouldRunDecision + " according to configured run conditions.");
            mLastDeterminedShouldRun = newShouldRunDecision;

            // React to the shouldRun condition change.
            if (newShouldRunDecision) {
                // Start syncthing.
                switch (mCurrentState) {
                    case DISABLED:
                    case INIT:
                        launchStartupTask(SyncthingRunnable.Command.main);
                        break;
                    case STARTING:
                    case ACTIVE:
                    case ERROR:
                        break;
                    default:
                        break;
                }
            } else {
                // Stop syncthing.
                if (mCurrentState == State.DISABLED) {
                    return;
                }
                shutdown(State.DISABLED);
            }
        }
    }

    /**
     * After sync preconditions changed, we need to inform {@link RestApi} to pause or
     * unpause devices and folders as defined in per-object sync preferences.
     */
    private void applyCustomRunConditions(RunConditionMonitor runConditionMonitor) {
        synchronized (mStateLock) {
            if (mRestApi != null && mCurrentState == State.ACTIVE) {
                // Forward event because syncthing is running.
                mRestApi.applyCustomRunConditions(runConditionMonitor);
                return;
            }
        }

        Boolean configChanged = false;
        ConfigXml configXml;

        // Read and parse the config from disk.
        configXml = new ConfigXml(this);
        try {
            configXml.loadConfig();
        } catch (ConfigXml.OpenConfigException e) {
            mNotificationHandler.showCrashedNotification(R.string.config_read_failed, "applyCustomRunConditions:OpenConfigException");
            synchronized (mStateLock) {
                onServiceStateChange(State.ERROR);
            }
            stopSelf();
            return;
        }

        // Check if the folders are available from config.
        List<Folder> folders = configXml.getFolders();
        if (folders != null) {
            for (Folder folder : folders) {
                // LogV("applyCustomRunConditions: Processing config of folder(" + folder.label + ")");
                Boolean folderCustomSyncConditionsEnabled = mPreferences.getBoolean(
                    Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id), false
                );
                if (folderCustomSyncConditionsEnabled) {
                    Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                        Constants.PREF_OBJECT_PREFIX_FOLDER + folder.id
                    );
                    LogV("applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? "1" : "0"));
                    if (folder.paused != !syncConditionsMet) {
                        configXml.setFolderPause(folder.id, !syncConditionsMet);
                        Log.d(TAG, "applyCustomRunConditions: f(" + folder.label + ")=" + (syncConditionsMet ? ">1" : ">0"));
                        configChanged = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "applyCustomRunConditions: folders == null");
            return;
        }

        // Check if the devices are available from config.
        List<Device> devices = configXml.getDevices(false);
        if (devices != null) {
            for (Device device : devices) {
                // LogV("applyCustomRunConditions: Processing config of device(" + device.name + ")");
                Boolean deviceCustomSyncConditionsEnabled = mPreferences.getBoolean(
                    Constants.DYN_PREF_OBJECT_CUSTOM_SYNC_CONDITIONS(Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID), false
                );
                if (deviceCustomSyncConditionsEnabled) {
                    Boolean syncConditionsMet = runConditionMonitor.checkObjectSyncConditions(
                        Constants.PREF_OBJECT_PREFIX_DEVICE + device.deviceID
                    );
                    LogV("applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? "1" : "0"));
                    if (device.paused != !syncConditionsMet) {
                        configXml.setDevicePause(device.deviceID, !syncConditionsMet);
                        Log.d(TAG, "applyCustomRunConditions: d(" + device.name + ")=" + (syncConditionsMet ? ">1" : ">0"));
                        configChanged = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "applyCustomRunConditions: devices == null");
            return;
        }

        if (configChanged) {
            LogV("applyCustomRunConditions: Saving changed config ...");
            configXml.saveChanges();
        } else {
            LogV("applyCustomRunConditions: No action was necessary.");
        }
    }

    /**
     * Prepares to launch the syncthing binary.
     */
    private void launchStartupTask(SyncthingRunnable.Command srCommand) {
        synchronized (mStateLock) {
            if (mCurrentState != State.DISABLED && mCurrentState != State.INIT) {
                Log.e(TAG, "launchStartupTask: Wrong state " + mCurrentState + " detected. Cancelling.");
                return;
            }
        }

        mConfig = new ConfigXml(this);
        try {
            mConfig.loadConfig();
        } catch (ConfigXml.OpenConfigException e) {
            mNotificationHandler.showCrashedNotification(R.string.config_read_failed, "launchStartupTask:OpenConfigException");
            synchronized (mStateLock) {
                onServiceStateChange(State.ERROR);
            }
            stopSelf();
            return;
        }

        // Check if the SyncthingNative's configured webgui port is allocated by another app or process. (issue #193)
        Integer webGuiTcpPort = mConfig.getWebGuiBindPort();
        Boolean isWebUIPortListening = Util.isTcpPortListening(webGuiTcpPort);
        if (isWebUIPortListening) {
            // We shouldn't start SyncthingNative as we would wait forever for life signs on the configured port. (ANR)
            Log.e(TAG, "launchStartupTask: WebUI tcp port " + Integer.toString(webGuiTcpPort) + " unavailable. Second instance?");
            mNotificationHandler.showCrashedNotification(R.string.webui_tcp_port_unavailable, Integer.toString(webGuiTcpPort));
            return;
        }

        onServiceStateChange(State.STARTING);

        if (mRestApi == null) {
            mRestApi = new RestApi(this, mConfig.getWebGuiUrl(), mConfig.getApiKey(),
                    this::onApiAvailable, () -> onServiceStateChange(mCurrentState));
            Log.i(TAG, "Web GUI will be available at " + mConfig.getWebGuiUrl());
        }

        // Check mSyncthingRunnable lifecycle and create singleton.
        if (mSyncthingRunnable != null || mSyncthingRunnableThread != null) {
            Log.e(TAG, "onStartupTaskCompleteListener: Syncthing binary lifecycle violated");
            return;
        }
        mSyncthingRunnable = new SyncthingRunnable(this, srCommand);

        /**
         * Check if an old syncthing instance is still running.
         * This happens after an in-place app upgrade. If so, end it.
         */
        mSyncthingRunnable.killSyncthing();

        // Start the syncthing binary in a separate thread.
        mSyncthingRunnableThread = new Thread(mSyncthingRunnable);
        mSyncthingRunnableThread.start();

        /**
         * Wait for the web-gui of the native syncthing binary to come online.
         *
         * In case the binary is to be stopped, also be aware that another thread could request
         * to stop the binary in the time while waiting for the GUI to become active. See the comment
         * for {@link SyncthingService#onDestroy} for details.
         */
        if (mPollWebGuiAvailableTask == null) {
            mPollWebGuiAvailableTask = new PollWebGuiAvailableTask(
                    this, getWebGuiUrl(), mConfig.getApiKey(), result -> {
                Log.i(TAG, "Web GUI has come online at " + mConfig.getWebGuiUrl());
                if (mRestApi != null) {
                    mRestApi.readConfigFromRestApi();
                }
            }
            );
        }
    }

    /**
     * Called when {@link RestApi#checkReadConfigFromRestApiCompleted} detects
     * the RestApi class has been fully initialized.
     * UI stressing results in mRestApi getting null on simultaneous shutdown, so
     * we check it for safety.
     */
    private void onApiAvailable() {
        if (mRestApi == null) {
            Log.e(TAG, "onApiAvailable: Did we stop the binary during startup? mRestApi == null");
            return;
        }
        synchronized (mStateLock) {
            if (mCurrentState != State.STARTING) {
                Log.e(TAG, "onApiAvailable: Wrong state " + mCurrentState + " detected. Cancelling callback.");
                return;
            }
            onServiceStateChange(State.ACTIVE);
        }
        if (mRestApi != null && mRunConditionMonitor != null) {
            mRestApi.applyCustomRunConditions(mRunConditionMonitor);
        }

        if (mEventProcessor == null) {
            mEventProcessor = new EventProcessor(SyncthingService.this, mRestApi);
            mEventProcessor.start();
        }
    }

    @Override
    public SyncthingServiceBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Stops the native binary.
     * Shuts down RunConditionMonitor instance.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mRunConditionMonitor != null) {
            /**
             * Shut down the OnShouldRunChangedListener so we won't get interrupted by run
             * condition events that occur during shutdown.
             */
            mRunConditionMonitor.shutdown();
        }
        if (mNotificationHandler != null) {
            mNotificationHandler.setAppShutdownInProgress(true);
        }
        if (!mStoragePermissionGranted) {
            // If the storage permission got revoked, we did not start the binary and
            // are in State.INIT requiring an immediate shutdown of this service class.
            Log.i(TAG, "Shutting down syncthing binary due to missing storage permission.");
        }
        shutdown(State.DISABLED);
        super.onDestroy();
    }

    /**
     * Stop SyncthingNative and all helpers like event processor and api handler.
     * Sets {@link #mCurrentState} to newState.
     * Performs a synchronous shutdown of the native binary.
     */
    private void shutdown(State newState) {
        if (mCurrentState == State.STARTING) {
            Log.w(TAG, "Deferring shutdown until State.STARTING was left");
            mHandler.postDelayed(() -> {
                shutdown(newState);
            }, 1000);
            return;
        }

        synchronized (mStateLock) {
            onServiceStateChange(newState);
        }

        if (mPollWebGuiAvailableTask != null) {
            mPollWebGuiAvailableTask.cancelRequestsAndCallback();
            mPollWebGuiAvailableTask = null;
        }

        if (mEventProcessor != null) {
            mEventProcessor.stop();
            mEventProcessor = null;
        }

        if (mNotificationHandler != null) {
            mNotificationHandler.cancelRestartNotification();
        }

        if (mRestApi != null) {
            if (mSyncthingRunnable != null) {
                mRestApi.shutdown();
            }
            mRestApi = null;
        }

        if (mSyncthingRunnable != null) {
            mSyncthingRunnable.killSyncthing();
            if (mSyncthingRunnableThread != null) {
                LogV("Waiting for mSyncthingRunnableThread to finish after killSyncthing ...");
                try {
                    mSyncthingRunnableThread.join();
                } catch (InterruptedException e) {
                    Log.w(TAG, "mSyncthingRunnableThread InterruptedException");
                }
                Log.d(TAG, "Finished mSyncthingRunnableThread.");
                mSyncthingRunnableThread = null;
            }
            mSyncthingRunnable = null;
        }
    }

    public @Nullable
    RestApi getApi() {
        return mRestApi;
    }

    /**
     * Force re-evaluating run conditions immediately e.g. after
     * preferences were modified by {@link ../activities/SettingsActivity#onStop}.
     */
    public void evaluateRunConditions() {
        if (mRunConditionMonitor == null) {
            return;
        }
        Log.d(TAG, "Forced re-evaluating run conditions ...");
        mRunConditionMonitor.updateShouldRunDecision();
    }

    /**
     * Register a listener for the syncthing API state changing.
     * The listener is called immediately with the current state, and again whenever the state
     * changes. The call is always from the GUI thread.
     *
     * @see #unregisterOnServiceStateChangeListener
     */
    public void registerOnServiceStateChangeListener(OnServiceStateChangeListener listener) {
        // Make sure we don't send an invalid state or syncthing might show a "disabled" message
        // when it's just starting up.
        listener.onServiceStateChange(mCurrentState);
        mOnServiceStateChangeListeners.add(listener);
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @see #registerOnServiceStateChangeListener
     */
    public void unregisterOnServiceStateChangeListener(OnServiceStateChangeListener listener) {
        mOnServiceStateChangeListeners.remove(listener);
    }

    /**
     * Called to notify listeners of an API change.
     */
    private void onServiceStateChange(State newState) {
        Log.i(TAG, "onServiceStateChange: from " + mCurrentState + " to " + newState);
        mCurrentState = newState;
        mHandler.post(() -> {
            mNotificationHandler.updatePersistentNotification(this);
            Iterator<OnServiceStateChangeListener> it = mOnServiceStateChangeListeners.iterator();
            while (it.hasNext()) {
                OnServiceStateChangeListener listener = it.next();
                if (listener != null) {
                    listener.onServiceStateChange(mCurrentState);
                } else {
                    it.remove();
                }
            }
        });
    }

    public void registerOnRunConditionCheckResultChange(OnRunConditionCheckResultListener listener) {
        listener.onRunConditionCheckResultChanged(mCurrentCheckResult.get());
        mOnRunConditionCheckResultListeners.add(listener);
    }

    public void unregisterOnRunConditionCheckResultChange(OnRunConditionCheckResultListener listener) {
        mOnRunConditionCheckResultListeners.remove(listener);
    }

    private void onRunConditionCheckResultChange(RunConditionCheckResult result) {
        mHandler.post(() -> {
            for (Iterator<OnRunConditionCheckResultListener> i = mOnRunConditionCheckResultListeners.iterator();
                 i.hasNext(); ) {
                OnRunConditionCheckResultListener listener = i.next();
                if (listener != null) {
                    listener.onRunConditionCheckResultChanged(result);
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

    public RunConditionCheckResult getCurrentRunConditionCheckResult() {
        return mCurrentCheckResult.get();
    }

    public NotificationHandler getNotificationHandler() {
        return mNotificationHandler;
    }

    public String getRunDecisionExplanation() {
        if (mRunConditionMonitor != null) {
            return mRunConditionMonitor.getRunDecisionExplanation();
        }

        Resources res = getResources();
        if (mPrefBroadcastServiceControl) {
            return res.getString(R.string.reason_broadcast_controlled);
        }

        // mRunConditionMonitor == null
        return res.getString(R.string.reason_run_condition_monitor_not_instantiated);
    }

    /**
     * Exports the local config and keys to {@link Constants#EXPORT_PATH}.
     *
     * Test with Android Virtual Device using emulator.
     * cls & adb shell su 0 "ls -a -l -R /data/data/com.github.catfriend1.syncthingandroid.debug/files; echo === SDCARD ===; ls -a -l -R /storage/emulated/0/backups/syncthing"
     *
     */
    public boolean exportConfig() {
        Boolean failSuccess = true;
        Log.d(TAG, "exportConfig BEGIN");

        if (mCurrentState != State.DISABLED) {
            // Shutdown synchronously.
            shutdown(State.DISABLED);
        }

        // Copy config, privateKey and/or publicKey to export path.
        Constants.EXPORT_PATH_OBJ.mkdirs();
        try {
            Files.copy(Constants.getConfigFile(this),
                    new File(Constants.EXPORT_PATH_OBJ, Constants.CONFIG_FILE));
            Files.copy(Constants.getPrivateKeyFile(this),
                    new File(Constants.EXPORT_PATH_OBJ, Constants.PRIVATE_KEY_FILE));
            Files.copy(Constants.getPublicKeyFile(this),
                    new File(Constants.EXPORT_PATH_OBJ, Constants.PUBLIC_KEY_FILE));
        } catch (IOException e) {
            Log.w(TAG, "Failed to export config", e);
            failSuccess = false;
        }

        // Export SharedPreferences.
        File file;
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            file = new File(Constants.EXPORT_PATH_OBJ, Constants.SHARED_PREFS_EXPORT_FILE);
            fileOutputStream = new FileOutputStream(file);
            if (!file.exists()) {
                file.createNewFile();
            }
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(mPreferences.getAll());
            objectOutputStream.flush();
            fileOutputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "exportConfig: Failed to export SharedPreferences #1", e);
            failSuccess = false;
        } finally {
            try {
                if (objectOutputStream != null) {
                    objectOutputStream.close();
                }
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "exportConfig: Failed to export SharedPreferences #2", e);
            }
        }

        /**
         * java.nio.file library is available since API level 26, see
         * https://developer.android.com/reference/java/nio/file/package-summary
         */
        if (Build.VERSION.SDK_INT >= 26) {
            Log.d(TAG, "exportConfig: Exporting index database");
            Path databaseSourcePath = Paths.get(this.getFilesDir() + "/" + Constants.INDEX_DB_FOLDER);
            Path databaseExportPath = Paths.get(Constants.EXPORT_PATH + "/" + Constants.INDEX_DB_FOLDER);
            if (java.nio.file.Files.exists(databaseExportPath)) {
                try {
                    FileUtils.deleteDirectoryRecursively(databaseExportPath);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to delete directory '" + databaseExportPath + "'" + e);
                }
            }
            try {
                java.nio.file.Files.walk(databaseSourcePath).forEach(source -> {
                    try {
                        java.nio.file.Files.copy(source, databaseExportPath.resolve(databaseSourcePath.relativize(source)));
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to copy file '" + source + "' to '" + databaseExportPath + "'");
                    }
                 });
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy directory '" + databaseSourcePath + "' to '" + databaseExportPath + "'");
            }
        }
        Log.d(TAG, "exportConfig END");

        // Start syncthing after export if run conditions apply.
        if (mLastDeterminedShouldRun) {
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable launchStartupTaskRunnable = new Runnable() {
                @Override
                public void run() {
                    launchStartupTask(SyncthingRunnable.Command.main);
                }
            };
            mainLooper.post(launchStartupTaskRunnable);
        }
        return failSuccess;
    }

    /**
     * Imports config and keys from {@link Constants#EXPORT_PATH}.
     *
     * Test with Android Virtual Device using emulator.
     * cls & adb shell su 0 "ls -a -l -R /data/data/com.github.catfriend1.syncthingandroid.debug/files; echo === SDCARD ===; ls -a -l -R /storage/emulated/0/backups/syncthing"
     *
     * @return True if the import was successful, false otherwise (eg if files aren't found).
     */
    public boolean importConfig() {
        Boolean failSuccess = true;
        Log.d(TAG, "importConfig BEGIN");

        if (mCurrentState != State.DISABLED) {
            // Shutdown synchronously.
            shutdown(State.DISABLED);
        }

        // Import config, privateKey and/or publicKey.
        try {
            File config = new File(Constants.EXPORT_PATH_OBJ, Constants.CONFIG_FILE);
            File privateKey = new File(Constants.EXPORT_PATH_OBJ, Constants.PRIVATE_KEY_FILE);
            File publicKey = new File(Constants.EXPORT_PATH_OBJ, Constants.PUBLIC_KEY_FILE);

            // Check if necessary files for import are available.
            if (config.exists() && privateKey.exists() && publicKey.exists()) {
                Files.copy(config, Constants.getConfigFile(this));
                Files.copy(privateKey, Constants.getPrivateKeyFile(this));
                Files.copy(publicKey, Constants.getPublicKeyFile(this));
            } else {
                Log.e(TAG, "importConfig: config, privateKey and/or publicKey files missing");
                failSuccess = false;
            }
        } catch (IOException e) {
            Log.w(TAG, "importConfig: Failed to import config", e);
            failSuccess = false;
        }

        // Import SharedPreferences.
        File file;
        FileInputStream fileInputStream = null;
        ObjectInputStream objectInputStream = null;
        Map<String, Object> sharedPrefsMap = null;
        try {
            file = new File(Constants.EXPORT_PATH_OBJ, Constants.SHARED_PREFS_EXPORT_FILE);
            if (file.exists()) {
                // Read, deserialize shared preferences.
                fileInputStream = new FileInputStream(file);
                objectInputStream = new ObjectInputStream(fileInputStream);
                sharedPrefsMap = (Map) objectInputStream.readObject();

                // Prepare a SharedPreferences commit.
                SharedPreferences.Editor editor = mPreferences.edit();
                editor.clear();
                for (Map.Entry<String, Object> e : sharedPrefsMap.entrySet()) {
                    String prefKey = e.getKey();
                    switch (prefKey) {
                        // Preferences that are no longer used and left-overs from previous versions of the app.
                        case "first_start":
                        case "advanced_folder_picker":
                        case "notification_type":
                        case "notify_crashes":
                        case "start_into_web_gui":
                            LogV("importConfig: Ignoring deprecated pref \"" + prefKey + "\".");
                            break;
                        // Cached information which is not available on SettingsActivity.
                        case Constants.PREF_DEBUG_FACILITIES_AVAILABLE:
                        case Constants.PREF_EVENT_PROCESSOR_LAST_SYNC_ID:
                        case Constants.PREF_LAST_BINARY_VERSION:
                        case Constants.PREF_LOCAL_DEVICE_ID:
                            LogV("importConfig: Ignoring cache pref \"" + prefKey + "\".");
                            break;
                        default:
                            Log.i(TAG, "importConfig: Adding pref \"" + prefKey + "\" to commit ...");

                            // The editor only provides typed setters.
                            if (e.getValue() instanceof Boolean) {
                                editor.putBoolean(prefKey, (Boolean) e.getValue());
                            } else if (e.getValue() instanceof String) {
                                editor.putString(prefKey, (String) e.getValue());
                            } else if (e.getValue() instanceof Integer) {
                                editor.putInt(prefKey, (int) e.getValue());
                            } else if (e.getValue() instanceof Float) {
                                editor.putFloat(prefKey, (float) e.getValue());
                            } else if (e.getValue() instanceof Long) {
                                editor.putLong(prefKey, (Long) e.getValue());
                            } else if (e.getValue() instanceof Set) {
                                editor.putStringSet(prefKey, (Set<String>) e.getValue());
                            } else {
                                Log.w(TAG, "importConfig: SharedPref type " + e.getValue().getClass().getName() + " is unknown");
                            }
                            break;
                    }
                }

                /**
                 * If all shared preferences have been added to the commit successfully,
                 * apply the commit.
                 */
                failSuccess = failSuccess && editor.commit();
            } else {
                // File not found.
                Log.w(TAG, "importConfig: SharedPreferences file missing.");
                /**
                 * Don't fail.
                 */
            }
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "importConfig: Failed to import SharedPreferences #1", e);
            failSuccess = false;
        } finally {
            try {
                if (objectInputStream != null) {
                    objectInputStream.close();
                }
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "importConfig: Failed to import SharedPreferences #2", e);
            }
        }

        /**
         * java.nio.file library is available since API level 26, see
         * https://developer.android.com/reference/java/nio/file/package-summary
         */
        if (Build.VERSION.SDK_INT >= 26) {
            Path databaseImportPath = Paths.get(Constants.EXPORT_PATH + "/" + Constants.INDEX_DB_FOLDER);
            if (java.nio.file.Files.exists(databaseImportPath)) {
                Log.d(TAG, "importConfig: Importing index database");
                Path databaseTargetPath = Paths.get(this.getFilesDir() + "/" + Constants.INDEX_DB_FOLDER);
                try {
                    FileUtils.deleteDirectoryRecursively(databaseTargetPath);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to delete directory '" + databaseTargetPath + "'" + e);
                }
                try {
                    java.nio.file.Files.walk(databaseImportPath).forEach(source -> {
                        try {
                            java.nio.file.Files.copy(source, databaseTargetPath.resolve(databaseImportPath.relativize(source)));
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to copy file '" + source + "' to '" + databaseTargetPath + "'");
                        }
                     });
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy directory '" + databaseImportPath + "' to '" + databaseTargetPath + "'");
                }
            }
        }
        Log.d(TAG, "importConfig END");

        // Start syncthing after import if run conditions apply.
        if (mLastDeterminedShouldRun) {
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable launchStartupTaskRunnable = new Runnable() {
                @Override
                public void run() {
                    launchStartupTask(SyncthingRunnable.Command.main);
                }
            };
            mainLooper.post(launchStartupTaskRunnable);
        }
        return failSuccess;
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
