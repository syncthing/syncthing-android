package com.nutomic.syncthingandroid.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ReceiverManager {

    private static final String TAG = "ReceiverManager";

    private static List<BroadcastReceiver> mReceivers = new ArrayList<BroadcastReceiver>();

    public static void registerReceiver(LocalBroadcastManager localBroadcastManager, BroadcastReceiver receiver, IntentFilter intentFilter) {
        mReceivers.add(receiver);
        localBroadcastManager.registerReceiver(receiver, intentFilter);
        Log.v(TAG, "Registered receiver: " + receiver + " with filter: " + intentFilter);
    }

    public static boolean isReceiverRegistered(BroadcastReceiver receiver) {
        return mReceivers.contains(receiver);
    }

    public static void unregisterReceiver(LocalBroadcastManager localBroadcastManager, BroadcastReceiver receiver) {
        if (localBroadcastManager == null) {
            Log.e(TAG, "unregisterReceiver: localBroadcastManager is null");
            return;
        }
        if (isReceiverRegistered(receiver)) {
            try {
                localBroadcastManager.unregisterReceiver(receiver);
                Log.v(TAG, "Unregistered receiver: " + receiver);
            } catch(IllegalArgumentException e) {
                // We have to catch the race condition a registration is still pending in android
                // according to https://stackoverflow.com/a/3568906
                Log.w(TAG, "unregisterReceiver(" + receiver + ") threw IllegalArgumentException");
            }
            mReceivers.remove(receiver);
        }
    }

    public static void unregisterAllReceivers(LocalBroadcastManager localBroadcastManager) {
        if (localBroadcastManager == null) {
            Log.e(TAG, "unregisterReceiver: localBroadcastManager is null");
            return;
        }
        Iterator<BroadcastReceiver> iter = mReceivers.iterator();
        while (iter.hasNext()) {
            BroadcastReceiver receiver = iter.next();
            if (isReceiverRegistered(receiver)) {
                try {
                    localBroadcastManager.unregisterReceiver(receiver);
                    Log.v(TAG, "Unregistered receiver: " + receiver);
                } catch(IllegalArgumentException e) {
                    // We have to catch the race condition a registration is still pending in android
                    // according to https://stackoverflow.com/a/3568906
                    Log.w(TAG, "unregisterReceiver(" + receiver + ") threw IllegalArgumentException");
                }
                iter.remove();
            }
        }
    }
}
