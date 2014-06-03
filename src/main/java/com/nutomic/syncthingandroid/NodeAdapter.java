package com.nutomic.syncthingandroid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.util.List;

/**
 * Generates item views for node items.
 */
public class NodeAdapter extends ArrayAdapter<RestApi.Node> {

	public NodeAdapter(Context context) {
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
		name.setText(getItem(position).Name);
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

}
