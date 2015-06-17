package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

public class SyncthingServiceBinderTest extends AndroidTestCase {

    public void testBinder() {
        SyncthingService service = new SyncthingService();
        SyncthingServiceBinder binder = new SyncthingServiceBinder(service);
        assertEquals(service, binder.getService());
    }

}
