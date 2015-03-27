package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBar.TabListener;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.dialogs.AsyncDialogs;
import com.nutomic.syncthingandroid.fragments.DevicesFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FoldersFragment;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

/**
 * Shows {@link com.nutomic.syncthingandroid.fragments.FoldersFragment} and {@link com.nutomic.syncthingandroid.fragments.DevicesFragment} in different tabs, and
 * {@link com.nutomic.syncthingandroid.fragments.DrawerFragment} in the navigation drawer.
 */
public class MainActivity extends SyncthingActivity
        implements SyncthingService.OnApiChangeListener {

    private AsyncTask mLoadingDialog;

    private AsyncTask mDisabledDialog;

    private boolean mIsDestroyed = false;

    /**
     * Causes population of folder and device lists, unlocks info drawer.
     */
    @Override
    @SuppressLint("InflateParams")
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE && !isFinishing() && !mIsDestroyed) {
            if (currentState == SyncthingService.State.DISABLED) {
                // Stop loading when disabled
                if (mLoadingDialog != null) {
                    mLoadingDialog.cancel(true);
                    mLoadingDialog = null;
                }
                mDisabledDialog = AsyncDialogs.showDisabledDialog(this);
            } else if (mLoadingDialog == null) {
                mLoadingDialog = AsyncDialogs.showLoadingDialog(this, getService().isFirstStart());
            }
            return;
        }

        if (mLoadingDialog != null) {
            mLoadingDialog.cancel(true);
            mLoadingDialog = null;
        }
        if (mDisabledDialog != null) {
            mDisabledDialog.cancel(true);
            mDisabledDialog = null;
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
                        case 0:
                            return mFolderFragment;
                        case 1:
                            return mDevicesFragment;
                        default:
                            return null;
                    }
                }

                @Override
                public int getCount() {
                    return 2;
                }

            };

    private FoldersFragment mFolderFragment;

    private DevicesFragment mDevicesFragment;

    private DrawerFragment mDrawerFragment;

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
                .setText(R.string.folders_fragment_title)
                .setTabListener(tabListener));
        actionBar.addTab(actionBar.newTab()
                .setText(R.string.devices_fragment_title)
                .setTabListener(tabListener));

        if (savedInstanceState != null) {
            FragmentManager fm = getSupportFragmentManager();
            mFolderFragment = (FoldersFragment) fm.getFragment(
                    savedInstanceState, FoldersFragment.class.getName());
            mDevicesFragment = (DevicesFragment) fm.getFragment(
                    savedInstanceState, DevicesFragment.class.getName());
            mDrawerFragment = (DrawerFragment) fm.getFragment(
                    savedInstanceState, DrawerFragment.class.getName());
            mViewPager.setCurrentItem(savedInstanceState.getInt("currentTab"));
        } else {
            mFolderFragment = new FoldersFragment();
            mDevicesFragment = new DevicesFragment();
            mDrawerFragment = new DrawerFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.drawer, mDrawerFragment)
                .commit();
        mDrawerToggle = new Toggle(this, mDrawerLayout);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoadingDialog != null) {
            mLoadingDialog.cancel(true);
        }
        mIsDestroyed = true;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnApiChangeListener(this);
        getService().registerOnApiChangeListener(mFolderFragment);
        getService().registerOnApiChangeListener(mDevicesFragment);
    }

    /**
     * Saves fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Avoid crash if called during startup.
        if (mFolderFragment != null && mDevicesFragment != null &&
                mDrawerFragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            fm.putFragment(outState, FoldersFragment.class.getName(), mFolderFragment);
            fm.putFragment(outState, DevicesFragment.class.getName(), mDevicesFragment);
            fm.putFragment(outState, DrawerFragment.class.getName(),
                    mDrawerFragment);
            outState.putInt("currentTab", mViewPager.getCurrentItem());
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

    @Override
     public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }


    /**
     * Receives drawer opened and closed events.
     */
    public class Toggle extends ActionBarDrawerToggle {
        public Toggle(Activity activity, DrawerLayout drawerLayout) {
            super(activity, drawerLayout, R.string.app_name, R.string.app_name);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            mDrawerFragment.onDrawerOpened();
            mFolderFragment.setHasOptionsMenu(false);
            mDevicesFragment.setHasOptionsMenu(false);
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            mDrawerFragment.onDrawerClosed();
            mFolderFragment.setHasOptionsMenu(true);
            mDevicesFragment.setHasOptionsMenu(true);
        }
    }

    /**
     * Closes the drawer. Use when navigating away from activity.
     */
    public void closeDrawer() {
        mDrawerLayout.closeDrawer(Gravity.START);
    }

}
