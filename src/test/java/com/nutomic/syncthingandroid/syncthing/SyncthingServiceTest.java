package com.nutomic.syncthingandroid.syncthing;

import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import com.nutomic.syncthingandroid.BuildConfig;
import com.nutomic.syncthingandroid.util.ConfigXml;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * These tests assume that syncthing keys have already been generated. If not, tests may fail
 * because startup takes too long.
 */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class SyncthingServiceTest {

    private SyncthingService mService;

    @Before
    public void setup() {
        mService = Robolectric.buildService(SyncthingService.class).get();
        mService.onCreate();
    }

    @Test
    public void testFirstStart() {
        assertTrue(mService.isFirstStart());
    }

    @Test
    public void testNotFirstStart() throws IOException {
        new File(mService.getFilesDir(), SyncthingService.PUBLIC_KEY_FILE).createNewFile();
        assertFalse(mService.isFirstStart());
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
        assertNotNull(mService);
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
            fail();
        }

        mService.exportConfig();

        config.delete();
        privateKey.delete();
        publicKey.delete();

        assertTrue(mService.importConfig());
        assertTrue(config.exists());
        assertTrue(privateKey.exists());
        assertTrue(publicKey.exists());
    }

    @Test
    public void testPassword() throws InterruptedException {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mService);
                assertNotNull(sp.getString("gui_user", null));
                assertEquals(20, sp.getString("gui_password", null).length());
            }
        }, 5000);
    }

}
