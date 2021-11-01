package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;

import javax.inject.Inject;

public class FirstStartActivity extends Activity {

    private static String TAG = "FirstStartActivity";
<<<<<<< HEAD
=======
    private static final int REQUEST_COARSE_LOCATION = 141;
    private static final int REQUEST_BACKGROUND_LOCATION = 142;
    private static final int REQUEST_FINE_LOCATION = 144;
    private static final int REQUEST_WRITE_STORAGE = 143;

    private static class Slide {
        public int layout;
        public int dotColorActive;
        public int dotColorInActive;

        Slide(int layout, int dotColorActive, int dotColorInActive) {
            this.layout = layout;
            this.dotColorActive = dotColorActive;
            this.dotColorInActive = dotColorInActive;
        }
    }
>>>>>>> 545a9ffb (Support SDcard read/write using all files access on Android 11+ (fixes #568) (#618))

    private static final int SLIDE_POS_LOCATION_PERMISSION = 1;

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout mDotsLayout;
    private TextView[] mDots;
    private int[] mLayouts;
    private Button mBackButton;
    private Button mNextButton;

    @Inject SharedPreferences mPreferences;

    /**
     * Handles activity behaviour depending on {@link #isFirstStart()} and {@link #haveStoragePermission()}.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        /**
         * Recheck storage permission. If it has been revoked after the user
         * completed the welcome slides, displays the slides again.
         */
<<<<<<< HEAD
        if (!mPreferences.getBoolean(Constants.PREF_FIRST_START, true) &&
                haveStoragePermission()) {
=======
        Boolean showSlideStoragePermission = !haveStoragePermission();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showSlideStoragePermission = showSlideStoragePermission || !haveAllFilesAccessPermission();
        }
        Boolean showSlideIgnoreDozePermission = !haveIgnoreDozePermission();
        Boolean showSlideLocationPermission = !haveLocationPermission();
        Boolean showSlideKeyGeneration = !checkForParseableConfig();

        /**
         * If we don't have to show slides for mandatory prerequisites,
         * start directly into MainActivity.
         */
        if (!showSlideStoragePermission &&
                !showSlideIgnoreDozePermission &&
                !showSlideKeyGeneration) {
>>>>>>> 545a9ffb (Support SDcard read/write using all files access on Android 11+ (fixes #568) (#618))
            startApp();
            return;
        }

        // Log what's missing and preventing us from directly starting into MainActivity.
        if (showSlideStoragePermission) {
            Log.d(TAG, "We (no longer?) have storage permission and will politely ask for it.");
        }
        if (showSlideIgnoreDozePermission) {
            Log.d(TAG, "We (no longer?) have ignore doze permission and will politely ask for it on phones.");
        }
        if (showSlideLocationPermission) {
            Log.d(TAG, "We (no longer?) have location permission and will politely ask for it.");
        }
        if (showSlideKeyGeneration) {
            Log.d(TAG, "We (no longer?) have a valid Syncthing config and will attempt to generate a fresh config.");
        }

        // Make notification bar transparent (API level 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // Show first start welcome wizard UI.
        setContentView(R.layout.activity_first_start);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);
        mDotsLayout = (LinearLayout) findViewById(R.id.layoutDots);
        mBackButton = (Button) findViewById(R.id.btn_back);
        mNextButton = (Button) findViewById(R.id.btn_next);

        mViewPager.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the event to prevent swiping through the slides.
                v.performClick();
                return true;
            }
        });

        // Layouts of all welcome slides
        mLayouts = new int[]{
                R.layout.activity_firststart_slide1,
                R.layout.activity_firststart_slide2,
                R.layout.activity_firststart_slide3};

        // Add bottom dots
        addBottomDots(0);

        // Make notification bar transparent
        changeStatusBarColor();

        mViewPagerAdapter = new ViewPagerAdapter();
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.addOnPageChangeListener(mViewPagerPageChangeListener);

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnBackClick();
            }
        });

        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBtnNextClick();
            }
        });
    }

    public void onBtnBackClick() {
        int current = getItem(-1);
        if (current >= 0) {
            // Move to previous slider.
            mViewPager.setCurrentItem(current);
            if (current == 0) {
                mBackButton.setVisibility(View.GONE);
            }
        }
    }

    public void onBtnNextClick() {
        // Check if we are allowed to advance to the next slide.
        if (mViewPager.getCurrentItem() == SLIDE_POS_LOCATION_PERMISSION) {
            // As the storage permission is a prerequisite to run syncthing, refuse to continue without it.
            Boolean storagePermissionsGranted = haveStoragePermission();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storagePermissionsGranted = storagePermissionsGranted && haveAllFilesAccessPermission();
            }
            if (!storagePermissionsGranted) {
                Toast.makeText(this, R.string.toast_write_storage_permission_required,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        int current = getItem(+1);
        if (current < mLayouts.length) {
            // Move to next slide.
            mViewPager.setCurrentItem(current);
            mBackButton.setVisibility(View.VISIBLE);
        } else {
            // Start the app after "mNextButton" was hit on the last slide.
            Log.v(TAG, "User completed first start UI.");
            mPreferences.edit().putBoolean(Constants.PREF_FIRST_START, false).apply();
            startApp();
        }
    }

    private void addBottomDots(int currentPage) {
        mDots = new TextView[mLayouts.length];

        int[] colorsActive = getResources().getIntArray(R.array.array_dot_active);
        int[] colorsInactive = getResources().getIntArray(R.array.array_dot_inactive);

        mDotsLayout.removeAllViews();
        for (int i = 0; i < mDots.length; i++) {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226;"));
            mDots[i].setTextSize(35);
            mDots[i].setTextColor(colorsInactive[currentPage]);
            mDotsLayout.addView(mDots[i]);
        }

        if (mDots.length > 0)
            mDots[currentPage].setTextColor(colorsActive[currentPage]);
    }

    private int getItem(int i) {
        return mViewPager.getCurrentItem() + i;
    }

    //  ViewPager change listener
    ViewPager.OnPageChangeListener mViewPagerPageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            addBottomDots(position);

            // Change the next button text from next to finish on last slide.
            mNextButton.setText(getString((position == mLayouts.length - 1) ? R.string.finish : R.string.cont));
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {

        }
    };

    /**
     * Making notification bar transparent
     */
    private void changeStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    /**
     * View pager adapter
     */
    public class ViewPagerAdapter extends PagerAdapter {
        private LayoutInflater layoutInflater;

        public ViewPagerAdapter() {
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View view = layoutInflater.inflate(mLayouts[position], container, false);

            /* Slide: storage permission */
            Button btnGrantStoragePerm = (Button) view.findViewById(R.id.btnGrantStoragePerm);
            if (btnGrantStoragePerm != null) {
                btnGrantStoragePerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestStoragePermission();
                    }
                });
            }

            /* Slide: location permission */
            Button btnGrantLocationPerm = (Button) view.findViewById(R.id.btnGrantLocationPerm);
            if (btnGrantLocationPerm != null) {
                btnGrantLocationPerm.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestLocationPermission();
                    }
                });
            }

            container.addView(view);
            return view;
        }

        @Override
        public int getCount() {
            return mLayouts.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object obj) {
            return view == obj;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View view = (View) object;
            container.removeView(view);
        }
    }

    /**
     * Preconditions:
     * Storage permission has been granted.
     */
    private void startApp() {
        Boolean doInitialKeyGeneration = !Constants.getConfigFile(this).exists();
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_KEY_GENERATION_IN_PROGRESS, doInitialKeyGeneration);

        /**
         * In case start_into_web_gui option is enabled, start both activities
         * so that back navigation works as expected.
         */
        if (mPreferences.getBoolean(Constants.PREF_START_INTO_WEB_GUI, false)) {
            startActivities(new Intent[] {mainIntent, new Intent(this, WebGuiActivity.class)});
        } else {
            startActivity(mainIntent);
        }
        finish();
    }

    /**
     * Permission check and request functions
     */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                PermissionUtil.getLocationPermissions(),
                Constants.PermissionRequestType.LOCATION.ordinal());
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestAllFilesAccessPermission();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    Constants.PermissionRequestType.STORAGE.ordinal());
        }
    }

    @TargetApi(30)
    private void requestAllFilesAccessPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            ComponentName componentName = intent.resolveActivity(getPackageManager());
            if (componentName != null) {
                // Launch "Allow all files access?" dialog.
                startActivity(intent);
                return;
            }
            Log.w(TAG, "Request all files access not supported");
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Request all files access not supported", e);
        }
        Toast.makeText(this, R.string.dialog_all_files_access_not_supported, Toast.LENGTH_LONG).show();
    }

    private boolean haveIgnoreDozePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Older android version don't have the doze feature so we'll assume having the anti-doze permission.
            return true;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    @SuppressLint("InlinedApi")
    @TargetApi(23)
    private void requestIgnoreDozePermission() {
        Boolean intentFailed = false;
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        try {
            ComponentName componentName = intent.resolveActivity(getPackageManager());
            if (componentName != null) {
                String className = componentName.getClassName();
                if (className != null && !className.equalsIgnoreCase("com.android.tv.settings.EmptyStubActivity")) {
                    // Launch "Exempt from doze mode?" dialog.
                    startActivity(intent);
                    return;
                }
                intentFailed = true;
            } else {
                Log.w(TAG, "Request ignore battery optimizations not supported");
                intentFailed = true;
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Request ignore battery optimizations not supported", e);
            intentFailed = true;
        }
        if (intentFailed) {
            // Some devices don't support this request.
            Toast.makeText(this, R.string.dialog_disable_battery_optimizations_not_supported, Toast.LENGTH_LONG).show();
        }
    }

    private boolean haveLocationPermission() {
        Boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Boolean backgroundLocationGranted = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return coarseLocationGranted && backgroundLocationGranted;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_FINE_LOCATION
            );
            return;
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    },
                    REQUEST_BACKGROUND_LOCATION
            );
            return;
        }
>>>>>>> 545a9ffb (Support SDcard read/write using all files access on Android 11+ (fixes #568) (#618))
        ActivityCompat.requestPermissions(this,
                Constants.getLocationPermissions(),
                Constants.PermissionRequestType.LOCATION.ordinal());
    }

    private boolean haveStoragePermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                Constants.PermissionRequestType.STORAGE.ordinal());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (Constants.PermissionRequestType.values()[requestCode]) {
            case LOCATION:
                boolean granted = grantResults.length != 0;
                if (!granted) {
                    Log.i(TAG, "No location permission in request-result");
                    break;
                }
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "User granted permission: " + permissions[i]);
                    } else {
                        granted = false;
                        Log.i(TAG, "User denied permission: " + permissions[i]);
                    }
                }
                if (granted) {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                }
                break;
<<<<<<< HEAD
            case STORAGE:
=======
            case REQUEST_FINE_LOCATION:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied ACCESS_FINE_LOCATION permission.");
                    return;
                }
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                Log.i(TAG, "User granted ACCESS_FINE_LOCATION permission.");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            },
                            REQUEST_BACKGROUND_LOCATION
                    );
                    return;
                }
                break;
            case REQUEST_WRITE_STORAGE:
>>>>>>> 545a9ffb (Support SDcard read/write using all files access on Android 11+ (fixes #568) (#618))
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied WRITE_EXTERNAL_STORAGE permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted WRITE_EXTERNAL_STORAGE permission.");
<<<<<<< HEAD
=======
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requestAllFilesAccessPermission();
                    }
                    mNextButton.requestFocus();
>>>>>>> 545a9ffb (Support SDcard read/write using all files access on Android 11+ (fixes #568) (#618))
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}
