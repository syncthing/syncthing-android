package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.syncthing.RestApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

    private static String TAG = "EventBasedModel";

    private transient final RestApi mApi;
    private final Serializable mMonitor = new Serializable() {}; // monitor needs to be deep-copied, too

    private long mLastId = 0;
    private transient boolean mChangeEventPending;
    private boolean mStillValid = true;

    private Map<String, DeviceConnection> mConnectedDevices = new HashMap<String, DeviceConnection>();
    private Map<DeviceFolder, Double> mCompletionStatus = new HashMap<DeviceFolder, Double>();
    private Map<String, String> mFolderState = new HashMap<String, String>();

    private transient List<OnEventBasedModelChangedListener> mListeners;

    public EventBasedModel(RestApi api) {
        mApi = api;
    }

    public boolean isSnapshot() {
        return mApi == null;
    }

    public boolean isInitializing() {
        synchronized (mMonitor) {
            return mLastId == 0;
        }
    }

    public void reset() {
        if (isSnapshot()) {
            throw new IllegalStateException("Cannot call reset() on a snapshot()");
        }
        synchronized (mMonitor) {
            mCompletionStatus.clear();
            mConnectedDevices.clear();
            mFolderState.clear();
            mStillValid = true;
            mLastId = 0;
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
            if (id - 1 != mLastId) {
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

    private void handleDeviceDisconnected(JSONObject data) {
        try {
            String deviceId = data.getString("id");
            mChangeEventPending = mConnectedDevices.remove(deviceId) != null || mChangeEventPending;

            Iterator<Map.Entry<DeviceFolder, Double>> it = mCompletionStatus.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<DeviceFolder, Double> entry = it.next();
                if (entry.getKey().deviceId.equals(deviceId)) {
                    it.remove();
                    mChangeEventPending = true;
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    private void handleDeviceConnected(JSONObject data) {
        try {
            String deviceId = data.getString("id");
            Device d = new Device();
            d.id = data.getString("id");
            d.name = data.optString("deviceName");
            d.address = data.optString("addr");
            d.clientName = data.optString("clientName");
            d.clientVersion = data.optString("clientVersion");

            DeviceConnection dc = new DeviceConnection(d, data.optString("type"));
            mConnectedDevices.put(deviceId, dc);
            mChangeEventPending = true;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        } // try/catch
    }

    private void handleFolderCompletion(JSONObject data) {
        try {
            String deviceId = data.getString("device");
            double completion = data.getDouble("completion");
            String folderName = data.getString("folder");

            DeviceFolder df = new DeviceFolder(deviceId, folderName);
            mCompletionStatus.put(df, completion);
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

    private void logStatusFormatted() {
        Log.d(TAG, "-------------------------------");
        Log.d(TAG, "Connected devices: " + TextUtils.join(", ", mConnectedDevices.values()));
        for (Map.Entry<DeviceFolder, Double> cs : mCompletionStatus.entrySet()) {
            Log.d(TAG, cs.getKey() + ": " + cs.getValue());
        }
        for (Map.Entry<String, String> fs : mFolderState.entrySet()) {
            Log.d(TAG, fs.getKey() + ": " + fs.getValue());
        }
        Log.d(TAG, "-------------------------------");
    }

    public EventBasedModel createSnapshot() {
        // deep-copy via serialization + deserializationt
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(this);
            oos.close();

            ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream oin = new ObjectInputStream(bin);
            return (EventBasedModel) oin.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deep-copy EventBasedModel: " + e.getMessage(), e);
        }
    }

    public void addOnEventBasedModelChangedListener(OnEventBasedModelChangedListener l) {
        synchronized (mMonitor) {
            if (mListeners == null) {
                mListeners = new LinkedList<OnEventBasedModelChangedListener>();
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
        return mStillValid;
    }

    public Map<String, DeviceConnection> getConnectedDevices() {
        return mConnectedDevices;
    }

    public Map<DeviceFolder, Double> getCompletionStatus() {
        return mCompletionStatus;
    }

    public Map<String, String> getFolderState() {
        return mFolderState;
    }
}
