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
        File testFile = new File(context.getFilesDir(), "was_running");
        assertFalse(testFile.exists());
        // Inject a differenct command instead of the syncthing binary for testing.
        new SyncthingRunnable(context, "touch " + testFile.getAbsolutePath() + "\n").run();
        assertTrue(testFile.exists());
        testFile.delete();
    }

}
