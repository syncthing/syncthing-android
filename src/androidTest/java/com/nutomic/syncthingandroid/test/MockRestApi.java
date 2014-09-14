package com.nutomic.syncthingandroid.test;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;

import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.util.List;

public class MockRestApi extends RestApi {

    public MockRestApi(Context context, String url, OnApiAvailableListener listener) {
        super(context, url, listener);
    }

    @Override
    public void setApiKey(String apiKey) {
        throw new UnsupportedOperationException();
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
    public List<Node> getNodes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getSystemInfo(final OnReceiveSystemInfoListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Repo> getRepos() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getConnections(final OnReceiveConnectionsListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getModel(final String repoId, final OnReceiveModelListener listener) {
    }

    @Override
    public void editNode(final Node node,
                         final OnNodeIdNormalizedListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean deleteNode(Node node, Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean editRepo(Repo repo, boolean create, Context context) {
        throw new UnsupportedOperationException();
    }

    public boolean deleteRepo(Repo repo, Context context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void normalizeNodeId(String id, final OnNodeIdNormalizedListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    @TargetApi(11)
    public void copyNodeId(String id) {
        throw new UnsupportedOperationException();
    }

}
