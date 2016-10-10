package com.nutomic.syncthingandroid.test.syncthing;

import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class SyncthingServiceBinderTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Test
    public void testBinder() {
        SyncthingService service = new SyncthingService();
        SyncthingServiceBinder binder = new SyncthingServiceBinder(service);
        Assert.assertEquals(service, binder.getService());
    }

}
