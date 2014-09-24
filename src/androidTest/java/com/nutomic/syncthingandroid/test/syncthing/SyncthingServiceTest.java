package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.test.Util;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * FIXME: There are some problems with shutting down the service after tests. It may be that the
 *        service remains running after short tests. As a workaround, kill the app in Android.
 * NOTE: It seems that @link #tearDown()} is not executed if a test fails, so the test data folder
 *       is not deleted (which may cause following tests to fail).
 */
public class SyncthingServiceTest extends ServiceTestCase<SyncthingService> {

    private Context mContext;

    public SyncthingServiceTest() {
        super(SyncthingService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new MockContext(getContext());
        setContext(mContext);
    }

    @Override
    protected void tearDown() throws Exception {
        Util.deleteRecursive(getContext().getFilesDir());
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().clear().commit();
        super.tearDown();
    }

    @LargeTest
    public void testStartService() throws InterruptedException {
        startService(new Intent(mContext, SyncthingService.class));
        final CountDownLatch latch = new CountDownLatch(2);
        getService().registerOnWebGuiAvailableListener(new SyncthingService.OnWebGuiAvailableListener() {
            @Override
            public void onWebGuiAvailable() {
                latch.countDown();
            }
        });
        getService().registerOnApiChangeListener(new SyncthingService.OnApiChangeListener() {
            @Override
            public void onApiChange(SyncthingService.State currentState) {
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
        assertNotNull(getService().getApi());
        assertNotNull(getService().getWebGuiUrl());
    }

    @SmallTest
    public void testFirstStart() {
        startService(new Intent(mContext, SyncthingService.class));
        assertTrue(getService().isFirstStart());
    }

    @MediumTest
    public void testNotFirstStart() throws IOException {
        startService(new Intent(mContext, SyncthingService.class));
        new File(mContext.getFilesDir(), SyncthingService.PUBLIC_KEY_FILE).createNewFile();
        assertFalse(getService().isFirstStart());
    }

    @SmallTest
    public void testBindService() throws InterruptedException {
        SyncthingServiceBinder binder = (SyncthingServiceBinder)
                bindService(new Intent(mContext, SyncthingService.class));
        SyncthingService service = binder.getService();
        final CountDownLatch latch = new CountDownLatch(2);
        getService().registerOnWebGuiAvailableListener(new SyncthingService.OnWebGuiAvailableListener() {
            @Override
            public void onWebGuiAvailable() {
                latch.countDown();
            }
        });
        getService().registerOnApiChangeListener(new SyncthingService.OnApiChangeListener() {
            @Override
            public void onApiChange(SyncthingService.State currentState) {
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
        assertNotNull(service);
    }

    private class Listener implements SyncthingService.OnApiChangeListener {

        private SyncthingService.State mLastState;

        @Override
        public void onApiChange(SyncthingService.State currentState) {
            mLastState = currentState;
        }

        public SyncthingService.State getLastState() {
            return mLastState;
        }

    }

    private Listener mListener = new Listener();

    @MediumTest
    public void testStatesAllRequired() throws InterruptedException {
        setupStatesTest(true, true);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(true, false, SyncthingService.State.DISABLED);
        assertState(false, true, SyncthingService.State.DISABLED);
        assertState(false, false, SyncthingService.State.DISABLED);
    }

    @MediumTest
    public void testStatesWifiRequired() throws InterruptedException {
        setupStatesTest(true, false);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(true, false, SyncthingService.State.DISABLED);
        assertState(false, true, SyncthingService.State.ACTIVE);
        assertState(false, false, SyncthingService.State.DISABLED);
    }

    @MediumTest
    public void testStatesChargingRequired() throws InterruptedException {
        setupStatesTest(false, true);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(true, false, SyncthingService.State.ACTIVE);
        assertState(false, true, SyncthingService.State.DISABLED);
        assertState(false, false, SyncthingService.State.DISABLED);
    }

    @MediumTest
    public void testStatesNoneRequired() throws InterruptedException {
        setupStatesTest(false, false);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(true, false, SyncthingService.State.ACTIVE);
        assertState(false, true, SyncthingService.State.ACTIVE);
        assertState(false, false, SyncthingService.State.ACTIVE);
    }

    public void assertState(boolean charging, boolean wifi, SyncthingService.State expected)
            throws InterruptedException {
        Intent i = new Intent(mContext, SyncthingService.class);
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, charging);
        i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, wifi);
        startService(i);
        // Wait for service to react to preference change.
        Thread.sleep(7500);
        assertEquals(expected, mListener.getLastState());
    }

    public void setupStatesTest(boolean syncOnlyWifi, boolean syncOnlyCharging)
            throws InterruptedException {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
                .putBoolean(SyncthingService.PREF_SYNC_ONLY_WIFI, syncOnlyWifi)
                .putBoolean(SyncthingService.PREF_SYNC_ONLY_CHARGING, syncOnlyCharging)
                .commit();
        // Wait for service to react to preference change.
        Thread.sleep(1000);
        startService(new Intent(getContext(), SyncthingService.class));
        getService().registerOnApiChangeListener(mListener);
        assertEquals(SyncthingService.State.INIT, mListener.getLastState());
    }
}
