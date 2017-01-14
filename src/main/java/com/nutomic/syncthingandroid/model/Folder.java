package com.nutomic.syncthingandroid.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Folder {

    public String id;
    public String label;
    public String path;
    public String type;
    public List<Device> devices = new ArrayList<>();
    public int rescanIntervalS;
    public final boolean ignorePerms = true;
    public boolean autoNormalize;
    public float minDiskFreePct;
    public Versioning versioning;
    public int copiers;
    public int pullers;
    public int hashers;
    public String order;
    public boolean ignoreDelete;
    public int scanProgressIntervalS;
    public int pullerSleepS;
    public int pullerPauseS;
    public int maxConflicts;
    public boolean disableSparseFiles;
    public boolean disableTempIndexes;
    public String invalid;

    public static class Versioning implements Serializable {
        public String type;
        public Map<String, String> params = new HashMap<>();
    }

    public void addDevice(String deviceId) {
        Device d = new Device();
        d.deviceID = deviceId;
        devices.add(d);
    }

    public Device getDevice(String deviceId) {
        for (Device d : devices) {
            if (d.deviceID.equals(deviceId)) {
                return d;
            }
        }
        return null;
    }

    public void removeDevice(String deviceId) {
        for (Iterator<Device> it = devices.iterator(); it.hasNext();) {
            String currentId = it.next().deviceID;
            if (currentId.equals(deviceId)) {
                it.remove();
            }
        }
    }

    public String toString() {
        return label;
    }
}
