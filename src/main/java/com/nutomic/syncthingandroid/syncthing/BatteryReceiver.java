package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives battery plug/unplug intents and sends the charging state to {@link SyncthingService}.
 */
public class BatteryReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		boolean isCharging = Intent.ACTION_POWER_CONNECTED.equals(intent.getAction());
		Intent i = new Intent(context, SyncthingService.class);
		i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, isCharging);
		context.startService(i);
	}

}
