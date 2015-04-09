package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.DevicesAdapter;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing devices.
 */
public class DevicesFragment extends ListFragment implements SyncthingService.OnApiChangeListener,
        ListView.OnItemClickListener {

    private DevicesAdapter mAdapter;

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
        setHasOptionsMenu(true);
    }

    private void initAdapter() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || activity.getApi() == null)
            return;

        mAdapter = new DevicesAdapter(activity);
        mAdapter.add(activity.getApi().getDevices(false));
        setListAdapter(mAdapter);
        setEmptyText(getString(R.string.devices_list_empty));
        getListView().setOnItemClickListener(this);
    }

    private void updateList() {
        if (mAdapter == null || getView() == null || getActivity().isFinishing())
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
        intent.putExtra(DeviceSettingsFragment.EXTRA_NODE_ID, mAdapter.getItem(i).deviceID);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.device_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_device:
                Intent intent = new Intent(getActivity(), SettingsActivity.class)
                        .setAction(SettingsActivity.ACTION_NODE_SETTINGS_FRAGMENT)
                        .putExtra(SettingsActivity.EXTRA_IS_CREATE, true);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
