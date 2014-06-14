package com.nutomic.syncthingandroid.gui;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.nutomic.syncthingandroid.util.NodeAdapter;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing nodes.
 */
public class NodesFragment extends LoadingListFragment implements
		RestApi.OnApiAvailableListener, ListView.OnItemClickListener {

	private NodeAdapter mAdapter;

	private Timer mTimer;

	private boolean mInitialized = false;

	@Override
	public void onInitAdapter(MainActivity activity) {
		mAdapter = new NodeAdapter(activity);
		mAdapter.add(activity.getApi().getNodes());
		setListAdapter(mAdapter, R.string.nodes_list_empty);
		mInitialized = true;
	}

	private void updateList() {
		if (!mInitialized)
			return;

		MainActivity activity = (MainActivity) getActivity();
		if (activity != null) {
			mAdapter.updateConnections(activity.getApi(), getListView());
		}
	}

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);

		if (isVisibleToUser) {
			mTimer = new Timer();
			mTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					updateList();
				}

			}, 0, SyncthingService.GUI_UPDATE_INTERVAL);
		}
		else if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		Intent intent = new Intent(getActivity(), NodeSettingsActivity.class);
		intent.setAction(NodeSettingsActivity.ACTION_EDIT);
		intent.putExtra(NodeSettingsActivity.KEY_NODE_ID, mAdapter.getItem(i).NodeID);
		startActivity(intent);
	}

}
