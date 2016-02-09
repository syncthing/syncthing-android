package com.nutomic.syncthingandroid.model;

/**
 * Represents basic information about a device.
 */
public class Device {

    private String id;

    private String name;

    private String address;

    private String clientName;

    private String clientVersion;

    public Device(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    @Override
    public String toString() {
        return "Device{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", clientName='" + clientName + '\'' +
                ", clientVersion='" + clientVersion + '\'' +
                '}';
    }
}
