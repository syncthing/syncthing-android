package com.nutomic.syncthingandroid.views;

import android.content.Context;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.util.Util;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Generates item views for device items.
 */
public class DevicesAdapter extends ArrayAdapter<Device> {

    private static final String TAG = "DevicesAdapter";

    /**
     * If (incoming_bits_per_second + outgoing_bits_per_second) top the threshold,
     * we'll assume syncing state for the device reporting that data throughput.
     */
    private static final long ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD = 50 * 1024 * 8;

    private Connections mConnections;

    public DevicesAdapter(Context context) {
        super(context, R.layout.item_device_list);
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_device_list, parent, false);
        }

        View rateInOutView = convertView.findViewById(R.id.rateInOutContainer);
        TextView name = convertView.findViewById(R.id.name);
        TextView status = convertView.findViewById(R.id.status);
        TextView download = convertView.findViewById(R.id.download);
        TextView upload = convertView.findViewById(R.id.upload);

        String deviceId = getItem(position).deviceID;

        name.setText(getItem(position).getDisplayName());
        Resources r = getContext().getResources();

        Connections.Connection conn = null;
        if (mConnections != null && mConnections.connections.containsKey(deviceId)) {
            conn = mConnections.connections.get(deviceId);
        }

        if (conn == null) {
            // Syncthing is not running.
            rateInOutView.setVisibility(GONE);
            status.setVisibility(GONE);
            status.setText(r.getString(R.string.device_state_unknown));
            status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
            return convertView;
        }

        if (conn.paused) {
            rateInOutView.setVisibility(GONE);
            status.setVisibility(VISIBLE);
            status.setText(r.getString(R.string.device_paused));
            status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_black));
            return convertView;
        }

        if (conn.connected) {
            download.setText(Util.readableTransferRate(getContext(), conn.inBits));
            upload.setText(Util.readableTransferRate(getContext(), conn.outBits));
            rateInOutView.setVisibility(VISIBLE);
            status.setVisibility(VISIBLE);
            if (conn.completion == 100) {
                /**
                 * UI polish - We distinguish the following cases:
                 * a) conn.completion == 100 because of the model init assignment, data transmission ongoing.
                 * b) conn.completion == 100 because of a finished sync, no data transmission.
                 */
                if ((conn.inBits + conn.outBits) >= ACTIVE_SYNC_BITS_PER_SECOND_THRESHOLD) {
                    // case a) device_syncing
                    status.setText(r.getString(R.string.state_syncing_general));
                    status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
                } else {
                    // case b) device_up_to_date
                    status.setText(r.getString(R.string.device_up_to_date));
                    status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_green));
                }
            } else {
                status.setText(r.getString(R.string.device_syncing, conn.completion));
                status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
            }
            return convertView;
        }

        // !conn.connected
        rateInOutView.setVisibility(GONE);
        status.setVisibility(VISIBLE);
        status.setText(r.getString(R.string.device_disconnected));
        status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
        return convertView;
    }

    /**
     * Requests new connection info for all devices visible in listView.
     */
    public void updateDeviceStatus(RestApi restApi) {
        if (restApi == null || !restApi.isConfigLoaded()) {
            // Syncthing is not running. Clear last state.
            mConnections = null;
            return;
        }
        for (int i = 0; i < getCount(); i++) {
            restApi.getConnections(this::onReceiveConnections);
        }
    }

    private void onReceiveConnections(Connections connections) {
        mConnections = connections;
        // This will invoke "getView" for all elements.
        notifyDataSetChanged();
    }
}
