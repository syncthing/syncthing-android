package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.os.BatteryManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.test.TestContext;

public class DeviceStateHolderTest extends AndroidTestCase {

    private DeviceStateHolder mReceiver;
    private TestContext mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new DeviceStateHolder(getContext());
        mContext = new TestContext(null);
    }

    @MediumTest
    public void testIsCharging() {
        Intent i = new Intent();
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, false);
        mReceiver.update(i);
        assertFalse(mReceiver.isCharging());

        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, true);
        mReceiver.update(i);
        assertTrue(mReceiver.isCharging());
    }

    @MediumTest
    public void testWifiConnected() {
        Intent i = new Intent();
        i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, false);
        mReceiver.update(i);
        assertFalse(mReceiver.isWifiConnected());

        i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, true);
        mReceiver.update(i);
        assertTrue(mReceiver.isWifiConnected());
    }

    @MediumTest
    public void testonReceiveInitialChargingState() {
        Intent i = new Intent();
        mReceiver.onReceive(mContext, i);
        assertFalse(mReceiver.isCharging());
        assertEquals(mContext.getLastUnregistered(), mReceiver);

        i.putExtra(BatteryManager.EXTRA_PLUGGED, 0);
        mReceiver.onReceive(mContext, i);
        assertFalse(mReceiver.isCharging());

        i.putExtra(BatteryManager.EXTRA_PLUGGED, 1);
        mReceiver.onReceive(mContext, i);
        assertTrue(mReceiver.isCharging());
    }

}
