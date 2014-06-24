package com.nutomic.syncthingandroid.gui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.NodesAdapter;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing nodes.
 */
public class NodesFragment extends ListFragment implements SyncthingService.OnApiChangeListener,
		ListView.OnItemClickListener {

	private NodesAdapter mAdapter;

	private Timer mTimer;

	@Override
	public void onResume() {
		super.onResume();
		setListShown(true);
	}

	@Override
	public void onApiChange(boolean isAvailable) {
		if (!isAvailable)
			return;

		initAdapter();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		initAdapter();
	}

	private void initAdapter() {
		MainActivity activity = (MainActivity) getActivity();
		if (activity == null || activity.getApi() == null)
			return;

		mAdapter = new NodesAdapter(activity);
		mAdapter.add(activity.getApi().getNodes());
		setListAdapter(mAdapter);
		setEmptyText(getString(R.string.nodes_list_empty));
		getListView().setOnItemClickListener(this);
	}

	private void updateList() {
		if (mAdapter == null || getView() == null)
			return;

		MainActivity activity = (MainActivity) getActivity();
		mAdapter.updateConnections(activity.getApi(), getListView());
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
