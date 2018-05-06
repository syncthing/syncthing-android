package com.nutomic.syncthingandroid.model;

import java.util.HashMap;

/**
 * This class caches remote folder and device synchronization
 * completion indicators defined in {@link CompletionInfo#CompletionInfo}
 * according to syncthing's REST "/completion" JSON result schema.
 * Completion model of syncthing's web UI is completion[deviceId][folderId]
 */
public class Completion {

    HashMap<String, HashMap<String, CompletionInfo>> deviceFolderMap =
        new HashMap<String, HashMap<String, CompletionInfo>>();

    // Adds a device to the cache model if it does not exist.
    private void addDevice(String deviceId) {
        if (!deviceFolderMap.containsKey(deviceId))
            deviceFolderMap.put(deviceId, new HashMap<String, CompletionInfo>());
    }

    // Removes a device from the cache model.
    public void removeDevice(String deviceId) {
        if (deviceFolderMap.containsKey(deviceId)) {
            deviceFolderMap.remove(deviceId);
        }
    }

    // Adds a folder to the cache model if it does not exist.
    private void addFolder(String deviceId, String folderId) {
        if (!deviceFolderMap.containsKey(deviceId))
            addDevice(deviceId);

        if (!deviceFolderMap.get(deviceId).containsKey(folderId))
            deviceFolderMap.get(deviceId).put(folderId, new CompletionInfo());
    }

    // Removes a folder from the cache model.
    public void removeFolder(String folderId) {
        for (String deviceId : deviceFolderMap.keySet()) {
            if (deviceFolderMap.get(deviceId).containsKey(folderId)) {
                deviceFolderMap.get(deviceId).remove(folderId);
                break;
            }
        }
    }

    /**
     * Calculates remote device sync completion percentage across all folders
     * shared with the device.
     */
    public int getDeviceCompletion(String deviceId) {
        addDevice(deviceId);
        int folderCount = 0;
        double sumCompletion = 0;
        for (String folderId : deviceFolderMap.get(deviceId).keySet()) {
            sumCompletion += getCompletionInfo(deviceId, folderId).completion;
            folderCount++;
        }
        return (int) Math.floor(sumCompletion / folderCount);
    }

    /**
     * Returns completionInfo from the completion[deviceId][folderId] model.
     */
    public CompletionInfo getCompletionInfo(String deviceId, String folderId) {
        addFolder(deviceId, folderId);
        return (deviceFolderMap.get(deviceId)).get(folderId);
    }

    // Set completionInfo within the completion[deviceId][folderId] model.
    public void setCompletionInfo(String deviceId, String folderId,
                                    CompletionInfo completionInfo) {
        addFolder(deviceId, folderId);
        (deviceFolderMap.get(deviceId)).put(folderId, completionInfo);
    }
}
