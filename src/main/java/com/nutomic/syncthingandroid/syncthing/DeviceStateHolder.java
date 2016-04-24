package com.nutomic.syncthingandroid.syncthing;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;

/**
 * Holds information about the current wifi and charging state of the device.
 * <p/>
 * This information is actively read on construction, and then updated from intents that are passed
 * to {@link #update(android.content.Intent)}.
 */
public class DeviceStateHolder extends BroadcastReceiver {

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

    public String getWifiSsid() {
        return mWifiSsid;
    }
}
