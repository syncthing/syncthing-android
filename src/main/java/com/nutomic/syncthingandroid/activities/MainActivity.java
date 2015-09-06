package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.DevicesFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FoldersFragment;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.Date;

import static java.lang.Math.min;

/**
 * Shows {@link com.nutomic.syncthingandroid.fragments.FoldersFragment} and
 * {@link com.nutomic.syncthingandroid.fragments.DevicesFragment} in different tabs, and
 * {@link com.nutomic.syncthingandroid.fragments.DrawerFragment} in the navigation drawer.
 */
public class MainActivity extends SyncthingActivity
        implements SyncthingService.OnApiChangeListener {

    private static final String TAG = "MainActivity";

    /**
     * Time after first start when usage reporting dialog should be shown.
     *
     * @see #showUsageReportingDialog()
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = 3 * 24 * 60 * 60 * 1000;

    private AlertDialog mLoadingDialog;

    private AlertDialog mDisabledDialog;

    /**
     * Causes population of folder and device lists, unlocks info drawer.
     */
    @Override
    @SuppressLint("InflateParams")
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState == SyncthingService.State.ACTIVE &&
                new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY &&
                getApi().getUsageReportAccepted() == RestApi.UsageReportSetting.UNDECIDED) {
            showUsageReportingDialog();
        } else if (currentState == SyncthingService.State.ERROR) {
            finish();
        } else if (currentState != SyncthingService.State.ACTIVE && !isFinishing()) {
            if (currentState == SyncthingService.State.DISABLED) {
                if (mLoadingDialog != null) {
                    mLoadingDialog.dismiss();
                    mLoadingDialog = null;
                }
                mDisabledDialog = SyncthingService.showDisabledDialog(MainActivity.this);
            } else if (mLoadingDialog == null) {
                LayoutInflater inflater = getLayoutInflater();
                View dialogLayout = inflater.inflate(R.layout.dialog_loading, null);
                TextView loadingText = (TextView) dialogLayout.findViewById(R.id.loading_text);
                loadingText.setText((getService().isFirstStart())
                        ? R.string.web_gui_creating_key
                        : R.string.api_loading);

                mLoadingDialog = new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(false)
                        .setView(dialogLayout)
                        .show();

                final SharedPreferences sp =
                        PreferenceManager.getDefaultSharedPreferences(MainActivity.this);

                // Make sure the first start dialog is shown on top.
                if (sp.getBoolean("first_start", true)) {
                    showFirstStartDialog(sp);
                }
            }
            return;
        }

        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
        if (mDisabledDialog != null) {
            mDisabledDialog.dismiss();
            mDisabledDialog = null;
        }

        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        mDrawerFragment.requestGuiUpdate();
    }

    /**
     * Returns the unix timestamp at which the app was first installed.
     */
    @TargetApi(9)
    private long getFirstStartTime() {
        PackageManager pm = getPackageManager();
        long firstInstallTime = 0;
        try {
            // No info is available on Froyo.
            firstInstallTime = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
                    ? pm.getPackageInfo(getPackageName(), 0).firstInstallTime
                    : 0;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "This should never happen", e);
        }
        return firstInstallTime;
    }

    /**
     * Displays information for first app start.
     */
    private void showFirstStartDialog(final SharedPreferences sp) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_text)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sp.edit().putBoolean("first_start", false).commit();
                    }
                })
                .show();
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

                @Override
                public CharSequence getPageTitle(int position) {
                    switch (position) {
                        case 0:
                            return getResources().getString(R.string.folders_fragment_title);
                        case 1:
                            return getResources().getString(R.string.devices_fragment_title);
                        default:
                            return String.valueOf(position);
                    }
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

        setContentView(R.layout.activity_main);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabContainer);
        tabLayout.setupWithViewPager(mViewPager);

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
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        setOptimalDrawerWidth(findViewById(R.id.drawer));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
        }
        if (getService() != null) {
            getService().unregisterOnApiChangeListener(this);
            getService().unregisterOnApiChangeListener(mFolderFragment);
            getService().unregisterOnApiChangeListener(mDevicesFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnApiChangeListener(this);
        getService().registerOnApiChangeListener(mFolderFragment);
        getService().registerOnApiChangeListener(mDevicesFragment);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Avoid crash if called during startup.
        if (mFolderFragment != null && mDevicesFragment != null && mDrawerFragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            fm.putFragment(outState, FoldersFragment.class.getName(), mFolderFragment);
            fm.putFragment(outState, DevicesFragment.class.getName(), mDevicesFragment);
            fm.putFragment(outState, DrawerFragment.class.getName(), mDrawerFragment);
            outState.putInt("currentTab", mViewPager.getCurrentItem());
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        mDrawerToggle.syncState();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
        }
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
     * Handles drawer opened and closed events, toggling option menu state.
     */
    public class Toggle extends ActionBarDrawerToggle {
        public Toggle(Activity activity, DrawerLayout drawerLayout) {
            super(activity, drawerLayout, R.string.app_name, R.string.app_name);
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            mDrawerFragment.onDrawerOpened();
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            mDrawerFragment.onDrawerClosed();
        }
    }

    /**
     * Closes the drawer. Use when navigating away from activity.
     */
    public void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    /**
     * Toggles the drawer on menu button press.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!mDrawerLayout.isDrawerOpen(GravityCompat.START))
                mDrawerLayout.openDrawer(GravityCompat.START);
            else
                closeDrawer();

            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    /**
     * Calculating width based on
     * http://www.google.com/design/spec/patterns/navigation-drawer.html#navigation-drawer-specs.
     */
    private void setOptimalDrawerWidth(View drawerContainer) {
        int actionBarSize = 0;
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
            actionBarSize = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
        }

        ViewGroup.LayoutParams params = drawerContainer.getLayoutParams();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int minScreenWidth = min(displayMetrics.widthPixels, displayMetrics.heightPixels);

        params.width = min(minScreenWidth - actionBarSize, 5 * actionBarSize);
        drawerContainer.requestLayout();
    }

    /**
     * Displays dialog asking user to accept/deny usage reporting.
     */
    @SuppressLint("InflateParams")
    private void showUsageReportingDialog() {
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                getApi().setUsageReportAccepted(
                        (which == DialogInterface.BUTTON_POSITIVE)
                                ? RestApi.UsageReportSetting.ACCEPTED
                                : RestApi.UsageReportSetting.DENIED,
                        MainActivity.this);
            }
        };

        getApi().getUsageReport(new RestApi.OnReceiveUsageReportListener() {
            @Override
            public void onReceiveUsageReport(String report) {
                View v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.dialog_usage_reporting, null);
                TextView tv = (TextView) v.findViewById(R.id.example);
                tv.setText(report);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.usage_reporting_dialog_title)
                        .setView(v)
                        .setPositiveButton(R.string.yes, listener)
                        .setNegativeButton(R.string.no, listener)
                        .setCancelable(false)
                        .show();
            }
        });
    }

}
