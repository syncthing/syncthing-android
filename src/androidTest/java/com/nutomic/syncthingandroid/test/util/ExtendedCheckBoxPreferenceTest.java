package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.nutomic.syncthingandroid.util.ExtendedCheckBoxPreference;

public class ExtendedCheckBoxPreferenceTest extends AndroidTestCase {

    public void testExtendedCheckBoxPreference() {
        Object o = new Object();
        ExtendedCheckBoxPreference cb = new ExtendedCheckBoxPreference(getContext(), o);
        assertEquals(cb.getObject(), o);
    }

}
