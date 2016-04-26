package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Receives battery plug/unplug intents and sends the charging state to {@link SyncthingService}.
 */
public class BatteryReceiver extends BroadcastReceiver {

    private static final String TAG = "BatteryReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                && !Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction()))
            return;

        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        boolean isCharging = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
        Log.v(TAG, "Received charger " + (isCharging ? "connected" : "disconnected") + " event");
        Intent i = new Intent(context, SyncthingService.class);
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, isCharging);
        context.startService(i);
    }

}
