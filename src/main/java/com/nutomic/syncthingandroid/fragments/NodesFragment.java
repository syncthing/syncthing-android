package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
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
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE)
            return;

        initAdapter();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initAdapter();
    }

    private void initAdapter() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
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
        } else if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(getActivity(), SettingsActivity.class);
        intent.setAction(SettingsActivity.ACTION_NODE_SETTINGS_FRAGMENT);
        intent.putExtra(SettingsActivity.EXTRA_IS_CREATE, false);
        intent.putExtra(NodeSettingsFragment.EXTRA_NODE_ID, mAdapter.getItem(i).NodeID);
        startActivity(intent);
    }

}
