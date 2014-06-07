/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nutomic.syncthingandroid.gui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

/**
 * {@link android.support.v4.app.ListFragment} that shows a configurable loading text.
 */
public abstract class LoadingListFragment extends Fragment implements RestApi.OnApiAvailableListener {

	private boolean mInitialized = false;

	private ListFragment mListFragment;

	private View mListFragmentHolder;

	private View mLoading;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			mListFragment = (ListFragment) getChildFragmentManager()
					.getFragment(savedInstanceState, ListFragment.class.getName());
		}
		else {
			mListFragment = new ListFragment();
		}
		getChildFragmentManager()
				.beginTransaction()
				.add(R.id.list_fragment, mListFragment)
				.commit();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.loading_list_fragment, container, false);
		mListFragmentHolder = view.findViewById(R.id.list_fragment);
		mLoading = view.findViewById(R.id.loading);
		TextView loadingTextView =  (TextView) view.findViewById(R.id.loading_text);

		if (SyncthingService.isFirstStart(getActivity())) {
			loadingTextView.setText(getString(R.string.web_gui_creating_key));
		}

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		getChildFragmentManager().putFragment(outState, ListFragment.class.getName(), mListFragment);
	}

	/**
	 * Sets adapter and empty text for {@link ListFragment}
	 * @param adapter Adapter to be used for {@link}ListFragment#setListAdapter}
	 * @param emptyText Resource id for text to be shown in the
	 *                  {@link ListFragment#setEmptyText(CharSequence)}.
	 */
	public void setListAdapter(ListAdapter adapter, int emptyText) {
		mListFragment.setListAdapter(adapter);

		mLoading.setVisibility(View.INVISIBLE);
		mListFragmentHolder.setVisibility(View.VISIBLE);
		mListFragment.setEmptyText(getString(emptyText));
	}

	@Override
	public void onStart() {
		super.onStart();
		onApiAvailable();
	}

	/**
	 * Calls onInitAdapter if it has not yet been called, ListFragment is initialized,
	 * and SyncthingService is not null, and
	 */
	@Override
	public void onApiAvailable() {
		MainActivity activity = (MainActivity) getActivity();
		if (!mInitialized && getActivity() != null &&
				activity.getApi() != null && mListFragment != null) {
			onInitAdapter(activity);
			mInitialized = true;
		}
	}

	/**
	 * Called when the list adapter should be set.
	 * @param activity
	 */
	public abstract void onInitAdapter(MainActivity activity);

}
