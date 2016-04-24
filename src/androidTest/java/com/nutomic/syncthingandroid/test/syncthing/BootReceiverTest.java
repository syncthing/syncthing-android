package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.preference.PreferenceManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.nutomic.syncthingandroid.syncthing.BootReceiver;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;

/**
 * Tests that {@link com.nutomic.syncthingandroid.syncthing.BootReceiver} starts the right service
 * ({@link com.nutomic.syncthingandroid.syncthing.SyncthingService}.
 */
public class BootReceiverTest extends AndroidTestCase {

    private BootReceiver mReceiver;
    private MockContext mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReceiver = new BootReceiver();
        mContext = new MockContext(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().commit();
        super.tearDown();
    }

    public void testOnReceiveCharging() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, true)
                .commit();
        mReceiver.onReceive(mContext, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(1, mContext.getReceivedIntents().size());

        Intent receivedIntent = mContext.getReceivedIntents().get(0);
        assertEquals(SyncthingService.class.getName(), receivedIntent.getComponent().getClassName());
        mContext.clearReceivedIntents();
    }

    public void testOnlyRunInForeground() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, false)
                .commit();
        mReceiver.onReceive(mContext, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(0, mContext.getReceivedIntents().size());
    }

}
