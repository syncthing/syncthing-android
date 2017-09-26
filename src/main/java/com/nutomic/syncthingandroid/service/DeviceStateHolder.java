package com.nutomic.syncthingandroid.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.nutomic.syncthingandroid.receiver.BatteryReceiver;
import com.nutomic.syncthingandroid.receiver.NetworkReceiver;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on construction, and then updated from intents that are passed
 * to {@link #update(android.content.Intent)}.
 */
public class DeviceStateHolder {

    private static final String TAG = "DeviceStateHolder";

    /**
     * Intent extra containing a boolean saying whether wifi is connected or not.
     */
    public static final String EXTRA_IS_ALLOWED_NETWORK_CONNECTION =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.IS_ALLOWED_NETWORK_CONNECTION";

    /**
     * Intent extra containging a boolean saying whether the device is
     * charging or not (any power source).
     */
    public static final String EXTRA_IS_CHARGING =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.IS_CHARGING";

    private final Context mContext;
    private final SharedPreferences mPreferences;

    private boolean mIsAllowedNetworkConnection = false;
    private String mWifiSsid;
    private boolean mIsCharging = false;

    public DeviceStateHolder(Context context) {
        mContext = context;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        BatteryReceiver.updateInitialChargingStatus(mContext);
        NetworkReceiver.updateNetworkStatus(mContext);
    }

    public boolean isCharging() {
        return mIsCharging;
    }

    public boolean isAllowedNetworkConnection() {
        return mIsAllowedNetworkConnection;
    }

    public void update(Intent intent) {
        mIsAllowedNetworkConnection =
                intent.getBooleanExtra(EXTRA_IS_ALLOWED_NETWORK_CONNECTION, mIsAllowedNetworkConnection);
        mIsCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, mIsCharging);
        Log.i(TAG, "State updated, allowed network connection: " + mIsAllowedNetworkConnection +
                ", charging: " + mIsCharging);

        updateWifiSsid();
    }

    private void updateWifiSsid() {
        mWifiSsid = null;
        WifiManager wifiManager =
                (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        // may be null, if WiFi has been turned off in meantime
        if (wifiInfo != null) {
            mWifiSsid = wifiInfo.getSSID();
        }
    }

    /**
     * Determines if Syncthing should currently run.
     */
    public boolean shouldRun() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                mPreferences.getBoolean("respect_battery_saving", true) &&
                pm.isPowerSaveMode()) {
            return false;
        }
        else if (SyncthingService.alwaysRunInBackground(mContext)) {
            // Check wifi/charging state against preferences and start if ok.
            boolean prefStopMobileData = mPreferences.getBoolean(SyncthingService.PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = mPreferences.getBoolean(SyncthingService.PREF_SYNC_ONLY_CHARGING, false);

            return (isCharging() || !prefStopNotCharging) &&
                    (!prefStopMobileData || isWhitelistedNetworkConnection());
        }
        else {
            return true;
        }
    }

    private boolean isWhitelistedNetworkConnection() {
        boolean wifiConnected = isAllowedNetworkConnection();
        if (wifiConnected) {
            Set<String> ssids = mPreferences.getStringSet(SyncthingService.PREF_SYNC_ONLY_WIFI_SSIDS, new HashSet<>());
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
        }
        Log.d(TAG, "Wifi not connected");
        return false;
    }

}
