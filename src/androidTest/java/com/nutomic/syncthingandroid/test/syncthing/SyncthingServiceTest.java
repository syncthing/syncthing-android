package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.test.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * These tests assume that syncthing keys have already been generated. If not, tests may fail
 * because startup takes too long.
 *
 * FIXME: These tests are rather fragile and may fail even if they shouldn't. Repeating them
 *        should fix this.
 * NOTE: If a test fails with "expected:<ACTIVE> but was:<INIT>", you may have to increase
 *       {@link #STARTUP_TIME_SECONDS}.
 */
public class SyncthingServiceTest extends ServiceTestCase<SyncthingService> {

    private static final int STARTUP_TIME_SECONDS = 90;

    private Context mContext;

    private CountDownLatch mLatch;

    public SyncthingServiceTest() {
        super(SyncthingService.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = new MockContext(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        Util.deleteRecursive(mContext.getFilesDir());
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit().clear().commit();
        super.tearDown();
    }

    @LargeTest
    public void testStartService() throws InterruptedException {
        startService(new Intent(getContext(), SyncthingService.class));
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
        setContext(mContext);
        startService(new Intent(mContext, SyncthingService.class));
        assertTrue(getService().isFirstStart());
    }

    @MediumTest
    public void testNotFirstStart() throws IOException {
        setContext(mContext);
        startService(new Intent(mContext, SyncthingService.class));
        new File(mContext.getFilesDir(), SyncthingService.PUBLIC_KEY_FILE).createNewFile();
        assertFalse(getService().isFirstStart());
    }

    @SmallTest
    public void testBindService() throws InterruptedException {
        SyncthingServiceBinder binder = (SyncthingServiceBinder)
                bindService(new Intent(getContext(), SyncthingService.class));
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
            mLatch.countDown();

            mLastState = currentState;
        }

        public SyncthingService.State getLastState() {
            return mLastState;
        }

    }

    private Listener mListener = new Listener();

    @MediumTest
    public void testStatesAllRequired() throws InterruptedException {
        setupStatesTest(true, true, true);

        assertState(true, true, SyncthingService.State.ACTIVE);

        assertState(true, false, SyncthingService.State.DISABLED);
        assertState(false, true, SyncthingService.State.DISABLED);
        assertState(false, false, SyncthingService.State.DISABLED);
    }

    @MediumTest
    public void testStatesWifiRequired() throws InterruptedException {
        setupStatesTest(true, true, false);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(false, true, SyncthingService.State.ACTIVE);

        assertState(true, false, SyncthingService.State.DISABLED);
        assertState(false, false, SyncthingService.State.DISABLED);
    }

    @MediumTest
    public void testStatesChargingRequired() throws InterruptedException {
        setupStatesTest(true, false, true);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(true, false, SyncthingService.State.ACTIVE);

        assertState(false, true, SyncthingService.State.DISABLED);
        assertState(false, false, SyncthingService.State.DISABLED);
    }

    @MediumTest
    public void testStatesNoneRequired() throws InterruptedException {
        setupStatesTest(true, false, false);

        assertState(true, true, SyncthingService.State.ACTIVE);
        assertState(true, false, SyncthingService.State.ACTIVE);
        assertState(false, true, SyncthingService.State.ACTIVE);
        assertState(false, false, SyncthingService.State.ACTIVE);
    }

    public void assertState(boolean charging, boolean wifi, SyncthingService.State expected)
            throws InterruptedException {
        Intent i = new Intent(getContext(), SyncthingService.class);
        i.putExtra(DeviceStateHolder.EXTRA_IS_CHARGING, charging);
        i.putExtra(DeviceStateHolder.EXTRA_HAS_WIFI, wifi);
        mLatch = new CountDownLatch(1);
        startService(i);
        // Wait for service to react to preference change.
        mLatch.await(1, TimeUnit.SECONDS);
        assertEquals(expected, mListener.getLastState());
    }

    public void setupStatesTest(boolean alwaysRunInBackground,
            boolean syncOnlyWifi, boolean syncOnlyCharging) throws InterruptedException {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .edit()
                .putBoolean(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND, alwaysRunInBackground)
                .putBoolean(SyncthingService.PREF_SYNC_ONLY_WIFI, syncOnlyWifi)
                .putBoolean(SyncthingService.PREF_SYNC_ONLY_CHARGING, syncOnlyCharging)
                .commit();

        startService(new Intent(getContext(), SyncthingService.class));
        // 3 calls plus 1 call immediately when registering.
        mLatch = new CountDownLatch(4);
        getService().registerOnApiChangeListener(mListener);
        if (mListener.getLastState() != SyncthingService.State.ACTIVE) {
            // Wait for service to start.
            mLatch.await(STARTUP_TIME_SECONDS, TimeUnit.SECONDS);
            assertEquals(SyncthingService.State.ACTIVE, mListener.getLastState());
        }
    }

    /**
     * For all possible settings and charging/wifi states, service should be active.
     */
    @LargeTest
    public void testOnlyForeground() throws InterruptedException {
        ArrayList<Pair<Boolean, Boolean>> values = new ArrayList<>();
        values.add(new Pair(true, true));
        values.add(new Pair(true, false));
        values.add(new Pair(false, true));
        values.add(new Pair(false, false));

        for (Pair<Boolean, Boolean> v : values) {
            setupStatesTest(false, v.first, v.second);

            assertState(true, true, SyncthingService.State.ACTIVE);
            assertState(true, false, SyncthingService.State.ACTIVE);
            assertState(false, true, SyncthingService.State.ACTIVE);
            assertState(false, false, SyncthingService.State.ACTIVE);
        }
    }

}
