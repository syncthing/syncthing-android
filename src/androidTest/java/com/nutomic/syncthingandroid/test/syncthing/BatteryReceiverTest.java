package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.receiver.BatteryReceiver;
import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class BatteryReceiverTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private BatteryReceiver mReceiver;
    private MockContext mContext;

    @Before
    public void setUp() throws Exception {
        mReceiver = new BatteryReceiver();
        mContext = new MockContext(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void tearDown() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().apply();
    }

    @Test
    public void testOnReceiveCharging() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .apply();
        Intent intent = new Intent(Intent.ACTION_POWER_CONNECTED);

        mReceiver.onReceive(mContext, intent);
        Assert.assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        Assert.assertTrue(receivedIntent.getBooleanExtra(DeviceStateHolder.EXTRA_IS_CHARGING, false));
        mContext.clearReceivedIntents();
    }

    @Test
    public void testOnReceiveNotCharging() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .apply();
        Intent intent = new Intent(Intent.ACTION_POWER_DISCONNECTED);

        mReceiver.onReceive(mContext, intent);
        Assert.assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        Assert.assertEquals(SyncthingService.class.getName(), receivedIntent.getComponent().getClassName());
        Assert.assertFalse(receivedIntent.getBooleanExtra(DeviceStateHolder.EXTRA_IS_CHARGING, true));
        mContext.clearReceivedIntents();
    }

    @Test
    public void testOnlyRunInForeground() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .apply();
        mReceiver.onReceive(mContext, new Intent(Intent.ACTION_POWER_CONNECTED));
        Assert.assertEquals(0, mContext.getReceivedIntents().size());
        mReceiver.onReceive(mContext, new Intent(Intent.ACTION_POWER_DISCONNECTED));
        Assert.assertEquals(0, mContext.getReceivedIntents().size());
    }

}
