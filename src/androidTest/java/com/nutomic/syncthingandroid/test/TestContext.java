package com.nutomic.syncthingandroid.test;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.test.mock.MockContext;

import junit.framework.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Context that saves all received intents, which can be retrieved later by test classes.
 */
public class TestContext extends MockContext {

    private Context mContext;

    private ArrayList<Intent> mReceivedIntents = new ArrayList<>();

    /**
     * Use the actual context for calls that aren't easily mocked. May be null if those
     * calls aren't needed.
     */
    public TestContext(Context context) {
        mContext = context;
    }

    @Override
    public String getPackageName() {
        return null;
    }

    @Override
    public Object getSystemService(String name) {
        return mContext.getSystemService(name);
    }

    @Override
    public ComponentName startService(Intent intent) {
        mReceivedIntents.add(intent);
        return null;
    }

    public List<Intent> getReceivedIntents() {
        return mReceivedIntents;
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
        File testFilesDir = new File(mContext.getFilesDir(), "test/");
        testFilesDir.mkdir();
        return testFilesDir;
    }

    @Override
    public Resources getResources() {
        return mContext.getResources();
    }
}
