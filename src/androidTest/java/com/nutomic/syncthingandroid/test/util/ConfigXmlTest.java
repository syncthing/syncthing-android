package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.io.File;
import java.io.IOException;

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

    public void testGetWebGuiUrl() {
        assertTrue(mConfig.getWebGuiUrl().startsWith("https://127.0.0.1:"));
    }

}
