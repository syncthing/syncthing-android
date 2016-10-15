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
import com.nutomic.syncthingandroid.model.Connection;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates item views for device items.
 */
public class DevicesAdapter extends ArrayAdapter<Device>
        implements RestApi.OnReceiveConnectionsListener {

    private Map<String, Connection> mConnections =
            new HashMap<>();

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

        TextView name = (TextView) convertView.findViewById(R.id.name);
        TextView status = (TextView) convertView.findViewById(R.id.status);
        TextView download = (TextView) convertView.findViewById(R.id.download);
        TextView upload = (TextView) convertView.findViewById(R.id.upload);

        String deviceId = getItem(position).deviceID;
        Connection conn = mConnections.get(deviceId);

        name.setText(RestApi.getDeviceDisplayName(getItem(position)));
        Resources r = getContext().getResources();
        if (conn != null && conn.connected) {
            if (conn.completion == 100) {
                status.setText(r.getString(R.string.device_up_to_date));
                status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_green));
            }
            else {
                status.setText(r.getString(R.string.device_syncing, conn.completion));
                status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_blue));
            }
            download.setText(Util.readableTransferRate(getContext(), conn.inBits));
            upload.setText(Util.readableTransferRate(getContext(), conn.outBits));
        }
        else {
            download.setText(Util.readableTransferRate(getContext(), 0));
            upload.setText(Util.readableTransferRate(getContext(), 0));
            status.setText(r.getString(R.string.device_disconnected));
            status.setTextColor(ContextCompat.getColor(getContext(), R.color.text_red));
        }

        return convertView;
    }

    /**
     * Requests new connection info for all devices visible in listView.
     */
    public void updateConnections(RestApi api) {
        for (int i = 0; i < getCount(); i++) {
            api.getConnections(this);
        }
    }

    @Override
    public void onReceiveConnections(Map<String, Connection> connections) {
        mConnections = connections;
        notifyDataSetChanged();
    }
}
