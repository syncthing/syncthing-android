package com.nutomic.syncthingandroid.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Model {
    public long globalBytes;
    public long globalDeleted;
    public long globalDirectories;
    public long globalFiles;
    public long globalSymlinks;
    public boolean ignorePatterns;
    public String invalid;
    public long localBytes;
    public long localDeleted;
    public long localDirectories;
    public long localSymlinks;
    public long localFiles;
    public long inSyncBytes;
    public long inSyncFiles;
    public long needBytes;
    public long needDeletes;
    public long needDirectories;
    public long needFiles;
    public long needItems;
    public long needSymlinks;
    public long pullErrors;
    public long sequence;
    public String state;
    public String stateChanged;
    public long version;
    public String watchError;

    /**
     * Stores completion information for each associated device.
     * Data is retrieved by the EventProcessor.
     */
    private List<Device> devices = new ArrayList<>();

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
}
