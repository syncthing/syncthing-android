package com.nutomic.syncthingandroid.model;

import java.io.Serializable;

/**
 * Represents basic information about a device.
 */
public class Device implements Serializable {

    public String id;

    public String name;

    public String address;

    public String clientName;

    public String clientVersion;
    
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
