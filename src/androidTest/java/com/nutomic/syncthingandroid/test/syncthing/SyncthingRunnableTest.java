package com.nutomic.syncthingandroid.test.syncthing;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.test.MockContext;

import java.io.File;

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
