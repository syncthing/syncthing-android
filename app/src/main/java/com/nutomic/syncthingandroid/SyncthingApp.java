package com.nutomic.syncthingandroid;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.StrictMode;

import com.google.android.material.color.DynamicColors;
import com.nutomic.syncthingandroid.util.Languages;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class SyncthingApp extends Application {

    // temporarily here
    @Inject
    SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this);

        super.onCreate();

        new Languages(this, mSharedPreferences).setLanguage(this);

        // The main point here is to use a VM policy without
        // `detectFileUriExposure`, as that leads to exceptions when e.g.
        // opening the ignores file. And it's enabled by default.
        // We might want to disable `detectAll` and `penaltyLog` on release (non-RC) builds too.
        StrictMode.VmPolicy policy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(policy);
    }

}
