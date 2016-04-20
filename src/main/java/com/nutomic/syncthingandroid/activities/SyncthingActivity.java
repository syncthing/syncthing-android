package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

import java.util.LinkedList;

/**
 * Connects to {@link SyncthingService} and provides access to it.
 */
public abstract class SyncthingActivity extends ToolbarBindingActivity implements ServiceConnection {

    private SyncthingService mSyncthingService;

    private LinkedList<OnServiceConnectedListener> mServiceConnectedListeners = new LinkedList<>();

    /**
     * To be used for Fragments.
     */
    public interface OnServiceConnectedListener {
        void onServiceConnected();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, SyncthingService.class));
    }

    @Override
    protected void onPause() {
        unbindService(this);

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        bindService(new Intent(this, SyncthingService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        SyncthingServiceBinder binder = (SyncthingServiceBinder) iBinder;
        mSyncthingService = binder.getService();
        for (OnServiceConnectedListener listener : mServiceConnectedListeners) {
            listener.onServiceConnected();
        }
        mServiceConnectedListeners.clear();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    /**
     * Used for Fragments to use the Activity's service connection.
     */
    public void registerOnServiceConnectedListener(OnServiceConnectedListener listener) {
        if (mSyncthingService != null) {
            listener.onServiceConnected();
        } else {
            mServiceConnectedListeners.addLast(listener);
        }
    }

    /**
     * Returns service object (or null if not bound).
     */
    public SyncthingService getService() {
        return mSyncthingService;
    }

    /**
     * Returns RestApi instance, or null if SyncthingService is not yet connected.
     */
    public RestApi getApi() {
        return (getService() != null)
                ? getService().getApi()
                : null;
    }

}
