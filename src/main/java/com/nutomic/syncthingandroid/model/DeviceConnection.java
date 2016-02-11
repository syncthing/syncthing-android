package com.nutomic.syncthingandroid.model;

import java.io.Serializable;

/**
 * Created by martin on 2/9/16.
 */
public class DeviceConnection implements Serializable {

    public Device device;
    public String connectionType;

    public DeviceConnection(Device device, String connectionType) {
        this.device = device;
        this.connectionType = connectionType;
    }

    @Override
    public String toString() {
        return "DeviceConnection{" +
                "device=" + device +
                ", connectionType='" + connectionType + '\'' +
                '}';
    }
}
