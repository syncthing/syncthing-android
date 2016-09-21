package com.nutomic.syncthingandroid.activities;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

/**
 * Shows restart dialog.
 *
 * The user can choose to restart Syncthing immediately. Otherwise, a restart notification is
 * displayed.
 */
public class RestartActivity extends SyncthingActivity {

    public static final int NOTIFICATION_RESTART = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.restart_title)
                .setPositiveButton(R.string.restart_now, (dialogInterface, i) -> {
                    getService().getApi().updateConfig();
                    finish();
                })
                .setNegativeButton(R.string.restart_later, (dialogInterface, i) -> {
                    createRestartNotification();
                    finish();
                })
                .setOnCancelListener(dialog -> {
                    createRestartNotification();
                    finish();
                })
                .show();
    }

    /**
     * Creates a notification prompting the user to restart the app.
     */
    private void createRestartNotification() {
        Intent intent = new Intent(this, SyncthingService.class)
                .setAction(SyncthingService.ACTION_RESTART);
        PendingIntent pi = PendingIntent.getService(this, 0, intent, 0);

        Notification n = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.restart_title))
                .setContentText(getString(R.string.restart_notification_text))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(pi)
                .build();
        n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_RESTART, n);
        getApi().setRestartPostponed();
    }

}
