package com.nutomic.syncthingandroid.model;

import java.io.Serializable;
import java.util.List;

public class Device implements Serializable {
    public List<String> addresses;
    public String name;
    public String deviceID;
    public String compression;
    public boolean introducer;
}
