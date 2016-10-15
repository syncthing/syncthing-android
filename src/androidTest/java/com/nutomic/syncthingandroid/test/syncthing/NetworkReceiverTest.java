package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.receiver.NetworkReceiver;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for correct extras on the Intent sent by
 * {@link NetworkReceiver}.
 *
 * Does not test for correct result value, as that would require mocking
 * {@link android.net.ConnectivityManager} (or replacing it with an interface).
 */
public class NetworkReceiverTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private NetworkReceiver mReceiver;
    private MockContext mContext;

    @Before
    public void setUp() throws Exception {
        mReceiver = new NetworkReceiver();
        mContext = new MockContext(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void tearDown() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().apply();
    }

    @Test
    public void testOnReceive() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .apply();
        mReceiver.onReceive(mContext, new Intent(ConnectivityManager.CONNECTIVITY_ACTION));
        Assert.assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        Assert.assertEquals(SyncthingService.class.getName(), receivedIntent.getComponent().getClassName());
        Assert.assertNull(receivedIntent.getAction());
        Assert.assertTrue(receivedIntent.hasExtra(DeviceStateHolder.EXTRA_HAS_WIFI));
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
    }

}
