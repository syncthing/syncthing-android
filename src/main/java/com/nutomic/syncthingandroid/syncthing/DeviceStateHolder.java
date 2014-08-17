package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.BatteryManager;

/**
 * Holds information about the current wifi and charging state of the device.
 *
 * This information is actively read on construction, and then updated from intents that are passed
 * to {@link #update(android.content.Intent)}.
 */
public class DeviceStateHolder extends BroadcastReceiver {

    /**
     * Intent extra containing a boolean saying whether wifi is connected or not.
     */
	public static final String EXTRA_HAS_WIFI = "has_wifi";

    /**
     * Intent extra containging a boolean saying whether the device is
     * charging or not (any power source).
     */
	public static final String EXTRA_IS_CHARGING = "is_charging";

	private boolean mIsInitialized = false;

	private boolean mIsWifiConnected = false;

	private boolean mIsCharging = false;

    private SyncthingService mService;

	public DeviceStateHolder(SyncthingService service) {
        mService = service;
		ConnectivityManager cm = (ConnectivityManager)
                mService.getSystemService(Context.CONNECTIVITY_SERVICE);
		mIsWifiConnected = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected();
	}

	@Override
    public void onReceive(Context context, Intent intent) {
        context.unregisterReceiver(this);
        int status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        mIsCharging = status != 0;
        mIsInitialized = true;
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
	}
}
