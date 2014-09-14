package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;

import com.nutomic.syncthingandroid.syncthing.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.syncthing.PostTask;
import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PollWebGuiAvailableTaskTest extends AndroidTestCase {

    private SyncthingRunnable mSyncthing;

    private ConfigXml mConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mConfig = new ConfigXml(new MockContext(getContext()));
        mConfig.updateIfNeeded();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        ConfigXml.getConfigFile(new MockContext(getContext())).delete();
    }

    public void testPolling() throws InterruptedException {
        mSyncthing = new SyncthingRunnable(new MockContext(null),
                getContext().getApplicationInfo().dataDir + "/" + SyncthingService.BINARY_NAME);

        final CountDownLatch latch = new CountDownLatch(1);
        new PollWebGuiAvailableTask() {
            @Override
            protected void onPostExecute(Void aVoid) {
                latch.countDown();
            }
        }.execute(mConfig.getWebGuiUrl());
        latch.await(1, TimeUnit.SECONDS);

        new PostTask().execute(mConfig.getWebGuiUrl(), PostTask.URI_SHUTDOWN,
                mSyncthing.getApiKey());
    }
}
