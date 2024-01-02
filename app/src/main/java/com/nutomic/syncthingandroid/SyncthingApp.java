package com.nutomic.syncthingandroid;

import android.app.Application;
import android.os.StrictMode;

import com.google.android.material.color.DynamicColors;
import com.nutomic.syncthingandroid.util.Languages;

import javax.inject.Inject;

public class SyncthingApp extends Application {

    @Inject DaggerComponent mComponent;

    @Override
    public void onCreate() {
        DynamicColors.applyToActivitiesIfAvailable(this);

        super.onCreate();

        DaggerDaggerComponent.builder()
                .syncthingModule(new SyncthingModule(this))
                .build()
                .inject(this);

        new Languages(this).setLanguage(this);

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

    public DaggerComponent component() {
        return mComponent;
    }
}
