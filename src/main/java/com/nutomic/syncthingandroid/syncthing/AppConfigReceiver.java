package com.nutomic.syncthingandroid.syncthing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 *
 * Created by sqrt-1764 on 25.03.16.
 */
public class AppConfigReceiver extends BroadcastReceiver {
    public static final String ACTION_QUIT_SYNCTHING = "quit";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();

        if (extras == null) return;

        for (String key : extras.keySet()) {
            switch (key) {
                case ACTION_QUIT_SYNCTHING:
                    context.stopService(new Intent(context, SyncthingService.class));
                    break;
            }
        }
    }
}
