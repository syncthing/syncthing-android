package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

/**
 * Receives network connection change intents and sends the wifi state to {@link SyncthingService}.
 */
public class NetworkReceiver extends BroadcastReceiver {

    private static final String TAG = "NetworkReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction()))
            return;

        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        boolean isWifiConnected = ni != null && ni.getType() == ConnectivityManager.TYPE_WIFI && ni.isConnected();
        Log.v(TAG, "Received wifi " + (isWifiConnected ? "connected" : "disconnected") + " event");
        Intent i = new Intent(context, SyncthingService.class);
        i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, isWifiConnected);
        context.startService(i);
    }

}
