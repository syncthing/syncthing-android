package com.nutomic.syncthingandroid.test;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Context that saves all received intents, which can be retrieved later by test classes.
 */
public class MockContext extends ContextWrapper {

    private ArrayList<Intent> mReceivedIntents = new ArrayList<>();
    private ArrayList<Intent> mStopServiceIntents = new ArrayList<>();

    /**
     * Use the actual context for calls that aren't easily mocked. May be null if those
     * calls aren't needed.
     */
    public MockContext(Context context) {
        super(context);
    }

    @Override
    public String getPackageName() {
        return null;
    }

    @Override
    public ComponentName startService(Intent intent) {
        mReceivedIntents.add(intent);
        return null;
    }

    @Override
    public boolean stopService(Intent intent) {
        mStopServiceIntents.add(intent);
        return true;
    }

    public List<Intent> getReceivedIntents() {
        return mReceivedIntents;
    }

    public List<Intent> getStopServiceIntents() {
        return mStopServiceIntents;
    }

    public void clearReceivedIntents() {
        mReceivedIntents.clear();
    }

    private BroadcastReceiver mLastUnregistered;

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        mLastUnregistered = receiver;
    }

    public BroadcastReceiver getLastUnregistered() {
        return mLastUnregistered;
    }

    @Override
    public File getFilesDir() {
        File testFilesDir = new File(super.getFilesDir(), "test/");
        testFilesDir.mkdir();
        return testFilesDir;
    }

}
