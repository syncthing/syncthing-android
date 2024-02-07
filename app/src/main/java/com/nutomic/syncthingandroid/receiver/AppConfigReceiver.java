package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.SyncthingService;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Broadcast-receiver to control and configure Syncthing remotely.
 */
@AndroidEntryPoint
public class AppConfigReceiver extends BroadcastReceiver {

    /**
     * Start the Syncthing-Service
     */
    private static final String ACTION_START = "com.nutomic.syncthingandroid.action.START";

    /**
     * Stop the Syncthing-Service
     * If startServiceOnBoot is enabled the service must not be stopped. Instead a
     * notification is presented to the user.
     */
    private static final String ACTION_STOP  = "com.nutomic.syncthingandroid.action.STOP";

    @Inject NotificationHandler mNotificationHandler;

    @Override
    public void onReceive(@ApplicationContext Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_START:
                BootReceiver.startServiceCompat(context);
                break;
            case ACTION_STOP:
                if (startServiceOnBoot(context)) {
                    mNotificationHandler.showStopSyncthingWarningNotification();
                } else {
                    context.stopService(new Intent(context, SyncthingService.class));
                }
                break;
        }
    }

    private static boolean startServiceOnBoot(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false);
    }
}
