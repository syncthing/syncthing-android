package com.nutomic.syncthingandroid.model;

import android.util.Log;

import com.nutomic.syncthingandroid.util.DefaultHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class caches remote folder and device synchronization
 * completion indicators defined in {@link CompletionInfo#CompletionInfo}
 * according to syncthing's REST "/completion" JSON result schema.
 * Completion model of syncthing's web UI is completion[deviceId][folderId]
 */
public class Completion {

    private static final String TAG = "Completion";

    DefaultHashMap<String, DefaultHashMap<String, CompletionInfo>> deviceFolderMap =
        new DefaultHashMap<String, DefaultHashMap<String, CompletionInfo>>();

    DefaultHashMap<String, CompletionInfo> defaultDevice = new DefaultHashMap<String, CompletionInfo>();
    CompletionInfo defaultFolder = new CompletionInfo();

    /**
     * Adds a device to the cache model if it does not exist.
     */
    private void addDevice(String deviceId) {
        if (!deviceFolderMap.containsKey(deviceId)) {
            deviceFolderMap.put(deviceId, defaultDevice);
        }
    }

    /**
     * Removes a device from the cache model.
     */
    private void removeDevice(String deviceId) {
        if (deviceFolderMap.containsKey(deviceId)) {
            deviceFolderMap.remove(deviceId);
        }
    }

    /**
     * Adds a folder to the cache model if it does not exist.
     */
    private void addFolder(String deviceId, String folderId) {
        // Add device parent node if it does not exist.
        if (!deviceFolderMap.containsKey(deviceId)) {
            deviceFolderMap.put(deviceId, defaultDevice);
        }
        // Add folder.
        if (!deviceFolderMap.get(deviceId).containsKey(folderId)) {
            deviceFolderMap.get(deviceId).put(folderId, defaultFolder);
        }
    }

    /**
     * Removes a folder from the cache model.
     */
    private void removeFolder(String folderId) {
        for (String deviceId : deviceFolderMap.keySet()) {
            if (deviceFolderMap.get(deviceId).containsKey(folderId)) {
                deviceFolderMap.get(deviceId).remove(folderId);
                break;
            }
        }
    }

    /**
     * Updates device information in the cache model.
     */
    public void updateDevices(List<Device> newDeviceSet) {
        List<String> removedDevices = new ArrayList<>();;
        Boolean deviceFound;
        for (String deviceId : deviceFolderMap.keySet()) {
            deviceFound = false;
            for (Device device : newDeviceSet) {
                if (device.deviceID.equals(deviceId)) {
                    deviceFound = true;
                    break;
                }
            }
            if (!deviceFound) {
                removedDevices.add(deviceId);
            }
        }
        for (String deviceId : removedDevices) {
            Log.v(TAG, "updateDevices: Remove device '" + deviceId + "' from cache model");
            removeDevice(deviceId);
        }
    }

    /**
     * Updates folder information in the cache model.
     */
    public void updateFolders(List<Folder> newFolderSet) {
        List<String> removedFolders = new ArrayList<>();;
        Boolean folderFound;
        for (String deviceId : deviceFolderMap.keySet()) {
            for (String folderId : deviceFolderMap.get(deviceId).keySet()) {
                folderFound = false;
                for (Folder folder : newFolderSet) {
                    if (folder.id.equals(folderId)) {
                        folderFound = true;
                        break;
                    }
                }
                if (!folderFound) {
                    removedFolders.add(folderId);
                }
            }
        }
        for (String folderId : removedFolders) {
            Log.v(TAG, "updateFolders: Remove folder '" + folderId + "' from cache model");
            removeFolder(folderId);
        }
    }

    /**
     * Calculates remote device sync completion percentage across all folders
     * shared with the device.
     */
    public int getDeviceCompletion(String deviceId) {
        int folderCount = 0;
        double sumCompletion = 0;
        for (String folderId : deviceFolderMap.getOrDefault(deviceId, defaultDevice).keySet()) {
            sumCompletion += (deviceFolderMap.get(deviceId)).get(folderId).completion;
            folderCount++;
        }
        if (folderCount == 0) {
            return 100;
        } else {
            return (int) Math.floor(sumCompletion / folderCount);
        }
    }

    /**
     * Set completionInfo within the completion[deviceId][folderId] model.
     */
    public void setCompletionInfo(String deviceId, String folderId,
                                    CompletionInfo completionInfo) {
        // Add device parent node if it does not exist.
        if (!deviceFolderMap.containsKey(deviceId)) {
            deviceFolderMap.put(deviceId, defaultDevice);
        }
        // Add folder or update existing folder entry.
        deviceFolderMap.get(deviceId).put(folderId, completionInfo);
    }
}
