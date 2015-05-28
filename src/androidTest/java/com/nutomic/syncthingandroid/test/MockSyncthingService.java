package com.nutomic.syncthingandroid.test;

import android.content.Intent;
import android.os.IBinder;

import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.LinkedList;

public class MockSyncthingService extends SyncthingService {

    private LinkedList<OnApiChangeListener> mOnApiChangedListeners = new LinkedList<>();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onCreate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDestroy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerOnWebGuiAvailableListener(OnWebGuiAvailableListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFirstStart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RestApi getApi() {
        return new MockRestApi(this, null, null, null, null, null);
    }

    @Override
    public void registerOnApiChangeListener(OnApiChangeListener listener) {
        mOnApiChangedListeners.add(listener);
    }

    public boolean containsListenerInstance(Class clazz) {
        for(OnApiChangeListener l : mOnApiChangedListeners) {
            if (l.getClass().equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getWebGuiUrl() {
        throw new UnsupportedOperationException();
    }

}
