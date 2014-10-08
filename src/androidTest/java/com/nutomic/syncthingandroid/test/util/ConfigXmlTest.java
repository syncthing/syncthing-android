package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

public class ConfigXmlTest extends AndroidTestCase {

    private MockContext mContext;

    private ConfigXml mConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mContext = new MockContext(getContext());
        assertFalse(ConfigXml.getConfigFile(mContext).exists());
        mConfig = new ConfigXml(mContext);
        assertTrue(ConfigXml.getConfigFile(mContext).exists());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        ConfigXml.getConfigFile(mContext).delete();
    }

    @SmallTest
    public void testGetWebGuiUrl() {
        assertEquals("http://127.0.0.1:8080", mConfig.getWebGuiUrl());
    }

    /**
     * Just make sure the file is actually changed.
     *
     * This is not ideal, but way less complicated than starting up syncthing and accessing the API.
     */
    @SmallTest
    public void testCreateCameraFolder() {
        long oldTime = ConfigXml.getConfigFile(mContext).lastModified();
        long oldSize = ConfigXml.getConfigFile(mContext).length();
        mConfig.createCameraFolder();
        assertNotSame(oldTime, ConfigXml.getConfigFile(mContext).lastModified());
        assertNotSame(oldSize, ConfigXml.getConfigFile(mContext).lastModified());
    }

    /**
     * Same as {@link #testCreateCameraFolder()}.
     */
    @MediumTest
    public void testUpdateIfNeeded() {
        long oldTime = ConfigXml.getConfigFile(mContext).lastModified();
        long oldSize = ConfigXml.getConfigFile(mContext).length();
        mConfig.updateIfNeeded();
        assertNotSame(oldTime, ConfigXml.getConfigFile(mContext).lastModified());
        assertNotSame(oldSize, ConfigXml.getConfigFile(mContext).lastModified());
        assertNotNull(mConfig.getApiKey());
    }

}
