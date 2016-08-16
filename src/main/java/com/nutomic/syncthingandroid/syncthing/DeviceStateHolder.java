package com.nutomic.syncthingandroid.syncthing;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds information about the current wifi and charging state of the device.
 * <p/>
 * This information is actively read on construction, and then updated from intents that are passed
 * to {@link #update(android.content.Intent)}.
 */
public class DeviceStateHolder extends BroadcastReceiver {

    private static final String TAG = "DeviceStateHolder";

    /**
     * Intent extra containing a boolean saying whether wifi is connected or not.
     */
    public static final String EXTRA_HAS_WIFI =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.HAS_WIFI";

    /**
     * Intent extra containging a boolean saying whether the device is
     * charging or not (any power source).
     */
    public static final String EXTRA_IS_CHARGING =
            "com.nutomic.syncthingandroid.syncthing.DeviceStateHolder.IS_CHARGING";

    private Context mContext;

    private boolean mIsWifiConnected = false;

    private String mWifiSsid;

    private boolean mIsCharging = false;

    @TargetApi(16)
    public DeviceStateHolder(Context context) {
        mContext = context;
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mIsWifiConnected = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
        if (android.os.Build.VERSION.SDK_INT >= 16 && cm.isActiveNetworkMetered())
            mIsWifiConnected = false;
        if (mIsWifiConnected) {
            updateWifiSsid();
        }
    }

    /**
     * Receiver for {@link Intent#ACTION_BATTERY_CHANGED}, which is used to determine the initial
     * charging state.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        context.unregisterReceiver(this);
        int status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        mIsCharging = status != 0;
    }

    public boolean isCharging() {
        return mIsCharging;
    }

    public boolean isWifiConnected() {
        return mIsWifiConnected;
    }

    public void update(Intent intent) {
        mIsWifiConnected = intent.getBooleanExtra(EXTRA_HAS_WIFI, mIsWifiConnected);
        mIsCharging = intent.getBooleanExtra(EXTRA_IS_CHARGING, mIsCharging);

        if (mIsWifiConnected) {
            updateWifiSsid();
        } else {
            mWifiSsid = null;
        }
    }

    public void updateWifiSsid() {
        mWifiSsid = null;
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        // may be null, if WiFi has been turned off in meantime
        if (wifiInfo != null) {
            mWifiSsid = wifiInfo.getSSID();
        }
    }

    private String getWifiSsid() {
        return mWifiSsid;
    }

    /**
     * Determines if Syncthing should currently run.
     */
    @TargetApi(21)
    public boolean shouldRun() {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP && pm.isPowerSaveMode()) {
            return false;
        }
        else if (!ContentResolver.getMasterSyncAutomatically()) {
            return false;
        }
        else if (SyncthingService.alwaysRunInBackground(mContext)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            // Check wifi/charging state against preferences and start if ok.
            boolean prefStopMobileData = prefs.getBoolean(SyncthingService.PREF_SYNC_ONLY_WIFI, false);
            boolean prefStopNotCharging = prefs.getBoolean(SyncthingService.PREF_SYNC_ONLY_CHARGING, false);

            return (isCharging() || !prefStopNotCharging) &&
                    (!prefStopMobileData || isAllowedWifiConnected());
        }
        else {
            return true;
        }
    }

    private boolean isAllowedWifiConnected() {
        boolean wifiConnected = isWifiConnected();
        if (wifiConnected) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
            Set<String> ssids = sp.getStringSet(SyncthingService.PREF_SYNC_ONLY_WIFI_SSIDS, new HashSet<String>());
            if (ssids.isEmpty()) {
                Log.d(TAG, "All SSIDs allowed for syncing");
                return true;
            } else {
                String ssid = getWifiSsid();
                if (ssid != null) {
                    if (ssids.contains(ssid)) {
                        Log.d(TAG, "SSID [" + ssid + "] found in whitelist: " + ssids);
                        return true;
                    }
                    Log.i(TAG, "SSID [" + ssid + "] not whitelisted: " + ssids);
                    return false;
                } else {
                    // Don't know the SSID (yet) (should not happen?!), so not allowing
                    Log.w(TAG, "SSID unknown (yet), cannot check SSID whitelist. Disallowing sync.");
                    return false;
                }
            }
        }
        Log.d(TAG, "Wifi not connected");
        return false;
    }

}
