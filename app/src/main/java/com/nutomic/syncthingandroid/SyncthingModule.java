package com.nutomic.syncthingandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.util.ConfigXml;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class SyncthingModule {

    @Provides
    @Singleton
    public static SharedPreferences getPreferences(
            @ApplicationContext Context mApp
    ) {
        return PreferenceManager.getDefaultSharedPreferences(mApp);
    }

    @Provides
    @Singleton
    public static NotificationHandler getNotificationHandler(
            @ApplicationContext Context mApp,
            SharedPreferences preferences
    ) {
        return new NotificationHandler(mApp, preferences);
    }

    @Provides
    @Singleton
    public static ConfigXml getConfigXml(@ApplicationContext Context mApp) {
        return new ConfigXml(mApp);
    }
}
