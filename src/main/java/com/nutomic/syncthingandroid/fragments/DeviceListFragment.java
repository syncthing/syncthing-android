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
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.DevicesAdapter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing devices.
 */
public class DeviceListFragment extends ListFragment implements SyncthingService.OnApiChangeListener,
        ListView.OnItemClickListener {

    private final static Comparator<Device> DEVICES_COMPARATOR = (lhs, rhs) -> lhs.name.compareTo(rhs.name);

    private DevicesAdapter mAdapter;

    private Timer mTimer;

    @Override
    public void onResume() {
        super.onResume();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                getActivity().runOnUiThread(DeviceListFragment.this::updateList);
            }

        }, 0, SyncthingService.GUI_UPDATE_INTERVAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        mTimer.cancel();
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE)
            return;

        updateList();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setEmptyText(getString(R.string.devices_list_empty));
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        updateList();
        setHasOptionsMenu(true);
    }

    /**
     * Refreshes ListView by updating devices and info.
     *
     * Also creates adapter if it doesn't exist yet.
     */
    private void updateList() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || activity.getApi() == null || getView() == null ||
                activity.isFinishing())
            return;

        if (mAdapter == null) {
            mAdapter = new DevicesAdapter(activity);
            setListAdapter(mAdapter);
        }

        // Prevent scroll position reset due to list update from clear().
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        List<Device> devices = activity.getApi().getDevices(false);
        Collections.sort(devices, DEVICES_COMPARATOR);
        mAdapter.addAll(devices);
        mAdapter.updateConnections(activity.getApi());
        mAdapter.notifyDataSetChanged();
        setListShown(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(getActivity(), SettingsActivity.class);
        intent.setAction(SettingsActivity.ACTION_DEVICE_SETTINGS);
        intent.putExtra(SettingsActivity.EXTRA_IS_CREATE, false);
        intent.putExtra(DeviceFragment.EXTRA_DEVICE_ID, mAdapter.getItem(i).deviceID);
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
                        .setAction(SettingsActivity.ACTION_DEVICE_SETTINGS)
                        .putExtra(SettingsActivity.EXTRA_IS_CREATE, true);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
