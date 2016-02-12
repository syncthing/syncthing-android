package com.nutomic.syncthingandroid.syncthing;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents a view of syncthing activity based on the events that have been received
 */
public class EventBasedModel implements Serializable {

    public interface OnEventBasedModelChangedListener {

        void onEventBasedModelChanged(EventBasedModel model);

    }

    public static class Snapshot {

        private EventBasedModel mModel;

        protected Snapshot(EventBasedModel model) {
            mModel = model;
        }

        public boolean isStillValid() {
            return mModel.isStillValid();
        }

        public boolean isInitializing() {
            return mModel.isInitializing();
        }

        public Map<String, RestApi.Device> getDevices() {
            synchronized (mModel.mMonitor) {
                return mModel.mDevices;
            }
        }

        public Map<String, RestApi.Connection> getConnections() {
            synchronized (mModel.mMonitor) {
                return mModel.mConnections;
            }
        }

        public Map<String, Map<String, Double>> getDeviceFolderCompletion() {
            synchronized (mModel.mMonitor) {
                return mModel.mDeviceFolderCompletion;
            }
        }

        public Map<String, String> getFolderState() {
            synchronized (mModel.mMonitor) {
                return mModel.mFolderState;
            }
        }
    }

    private static String TAG = "EventBasedModel";
    private static final long INIT_VIA_API = -1;

    private transient final RestApi mApi;
    private final Serializable mMonitor = new Serializable() {}; // monitor needs to be deep-copied, too

    private long mLastId = INIT_VIA_API;
    private transient boolean mChangeEventPending;
    private boolean mStillValid = true;

    private Map<String, RestApi.Device> mDevices = new HashMap<>();
    private Map<String, RestApi.Connection> mConnections = new HashMap<>();
    private Map<String, Map<String, Double>> mDeviceFolderCompletion = new HashMap<>();
    private Map<String, String> mFolderState = new HashMap<>();

    private transient List<OnEventBasedModelChangedListener> mListeners;

    public EventBasedModel(RestApi api) {
        mApi = api;
    }

    public boolean isSnapshot() {
        return mApi == null;
    }

    public boolean isInitializing() {
        synchronized (mMonitor) {
            return mLastId == INIT_VIA_API;
        }
    }

    public void reset() {
        if (isSnapshot()) {
            throw new IllegalStateException("Cannot call reset() on a snapshot()");
        }
        synchronized (mMonitor) {
            mConnections.clear();
            mStillValid = true;
            mLastId = INIT_VIA_API;
        }
        fireOnEventBasedModelChanged();
    }


    public void updateByEvent(long id, String type, JSONObject data) {
        boolean notify;
        synchronized (mMonitor) {
            if (!mStillValid) {
                Log.w(TAG, "Attempting to update an outdated model. Ignore.");
                return;
            }
            if (mLastId != INIT_VIA_API && id - 1 != mLastId) {
                Log.w(TAG, "Missed an event. Need to re-initialize the model.");
                mStillValid = false;
                return;
            } // if

            processEvent(id, type, data);
            notify = mChangeEventPending;
            mChangeEventPending = false;
        }
        if (notify) fireOnEventBasedModelChanged();
    }

    private void processEvent(long id, String type, JSONObject data) {
        Log.v(TAG, MessageFormat.format("Processing event {0}: {1}: {2}", id, type, data));
        if (mLastId == INIT_VIA_API) {
            initViaApi();
        }
        mLastId = id;
        switch (type) {
            case "DeviceConnected":
                handleDeviceConnected(data);
                break;
            case "DeviceDisconnected":
                handleDeviceDisconnected(data);
                break;
            case "FolderCompletion":
                handleFolderCompletion(data);
                break;
            case "StateChanged":
                handleStateChanged(data);
                break;
            default:
                Log.i(TAG, "Unhandled event: " + type);
        }
        //logStatusFormatted();
    }

    private void initViaApi() {
        List<RestApi.Device> devices = mApi.getDevices(false);
        for (RestApi.Device d : devices) {
           mDevices.put(d.deviceID, d);
        }
    }

    private void handleDeviceDisconnected(JSONObject data) {
        try {
            String deviceId = data.getString("id");
            mChangeEventPending |= mConnections.remove(deviceId) != null;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    private void handleDeviceConnected(JSONObject data) {
        try {
            String deviceId = data.getString("id");
            RestApi.Connection c = new RestApi.Connection();
            c.address = data.optString("addr");
            c.clientVersion = data.optString("clientVersion");
            c.deviceName = data.optString("deviceName");
            c.connected = true;
            mConnections.put(deviceId, c);
            mChangeEventPending |= true;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        } // try/catch
    }

    private void handleFolderCompletion(JSONObject data) {
        try {
            String deviceId = data.getString("device");
            double completion = data.getDouble("completion");
            String folderName = data.getString("folder");

            Map<String, Double> folderCompletions = mDeviceFolderCompletion.get(deviceId);
            if (folderCompletions == null) {
                folderCompletions = new HashMap<>();
                mDeviceFolderCompletion.put(deviceId, folderCompletions);
            }
            folderCompletions.put(folderName, completion);
            mChangeEventPending = true;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    private void handleStateChanged(JSONObject data) {
        try {
            String folder = data.getString("folder");
            String newState = data.getString("to");

            mFolderState.put(folder, newState);
            mChangeEventPending = true;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    public EventBasedModel.Snapshot createSnapshot() {
        // deep-copy via serialization + deserializationt
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            oos.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream oin = new ObjectInputStream(bin);
            return new Snapshot((EventBasedModel) oin.readObject());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deep-copy EventBasedModel: " + e.getMessage(), e);
        }
    }

    public void addOnEventBasedModelChangedListener(OnEventBasedModelChangedListener l) {
        synchronized (mMonitor) {
            if (mListeners == null) {
                mListeners = new LinkedList<>();
                mListeners.add(l);
            }
        }
    }

    public void removeOnEventBasedModelChangedListener(OnEventBasedModelChangedListener l) {
        synchronized (mMonitor) {
            if (mListeners == null) return;
            mListeners.remove(l);
            if (mListeners.isEmpty()) mListeners = null;
        }
    }

    protected void fireOnEventBasedModelChanged() {
        ArrayList<OnEventBasedModelChangedListener> copy;
        synchronized (mListeners) {
            if (mListeners == null) return;
            copy = new ArrayList<>(mListeners);
        }
        for (OnEventBasedModelChangedListener l : copy) {
            l.onEventBasedModelChanged(this);
        }
    }

    public boolean isStillValid() {
        synchronized (mMonitor) {
            return mStillValid;
        }
    }
}
