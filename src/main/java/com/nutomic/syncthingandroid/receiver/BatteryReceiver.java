package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import com.nutomic.syncthingandroid.service.DeviceStateHolder;
import com.nutomic.syncthingandroid.service.SyncthingService;

/**
 * Receives battery plug/unplug intents and sends the charging state to {@link SyncthingService}.
 */
public class BatteryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())
                && !Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction()))
            return;

        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        boolean isCharging = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
        Intent i = new Intent(context, SyncthingService.class);
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, isCharging);
        context.startService(i);
    }

    /**
     * Get the current charging status without waiting for connected/disconnected events.
     */
    public static void updateInitialChargingStatus(Context context) {
        Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        Intent intent = new Intent(context, SyncthingService.class);
        intent.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, isCharging);
        context.startService(intent);
    }

}
