package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.syncthing.BootReceiver;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests that {@link com.nutomic.syncthingandroid.syncthing.BootReceiver} starts the right service
 * ({@link com.nutomic.syncthingandroid.syncthing.SyncthingService}.
 */
public class BootReceiverTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private BootReceiver mReceiver;
    private MockContext mContext;

    @Before
    public void setUp() throws Exception {
        mReceiver = new BootReceiver();
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
        mReceiver.onReceive(mContext, new Intent(Intent.ACTION_BOOT_COMPLETED));
        Assert.assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        Assert.assertEquals(SyncthingService.class.getName(), receivedIntent.getComponent().getClassName());
        mContext.clearReceivedIntents();
    }

    @Test
    public void testOnlyRunInForeground() {
        PreferenceManager.getDefaultSharedPreferences(InstrumentationRegistry.getTargetContext())
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .apply();
        mReceiver.onReceive(mContext, new Intent(Intent.ACTION_BOOT_COMPLETED));
        Assert.assertEquals(0, mContext.getReceivedIntents().size());
    }

}
