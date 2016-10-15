package com.nutomic.syncthingandroid.receiver;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

/**
 * Broadcast-receiver to control and configure SyncThing remotely
 *
 * Created by sqrt-1764 on 25.03.16.
 */
public class AppConfigReceiver extends BroadcastReceiver {
    private static final int ID_NOTIFICATION_BACKGROUND_ACTIVE = 3;

    /**
     * Start the Syncthing-Service
     */
    public static final String ACTION_START = "com.nutomic.syncthingandroid.action.START";

    /**
     * Stop the Syncthing-Service
     * If alwaysRunInBackground is enabled the service must not be stopped. Instead a
     * notification is presented to the user.
     */
    public static final String ACTION_STOP  = "com.nutomic.syncthingandroid.action.STOP";

    @Override
    @TargetApi(21)
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ACTION_START:
                context.startService(new Intent(context, SyncthingService.class));
                break;

            case ACTION_STOP:
                if (SyncthingService.alwaysRunInBackground(context)) {
                    final String msg = context.getString(R.string.appconfig_receiver_background_enabled);

                    Context appContext = context.getApplicationContext();

                    NotificationCompat.Builder nb = new NotificationCompat.Builder(context)
                            .setContentText(msg)
                            .setTicker(msg)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                            .setContentTitle(context.getText(context.getApplicationInfo().labelRes))
                            .setSmallIcon(R.drawable.ic_stat_notify)
                            .setAutoCancel(true)
                            .setContentIntent(PendingIntent.getActivity(appContext,
                                                        0,
                                                        new Intent(appContext, MainActivity.class),
                                                        PendingIntent.FLAG_UPDATE_CURRENT));

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        nb.setCategory(Notification.CATEGORY_ERROR);        // Only supported in API 21 or better
                    }

                    NotificationManager nm =
                            (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);

                    nm.notify(ID_NOTIFICATION_BACKGROUND_ACTIVE, nb.build());

                } else {
                    context.stopService(new Intent(context, SyncthingService.class));
                }
                break;
        }
    }
}
