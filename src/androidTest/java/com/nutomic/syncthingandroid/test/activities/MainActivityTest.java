package com.nutomic.syncthingandroid.test.activities;

import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.fragments.NodesFragment;
import com.nutomic.syncthingandroid.fragments.ReposFragment;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.test.MockSyncthingService;

public class MainActivityTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MockSyncthingService mService = new MockSyncthingService();

    public MainActivityTest() {
        super(MainActivity.class);
    }

    @SmallTest
    public void testOnServiceConnected() {
        getActivity().onServiceConnected(null, new SyncthingServiceBinder(mService));
        assertTrue(mService.containsListenerInstance(MainActivity.class));
        assertTrue(mService.containsListenerInstance(ReposFragment.class));
        assertTrue(mService.containsListenerInstance(NodesFragment.class));
    }

}
