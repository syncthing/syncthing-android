package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.syncthing.PostTask;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.TestContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RestApiTest extends AndroidTestCase {

    private SyncthingRunnable mSyncthing;

    private ConfigXml mConfig;

    private RestApi mApi;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSyncthing = new SyncthingRunnable(new TestContext(null),
                getContext().getApplicationInfo().dataDir + "/" + SyncthingService.BINARY_NAME);

        mConfig = new ConfigXml(new TestContext(getContext()));
        mConfig.createCameraRepo();
        mConfig.updateIfNeeded();

        final CountDownLatch latch = new CountDownLatch(2);
        new PollWebGuiAvailableTask() {
            @Override
            protected void onPostExecute(Void aVoid) {
                mApi.onWebGuiAvailable();
                latch.countDown();
            }
        }.execute(mConfig.getWebGuiUrl());
        mApi = new RestApi(getContext(), mConfig.getWebGuiUrl(), mConfig.getApiKey(),
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

        final CountDownLatch latch = new CountDownLatch(1);
        new PostTask() {
            @Override
            protected void onPostExecute(Boolean aBoolean) {
                assertTrue(aBoolean);
                latch.countDown();
            }
        }.execute(mConfig.getWebGuiUrl(), PostTask.URI_SHUTDOWN, mConfig.getApiKey());
        latch.await(1, TimeUnit.SECONDS);
        ConfigXml.getConfigFile(new TestContext(getContext())).delete();
    }

    @SmallTest
    public void testGetVersion() {
        assertNotNull(mApi.getVersion());
        assertFalse(mApi.getVersion().equals(""));
    }

    @SmallTest
    public void testGetNodes() {
        assertNotNull(mApi.getNodes());
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
    public void testGetRepos() {
        assertNotNull(mApi.getRepos());
    }

    @SmallTest
    public void testReadableFileSize() {
        assertEquals("1 MB", RestApi.readableFileSize(getContext(), 1048576));
        assertEquals("1 GB", RestApi.readableFileSize(getContext(), 1073741824));
    }

    @SmallTest
    public void testGetReadableTransferRate() {
        assertEquals("1 Mb/s", RestApi.readableTransferRate(getContext(), 1048576));
        assertEquals("1 Gb/s", RestApi.readableTransferRate(getContext(), 1073741824));
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
            public void onReceiveModel(String repoId, RestApi.Model model) {
                assertNotNull(model);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

    @LargeTest
    public void testModifyNode() throws InterruptedException {
        final RestApi.Node node = new RestApi.Node();
        node.NodeID = "P56IOI7-MZJNU2Y-IQGDREY-DM2MGTI-MGL3BXN-PQ6W5BM-TBBZ4TJ-XZWICQ2";
        node.Addresses = "dynamic";
        node.Name = "my node";
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.editNode(node, new RestApi.OnNodeIdNormalizedListener() {
            @Override
            public void onNodeIdNormalized(String normalizedId, String error) {
                assertEquals(node.NodeID, normalizedId);
                assertEquals(null, error);
                latch.countDown();
            }
        });
        latch.await(10, TimeUnit.SECONDS);

        assertTrue(mApi.deleteNode(node, getContext()));
    }

    @SmallTest
    public void testModifyRepo() {
        RestApi.Repo repo = new RestApi.Repo();
        repo.Directory = "/my/dir";
        repo.ID = "my-repo";
        repo.Nodes = new ArrayList<>();
        repo.ReadOnly = false;
        repo.Versioning = new RestApi.Versioning();
        assertTrue(mApi.editRepo(repo, true, getContext()));

        assertTrue(mApi.deleteRepo(repo, getContext()));
    }

    @MediumTest
    public void testNormalizeNodeId() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mApi.normalizeNodeId("p56ioi7m--zjnu2iq-gdr-eydm-2mgtmgl3bxnpq6w5btbbz4tjxzwicq",
                new RestApi.OnNodeIdNormalizedListener() {
            @Override
            public void onNodeIdNormalized(String normalizedId, String error) {
                assertEquals("P56IOI7-MZJNU2Y-IQGDREY-DM2MGTI-MGL3BXN-PQ6W5BM-TBBZ4TJ-XZWICQ2",
                        normalizedId);
                latch.countDown();
            }
        });
        latch.await(1, TimeUnit.SECONDS);
    }

}
