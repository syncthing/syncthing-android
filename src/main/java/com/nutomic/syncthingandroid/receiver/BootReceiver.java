package com.nutomic.syncthingandroid.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.nutomic.syncthingandroid.service.SyncthingService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) &&
                !intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED))
            return;

        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        context.startService(new Intent(context, SyncthingService.class));
    }
}
