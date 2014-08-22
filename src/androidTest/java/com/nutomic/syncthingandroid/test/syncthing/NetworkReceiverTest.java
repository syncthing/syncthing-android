package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.nutomic.syncthingandroid.syncthing.NetworkReceiver;
import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.TestContext;

/**
 * Tests for correct extras on the Intent sent by
 * {@link com.nutomic.syncthingandroid.syncthing.NetworkReceiver}.
 *
 * Does not test for correct result value, as that would require mocking
 * {@link android.net.ConnectivityManager} (or replacing it with an interface).
 */
public class NetworkReceiverTest extends AndroidTestCase {

    private NetworkReceiver mReceiver;
    private TestContext mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new NetworkReceiver();
        mContext = new TestContext(getContext());
    }

    @MediumTest
    public void testOnReceive() {
        mReceiver.onReceive(mContext, null);
        assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        assertEquals(receivedIntent.getComponent().getClassName(), SyncthingService.class.getName());
        assertNull(receivedIntent.getAction());
        assertTrue(receivedIntent.hasExtra(DeviceStateHolder.EXTRA_HAS_WIFI));
        mContext.clearReceivedIntents();
    }

}
