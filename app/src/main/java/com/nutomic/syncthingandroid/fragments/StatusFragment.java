package com.nutomic.syncthingandroid.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.util.ArrayList;

/**
 * Displays why syncthing is running or disabled.
 */
public class StatusFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "StatusFragment";
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
        setHasOptionsMenu(true);
        updateStatus();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.status_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.open_preferences:
                startActivity(new Intent(getContext(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateStatus() {
        SyncthingActivity syncthingActivity = (SyncthingActivity) getActivity();
        if (syncthingActivity == null || getView() == null || syncthingActivity.isFinishing()) {
            return;
        }
        SyncthingService syncthingService = syncthingActivity.getService();
        if (syncthingService == null) {
            return;
        }

        // Get explanation why syncthing is running or not running from RunConditionMonitor.
        String syncthingStateExplantion = getString(mServiceState == SyncthingService.State.ACTIVE ?
            R.string.syncthing_running : R.string.syncthing_not_running);
        syncthingStateExplantion += " " + getString(R.string.reason) + "\n";
        syncthingStateExplantion += "- " + syncthingService.getRunDecisionExplanation().trim().replace("\n", "\n- ");

        // Prepare status items for ArrayAdapter.
        ArrayList<String> statusItems = new ArrayList<String>();
        statusItems.add(syncthingStateExplantion);
        setListAdapter(new ArrayAdapter(getActivity(), android.R.layout.simple_list_item_1, statusItems));
    }

}
