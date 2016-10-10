package com.nutomic.syncthingandroid.test.util;

import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;

import com.nutomic.syncthingandroid.test.MockContext;
import com.nutomic.syncthingandroid.util.ConfigXml;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ConfigXmlTest {

    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    private MockContext mContext;

    private ConfigXml mConfig;

    @Before
    public void setUp() throws Exception {
        mContext = new MockContext(InstrumentationRegistry.getTargetContext());
        Assert.assertFalse(ConfigXml.getConfigFile(mContext).exists());
        mConfig = new ConfigXml(mContext);
        Assert.assertTrue(ConfigXml.getConfigFile(mContext).exists());
    }

    @After
    public void tearDown() throws Exception {
        ConfigXml.getConfigFile(mContext).delete();
    }

    @Test
    public void testGetWebGuiUrl() {
        Assert.assertTrue(mConfig.getWebGuiUrl().toString().startsWith("https://127.0.0.1:"));
    }

}
