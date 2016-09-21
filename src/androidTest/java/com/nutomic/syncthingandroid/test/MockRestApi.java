package com.nutomic.syncthingandroid.test;

import android.app.Activity;
import android.content.Context;

import android.support.annotation.NonNull;
import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.util.ArrayList;
import java.util.List;

public class MockRestApi extends RestApi {

    public MockRestApi(Context context, String url, String apiKey,
                       OnApiAvailableListener listener) {
        super(context, url, apiKey, listener, null);
    }

    @Override
    public void onWebGuiAvailable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getValue(String name, String key) {
        return "";
    }

    @Override
    public <T> void setValue(String name, String key, T value, boolean isArray, Activity activity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Device> getDevices(boolean includeLocal) {
        return new ArrayList<>();
    }

    @Override
    public void getSystemInfo(final OnReceiveSystemInfoListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Folder> getFolders() {
        return new ArrayList<>();
    }

    @Override
    public void getConnections(final OnReceiveConnectionsListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getModel(final String folderId, final OnReceiveModelListener listener) {
    }

    @Override
    public void editDevice(@NonNull Device device, Activity activity, OnDeviceIdNormalizedListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteDevice(Device device, Activity activity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean editFolder(Folder folder, boolean create, Activity activity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteFolder(Folder folder, Activity activity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void normalizeDeviceId(String id, final OnDeviceIdNormalizedListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyDeviceId(String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onFolderFileChange(String folderId, String relativePath) {
        throw new UnsupportedOperationException();
    }
}
