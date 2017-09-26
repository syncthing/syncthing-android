package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.os.BatteryManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.service.DeviceStateHolder;
import com.nutomic.syncthingandroid.test.MockContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeviceStateHolderTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private DeviceStateHolder mReceiver;
    private MockContext mContext;

    @Before
    public void setUp() throws Exception {
        mReceiver = new DeviceStateHolder(InstrumentationRegistry.getTargetContext());
        mContext = new MockContext(null);
    }

    @Test
    public void testIsCharging() {
        Intent i = new Intent();
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, false);
        mReceiver.update(i);
        Assert.assertFalse(mReceiver.isCharging());

        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, true);
        mReceiver.update(i);
        Assert.assertTrue(mReceiver.isCharging());
    }

    @Test
    public void testWifiConnected() {
        Intent i = new Intent();
        i.putExtra(DeviceStateHolder.EXTRA_IS_ALLOWED_NETWORK_CONNECTION, false);
        mReceiver.update(i);
        Assert.assertFalse(mReceiver.isAllowedNetworkConnection());

        i.putExtra(DeviceStateHolder.EXTRA_IS_ALLOWED_NETWORK_CONNECTION, true);
        mReceiver.update(i);
        Assert.assertTrue(mReceiver.isAllowedNetworkConnection());
    }

}
