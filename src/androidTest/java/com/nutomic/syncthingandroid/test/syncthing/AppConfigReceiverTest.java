package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.receiver.AppConfigReceiver;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test the correct behaviour of the AppConfigReceiver
 */
public class AppConfigReceiverTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private AppConfigReceiver mReceiver;
    private MockContext mContext;

    @Before
    public void setUp() throws Exception {
        mReceiver = new AppConfigReceiver();
        mContext = new MockContext(InstrumentationRegistry.getTargetContext());
    }

    @After
    public void tearDown() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().apply();
    }


    /**
     * Test starting the Syncthing-Service if "always run in background" is enabled
     * In this case starting the service is allowed
     */
    @Test
    public void testStartSyncthingServiceBackground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .apply();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_START);

        mReceiver.onReceive(mContext, intent);
        Assert.assertEquals("Start SyncthingService Background", 1, mContext.getReceivedIntents().size());
        Assert.assertEquals("Start SyncthingService Background", SyncthingService.class.getName(),
                        mContext.getReceivedIntents().get(0).getComponent().getClassName());
    }

    /**
     * Test stopping the service if "alway run in background" is enabled.
     * Stopping the service in this mode is not allowed, so no stopService-intent may be issued.
     */
    @Test
    public void testStopSyncthingServiceBackground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .apply();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_STOP);

        mReceiver.onReceive(mContext, intent);
        Assert.assertEquals("Stop SyncthingService Background", 0, mContext.getStopServiceIntents().size());
    }

    /**
     * Test starting the Syncthing-Service if "always run in background" is disabled
     * In this case starting the service is allowed
     */
    @Test
    public void testStartSyncthingServiceForeground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .apply();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_START);

        mReceiver.onReceive(mContext, intent);
        Assert.assertEquals("Start SyncthingService Foreround", 1, mContext.getReceivedIntents().size());
        Assert.assertEquals("Start SyncthingService Foreround", SyncthingService.class.getName(),
                            mContext.getReceivedIntents().get(0).getComponent().getClassName());
    }

    /**
     * Test stopping the Syncthing-Service if "always run in background" is disabled
     * In this case stopping the service is allowed
     */
    @Test
    public void testStopSyncthingServiceForeground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .apply();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_STOP);

        mReceiver.onReceive(mContext, intent);
        Assert.assertEquals("Stop SyncthingService Foreround", 1, mContext.getStopServiceIntents().size());
        Intent receivedIntent = mContext.getStopServiceIntents().get(0);
        Assert.assertEquals("Stop SyncthingService Foreround", SyncthingService.class.getName(),
                                                receivedIntent.getComponent().getClassName());
    }
}
