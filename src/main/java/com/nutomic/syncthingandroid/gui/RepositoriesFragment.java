package com.nutomic.syncthingandroid.gui;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.RepositoryAdapter;
import com.nutomic.syncthingandroid.syncthing.RestApi;

/**
 * Displays a list of all existing repositories.
 */
public class RepositoriesFragment extends LoadingListFragment implements
		RestApi.OnApiAvailableListener {

	@Override
	public void onInitAdapter(MainActivity activity) {
		RepositoryAdapter adapter = new RepositoryAdapter(activity);
		adapter.add(activity.getApi().getRepositories());
		setListAdapter(adapter, R.string.repositories_list_empty);
	}

}
