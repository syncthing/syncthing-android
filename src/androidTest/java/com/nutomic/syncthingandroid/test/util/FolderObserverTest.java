package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.test.Util;
import com.nutomic.syncthingandroid.util.FolderObserver;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FolderObserverTest extends AndroidTestCase
        implements FolderObserver.OnFolderFileChangeListener {

    private File mTestFolder;

    private String mCurrentTest;

    private CountDownLatch mLatch;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestFolder = new File(new MockContext(getContext()).getFilesDir(), "observer-test");
        mTestFolder.mkdir();
    }

    @Override
    protected void tearDown() throws Exception {
        Util.deleteRecursive(mTestFolder);
        super.tearDown();
    }

    @Override
    public void onFolderFileChange(String folderId, String relativePath) {
        mLatch.countDown();
        assertEquals(mCurrentTest, folderId);
        assertFalse(relativePath.endsWith("should-not-notifiy"));
    }

    private RestApi.Folder createFolder(String id) {
        RestApi.Folder r = new RestApi.Folder();
        r.Path = mTestFolder.getAbsolutePath();
        r.ID = id;
        return r;
    }

    @MediumTest
    public void testRecursion() throws IOException, InterruptedException {
        mCurrentTest = "testRecursion";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        FolderObserver ro = new FolderObserver(this, createFolder(mCurrentTest));
        File testFile = new File(subFolder, "test");
        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        mLatch.await(1, TimeUnit.SECONDS);
        ro.stopWatching();
    }

    @MediumTest
    public void testRemoveDirectory() throws IOException {
        mCurrentTest = "testRemoveDirectory";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        FolderObserver ro = new FolderObserver(this, createFolder(mCurrentTest));
        File movedSubFolder = new File(getContext().getFilesDir(), subFolder.getName());
        subFolder.renameTo(movedSubFolder);
        File testFile = new File(movedSubFolder, "should-not-notifiy");
        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        ro.stopWatching();
        Util.deleteRecursive(subFolder);
    }

    @MediumTest
    public void testAddDirectory() throws IOException, InterruptedException {
        mCurrentTest = "testAddDirectory";
        File subFolder = new File(mTestFolder, "subfolder");
        FolderObserver ro = new FolderObserver(this, createFolder(mCurrentTest));
        subFolder.mkdir();
        File testFile = new File(subFolder, "test");
        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        mLatch.await(1, TimeUnit.SECONDS);
        ro.stopWatching();
    }

}
