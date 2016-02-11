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
        r.path = mTestFolder.getPath();
        r.id = id;
        return r;
    }

    public void testRecursion() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testRecursion";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));
        File testFile = new File(subFolder, "test");

        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        mLatch.await(1, TimeUnit.SECONDS);

        fo.stopWatching();
    }

    public void testRemoveFile() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testRemoveFile";
        File test = new File(mTestFolder, "test");
        test.createNewFile();
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));

        mLatch = new CountDownLatch(1);
        test.delete();
        mLatch.await(1, TimeUnit.SECONDS);
        assertEquals(0, mLatch.getCount());

        fo.stopWatching();
    }

    public void testMoveDirectoryOut() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testMoveDirectory";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));

        File movedSubFolder = new File(getContext().getFilesDir(), subFolder.getName());
        subFolder.renameTo(movedSubFolder);
        File testFile = new File(movedSubFolder, "should-not-notify");
        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        mLatch.await(1, TimeUnit.SECONDS);
        assertEquals(1, mLatch.getCount());

        fo.stopWatching();
    }

    public void testAddDirectory() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testAddDirectory";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        File testFile = new File(subFolder, "test");
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));

        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        mLatch.await(1, TimeUnit.SECONDS);
        assertEquals(0, mLatch.getCount());

        fo.stopWatching();
    }

    public void testNotExisting() throws IOException, InterruptedException {
        RestApi.Folder r = new RestApi.Folder();
        r.path = new File(new MockContext(getContext()).getFilesDir(), "not-existing").getPath();
        r.id = "testNotExisting";
        try {
            new FolderObserver(this, r);
            fail("Expected FolderNotExistingException");
        } catch (FolderObserver.FolderNotExistingException e) {
            assertTrue(e.getMessage().contains(r.path));
        }
    }

}
