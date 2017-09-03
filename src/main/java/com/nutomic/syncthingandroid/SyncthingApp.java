package com.nutomic.syncthingandroid;

import android.app.Application;
import android.os.StrictMode;

import com.nutomic.syncthingandroid.util.Languages;

public class SyncthingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new Languages(this).setLanguage(this);

        // Set VM policy to avoid crash when sending folder URI to file manager.
        StrictMode.VmPolicy policy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(policy);
    }
}
