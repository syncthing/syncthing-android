package com.nutomic.syncthingandroid.gui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBar.TabListener;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

/**
 * Shows {@link ReposFragment} and {@link NodesFragment} in different tabs, and
 * {@link LocalNodeInfoFragment} in the navigation drawer.
 */
public class MainActivity extends ActionBarActivity
		implements SyncthingService.OnApiChangeListener {

	private SyncthingService mSyncthingService;

	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mSyncthingService.registerOnApiChangeListener(MainActivity.this);
			mSyncthingService.registerOnApiChangeListener(mRepositoriesFragment);
			mSyncthingService.registerOnApiChangeListener(mNodesFragment);
			mSyncthingService.registerOnApiChangeListener(mLocalNodeInfoFragment);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	private AlertDialog mLoadingDialog;

	/**
	 * Causes population of repo and node lists, unlocks info drawer.
	 */
	@Override
	@SuppressLint("InflateParams")
	public void onApiChange(boolean isAvailable) {
		if (!isAvailable) {
			final SharedPreferences prefs =
					PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

			LayoutInflater inflater = getLayoutInflater();
			View dialogLayout = inflater.inflate(R.layout.loading_dialog, null);
			TextView loadingText = (TextView) dialogLayout.findViewById(R.id.loading_text);
			loadingText.setText((mSyncthingService.isFirstStart())
					? R.string.web_gui_creating_key
					: R.string.api_loading);

			mLoadingDialog = new AlertDialog.Builder(this)
					.setCancelable(false)
					.setView(dialogLayout)
					.show();

			// Make sure the first start dialog is shown on top.
			if (prefs.getBoolean("first_start", true)) {
				new AlertDialog.Builder(this)
						.setTitle(R.string.welcome_title)
						.setMessage(R.string.welcome_text)
						.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								prefs.edit().putBoolean("first_start", false).commit();
							}
						})
						.show();
			}

			return;
		}

		if (mLoadingDialog != null) {
			mLoadingDialog.dismiss();
		}
		mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
	}

	private final FragmentPagerAdapter mSectionsPagerAdapter =
			new FragmentPagerAdapter(getSupportFragmentManager()) {

		@Override
		public Fragment getItem(int position) {
			switch (position) {
			case 0: return mRepositoriesFragment;
			case 1: return mNodesFragment;
			default: return null;
			}
		}

		@Override
		public int getCount() {
			return 2;
		}

	};

	private ReposFragment mRepositoriesFragment;

	private NodesFragment mNodesFragment;

	private LocalNodeInfoFragment mLocalNodeInfoFragment;

	private ViewPager mViewPager;

	private ActionBarDrawerToggle mDrawerToggle;

	private DrawerLayout mDrawerLayout;

	/**
	 * Initializes tab navigation.
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ActionBar actionBar = getSupportActionBar();

		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		setContentView(R.layout.main_activity);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setSelectedNavigationItem(position);
			}
		});

		TabListener tabListener = new TabListener() {
			public void onTabSelected(Tab tab, FragmentTransaction ft) {
				mViewPager.setCurrentItem(tab.getPosition());
			}

			@Override
			public void onTabReselected(Tab tab, FragmentTransaction ft) {
			}

			@Override
			public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			}
		};

		actionBar.addTab(actionBar.newTab()
				.setText(R.string.repositories_fragment_title)
				.setTabListener(tabListener));
		actionBar.addTab(actionBar.newTab()
				.setText(R.string.nodes_fragment_title)
				.setTabListener(tabListener));

		if (savedInstanceState != null) {
			FragmentManager fm = getSupportFragmentManager();
			mRepositoriesFragment = (ReposFragment) fm.getFragment(
					savedInstanceState, ReposFragment.class.getName());
			mNodesFragment = (NodesFragment) fm.getFragment(
					savedInstanceState, NodesFragment.class.getName());
			mLocalNodeInfoFragment = (LocalNodeInfoFragment) fm.getFragment(
					savedInstanceState, LocalNodeInfoFragment.class.getName());
			mViewPager.setCurrentItem(savedInstanceState.getInt("currentTab"));
		}
		else {
			mRepositoriesFragment = new ReposFragment();
			mNodesFragment = new NodesFragment();
			mLocalNodeInfoFragment = new LocalNodeInfoFragment();
		}

		getApplicationContext().startService(
				new Intent(this, SyncthingService.class));
		bindService(new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);

		getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.drawer, mLocalNodeInfoFragment)
				.commit();
		mDrawerToggle = mLocalNodeInfoFragment.new Toggle(this, mDrawerLayout,
				R.drawable.ic_drawer);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
		if (mLoadingDialog != null) {
			mLoadingDialog.dismiss();
		}
	}

	/**
	 * Saves fragment states.
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Avoid crash if called during startup.
		if (mRepositoriesFragment != null && mNodesFragment != null &&
				mLocalNodeInfoFragment != null) {
			FragmentManager fm = getSupportFragmentManager();
			fm.putFragment(outState, ReposFragment.class.getName(), mRepositoriesFragment);
			fm.putFragment(outState, NodesFragment.class.getName(), mNodesFragment);
			fm.putFragment(outState, LocalNodeInfoFragment.class.getName(), mLocalNodeInfoFragment);
			outState.putInt("currentTab", mViewPager.getCurrentItem());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Shows menu only once syncthing service is running, and shows "share" option only when
	 * drawer is open.
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean drawerOpen = mDrawerLayout.isDrawerOpen(findViewById(R.id.drawer));
		menu.findItem(R.id.share_node_id).setVisible(drawerOpen);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mLocalNodeInfoFragment.onOptionsItemSelected(item) ||
				mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch (item.getItemId()) {
			case R.id.add_repo:
				Intent intent = new Intent(this, RepoSettingsActivity.class);
				intent.setAction(RepoSettingsActivity.ACTION_CREATE);
				startActivity(intent);
				return true;
			case R.id.add_node:
				intent = new Intent(this, NodeSettingsActivity.class);
				intent.setAction(NodeSettingsActivity.ACTION_CREATE);
				startActivity(intent);
				return true;
			case R.id.web_gui:
				startActivity(new Intent(this, WebGuiActivity.class));
				return true;
			case R.id.settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case R.id.exit:
				// Make sure we unbind first.
				finish();
				getApplicationContext().stopService(new Intent(this, SyncthingService.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}


	/**
	 * Returns RestApi instance, or null if SyncthingService is not yet connected.
	 */
	public RestApi getApi() {
		return (mSyncthingService != null)
				? mSyncthingService.getApi()
				: null;
	}
}
