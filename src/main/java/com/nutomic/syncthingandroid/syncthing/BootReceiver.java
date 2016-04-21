package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
            return;

        if (!SyncthingService.alwaysRunInBackground(context))
            return;

        context.startService(new Intent(context, SyncthingService.class));
    }

}
