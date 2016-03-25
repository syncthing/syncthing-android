package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;

import com.nutomic.syncthingandroid.syncthing.AppConfigReceiver;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;

/**
 * Test the correct behaviour of the AppConfigReceiver
 *
 * Created by sqrt-1674 on 27.03.16.
 */
public class AppConfigReceiverTest extends AndroidTestCase {
    private AppConfigReceiver mReceiver;
    private MockContext mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new AppConfigReceiver();
        mContext = new MockContext(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().commit();
        super.tearDown();
    }


    /**
     * Test starting the Syncthing-Service if "always run in background" is enabled
     * In this case starting the service is allowed
     */
    public void testStartSyncthingServiceBackground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .commit();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_START);

        mReceiver.onReceive(mContext, intent);
        assertEquals("Start SyncthingService Background", 1, mContext.getReceivedIntents().size());
        assertEquals("Start SyncthingService Background", SyncthingService.class.getName(),
                        mContext.getReceivedIntents().get(0).getComponent().getClassName());
    }

    /**
     * Test stopping the service if "alway run in background" is enabled.
     * Stopping the service in this mode is not allowed, so no stopService-intent may be issued.
     */
    public void testStopSyncthingServiceBackground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .commit();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_STOP);

        mReceiver.onReceive(mContext, intent);
        assertEquals("Stop SyncthingService Background", 0, mContext.getStopServiceIntents().size());
    }

    /**
     * Test starting the Syncthing-Service if "always run in background" is disabled
     * In this case starting the service is allowed
     */
    public void testStartSyncthingServiceForeground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .commit();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_START);

        mReceiver.onReceive(mContext, intent);
        assertEquals("Start SyncthingService Foreround", 1, mContext.getReceivedIntents().size());
        assertEquals("Start SyncthingService Foreround", SyncthingService.class.getName(),
                            mContext.getReceivedIntents().get(0).getComponent().getClassName());
    }

    /**
     * Test stopping the Syncthing-Service if "always run in background" is disabled
     * In this case stopping the service is allowed
     */
    public void testStopSyncthingServiceForeground() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .commit();

        Intent intent = new Intent(new Intent(mContext, AppConfigReceiver.class));
        intent.setAction(AppConfigReceiver.ACTION_STOP);

        mReceiver.onReceive(mContext, intent);
        assertEquals("Stop SyncthingService Foreround", 1, mContext.getStopServiceIntents().size());
        Intent receivedIntent = mContext.getStopServiceIntents().get(0);
        assertEquals("Stop SyncthingService Foreround", SyncthingService.class.getName(),
                                                receivedIntent.getComponent().getClassName());
    }
}
