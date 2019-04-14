package com.nutomic.syncthingandroid.service;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.model.RunConditionCheckResult;
import com.nutomic.syncthingandroid.service.ReceiverManager;
import com.nutomic.syncthingandroid.util.JobUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import static com.nutomic.syncthingandroid.model.RunConditionCheckResult.*;
import static com.nutomic.syncthingandroid.model.RunConditionCheckResult.BlockerReason.*;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on instance creation, and then updated from intents.
 */
public class RunConditionMonitor {

    private static final String TAG = "RunConditionMonitor";

    private Boolean ENABLE_VERBOSE_LOG = false;

    public static final String ACTION_SYNC_TRIGGER_FIRED =
        "com.github.catfriend1.syncthingandroid.service.RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED";

    private static final String POWER_SOURCE_CHARGER_BATTERY = "ac_and_battery_power";
    private static final String POWER_SOURCE_CHARGER = "ac_power";
    private static final String POWER_SOURCE_BATTERY = "battery_power";

    private @Nullable Object mSyncStatusObserverHandle = null;
    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
            /**
             * We need a Looper here, see issue https://github.com/syncthing/syncthing-android/issues/149
             */
            Handler mainLooper = new Handler(Looper.getMainLooper());
            Runnable updateShouldRunDecisionRunnable = new Runnable() {
                @Override
                public void run() {
                    updateShouldRunDecision();
                }
            };
            mainLooper.post(updateShouldRunDecisionRunnable);
        }
    };

    public interface OnRunConditionChangedListener {
        void onRunConditionChanged(RunConditionCheckResult result);
    }

    public interface OnSyncPreconditionChangedListener {
        void onSyncPreconditionChanged(RunConditionMonitor runConditionMonitor);
    }

    private class SyncConditionResult {
        public Boolean conditionMet = false;
        public String explanation = "";

        SyncConditionResult(Boolean conditionMet) {
            this.conditionMet = conditionMet;
        }

        SyncConditionResult(Boolean conditionMet, String explanation) {
            this.conditionMet = conditionMet;
            this.explanation = explanation;
        }
    }

    private final Context mContext;
    private ReceiverManager mReceiverManager;
    private @Nullable SyncTriggerReceiver mSyncTriggerReceiver = null;
    private Resources res;
    private String mRunDecisionExplanation = "";

    /**
     * Only relevant if the user has enabled turning Syncthing on by
     * time schedule for a specific amount of time periodically.
     * Holds true if we are within a "SyncthingNative should run" time frame.
     */
    private Boolean mTimeConditionMatch = false;

    @Inject
    SharedPreferences mPreferences;

    /**
     * Sending callback notifications through OnShouldRunChangedListener is enabled if not null.
     */
    private @Nullable OnRunConditionChangedListener mOnRunConditionChangedListener = null;

    /**
     * Sending callback notifications through OnSyncPreconditionChangedListener is enabled if not null.
     */
    private @Nullable OnSyncPreconditionChangedListener mOnSyncPreconditionChangedListener = null;

    /**
     * Stores the result of the last call to {@link #decideShouldRun}.
     */
    private RunConditionCheckResult lastRunConditionCheckResult;

    public RunConditionMonitor(Context context,
            OnRunConditionChangedListener onRunConditionChangedListener,
            OnSyncPreconditionChangedListener onSyncPreconditionChangedListener) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
        LogV("Created new instance");
        mContext = context;
        res = mContext.getResources();
        mOnRunConditionChangedListener = onRunConditionChangedListener;
        mOnSyncPreconditionChangedListener = onSyncPreconditionChangedListener;

        /**
         * Register broadcast receivers.
         */
        // NetworkReceiver
        ReceiverManager.registerReceiver(mContext, new NetworkReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // BatteryReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ReceiverManager.registerReceiver(mContext, new BatteryReceiver(), filter);

        // PowerSaveModeChangedReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ReceiverManager.registerReceiver(mContext,
                    new PowerSaveModeChangedReceiver(),
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        }

        // SyncStatusObserver to monitor android's "AutoSync" quick toggle.
        mSyncStatusObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);

        // SyncTriggerReceiver
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mSyncTriggerReceiver = new SyncTriggerReceiver();
        localBroadcastManager.registerReceiver(mSyncTriggerReceiver,
                new IntentFilter(ACTION_SYNC_TRIGGER_FIRED));

        // Initially determine if syncthing should run under current circumstances.
        updateShouldRunDecision();

        // Initially schedule the SyncTrigger job.
        JobUtils.scheduleSyncTriggerServiceJob(context, Constants.WAIT_FOR_NEXT_SYNC_DELAY_SECS);
    }

    public void shutdown() {
        LogV("Shutting down");
        JobUtils.cancelAllScheduledJobs(mContext);
        if (mSyncStatusObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncStatusObserverHandle);
            mSyncStatusObserverHandle = null;
        }
        if (mSyncTriggerReceiver != null) {
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(mContext);
            localBroadcastManager.unregisterReceiver(mSyncTriggerReceiver);
            mSyncTriggerReceiver = null;
        }
        mReceiverManager.unregisterAllReceivers(mContext);
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    || Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class PowerSaveModeChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(intent.getAction())) {
                updateShouldRunDecision();
            }
        }
    }

    private class SyncTriggerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogV("SyncTriggerReceiver: onReceive");
            boolean prefRunOnTimeSchedule = mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);
            if (!prefRunOnTimeSchedule) {
                mTimeConditionMatch = false;
            } else {
                /**
                 * Toggle the "digital input" for this condition as the condition change is
                 * triggered by a time schedule and not the OS notifying us.
                 */
                mTimeConditionMatch = !mTimeConditionMatch;
                updateShouldRunDecision();
            }

            /**
             * Reschedule the job.
             * If we are within a "SyncthingNative should run" time frame,
             * let the receiver fire and change to "SyncthingNative shouldn't run" after
             * TRIGGERED_SYNC_DURATION_SECS seconds elapsed.
             * If we are within a "SyncthingNative shouldn't run" time frame,
             * let the receiver fire and change to "SyncthingNative should run" after
             * WAIT_FOR_NEXT_SYNC_DELAY_SECS seconds elapsed.
             */
            JobUtils.scheduleSyncTriggerServiceJob(
                    context,
                    mTimeConditionMatch ? Constants.TRIGGERED_SYNC_DURATION_SECS : Constants.WAIT_FOR_NEXT_SYNC_DELAY_SECS
            );
        }
    }

    /**
     * Event handler that is fired after preconditions changed.
     * We then need to decide if syncthing should run.
     */
    public void updateShouldRunDecision() {
        // Reason if the current conditions changed the result of decideShouldRun()
        // compared to the last determined result.
        RunConditionCheckResult result = decideShouldRun();
        boolean change;
        synchronized (this) {
            change = lastRunConditionCheckResult == null || !lastRunConditionCheckResult.equals(result);
            lastRunConditionCheckResult = result;
        }
        if (change) {
            if (mOnRunConditionChangedListener != null) {
                mOnRunConditionChangedListener.onRunConditionChanged(result);
            }
        }
    }

    public String getRunDecisionExplanation() {
        return mRunDecisionExplanation;
    }

    /**
     * Each sync condition has its own evaluator function which
     * determines if the condition is met.
     */
    /**
     * Constants.PREF_RUN_ON_WIFI
     */
    private SyncConditionResult checkConditionSyncOnWifi(String prefNameSyncOnWifi) {
        boolean prefSyncOnWifi = mPreferences.getBoolean(prefNameSyncOnWifi, true);
        if (!prefSyncOnWifi) {
            return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_wifi_disallowed));
        }

        if (isWifiOrEthernetConnection()) {
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_wifi));
        }

        /**
         * if (prefRunOnWifi && !isWifiOrEthernetConnection()) { return false; }
         * This is intentionally not returning "false" as the flight mode workaround
         * relevant for some phone models needs to be done by the code below.
         * ConnectivityManager.getActiveNetworkInfo() returns "null" on those phones which
         * results in assuming !isWifiOrEthernetConnection even if the phone is connected
         * to wifi during flight mode, see {@link isWifiOrEthernetConnection}.
         */
        return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_on_wifi));
    }

    /**
     * Constants.PREF_WIFI_SSID_WHITELIST
     */
    private SyncConditionResult checkConditionSyncOnWhitelistedWifi(
            String prefNameUseWifiWhitelist,
            String prefNameSelectedWhitelistSsid) {
        boolean wifiWhitelistEnabled = mPreferences.getBoolean(prefNameUseWifiWhitelist, false);
        Set<String> whitelistedWifiSsids = mPreferences.getStringSet(prefNameSelectedWhitelistSsid, new HashSet<>());
        try {
            if (wifiWhitelistConditionMet(wifiWhitelistEnabled, whitelistedWifiSsids)) {
                return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_whitelisted_wifi));
            }
            return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_on_whitelisted_wifi));
        } catch (LocationUnavailableException e) {
            return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_location_unavailable));
        }
    }

    /**
     * Constants.PREF_RUN_ON_METERED_WIFI
     */
    private SyncConditionResult checkConditionSyncOnMeteredWifi(String prefNameSyncOnMeteredWifi) {
        boolean prefSyncOnMeteredWifi = mPreferences.getBoolean(prefNameSyncOnMeteredWifi, false);
        if (prefSyncOnMeteredWifi) {
            // Condition is always met as we allow both types of wifi - metered and non-metered.
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_metered_nonmetered_wifi));
        }

        // Check if we are on a non-metered wifi.
        if (!isMeteredNetworkConnection()) {
            return new SyncConditionResult(true, "\n" + res.getString(R.string.reason_on_nonmetered_wifi));
        }

        // We disallowed non-metered wifi and are connected to metered wifi.
        return new SyncConditionResult(false, "\n" + res.getString(R.string.reason_not_nonmetered_wifi));
    }

    /**
     * Constants.PREF_RUN_ON_MOBILE_DATA
     */
    private SyncConditionResult checkConditionSyncOnMobileData(String prefNameSyncOnMobileData) {
        boolean prefSyncOnMobileData = mPreferences.getBoolean(prefNameSyncOnMobileData, false);
        if (!prefSyncOnMobileData) {
            return new SyncConditionResult(false, res.getString(R.string.reason_mobile_data_disallowed));
        }

        if (isMobileDataConnection()) {
            return new SyncConditionResult(true, res.getString(R.string.reason_on_mobile_data));
        }

        return new SyncConditionResult(false, res.getString(R.string.reason_not_on_mobile_data));
    }

    /**
     * Determines if Syncthing should currently run.
     * Updates mRunDecisionExplanation.
     */
    private RunConditionCheckResult decideShouldRun() {
        // Get sync condition preferences.
        String prefPowerSource = mPreferences.getString(Constants.PREF_POWER_SOURCE, POWER_SOURCE_CHARGER_BATTERY);
        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRespectMasterSync = mPreferences.getBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false);
        boolean prefRunInFlightMode = mPreferences.getBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, false);
        boolean prefRunOnTimeSchedule = mPreferences.getBoolean(Constants.PREF_RUN_ON_TIME_SCHEDULE, false);

        List<BlockerReason> blockerReasons = new ArrayList<>();

        // PREF_RUN_ON_TIME_SCHEDULE
        if (prefRunOnTimeSchedule && !mTimeConditionMatch) {
            // Currently, we aren't within a "SyncthingNative should run" time frame.
            LogV("decideShouldRun: PREF_RUN_ON_TIME_SCHEDULE && !mTimeConditionMatch");
            blockerReasons.add(NOT_WITHIN_TIMEFRAME);
        }

        // PREF_POWER_SOURCE
        switch (prefPowerSource) {
            case POWER_SOURCE_CHARGER:
                if (!isCharging()) {
                    LogV("decideShouldRun: POWER_SOURCE_AC && !isCharging");
                    blockerReasons.add(ON_BATTERY);
                }
                break;
            case POWER_SOURCE_BATTERY:
                if (isCharging()) {
                    LogV("decideShouldRun: POWER_SOURCE_BATTERY && isCharging");
                    blockerReasons.add(ON_CHARGER);
                }
                break;
            case POWER_SOURCE_CHARGER_BATTERY:
            default:
                break;
        }

        // Power saving
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (prefRespectPowerSaving && isPowerSaving()) {
                LogV("decideShouldRun: prefRespectPowerSaving && isPowerSaving");
                blockerReasons.add(POWERSAVING_ENABLED);
            }
        }

        // Android global AutoSync setting.
        if (prefRespectMasterSync && !ContentResolver.getMasterSyncAutomatically()) {
            LogV("decideShouldRun: prefRespectMasterSync && !getMasterSyncAutomatically");
            blockerReasons.add(GLOBAL_SYNC_DISABLED);
        }

        // Run on mobile data?
        SyncConditionResult scr = checkConditionSyncOnMobileData(Constants.PREF_RUN_ON_MOBILE_DATA);
        if (blockerReasons.isEmpty() && scr.conditionMet) {
            // Mobile data is connected.
            LogV("decideShouldRun: checkConditionSyncOnMobileData");
            return SHOULD_RUN;
        }

        // Run on WiFi?
        scr = checkConditionSyncOnWifi(Constants.PREF_RUN_ON_WIFI);
        if (scr.conditionMet) {
            // Wifi is connected.
            LogV("decideShouldRun: checkConditionSyncOnWifi");

            scr = checkConditionSyncOnMeteredWifi(Constants.PREF_RUN_ON_METERED_WIFI);
            if (scr.conditionMet) {
                // Wifi type is allowed.
                LogV("decideShouldRun: checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi");

                scr = checkConditionSyncOnWhitelistedWifi(Constants.PREF_USE_WIFI_SSID_WHITELIST, Constants.PREF_WIFI_SSID_WHITELIST);
                if (scr.conditionMet) {
                    // Wifi is whitelisted.
                    LogV("decideShouldRun: checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi && checkConditionSyncOnWhitelistedWifi");
                    if (blockerReasons.isEmpty()) return SHOULD_RUN;
                }
            }
        }

        // Run in flight mode.
        if (prefRunInFlightMode && isFlightMode()) {
            LogV("decideShouldRun: prefRunInFlightMode && isFlightMode");
            if (blockerReasons.isEmpty()) return SHOULD_RUN;
        }

        /**
         * If none of the above run conditions matched, don't run.
         */
        LogV("decideShouldRun: return false");
        if (blockerReasons.isEmpty()) {
            blockerReasons.add(NO_NETWORK_OR_FLIGHTMODE);
        }
        return new RunConditionCheckResult(blockerReasons);
    }

    /**
     * Check if an object's individual sync conditions are met.
     */
    public Boolean checkObjectSyncConditions(String objectPrefixAndId) {
        // Sync on mobile data?
        SyncConditionResult scr = checkConditionSyncOnMobileData(Constants.DYN_PREF_OBJECT_SYNC_ON_MOBILE_DATA(objectPrefixAndId));
        if (scr.conditionMet) {
            // Mobile data is connected.
            LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnMobileData");
            return true;
        }

        // Sync on WiFi?
        scr = checkConditionSyncOnWifi(Constants.DYN_PREF_OBJECT_SYNC_ON_WIFI(objectPrefixAndId));
        if (scr.conditionMet) {
            // Wifi is connected.
            LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi");

            scr = checkConditionSyncOnMeteredWifi(Constants.DYN_PREF_OBJECT_SYNC_ON_METERED_WIFI(objectPrefixAndId));
            if (scr.conditionMet) {
                // Wifi type is allowed.
                LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi");

                scr = checkConditionSyncOnWhitelistedWifi(
                    Constants.DYN_PREF_OBJECT_USE_WIFI_SSID_WHITELIST(objectPrefixAndId),
                    Constants.DYN_PREF_OBJECT_SELECTED_WHITELIST_SSID(objectPrefixAndId)
                );
                if (scr.conditionMet) {
                    // Wifi is whitelisted.
                    LogV("checkObjectSyncConditions(" + objectPrefixAndId + "): checkConditionSyncOnWifi && checkConditionSyncOnMeteredWifi && checkConditionSyncOnWhitelistedWifi");
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return whether the wifi whitelist run condition is met.
     * Precondition: An active wifi connection has been detected.
     */
    private boolean wifiWhitelistConditionMet (boolean prefWifiWhitelistEnabled,
            Set<String> whitelistedWifiSsids) throws LocationUnavailableException {
        if (!prefWifiWhitelistEnabled) {
            LogV("handleWifiWhitelist: !prefWifiWhitelistEnabled");
            return true;
        }
        if (isWifiConnectionWhitelisted(whitelistedWifiSsids)) {
            LogV("handleWifiWhitelist: isWifiConnectionWhitelisted");
            return true;
        }
        return false;
    }

    /**
     * Functions for run condition information retrieval.
     */
    private boolean isCharging() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // API level < 21
            return isCharging_API16();
        } else {
            // API level >= 21
            return isCharging_API17();
        }
    }

    @TargetApi(16)
    private boolean isCharging_API16() {
        Intent batteryIntent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL;
    }

    @TargetApi(17)
    private boolean isCharging_API17() {
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    @TargetApi(21)
    private boolean isPowerSaving() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e(TAG, "isPowerSaving may not be called on pre-lollipop android versions.");
            return false;
        }
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.e(TAG, "getSystemService(POWER_SERVICE) unexpectedly returned NULL.");
            return false;
        }
        return powerManager.isPowerSaveMode();
    }

    private boolean isFlightMode() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni == null;
    }

    private boolean isMeteredNetworkConnection() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // In flight mode.
            return false;
        }
        if (!ni.isConnected()) {
            // No network connection.
            return false;
        }
        if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
            /**
             * We treat Wi-Fi and ETHERNET as "Wi-Fi" connection.
             * Assume ETHERNET connection is un-metered to allow syncing on
             * Android TV or VirtualBox ETHERNET connection.
             */
             return false;
        }
        return cm.isActiveNetworkMetered();
    }

    private boolean isMobileDataConnection() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // In flight mode.
            return false;
        }
        if (!ni.isConnected()) {
            // No network connection.
            return false;
        }
        switch (ni.getType()) {
            case ConnectivityManager.TYPE_BLUETOOTH:
            case ConnectivityManager.TYPE_MOBILE:
            case ConnectivityManager.TYPE_MOBILE_DUN:
            case ConnectivityManager.TYPE_MOBILE_HIPRI:
                return true;
            default:
                return false;
        }
    }

    private boolean isWifiOrEthernetConnection() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni == null) {
            // In flight mode.
            return false;
        }
        if (!ni.isConnected()) {
            // No network connection.
            return false;
        }
        switch (ni.getType()) {
            case ConnectivityManager.TYPE_WIFI:
            case ConnectivityManager.TYPE_WIMAX:
            case ConnectivityManager.TYPE_ETHERNET:
                return true;
            default:
                return false;
        }
    }

    private boolean isWifiConnectionWhitelisted(Set<String> whitelistedSsids)
            throws LocationUnavailableException{
        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            // May be null, if wifi has been turned off in the meantime.
            Log.d(TAG, "isWifiConnectionWhitelisted: SSID unknown due to wifiInfo == null");
            return false;
        }
        String wifiSsid = wifiInfo.getSSID();
        if (wifiSsid == null || wifiSsid.equals("<unknown ssid>")) {
            throw new LocationUnavailableException("isWifiConnectionWhitelisted: Got null SSID. Try to enable android location service.");
        }
        return whitelistedSsids.contains(wifiSsid);
    }

    public class LocationUnavailableException extends Exception {

        public LocationUnavailableException(String message) {
            super(message);
        }

        public LocationUnavailableException(String message, Throwable throwable) {
            super(message, throwable);
        }

    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }
}
