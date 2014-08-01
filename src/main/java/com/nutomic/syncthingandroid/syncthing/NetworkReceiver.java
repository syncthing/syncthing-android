package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Receives network connection change intents and sends the wifi state to {@link SyncthingService}.
 */
public class NetworkReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		ConnectivityManager cm =
				(ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
		boolean isWifiConnected = (wifiInfo != null && wifiInfo.isConnected()) ||
				activeNetworkInfo == null;
		Intent i = new Intent(context, SyncthingService.class);
		i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, isWifiConnected);
		context.startService(i);
	}

}
