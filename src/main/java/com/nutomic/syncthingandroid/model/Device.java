package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;

import java.io.Serializable;
import java.util.List;

public class Device implements Serializable {
    public List<String> addresses;
    public String name;
    public String deviceID;
    public String compression;
    public boolean introducer;

    /**
     * Returns the device name, or the first characters of the ID if the name is empty.
     */
    public String getDisplayName() {
        return (TextUtils.isEmpty(name))
                ? deviceID.substring(0, 7)
                : name;
    }
}
