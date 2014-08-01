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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates item views for node items.
 */
public class NodesAdapter extends ArrayAdapter<RestApi.Node>
		implements RestApi.OnReceiveConnectionsListener {

	private Map<String, RestApi.Connection> mConnections =
			new HashMap<>();

	public NodesAdapter(Context context) {
		super(context, R.layout.node_list_item);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			LayoutInflater inflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			convertView = inflater.inflate(R.layout.node_list_item, parent, false);
		}

		TextView name = (TextView) convertView.findViewById(R.id.name);
		TextView status = (TextView) convertView.findViewById(R.id.status);
		TextView download = (TextView) convertView.findViewById(R.id.download);
		TextView upload = (TextView) convertView.findViewById(R.id.upload);

		name.setText(getItem(position).Name);
		final String nodeId = getItem(position).NodeID;

		RestApi.Connection conn = mConnections.get(nodeId);
		Resources res = getContext().getResources();
		if (conn != null) {
			if (conn.Completion == 100) {
				status.setText(res.getString(R.string.node_up_to_date));
				status.setTextColor(res.getColor(R.color.text_green));
			}
			else {
				status.setText(res.getString(R.string.node_syncing, conn.Completion));
				status.setTextColor(res.getColor(R.color.text_blue));
			}
			download.setText(RestApi.readableTransferRate(getContext(), conn.InBits));
			upload.setText(RestApi.readableTransferRate(getContext(), conn.OutBits));
		}
		else {
			download.setText("0 " + res.getStringArray(R.array.transfer_rate_units)[0]);
			upload.setText("0 " + res.getStringArray(R.array.transfer_rate_units)[0]);
			status.setText(res.getString(R.string.node_disconnected));
			status.setTextColor(res.getColor(R.color.text_red));
		}

		return convertView;
	}

	/**
	 * Replacement for addAll, which is not implemented on lower API levels.
	 */
	public void add(List<RestApi.Node> nodes) {
		for (RestApi.Node n : nodes) {
			add(n);
		}
	}

	/**
	 * Requests new connection info for all nodes visible in listView.
	 */
	public void updateConnections(RestApi api, ListView listView) {
		for (int i = 0; i < getCount(); i++) {
			if ( i >= listView.getFirstVisiblePosition() &&
					i <= listView.getLastVisiblePosition()) {
				api.getConnections(this);
			}
		}
	}

	@Override
	public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
		mConnections = connections;
		notifyDataSetInvalidated();
	}
}
