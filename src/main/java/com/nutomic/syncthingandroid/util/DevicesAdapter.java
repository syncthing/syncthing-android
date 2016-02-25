package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates item views for device items.
 */
public class DevicesAdapter extends ArrayAdapter<RestApi.Device>
        implements RestApi.OnReceiveConnectionsListener {

    private Map<String, RestApi.Connection> mConnections =
            new HashMap<>();

    private final static Comparator<RestApi.Device> COMPARATOR = new Comparator<RestApi.Device>() {
        @Override
        public int compare(RestApi.Device lhs, RestApi.Device rhs) {
            return lhs.name.compareTo(rhs.name);
        }
    };

    public DevicesAdapter(Context context) {
        super(context, R.layout.item_device_list);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
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
        RestApi.Connection conn = mConnections.get(deviceId);

        name.setText(RestApi.getDeviceDisplayName(getItem(position)));
        Resources r = getContext().getResources();
        if (conn != null && conn.connected) {
            if (conn.completion == 100) {
                status.setText(r.getString(R.string.device_up_to_date));
                status.setTextColor(r.getColor(R.color.text_green));
            }
            else {
                status.setText(r.getString(R.string.device_syncing, conn.completion));
                status.setTextColor(r.getColor(R.color.text_blue));
            }
            download.setText(RestApi.readableTransferRate(getContext(), conn.inBits));
            upload.setText(RestApi.readableTransferRate(getContext(), conn.outBits));
        }
        else {
            download.setText(RestApi.readableTransferRate(getContext(), 0));
            upload.setText(RestApi.readableTransferRate(getContext(), 0));
            status.setText(r.getString(R.string.device_disconnected));
            status.setTextColor(r.getColor(R.color.text_red));
        }

        return convertView;
    }

    /**
     * Replacement for addAll, which is not implemented on lower API levels.
     */
    public void add(List<RestApi.Device> devices) {
        for (RestApi.Device n : devices) {
            add(n);
        }
    }

    /**
     * Sorts adapter after insert.
     */
    @Override
    public void add(RestApi.Device device) {
        super.add(device);
        sort(COMPARATOR);
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
    public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
        mConnections = connections;
        notifyDataSetInvalidated();
    }
}
