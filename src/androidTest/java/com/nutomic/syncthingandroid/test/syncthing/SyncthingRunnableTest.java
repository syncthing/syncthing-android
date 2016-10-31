package com.nutomic.syncthingandroid.test.syncthing;

import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.service.SyncthingRunnable;
import com.nutomic.syncthingandroid.test.MockContext;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

/**
 * NOTE: This test will cause a "syncthing binary crashed" notification, because
 * {@code -home " + mContext.getFilesDir()} is run as a "command" and fails.
 */
public class SyncthingRunnableTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Test
    public void testRunning() throws InterruptedException {
        MockContext context = new MockContext(InstrumentationRegistry.getTargetContext());
        File testFile = new File(context.getFilesDir(), SyncthingRunnable.UNIT_TEST_PATH);
        Assert.assertFalse(testFile.exists());
        // Inject a different command instead of the Syncthing binary for testing.
        new SyncthingRunnable(context, new String[]{"touch", testFile.getAbsolutePath()}).run();
        Assert.assertTrue(testFile.exists());
        testFile.delete();
    }

}
