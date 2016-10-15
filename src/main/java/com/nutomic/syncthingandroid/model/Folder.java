package com.nutomic.syncthingandroid.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Folder implements Serializable {

    public String id;
    public String label;
    public String path;
    public String type;
    private transient List<Map<String, String>> devices;
    public int rescanIntervalS;
    public boolean ignorePerms;
    public boolean autoNormalize;
    public int minDiskFreePct;
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
        public Map<String, String> params;
    }

    public List<String> getDevices() {
        if (devices == null)
            return new ArrayList<>();

        List<String> devicesList = new ArrayList<>();
        for (Map<String, String> map : devices) {
            devicesList.addAll(map.values());
        }
        return devicesList;
    }

    public void setDevices(List<String> newDvices) {
        devices.clear();
        for (String d : newDvices) {
            Map<String, String> map = new HashMap<>();
            map.put("deviceID", d);
            devices.add(map);
        }
    }
}
