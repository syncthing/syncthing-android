package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
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
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.model.Options;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.min;

/**
 * Shows {@link FolderListFragment} and
 * {@link DeviceListFragment} in different tabs, and
 * {@link DrawerFragment} in the navigation drawer.
 */
public class MainActivity extends SyncthingActivity
        implements SyncthingService.OnApiChangeListener {

    private static final String TAG = "MainActivity";
    private static final String IS_SHOWING_RESTART_DIALOG =
            "com.nutomic.syncthingandroid.activities.MainActivity.RESTART_DIALOG_STATE";
    private static final String BATTERY_DIALOG_DISMISSED =
            "com.nutomic.syncthingandroid.activities.MainActivity.BATTERY_DIALOG_STATE";

    /**
     * Time after first start when usage reporting dialog should be shown.
     *
     * @see #showUsageReportingDialog()
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3);

    private AlertDialog mDisabledDialog;
    private AlertDialog mBatteryOptimizationsDialog;

    private Dialog mRestartDialog;

    private boolean mIsShowingRestartDialog;
    private boolean mBatteryOptimizationDialogDismissed;


    private ViewPager mViewPager;

    private FolderListFragment mFolderListFragment;
    private DeviceListFragment mDeviceListFragment;
    private DrawerFragment     mDrawerFragment;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout          mDrawerLayout;

    /**
     * Handles various dialogs based on current state.
     */
    @Override
    public void onApiChange(SyncthingService.State currentState) {
        switch (currentState) {
            case STARTING:
                dismissDisabledDialog();
                break;
            case ACTIVE:
                dismissDisabledDialog();
                showBatteryOptimizationDialogIfNecessary();
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                mDrawerFragment.requestGuiUpdate();
                if (new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY &&
                        getApi().getOptions().getUsageReportValue() == Options.USAGE_REPORTING_UNDECIDED) {
                    showUsageReportingDialog();
                }
                break;
            case ERROR:
                finish();
                break;
            case DISABLED:
                if (!isFinishing()) {
                    showDisabledDialog();
                }
                break;
        }
    }

    @TargetApi(23)
    private void showBatteryOptimizationDialogIfNecessary() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean dontShowAgain = sp.getBoolean("battery_optimization_dont_show_again", false);
        if (dontShowAgain || mBatteryOptimizationsDialog != null ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                pm.isIgnoringBatteryOptimizations(getPackageName()) ||
                mBatteryOptimizationDialogDismissed) {
            return;
        }

        mBatteryOptimizationsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_disable_battery_optimization_title)
                .setMessage(R.string.dialog_disable_battery_optimization_message)
                .setPositiveButton(R.string.dialog_disable_battery_optimization_turn_off, (d, i) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNeutralButton(R.string.dialog_disable_battery_optimization_later, (d, i) -> mBatteryOptimizationDialogDismissed = true)
                .setNegativeButton(R.string.dialog_disable_battery_optimization_dont_show_again, (d, i) -> {
                    sp.edit().putBoolean("battery_optimization_dont_show_again", true).apply();
                })
                .setOnCancelListener(d -> mBatteryOptimizationDialogDismissed = true)
                .show();
    }

    private void dismissDisabledDialog() {
        if (mDisabledDialog != null) {
            mDisabledDialog.dismiss();
            mDisabledDialog = null;
        }
    }

    /**
     * Returns the unix timestamp at which the app was first installed.
     */
    private long getFirstStartTime() {
        PackageManager pm = getPackageManager();
        long firstInstallTime = 0;
        try {
            firstInstallTime = pm.getPackageInfo(getPackageName(), 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "This should never happen", e);
        }
        return firstInstallTime;
    }

    private final FragmentPagerAdapter mSectionsPagerAdapter =
            new FragmentPagerAdapter(getSupportFragmentManager()) {

                @Override
                public Fragment getItem(int position) {
                    switch (position) {
                        case 0:
                            return mFolderListFragment;
                        case 1:
                            return mDeviceListFragment;
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
            mFolderListFragment = (FolderListFragment) fm.getFragment(
                    savedInstanceState, FolderListFragment.class.getName());
            mDeviceListFragment = (DeviceListFragment) fm.getFragment(
                    savedInstanceState, DeviceListFragment.class.getName());
            mDrawerFragment = (DrawerFragment) fm.getFragment(
                    savedInstanceState, DrawerFragment.class.getName());
            mViewPager.setCurrentItem(savedInstanceState.getInt("currentTab"));

            mIsShowingRestartDialog = savedInstanceState.getBoolean(IS_SHOWING_RESTART_DIALOG);
            if (mIsShowingRestartDialog){
                showRestartDialog();
            }
            mBatteryOptimizationDialogDismissed = savedInstanceState.getBoolean(BATTERY_DIALOG_DISMISSED);
        } else {
            mFolderListFragment = new FolderListFragment();
            mDeviceListFragment = new DeviceListFragment();
            mDrawerFragment = new DrawerFragment();
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.drawer, mDrawerFragment)
                .commit();
        mDrawerToggle = new Toggle(this, mDrawerLayout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        setOptimalDrawerWidth(findViewById(R.id.drawer));

        onNewIntent(getIntent());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissDisabledDialog();
        if (getService() != null) {
            getService().unregisterOnApiChangeListener(this);
            getService().unregisterOnApiChangeListener(mFolderListFragment);
            getService().unregisterOnApiChangeListener(mDeviceListFragment);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnApiChangeListener(this);
        getService().registerOnApiChangeListener(mFolderListFragment);
        getService().registerOnApiChangeListener(mDeviceListFragment);
    }

    /**
     * Saves current tab index and fragment states.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Avoid crash if called during startup.
        if (mFolderListFragment != null && mDeviceListFragment != null && mDrawerFragment != null) {
            FragmentManager fm = getSupportFragmentManager();
            fm.putFragment(outState, FolderListFragment.class.getName(), mFolderListFragment);
            fm.putFragment(outState, DeviceListFragment.class.getName(), mDeviceListFragment);
            fm.putFragment(outState, DrawerFragment.class.getName(), mDrawerFragment);
            outState.putInt("currentTab", mViewPager.getCurrentItem());
            outState.putBoolean(BATTERY_DIALOG_DISMISSED, mBatteryOptimizationDialogDismissed);

            outState.putBoolean(IS_SHOWING_RESTART_DIALOG, mIsShowingRestartDialog);
            if (mRestartDialog != null){
                mRestartDialog.cancel();
            }

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

    private void showDisabledDialog() {
        mDisabledDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.syncthing_disabled_title)
                .setMessage(R.string.syncthing_disabled_message)
                .setPositiveButton(R.string.syncthing_disabled_change_settings,
                        (dialogInterface, i) -> {
                            finish();
                            startActivity(new Intent(this, SettingsActivity.class));
                        }
                )
                .setNegativeButton(R.string.exit,
                        (dialogInterface, i) -> finish()
                )
                .setOnCancelListener(dialogInterface -> finish())
                .show();
        mDisabledDialog.setCanceledOnTouchOutside(false);
    }

    public void showRestartDialog(){
        mRestartDialog = createRestartDialog();
        mRestartDialog.show();
        mIsShowingRestartDialog = true;
    }

    private Dialog createRestartDialog(){
        return  new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_confirm_restart)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i1) -> this.startService(new Intent(this, SyncthingService.class)
                        .setAction(SyncthingService.ACTION_RESTART)))
                .setNegativeButton(android.R.string.no, (dialog, which) -> {mIsShowingRestartDialog = false; })
                .setOnCancelListener(dialog -> {mIsShowingRestartDialog = false; })
                .create();
    }

    @Override
     public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    /**
     * Handles drawer opened and closed events, toggling option menu state.
     */
    private class Toggle extends ActionBarDrawerToggle {
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

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            super.onDrawerSlide(drawerView, 0);
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
     * Close drawer on back button press.
     */
    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START))
            closeDrawer();
        else
            super.onBackPressed();
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
    private void showUsageReportingDialog() {
        final DialogInterface.OnClickListener listener = (dialog, which) -> {
            Options options = getApi().getOptions();
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    options.urAccepted = Options.USAGE_REPORTING_ACCEPTED;
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    options.urAccepted = Options.USAGE_REPORTING_DENIED;
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    Uri uri = Uri.parse("https://data.syncthing.net");
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    break;
            }
            getApi().editSettings(getApi().getGui(), options, this);
        };

        getApi().getUsageReport(report -> {
            @SuppressLint("InflateParams")
            View v = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.dialog_usage_reporting, null);
            TextView tv = (TextView) v.findViewById(R.id.example);
            tv.setText(report);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.usage_reporting_dialog_title)
                    .setView(v)
                    .setPositiveButton(R.string.yes, listener)
                    .setNegativeButton(R.string.no, listener)
                    .setNeutralButton(R.string.open_website, listener)
                    .show();
        });
    }

}
