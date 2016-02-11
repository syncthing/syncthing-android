package com.nutomic.syncthingandroid.model;

import java.io.Serializable;

/**
 * A folder on a device.
 */
public class DeviceFolder implements Serializable {

    public String deviceId;

    public String folderName;

    public DeviceFolder(String deviceId, String folderName) {
        this.deviceId = deviceId;
        this.folderName = folderName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeviceFolder that = (DeviceFolder) o;

        if (deviceId != null ? !deviceId.equals(that.deviceId) : that.deviceId != null)
            return false;
        return !(folderName != null ? !folderName.equals(that.folderName) : that.folderName != null);

    }

    @Override
    public int hashCode() {
        int result = deviceId != null ? deviceId.hashCode() : 0;
        result = 31 * result + (folderName != null ? folderName.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DeviceFolder{" +
                "deviceId='" + deviceId + '\'' +
                ", folderName='" + folderName + '\'' +
                '}';
    }
}
