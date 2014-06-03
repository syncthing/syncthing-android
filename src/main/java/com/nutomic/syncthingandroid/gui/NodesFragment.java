package com.nutomic.syncthingandroid.gui;

import com.nutomic.syncthingandroid.NodeAdapter;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;

/**
 * Displays a list of all existing nodes.
 */
public class NodesFragment extends LoadingListFragment implements
		RestApi.OnApiAvailableListener {

	@Override
	public void onInitAdapter(MainActivity activity) {
		NodeAdapter adapter = new NodeAdapter(activity);
		adapter.add(activity.getApi().getNodes());
		setListAdapter(adapter, R.string.nodes_list_empty);
	}

}
