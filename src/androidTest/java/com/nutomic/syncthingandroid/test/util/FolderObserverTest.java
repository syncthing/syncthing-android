package com.nutomic.syncthingandroid.test.util;

import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.test.Util;
import com.nutomic.syncthingandroid.util.FolderObserver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FolderObserverTest implements FolderObserver.OnFolderFileChangeListener {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private File mTestFolder;

    private String mCurrentTest;

    private CountDownLatch mLatch;

    @Before
    public void setUp() throws Exception {
        mTestFolder = new File(new MockContext(InstrumentationRegistry.getTargetContext()).getFilesDir(), "observer-test");
        mTestFolder.mkdir();
    }

    @After
    public void tearDown() throws Exception {
        Util.deleteRecursive(mTestFolder);
    }

    @Override
    public void onFolderFileChange(String folderId, String relativePath) {
        mLatch.countDown();
        Assert.assertEquals(mCurrentTest, folderId);
        Assert.assertFalse(relativePath.endsWith("should-not-notifiy"));
    }

    private RestApi.Folder createFolder(String id) {
        RestApi.Folder r = new RestApi.Folder();
        r.path = mTestFolder.getPath();
        r.id = id;
        return r;
    }

    @Test
    public void testRecursion() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testRecursion";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));
        File testFile = new File(subFolder, "test");

        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        Assert.assertTrue(mLatch.await(1, TimeUnit.SECONDS));

        fo.stopWatching();
    }

    @Test
    public void testRemoveFile() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testRemoveFile";
        File test = new File(mTestFolder, "test");
        test.createNewFile();
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));

        mLatch = new CountDownLatch(1);
        test.delete();
        Assert.assertTrue(mLatch.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(0, mLatch.getCount());

        fo.stopWatching();
    }

    @Test
    public void testAddDirectory() throws IOException, InterruptedException,
            FolderObserver.FolderNotExistingException {
        mCurrentTest = "testAddDirectory";
        File subFolder = new File(mTestFolder, "subfolder");
        subFolder.mkdir();
        File testFile = new File(subFolder, "test");
        FolderObserver fo = new FolderObserver(this, createFolder(mCurrentTest));

        mLatch = new CountDownLatch(1);
        testFile.createNewFile();
        Assert.assertTrue(mLatch.await(1, TimeUnit.SECONDS));
        Assert.assertEquals(0, mLatch.getCount());

        fo.stopWatching();
    }

    @Test
    public void testNotExisting() throws IOException, InterruptedException {
        RestApi.Folder r = new RestApi.Folder();
        r.path = new File(new MockContext(InstrumentationRegistry.getTargetContext()).getFilesDir(), "not-existing").getPath();
        r.id = "testNotExisting";
        try {
            new FolderObserver(this, r);
            Assert.fail("Expected FolderNotExistingException");
        } catch (FolderObserver.FolderNotExistingException e) {
            Assert.assertTrue(e.getMessage().contains(r.path));
        }
    }

}
