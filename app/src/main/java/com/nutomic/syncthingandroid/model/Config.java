package com.nutomic.syncthingandroid.model;

import java.util.List;

public class Config {
    public int version;
    public List<Device> devices;
    public List<Folder> folders;
    public Gui gui;
    public Options options;
    public List<PendingDevice> pendingDevices;
    public List<RemoteIgnoredDevice> remoteIgnoredDevices;
}
