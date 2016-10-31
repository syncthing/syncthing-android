package com.nutomic.syncthingandroid.test.activities;

import android.support.test.rule.ActivityTestRule;

import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.test.MockSyncthingService;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class MainActivityTest {

    @Rule
    public final ActivityTestRule<MainActivity> mRule = new ActivityTestRule<>(MainActivity.class);

    private final MockSyncthingService mService = new MockSyncthingService();

    @Test
    public void testOnServiceConnected() {
        mRule.getActivity().onServiceConnected(null, new SyncthingServiceBinder(mService));
        Assert.assertTrue(mService.containsListenerInstance(MainActivity.class));
        Assert.assertTrue(mService.containsListenerInstance(FolderListFragment.class));
        Assert.assertTrue(mService.containsListenerInstance(DeviceListFragment.class));
    }

}
