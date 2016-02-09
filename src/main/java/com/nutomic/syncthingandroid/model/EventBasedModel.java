package com.nutomic.syncthingandroid.model;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

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
public class EventBasedModel {

    public interface OnEventBasedModelChangedListener {

        void onEventBasedModelChanged(EventBasedModel model);

    }

    private static String TAG = "EventBasedModel";

    private Object mMonitor = new Object();

    private long lastId = -1;
    private boolean changeEventPending;
    private boolean stillValid = true;

    private Map<String, DeviceConnection> connectedDevices = new HashMap<String, DeviceConnection>();
    private Map<DeviceFolder, Double> completionStatus = new HashMap<DeviceFolder, Double>();
    private Map<String, String> folderState = new HashMap<String, String>();

    private List<OnEventBasedModelChangedListener> mListeners;

    public EventBasedModel() {
    }

    private EventBasedModel(EventBasedModel s) {
        connectedDevices.putAll(s.connectedDevices);
        completionStatus.putAll(s.completionStatus);
        folderState.putAll(s.folderState);
    }

    public void reset() {
        synchronized (mMonitor) {
            completionStatus.clear();
            connectedDevices.clear();
            folderState.clear();
            stillValid = true;
            lastId = -1;
        }
        fireOnEventBasedModelChanged();
    }


    public void updateByEvent(long id, String type, JSONObject data) {
        boolean notify;
        synchronized (mMonitor) {
            if (!stillValid) {
                Log.w(TAG, "Attempting to update an outdated model. Ignore.");
                return;
            }
            if (id <= lastId) {
                Log.w(TAG, "Event-ID backward jump. Need to re-initialize the model.");
                stillValid = false;
                return;
            } // if

            processEvent(id, type, data);
            notify = changeEventPending;
        }
        if (notify) fireOnEventBasedModelChanged();
    }

    private void processEvent(long id, String type, JSONObject data) {
        Log.v(TAG, MessageFormat.format("Processing event {0}: {1}: {2}", id, type, data));
        lastId = id;
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
            changeEventPending = connectedDevices.remove(deviceId) != null || changeEventPending;

            Iterator<Map.Entry<DeviceFolder, Double>> it = completionStatus.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<DeviceFolder, Double> entry = it.next();
                if (entry.getKey().getDeviceId().equals(deviceId)) {
                    it.remove();
                    changeEventPending = true;
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    private void handleDeviceConnected(JSONObject data) {
        try {
            String deviceId = data.getString("id");
            Device d = new Device(deviceId);
            d.setName(data.optString("deviceName"));
            d.setAddress(data.optString("addr"));
            d.setClientName(data.optString("clientName"));
            d.setClientVersion(data.optString("clientVersion"));

            DeviceConnection dc = new DeviceConnection(d, data.optString("type"));
            connectedDevices.put(deviceId, dc);
            changeEventPending = true;
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
            completionStatus.put(df, completion);
            changeEventPending = true;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    private void handleStateChanged(JSONObject data) {
        try {
            String folder = data.getString("folder");
            String newState = data.getString("to");

            folderState.put(folder, newState);
            changeEventPending = true;
        } catch (JSONException e) {
            Log.w(TAG, MessageFormat.format("Could not extract required information from data: {0}: data={1}", e.getMessage(), data), e);
        }
    }

    private void logStatusFormatted() {
        Log.d(TAG, "-------------------------------");
        Log.d(TAG, "Connected devices: " + TextUtils.join(", ", connectedDevices.values()));
        for (Map.Entry<DeviceFolder, Double> cs : completionStatus.entrySet()) {
            Log.d(TAG, cs.getKey() + ": " + cs.getValue());
        }
        for (Map.Entry<String, String> fs : folderState.entrySet()) {
            Log.d(TAG, fs.getKey() + ": " + fs.getValue());
        }
        Log.d(TAG, "-------------------------------");
    }

    public EventBasedModel createSnapshot() {
        return new EventBasedModel(this);
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
        return stillValid;
    }

    public Map<String, DeviceConnection> getConnectedDevices() {
        return connectedDevices;
    }

    public Map<DeviceFolder, Double> getCompletionStatus() {
        return completionStatus;
    }

    public Map<String, String> getFolderState() {
        return folderState;
    }
}
