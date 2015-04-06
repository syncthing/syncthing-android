package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * Receives network connection change intents and sends the wifi state to {@link SyncthingService}.
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";
    private static final int WIFI_AP_STATE_ENABLED = 13;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        Intent i = new Intent(context, SyncthingService.class);
        String action = intent.getAction();
        if (action.equals("android.net.wifi.WIFI_AP_STATE_CHANGED")) {
            int state = intent.getIntExtra("wifi_state", 0);
            boolean apon = state == WIFI_AP_STATE_ENABLED;
            i.putExtra(DeviceStateHolder.EXTRA_AP_ENABLED, apon);
        } else {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            boolean isWifiConnected = (wifiInfo != null && wifiInfo.isConnected());
            Log.v(TAG, "Received wifi " + (isWifiConnected ? "connected" : "disconnected") + " event");
            i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, isWifiConnected);
        }
        context.startService(i);
    }

}
