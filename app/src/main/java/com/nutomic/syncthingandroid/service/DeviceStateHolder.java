package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.collect.Lists;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.ReceiverManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on instance creation, and then updated from intents
 * that are passed with {@link #ACTION_DEVICE_STATE_CHANGED}.
 */
public class DeviceStateHolder implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "DeviceStateHolder";

    /**
     * Returns the value of "always_run_in_background" preference.
     */
    public static boolean alwaysRunInBackground(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_ALWAYS_RUN_IN_BACKGROUND, false);
    }

    public interface OnDeviceStateChangedListener {
        void onDeviceStateChanged(boolean shouldRun);
    }

    private final Context mContext;
    @Inject SharedPreferences mPreferences;
    private ReceiverManager mReceiverManager;

    /**
     * Sending callback notifications through {@link OnDeviceStateChangedListener} is enabled if not null.
     */
    private @Nullable OnDeviceStateChangedListener mOnDeviceStateChangedListener = null;

    /**
     * Stores the result of the last call to {@link decideShouldRun}.
     */
    private boolean lastDeterminedShouldRun = false;

    public DeviceStateHolder(Context context, OnDeviceStateChangedListener listener) {
        Log.v(TAG, "Created new instance");
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        mOnDeviceStateChangedListener = listener;

        registerChildReceivers();
    }

    public void shutdown() {
        Log.v(TAG, "Shutting down");
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
        mReceiverManager.unregisterAllReceivers(mContext);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        List<String> watched = Lists.newArrayList(Constants.PREF_SYNC_ONLY_CHARGING,
                Constants.PREF_SYNC_ONLY_WIFI, Constants.PREF_RESPECT_BATTERY_SAVING,
                Constants.PREF_SYNC_ONLY_WIFI_SSIDS);
        if (watched.contains(key)) {
            mReceiverManager.unregisterAllReceivers(mContext);
            registerChildReceivers();
        }
    }

    private void registerChildReceivers() {
        // NetworkReceiver
        ReceiverManager.registerReceiver(mContext, new NetworkReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // BatteryReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        ReceiverManager.registerReceiver(mContext, new BatteryReceiver(), filter);

        // PowerSaveModeChangedReceiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ReceiverManager.registerReceiver(mContext, new PowerSaveModeChangedReceiver(),
                    new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));
        }
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

    private void updateWifiSsid() {
        // ToDo mWifiSsid = null;
        WifiManager wifiManager =
                (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        // May be null, if WiFi has been turned off in meantime.
        if (wifiInfo != null) {
            // ToDo mWifiSsid = wifiInfo.getSSID();
        }
    }

    public void updateShouldRunDecision() {
        // Check if the current conditions changed the result of decideShouldRun()
        // compared to the last determined result.
        boolean newShouldRun = decideShouldRun();
        if (newShouldRun != lastDeterminedShouldRun) {
            if (mOnDeviceStateChangedListener != null) {
                mOnDeviceStateChangedListener.onDeviceStateChanged(newShouldRun);
            }
            lastDeterminedShouldRun = newShouldRun;
        }
    }

    /**
     * Determines if Syncthing should currently run.
     */
    private boolean decideShouldRun() {
        // ToDo Log.v(TAG, "State updated: IsAllowedConnectionType: " + mIsAllowedConnectionType +
        //        ", IsCharging: " + mIsCharging + ", IsPowerSaving: " + mIsPowerSaving);

        boolean prefRespectPowerSaving = mPreferences.getBoolean(Constants.PREF_RESPECT_BATTERY_SAVING, true);
        // ToDo if (prefRespectPowerSaving && mIsPowerSaving)
        //    return false;

        if (alwaysRunInBackground(mContext)) {
            boolean prefStopMobileData = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = mPreferences.getBoolean(Constants.PREF_SYNC_ONLY_CHARGING, false);

            updateWifiSsid();
            if (prefStopMobileData && !isWhitelistedWifiConnection())
                return false;

            //if (prefStopNotCharging && !mIsCharging)
            //    return false;
        }

        return true;
    }

    private boolean isWhitelistedWifiConnection() {
        /*
        boolean wifiConnected = false; // ToDo mIsAllowedConnectionType;
        if (wifiConnected) {
            Set<String> ssids = mPreferences.getStringSet(Constants.PREF_SYNC_ONLY_WIFI_SSIDS, new HashSet<>());
            if (ssids.isEmpty()) {
                Log.d(TAG, "All SSIDs allowed for syncing");
                return true;
            } else {
                if (mWifiSsid != null && ssids.contains(mWifiSsid)) {
                    Log.d(TAG, "SSID [" + mWifiSsid + "] found in whitelist: " + ssids);
                    return true;
                } else {
                    Log.w(TAG, "SSID [" + mWifiSsid + "] unknown or not whitelisted, disallowing sync.");
                    return false;
                }
            }
        }*/
        return false;
    }

}
