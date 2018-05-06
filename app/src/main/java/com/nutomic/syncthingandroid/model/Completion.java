package com.nutomic.syncthingandroid.model;

import java.util.HashMap;

public class Completion {

    HashMap<String, HashMap<String, CompletionInfo>> deviceFolderMap =
        new HashMap<String, HashMap<String, CompletionInfo>>();

    private void addDevice(String deviceId){
        if (!deviceFolderMap.containsKey(deviceId))
            deviceFolderMap.put(deviceId, new HashMap<String, CompletionInfo>());
    }

    private void addFolder(String deviceId, String folderId) {
        if (!deviceFolderMap.containsKey(deviceId))
            addDevice(deviceId);

        if (!deviceFolderMap.get(deviceId).containsKey(folderId))
            deviceFolderMap.get(deviceId).put(folderId, new CompletionInfo());
    }

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

    public CompletionInfo getCompletionInfo(String deviceId, String folderId) {
        addFolder(deviceId, folderId);
        return (deviceFolderMap.get(deviceId)).get(folderId);
    }

    public void setCompletionInfo(String deviceId, String folderId,
                                    CompletionInfo completionInfo) {
        addFolder(deviceId, folderId);
        (deviceFolderMap.get(deviceId)).put(folderId, completionInfo);
    }
}
