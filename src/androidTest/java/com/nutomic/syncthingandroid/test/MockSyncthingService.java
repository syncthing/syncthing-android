package com.nutomic.syncthingandroid.test;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.LinkedList;
import java.util.List;

public class MockSyncthingService extends SyncthingService {

    private LinkedList<OnApiChangeListener> mOnApiChangedListeners = new LinkedList<>();

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

}
