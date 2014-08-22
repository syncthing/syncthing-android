package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.nutomic.syncthingandroid.syncthing.BatteryReceiver;
import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.TestContext;

public class BatteryReceiverTest extends AndroidTestCase {

    private BatteryReceiver mReceiver;
    private TestContext mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new BatteryReceiver();
        mContext = new TestContext(null);
    }

    @MediumTest
    public void testOnReceiveCharging() {
        Intent intent = new Intent(Intent.ACTION_POWER_CONNECTED);

        mReceiver.onReceive(mContext, intent);
        assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        assertTrue(receivedIntent.getBooleanExtra(DeviceStateHolder.EXTRA_IS_CHARGING, false));
        mContext.clearReceivedIntents();
    }

    @MediumTest
    public void testOnReceiveNotCharging() {
        Intent intent = new Intent(Intent.ACTION_POWER_DISCONNECTED);

        mReceiver.onReceive(mContext, intent);
        assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        assertEquals(receivedIntent.getComponent().getClassName(), SyncthingService.class.getName());
        assertFalse(receivedIntent.getBooleanExtra(DeviceStateHolder.EXTRA_IS_CHARGING, true));
        mContext.clearReceivedIntents();
    }

}
