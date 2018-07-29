package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.FolderActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.views.FoldersAdapter;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing folders.
 */
public class FolderListFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener,
        AdapterView.OnItemClickListener {

    private FoldersAdapter mAdapter;

    private Timer mTimer;

    @Override
    public void onPause() {
        super.onPause();
        if (mTimer != null) {
            mTimer.cancel();
        }
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE)
            return;

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (getActivity() == null)
                    return;

                getActivity().runOnUiThread(FolderListFragment.this::updateList);
            }

        }, 0, Constants.GUI_UPDATE_INTERVAL);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(true);
        setEmptyText(getString(R.string.folder_list_empty));
        getListView().setOnItemClickListener(this);
    }

    /**
     * Refreshes ListView by updating folders and info.
     *
     * Also creates adapter if it doesn't exist yet.
     */
    private void updateList() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || getView() == null || activity.isFinishing()) {
            return;
        }
        RestApi restApi = activity.getApi();
        if (restApi == null || !restApi.isConfigLoaded()) {
            return;
        }
        List<Folder> folders = restApi.getFolders();
        if (folders == null) {
            return;
        }
        if (mAdapter == null) {
            mAdapter = new FoldersAdapter(activity);
            setListAdapter(mAdapter);
        }

        // Prevent scroll position reset due to list update from clear().
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        mAdapter.addAll(folders);
        mAdapter.updateFolderStatus(restApi);
        mAdapter.notifyDataSetChanged();
        setListShown(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(getActivity(), FolderActivity.class)
                .putExtra(FolderActivity.EXTRA_IS_CREATE, false)
                .putExtra(FolderActivity.EXTRA_FOLDER_ID, mAdapter.getItem(i).id);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.folder_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_folder:
                Intent intent = new Intent(getActivity(), FolderActivity.class)
                        .putExtra(FolderActivity.EXTRA_IS_CREATE, true);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
