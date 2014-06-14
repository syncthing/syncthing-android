package com.nutomic.syncthingandroid.gui;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.RepositoryAdapter;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a list of all existing repositories.
 */
public class RepositoriesFragment extends LoadingListFragment implements
		RestApi.OnApiAvailableListener, AdapterView.OnItemClickListener {

	private RepositoryAdapter mAdapter;

	private Timer mTimer;

	private boolean mInitialized = false;

	@Override
	public void onInitAdapter(MainActivity activity) {
		mAdapter = new RepositoryAdapter(activity);
		mAdapter.add(activity.getApi().getRepositories());
		setListAdapter(mAdapter, R.string.repositories_list_empty);
		mInitialized = true;
	}

	private void updateList() {
		if (!mInitialized)
			return;

		MainActivity activity = (MainActivity) getActivity();
		if (activity != null) {
			mAdapter.updateModel(activity.getApi(), getListView());
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
		Intent intent = new Intent(getActivity(), RepoSettingsActivity.class);
		intent.setAction(RepoSettingsActivity.ACTION_EDIT);
		intent.putExtra(RepoSettingsActivity.KEY_REPOSITORY_ID, mAdapter.getItem(i).ID);
		startActivity(intent);
	}
}
