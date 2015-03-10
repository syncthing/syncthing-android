package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.util.Pair;

import com.nutomic.syncthingandroid.syncthing.DeviceStateHolder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.test.Util;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * These tests assume that syncthing keys have already been generated. If not, tests may fail
 * because startup takes too long.
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
        latch.await(5, TimeUnit.SECONDS);
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

    public void testImportExportConfig() {
        setContext(mContext);
        SyncthingServiceBinder binder = (SyncthingServiceBinder)
                bindService(new Intent(getContext(), SyncthingService.class));
        SyncthingService service = binder.getService();
        File config    = new File(mContext.getFilesDir(), ConfigXml.CONFIG_FILE);
        File privateKey = new File(mContext.getFilesDir(), SyncthingService.PRIVATE_KEY_FILE);
        File publicKey = new File(mContext.getFilesDir(), SyncthingService.PUBLIC_KEY_FILE);

        try {
            config.createNewFile();
            privateKey.createNewFile();
            publicKey.createNewFile();
        } catch (IOException e) {
            fail();
        }

        service.exportConfig();

        config.delete();
        privateKey.delete();
        publicKey.delete();

        service.importConfig();
        assertTrue(config.exists());
        assertTrue(privateKey.exists());
        assertTrue(publicKey.exists());
    }

}
