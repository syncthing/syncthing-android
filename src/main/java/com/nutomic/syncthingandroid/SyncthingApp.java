package com.nutomic.syncthingandroid;

import android.app.Application;

import com.nutomic.syncthingandroid.util.Languages;

import javax.inject.Inject;

public class SyncthingApp extends Application {

    @Inject DaggerComponent mComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        DaggerDaggerComponent.builder()
                .syncthingModule(new SyncthingModule(this))
                .build()
                .inject(this);

        new Languages(this).setLanguage(this);
    }

    public DaggerComponent component() {
        return mComponent;
    }
}
