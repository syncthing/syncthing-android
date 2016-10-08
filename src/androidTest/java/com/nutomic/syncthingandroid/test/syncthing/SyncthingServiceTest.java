package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ConfigXml;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * These tests assume that syncthing keys have already been generated. If not, tests may fail
 * because startup takes too long.
 */
public class SyncthingServiceTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private SyncthingService mService;

    @Before
    public void setup() throws TimeoutException {
        Intent intent =
                new Intent(InstrumentationRegistry.getTargetContext(), SyncthingService.class);
        SyncthingServiceBinder binder = (SyncthingServiceBinder) mServiceRule.bindService(intent);
        mService = binder.getService();
    }

    @Test
    public void testFirstStart() {
        Assert.assertTrue(mService.isFirstStart());
    }

    @Test
    public void testNotFirstStart() throws IOException {
        new File(mService.getFilesDir(), SyncthingService.PUBLIC_KEY_FILE).createNewFile();
        Assert.assertFalse(mService.isFirstStart());
    }

    @Test
    public void testBindService() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);
        mService.registerOnWebGuiAvailableListener(latch::countDown);
        mService.registerOnApiChangeListener(new SyncthingService.OnApiChangeListener() {
            @Override
            public void onApiChange(SyncthingService.State currentState) {
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
        Assert.assertNotNull(mService);
    }

    @Test
    public void testImportExportConfig() {
        File config     = new File(mService.getFilesDir(), ConfigXml.CONFIG_FILE);
        File privateKey = new File(mService.getFilesDir(), SyncthingService.PRIVATE_KEY_FILE);
        File publicKey  = new File(mService.getFilesDir(), SyncthingService.PUBLIC_KEY_FILE);

        try {
            config.createNewFile();
            privateKey.createNewFile();
            publicKey.createNewFile();
        } catch (IOException e) {
            Assert.fail();
        }

        mService.exportConfig();

        config.delete();
        privateKey.delete();
        publicKey.delete();

        Assert.assertTrue(mService.importConfig());
        Assert.assertTrue(config.exists());
        Assert.assertTrue(privateKey.exists());
        Assert.assertTrue(publicKey.exists());
    }

    @Test
    public void testPassword() throws InterruptedException {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mService);
                Assert.assertNotNull(sp.getString("gui_user", null));
                Assert.assertEquals(20, sp.getString("gui_password", null).length());
            }
        }, 5000);
    }

}
