package com.nutomic.syncthingandroid.service;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import android.util.Log;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.di.DefaultSharedPreferences;
import com.nutomic.syncthingandroid.model.RunConditionCheckResult;

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
 * This information is actively read on instance creation, and then updated from intents
 * that are passed with {@link #ACTION_DEVICE_STATE_CHANGED}.
 */
public class RunConditionMonitor {

    private static final String TAG = "RunConditionMonitor";

    private static final String POWER_SOURCE_CHARGER_BATTERY = "ac_and_battery_power";
    private static final String POWER_SOURCE_CHARGER = "ac_power";
    private static final String POWER_SOURCE_BATTERY = "battery_power";

    private @Nullable Object mSyncStatusObserverHandle = null;
    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        @Override
        public void onStatusChanged(int which) {
            updateShouldRunDecision();
        }
    };

    public interface OnRunConditionChangedListener {
        void onRunConditionChanged(RunConditionCheckResult result);
    }

    private final Context mContext;
    private ReceiverManager mReceiverManager;

    @Inject
    @DefaultSharedPreferences
    SharedPreferences mPreferences;

    /**
     * Sending callback notifications through {@link OnRunConditionChangedListener} is enabled if not null.
     */
    private @Nullable OnRunConditionChangedListener mOnRunConditionChangedListener = null;

    /**
     * Stores the result of the last call to {@link #decideShouldRun()}.
     */
    private RunConditionCheckResult lastRunConditionCheckResult;

    public RunConditionMonitor(Context context, OnRunConditionChangedListener listener) {
        Log.v(TAG, "Created new instance");
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mOnRunConditionChangedListener = listener;

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
        ReceiverManager.registerReceiver(mContext,
                new PowerSaveModeChangedReceiver(),
                new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));

        // SyncStatusObserver to monitor android's "AutoSync" quick toggle.
        mSyncStatusObserverHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, mSyncStatusObserver);

        // Initially determine if syncthing should run under current circumstances.
        updateShouldRunDecision();
    }

    public void shutdown() {
        Log.v(TAG, "Shutting down");
        if (mSyncStatusObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncStatusObserverHandle);
            mSyncStatusObserverHandle = null;
        }
        mReceiverManager.unregisterAllReceivers(mContext);
    }

    private class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                    || Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateShouldRunDecision();
                    }
                }, 5000);
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

    /**
     * Determines if Syncthing should currently run.
     */
    private RunConditionCheckResult decideShouldRun() {
        // Get run conditions preferences.
        boolean prefRunConditions= mPreferences.getBoolean(Constants.PREF_RUN_CONDITIONS, true);
        boolean prefRunOnMobileData= mPreferences.getBoolean(Constants.PREF_RUN_ON_MOBILE_DATA, false);
        boolean prefRunOnWifi= mPreferences.getBoolean(Constants.PREF_RUN_ON_WIFI, true);
        boolean prefRunOnMeteredWifi= mPreferences.getBoolean(Constants.PREF_RUN_ON_METERED_WIFI, false);
        Set<String> whitelistedWifiSsids = mPreferences.getStringSet(Constants.PREF_WIFI_SSID_WHITELIST, new HashSet<>());
        boolean prefWifiWhitelistEnabled = !whitelistedWifiSsids.isEmpty();
        boolean prefRunInFlightMode = mPreferences.getBoolean(Constants.PREF_RUN_IN_FLIGHT_MODE, false);
        String prefPowerSource = mPreferences.getString(Constants.PREF_POWER_SOURCE, POWER_SOURCE_CHARGER_BATTERY);
        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        boolean prefRespectMasterSync = mPreferences.getBoolean(Constants.PREF_RESPECT_MASTER_SYNC, false);

        if (!prefRunConditions) {
            Log.v(TAG, "decideShouldRun: !runConditions");
            return SHOULD_RUN;
        }

        List<BlockerReason> blockerReasons = new ArrayList<>();

        // PREF_POWER_SOURCE
        switch (prefPowerSource) {
            case POWER_SOURCE_CHARGER:
                if (!isCharging()) {
                    Log.v(TAG, "decideShouldRun: POWER_SOURCE_AC && !isCharging");
                    blockerReasons.add(ON_BATTERY);
                }
                break;
            case POWER_SOURCE_BATTERY:
                if (isCharging()) {
                    Log.v(TAG, "decideShouldRun: POWER_SOURCE_BATTERY && isCharging");
                    blockerReasons.add(ON_CHARGER);
                }
                break;
            case POWER_SOURCE_CHARGER_BATTERY:
            default:
                break;
        }

        // Power saving
        if (prefRespectPowerSaving && isPowerSaving()) {
            Log.v(TAG, "decideShouldRun: prefRespectPowerSaving && isPowerSaving");
            blockerReasons.add(POWERSAVING_ENABLED);
        }

        // Android global AutoSync setting.
        if (prefRespectMasterSync && !ContentResolver.getMasterSyncAutomatically()) {
            Log.v(TAG, "decideShouldRun: prefRespectMasterSync && !getMasterSyncAutomatically");
            blockerReasons.add(GLOBAL_SYNC_DISABLED);
        }

        // Run on mobile data.
        if (blockerReasons.isEmpty() && prefRunOnMobileData && isMobileDataConnection()) {
            Log.v(TAG, "decideShouldRun: prefRunOnMobileData && isMobileDataConnection");
            return SHOULD_RUN;
        }

        // Run on wifi.
        if (prefRunOnWifi && isWifiOrEthernetConnection()) {
            if (prefRunOnMeteredWifi) {
                // We are on non-metered or metered wifi. Reason if wifi whitelist run condition is met.
                if (wifiWhitelistConditionMet(prefWifiWhitelistEnabled, whitelistedWifiSsids)) {
                    Log.v(TAG, "decideShouldRun: prefRunOnWifi && isWifiOrEthernetConnection && prefRunOnMeteredWifi && wifiWhitelistConditionMet");
                    if (blockerReasons.isEmpty()) return SHOULD_RUN;
                } else {
                    blockerReasons.add(WIFI_SSID_NOT_WHITELISTED);
                }
            } else {
                // Reason if we are on a non-metered wifi and if wifi whitelist run condition is met.
                if (!isMeteredNetworkConnection()) {
                    if (wifiWhitelistConditionMet(prefWifiWhitelistEnabled, whitelistedWifiSsids)) {
                        Log.v(TAG, "decideShouldRun: prefRunOnWifi && isWifiOrEthernetConnection && !prefRunOnMeteredWifi && !isMeteredNetworkConnection && wifiWhitelistConditionMet");
                        if (blockerReasons.isEmpty()) return SHOULD_RUN;
                    } else {
                        blockerReasons.add(WIFI_SSID_NOT_WHITELISTED);
                    }
                } else {
                    blockerReasons.add(WIFI_WIFI_IS_METERED);
                }
            }
        }

        // Run in flight mode.
        if (prefRunInFlightMode && isFlightMode()) {
            Log.v(TAG, "decideShouldRun: prefRunInFlightMode && isFlightMode");
            if (blockerReasons.isEmpty()) return SHOULD_RUN;
        }

        /**
         * If none of the above run conditions matched, don't run.
         */
        Log.v(TAG, "decideShouldRun: return false");
        if (blockerReasons.isEmpty()) {
            if (isFlightMode()) {
                blockerReasons.add(NO_NETWORK_OR_FLIGHTMODE);
            } else if (!prefRunOnWifi && !prefRunOnMobileData) {
                blockerReasons.add(NO_ALLOWED_NETWORK);
            } else if (prefRunOnMobileData) {
                blockerReasons.add(NO_MOBILE_CONNECTION);
            } else if (prefRunOnWifi) {
                blockerReasons.add(NO_WIFI_CONNECTION);
            } else {
                blockerReasons.add(NO_NETWORK_OR_FLIGHTMODE);
            }
        }
        return new RunConditionCheckResult(blockerReasons);
    }

    /**
     * Return whether the wifi whitelist run condition is met.
     * Precondition: An active wifi connection has been detected.
     */
    private boolean wifiWhitelistConditionMet(boolean prefWifiWhitelistEnabled,
            Set<String> whitelistedWifiSsids) {
        if (!prefWifiWhitelistEnabled) {
            Log.v(TAG, "handleWifiWhitelist: !prefWifiWhitelistEnabled");
            return true;
        }
        if (isWifiConnectionWhitelisted(whitelistedWifiSsids)) {
            Log.v(TAG, "handleWifiWhitelist: isWifiConnectionWhitelisted");
            return true;
        }
        return false;
    }

    /**
     * Functions for run condition information retrieval.
     */
    private boolean isCharging() {
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    private boolean isPowerSaving() {
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

    private boolean isWifiConnectionWhitelisted(Set<String> whitelistedSsids) {
        WifiManager wifiManager = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            // May be null, if wifi has been turned off in the meantime.
            Log.d(TAG, "isWifiConnectionWhitelisted: SSID unknown due to wifiInfo == null");
            return false;
        }
        String wifiSsid = wifiInfo.getSSID();
        if (wifiSsid == null) {
            Log.w(TAG, "isWifiConnectionWhitelisted: Got null SSID. Try to enable android location service.");
            return false;
        }
        return whitelistedSsids.contains(wifiSsid);
    }

}
