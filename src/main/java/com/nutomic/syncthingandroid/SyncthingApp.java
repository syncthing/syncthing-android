package com.nutomic.syncthingandroid;

import android.app.Application;

import com.nutomic.syncthingandroid.util.Languages;

public class SyncthingApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new Languages(this).setLanguage(this);
    }
}
