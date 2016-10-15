package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.views.FoldersAdapter;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing folders.
 */
public class FolderListFragment extends ListFragment implements SyncthingService.OnApiChangeListener,
        AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    /**
     * Compares folders by labels, uses the folder ID as fallback if the label is empty
     */
    private final static Comparator<Folder> FOLDERS_COMPARATOR = (lhs, rhs) -> {
        String lhsLabel = lhs.label != null && !lhs.label.isEmpty() ? lhs.label : lhs.id;
        String rhsLabel = rhs.label != null && !rhs.label.isEmpty() ? rhs.label : rhs.id;

        return lhsLabel.compareTo(rhsLabel);
    };

    private FoldersAdapter mAdapter;

    private Timer mTimer;

    @Override
    public void onResume() {
        super.onResume();
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                getActivity().runOnUiThread(FolderListFragment.this::updateList);
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

        setEmptyText(getString(R.string.folder_list_empty));
        getListView().setOnItemClickListener(this);
        getListView().setOnItemLongClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        updateList();
        setHasOptionsMenu(true);
    }

    /**
     * Refreshes ListView by updating folders and info.
     *
     * Also creates adapter if it doesn't exist yet.
     */
    private void updateList() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || activity.getApi() == null || getView() == null ||
                activity.isFinishing())
            return;

        if (mAdapter == null) {
            mAdapter = new FoldersAdapter(activity);
            setListAdapter(mAdapter);
        }

        // Prevent scroll position reset due to list update from clear().
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        List<Folder> folders = activity.getApi().getFolders();
        Collections.sort(folders, FOLDERS_COMPARATOR);
        mAdapter.addAll(folders);
        mAdapter.updateModel(activity.getApi());
        mAdapter.notifyDataSetChanged();
        setListShown(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(getActivity(), SettingsActivity.class)
                .setAction(SettingsActivity.ACTION_FOLDER_SETTINGS)
                .putExtra(SettingsActivity.EXTRA_IS_CREATE, false)
                .putExtra(FolderFragment.EXTRA_FOLDER_ID, mAdapter.getItem(i).id);
        startActivity(intent);
    }

    /**
     * Opens the folder path with a user selected app.
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(mAdapter.getItem(i).path);
        intent.setDataAndType(uri, "*/*");
        startActivity(intent);
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.folder_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_folder:
                Intent intent = new Intent(getActivity(), SettingsActivity.class)
                        .setAction(SettingsActivity.ACTION_FOLDER_SETTINGS)
                        .putExtra(SettingsActivity.EXTRA_IS_CREATE, true);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
