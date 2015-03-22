package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RestApiTest extends AndroidTestCase {

    private RestApi mApi;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSyncthing = new SyncthingRunnable(new MockContext(null), SyncthingRunnable.Command.main);

        ConfigXml config = new ConfigXml(new MockContext(getContext()));
        config.changeDefaultFolder();

        String httpsCertPath = getContext().getFilesDir() + "/" + SyncthingService.HTTPS_CERT_FILE;

        final CountDownLatch latch = new CountDownLatch(2);
        new PollWebGuiAvailableTask(httpsCertPath) {
            @Override
            protected void onPostExecute(Void aVoid) {
                mApi.onWebGuiAvailable();
                latch.countDown();
            }
        }.execute(config.getWebGuiUrl());
        mApi = new RestApi(getContext(), config.getWebGuiUrl(), config.getApiKey(),
                new RestApi.OnApiAvailableListener() {
            @Override
            public void onApiAvailable() {
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        SyncthingRunnable.killSyncthing();
        ConfigXml.getConfigFile(new MockContext(getContext())).delete();
    }

    @SmallTest
    public void testGetDevices() {
        assertNotNull(mApi.getDevices(false));
    }

    @MediumTest
    public void testGetSystemInfo() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getSystemInfo(new RestApi.OnReceiveSystemInfoListener() {
            @Override
            public void onReceiveSystemInfo(RestApi.SystemInfo info) {
                assertNotNull(info);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @SmallTest
    public void testGetFolders() {
        assertNotNull(mApi.getFolders());
    }

    @SmallTest
    public void testReadableFileSize() {
        assertEquals("1 MiB", RestApi.readableFileSize(getContext(), 1048576));
        assertEquals("1 GiB", RestApi.readableFileSize(getContext(), 1073741824));
    }

    @SmallTest
    public void testGetReadableTransferRate() {
        assertEquals("1 Mib/s", RestApi.readableTransferRate(getContext(), 1048576));
        assertEquals("1 Gib/s", RestApi.readableTransferRate(getContext(), 1073741824));
    }

    @MediumTest
    public void testGetConnections() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getConnections(new RestApi.OnReceiveConnectionsListener() {
            @Override
            public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
                assertNotNull(connections);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @MediumTest
    public void testGetModel() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getModel("camera", new RestApi.OnReceiveModelListener() {
            @Override
            public void onReceiveModel(String folderId, RestApi.Model model) {
                assertNotNull(model);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @MediumTest
    public void testNormalizeDeviceId() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.normalizeDeviceId("p56ioi7m--zjnu2iq-gdr-eydm-2mgtmgl3bxnpq6w5btbbz4tjxzwicq",
                new RestApi.OnDeviceIdNormalizedListener() {
            @Override
            public void onDeviceIdNormalized(String normalizedId, String error) {
                assertEquals("P56IOI7-MZJNU2Y-IQGDREY-DM2MGTI-MGL3BXN-PQ6W5BM-TBBZ4TJ-XZWICQ2",
                        normalizedId);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @SmallTest
    public void testGetValueEarly() {
        // Should never throw an exception.
        mApi.getValue("Options", "ListenAddress");
    }

}
