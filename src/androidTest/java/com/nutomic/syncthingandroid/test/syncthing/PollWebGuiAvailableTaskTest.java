package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;

import com.nutomic.syncthingandroid.syncthing.PollWebGuiAvailableTask;
import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PollWebGuiAvailableTaskTest extends AndroidTestCase {

    private ConfigXml mConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mConfig = new ConfigXml(new MockContext(getContext()));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        ConfigXml.getConfigFile(new MockContext(getContext())).delete();
    }

    public void testPolling() throws InterruptedException {
        new SyncthingRunnable(new MockContext(null), SyncthingRunnable.Command.main);

        String httpsCertPath = getContext().getFilesDir() + "/" + SyncthingService.HTTPS_CERT_FILE;

        final CountDownLatch latch = new CountDownLatch(1);
        new PollWebGuiAvailableTask(httpsCertPath) {
            @Override
            protected void onPostExecute(Void aVoid) {
                latch.countDown();
            }
        }.execute(mConfig.getWebGuiUrl());
        latch.await(1, TimeUnit.SECONDS);

        SyncthingRunnable.killSyncthing();
    }
}
