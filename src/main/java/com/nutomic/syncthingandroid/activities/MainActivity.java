package com.nutomic.syncthingandroid.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
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
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.DeviceListFragment;
import com.nutomic.syncthingandroid.fragments.DrawerFragment;
import com.nutomic.syncthingandroid.fragments.FolderListFragment;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.Arrays;
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

    /**
     * Time after first start when usage reporting dialog should be shown.
     *
     * @see #showUsageReportingDialog()
     */
    private static final long USAGE_REPORTING_DIALOG_DELAY = TimeUnit.DAYS.toMillis(3);

    private static final int REQUEST_WRITE_STORAGE = 142;

    private AlertDialog mLoadingDialog;
    private AlertDialog mDisabledDialog;

    private ViewPager mViewPager;

    private FolderListFragment mFolderListFragment;
    private DeviceListFragment mDeviceListFragment;
    private DrawerFragment     mDrawerFragment;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout          mDrawerLayout;

    private SharedPreferences mPreferences;

    /**
     * Handles various dialogs based on current state.
     */
    @Override
    public void onApiChange(SyncthingService.State currentState) {
        switch (currentState) {
            case INIT:
                showLoadingDialog();
                // Make sure the first start dialog is shown on top.
                if (isFirstStart()) {
                    showFirstStartDialog();
                }
                break;
            case STARTING:
                showLoadingDialog();
                dismissDisabledDialog();
                int permissionState = ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (permissionState != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            REQUEST_WRITE_STORAGE);
                }
                break;
            case ACTIVE:
                dismissDisabledDialog();
                dismissLoadingDialog();
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                mDrawerFragment.requestGuiUpdate();
                if (new Date().getTime() > getFirstStartTime() + USAGE_REPORTING_DIALOG_DELAY &&
                        getApi().getUsageReportAccepted() == RestApi.USAGE_REPORTING_UNDECIDED) {
                    showUsageReportingDialog();
                }
                break;
            case ERROR:
                finish();
                break;
            case DISABLED:
                dismissLoadingDialog();
                if (!isFinishing()) {
                    mDisabledDialog = SyncthingService.showDisabledDialog(MainActivity.this);
                }
                break;
        }
    }

    private void dismissDisabledDialog() {
        if (mDisabledDialog != null) {
            mDisabledDialog.dismiss();
            mDisabledDialog = null;
        }
    }

    private boolean isFirstStart() {
        return mPreferences.getBoolean("first_start", true);
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

    /**
     * Displays information for first app start.
     */
    private void showFirstStartDialog() {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_text)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mPreferences.edit().putBoolean("first_start", false).apply();
                    }
                })
                .show();
    }

    /**
     * Shows the loading dialog with the correct text ("creating keys" or "loading").
     */
    private void showLoadingDialog() {
        if (mLoadingDialog != null)
            return;

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams")
        View dialogLayout = inflater.inflate(R.layout.dialog_loading, null);
        TextView loadingText = (TextView) dialogLayout.findViewById(R.id.loading_text);
        loadingText.setText((isFirstStart())
                ? R.string.web_gui_creating_key
                : R.string.api_loading);

        mLoadingDialog = new AlertDialog.Builder(MainActivity.this)
                .setCancelable(false)
                .setView(dialogLayout)
                .show();
    }

    private void dismissLoadingDialog() {
        if (mLoadingDialog != null) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
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
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
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
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        setOptimalDrawerWidth(findViewById(R.id.drawer));

        onNewIntent(getIntent());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dismissDisabledDialog();
        dismissLoadingDialog();
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
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        getApi().setUsageReportAccepted(RestApi.USAGE_REPORTING_ACCEPTED,
                                                        MainActivity.this);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        getApi().setUsageReportAccepted(RestApi.USAGE_REPORTING_DENIED,
                                                        MainActivity.this);
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        Uri uri = Uri.parse("https://data.syncthing.net");
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        break;
                }

            }
        };

        getApi().getUsageReport(new RestApi.OnReceiveUsageReportListener() {
            @Override
            public void onReceiveUsageReport(String report) {
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
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                Log.d(TAG, Arrays.toString(grantResults));
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.toast_write_storage_permission_required,
                                   Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}
