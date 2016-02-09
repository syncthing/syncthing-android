package com.nutomic.syncthingandroid.model;

/**
 * Created by martin on 2/9/16.
 */
public class DeviceConnection {

    private Device device;
    private String connectionType;

    public DeviceConnection(Device device, String connectionType) {
        this.device = device;
        this.connectionType = connectionType;
    }

    public Device getDevice() {
        return device;
    }

    public String getConnectionType() {
        return connectionType;
    }

    @Override
    public String toString() {
        return "DeviceConnection{" +
                "device=" + device +
                ", connectionType='" + connectionType + '\'' +
                '}';
    }
}
