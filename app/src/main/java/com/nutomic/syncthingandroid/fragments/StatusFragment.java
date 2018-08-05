package com.nutomic.syncthingandroid.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.util.ArrayList;

/**
 * Displays why syncthing is running or disabled.
 */
public class StatusFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener {

    private SyncthingService.State mServiceState = SyncthingService.State.INIT;

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
        updateStatus();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateStatus();
    }

    private void updateStatus() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || getView() == null || activity.isFinishing()) {
            return;
        }

        ArrayList<String> statusItems = new ArrayList<String>();

        if (mServiceState == SyncthingService.State.ACTIVE) {
            statusItems.add("ToDo StatusFragment ACTIVE");
        } else {
            statusItems.add("ToDo StatusFragment DISABLED");
        }

        setListAdapter(new ArrayAdapter(getActivity(), R.layout.item_status_list, statusItems));
    }

}
