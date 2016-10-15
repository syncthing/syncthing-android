package com.nutomic.syncthingandroid.test.syncthing;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.http.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;
import com.nutomic.syncthingandroid.util.Util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RestApiTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private RestApi mApi;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();

        new SyncthingRunnable(context, SyncthingRunnable.Command.main);

        ConfigXml config = new ConfigXml(context);

        String httpsCertPath = context.getFilesDir() + "/" + SyncthingService.HTTPS_CERT_FILE;

        final CountDownLatch latch = new CountDownLatch(2);
        new PollWebGuiAvailableTask(config.getWebGuiUrl(), httpsCertPath, config.getApiKey(), result -> {
            mApi.onWebGuiAvailable();
            latch.countDown();
        }).execute();
        mApi = new RestApi(context, config.getWebGuiUrl(), config.getApiKey(),
                new RestApi.OnApiAvailableListener() {
                    @Override
                    public void onApiAvailable() {
                        latch.countDown();
                    }
                }, null);
        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() throws Exception {
        // TODO: Unit tests fail when Syncthing is killed
        // SyncthingRunnable.killSyncthing();
        Context context = InstrumentationRegistry.getTargetContext();
        ConfigXml.getConfigFile(new MockContext(context)).delete();
    }

    @Test
    public void testGetDevices() {
        Assert.assertNotNull(mApi.getDevices(false));
    }

    @Test
    public void testGetSystemInfo() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getSystemInfo((info) -> {
            Assert.assertNotNull(info);
            latch.countDown();
        });
        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testGetSystemVersion() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getSystemVersion(info -> {
            Assert.assertNotNull(info);
            latch.countDown();
        });
        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testGetFolders() {
        Assert.assertNotNull(mApi.getFolders());
    }

    @Test
    public void testConvertNotCrashing() {
        long[] values = new long[]{-1, 0, 1, 2, 4, 8, 16, 1024, 2^10, 2^15, 2^20, 2^25, 2^30};
        for (long l : values) {
            Assert.assertNotSame("", Util.readableFileSize(InstrumentationRegistry.getTargetContext(), l));
            Assert.assertNotSame("", Util.readableTransferRate(InstrumentationRegistry.getTargetContext(), l));
        }
    }

    @Test
    public void testGetConnections() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getConnections(connections -> {
            Assert.assertNotNull(connections);
            latch.countDown();
        });
        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testGetModel() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getModel("camera", (folderId, model) -> {
            Assert.assertNotNull(model);
            latch.countDown();
        });
        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testNormalizeDeviceId() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.normalizeDeviceId("p56ioi7m--zjnu2iq-gdr-eydm-2mgtmgl3bxnpq6w5btbbz4tjxzwicq",
                (normalizedId, error) -> {
            Assert.assertEquals("P56IOI7-MZJNU2Y-IQGDREY-DM2MGTI-MGL3BXN-PQ6W5BM-TBBZ4TJ-XZWICQ2",
                    normalizedId);
            latch.countDown();
        });
        Assert.assertTrue(latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void testGetValueEarly() {
        // Should never throw an exception.
        mApi.getValue("Options", "ListenAddress");
    }

}
