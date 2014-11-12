package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.FoldersAdapter;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing folders.
 */
public class FoldersFragment extends ListFragment implements SyncthingService.OnApiChangeListener,
        AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    private FoldersAdapter mAdapter;

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
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null || activity.getApi() == null)
            return;

        mAdapter = new FoldersAdapter(activity);
        mAdapter.add(activity.getApi().getFolders());
        setListAdapter(mAdapter);
        setEmptyText(getString(R.string.folder_list_empty));
        getListView().setOnItemClickListener(this);
        getListView().setOnItemLongClickListener(this);
    }

    private void updateList() {
        if (mAdapter == null || getView() == null)
            return;

        MainActivity activity = (MainActivity) getActivity();
        mAdapter.updateModel(activity.getApi(), getListView());
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
        Intent intent = new Intent(getActivity(), SettingsActivity.class)
                .setAction(SettingsActivity.ACTION_REPO_SETTINGS_FRAGMENT)
                .putExtra(SettingsActivity.EXTRA_IS_CREATE, false)
                .putExtra(FolderSettingsFragment.EXTRA_REPO_ID, mAdapter.getItem(i).ID);
        startActivity(intent);
    }

    /**
     * Opens the folder path with a user selected app.
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(mAdapter.getItem(i).Path);
        intent.setDataAndType(uri, "*/*");
        startActivity(intent);
        return true;
    }

}
