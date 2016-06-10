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

        new SyncthingRunnable(new MockContext(getContext()), SyncthingRunnable.Command.main);

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
                null, null,
                new RestApi.OnApiAvailableListener() {
            @Override
            public void onApiAvailable() {
                latch.countDown();
            }
        }, null);
        latch.await(1, TimeUnit.SECONDS);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // TODO: Unit tests fail when Syncthing is killed SyncthingRunnable.killSyncthing();
        ConfigXml.getConfigFile(new MockContext(getContext())).delete();
    }

    public void testGetDevices() {
        assertNotNull(mApi.getDevices(false));
    }

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

    public void testGetSystemVersion() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.getSystemVersion(new RestApi.OnReceiveSystemVersionListener() {
            @Override
            public void onReceiveSystemVersion(RestApi.SystemVersion info) {
                assertNotNull(info);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    public void testGetFolders() {
        assertNotNull(mApi.getFolders());
    }
    
    public void testConvertNotCrashing() {
        long[] values = new long[]{-1, 0, 1, 2, 4, 8, 16, 1024, 2^10, 2^15, 2^20, 2^25, 2^30};
        for (long l : values) {
            assertNotSame("", RestApi.readableFileSize(getContext(), l));
            assertNotSame("", RestApi.readableTransferRate(getContext(), l));
        }
    }

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

    public void testGetValueEarly() {
        // Should never throw an exception.
        mApi.getValue("Options", "ListenAddress");
    }

}
