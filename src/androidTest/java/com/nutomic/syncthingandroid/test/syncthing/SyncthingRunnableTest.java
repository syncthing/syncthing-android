package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.test.MockContext;

import java.io.File;

/**
 * NOTE: This test will cause a "syncthing binary crashed" notification, because
 * {@code -home " + mContext.getFilesDir()} is run as a "command" and fails.
 */
public class SyncthingRunnableTest extends AndroidTestCase {

    @SmallTest
    public void testRunning() throws InterruptedException {
        MockContext context = new MockContext(getContext());
        File testFile = new File(context.getFilesDir(), SyncthingRunnable.UNIT_TEST_PATH);
        assertFalse(testFile.exists());
        // Inject a different command instead of the Syncthing binary for testing.
        new SyncthingRunnable(context, new String[]{"touch", testFile.getAbsolutePath()}).run();
        assertTrue(testFile.exists());
        testFile.delete();
    }

}
