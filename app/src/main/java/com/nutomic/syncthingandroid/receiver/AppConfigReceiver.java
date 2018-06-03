package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.DeviceStateHolder;
import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.SyncthingService;

import javax.inject.Inject;

/**
 * Broadcast-receiver to control and configure Syncthing remotely.
 */
public class AppConfigReceiver extends BroadcastReceiver {

    private static final String TAG = "AppConfigReceiver";

    /**
     * Start the Syncthing-Service
     */
    private static final String ACTION_START = "com.nutomic.syncthingandroid.action.START";

    /**
     * Stop the Syncthing-Service
     * If alwaysRunInBackground is enabled the service must not be stopped. Instead a
     * notification is presented to the user.
     */
    private static final String ACTION_STOP  = "com.nutomic.syncthingandroid.action.STOP";

    @Inject NotificationHandler mNotificationHandler;

    @Override
    public void onReceive(Context context, Intent intent) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        switch (intent.getAction()) {
            case ACTION_START:
                Log.v(TAG, "AppConfigReceiver startServiceCompat");
                BootReceiver.startServiceCompat(context);
                break;
            case ACTION_STOP:
                if (DeviceStateHolder.alwaysRunInBackground(context)) {
                    mNotificationHandler.showStopSyncthingWarningNotification();
                } else {
                    context.stopService(new Intent(context, SyncthingService.class));
                }
                break;
        }
    }
}
