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

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.color.MaterialColors;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.PermissionUtil;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;

import org.apache.commons.io.FileUtils;

import javax.inject.Inject;

public class FirstStartActivity extends Activity {

    private enum Slide {

        INTRO(R.layout.activity_firststart_slide_intro),

        STORAGE(R.layout.activity_firststart_slide_storage),
        LOCATION(R.layout.activity_firststart_slide_location),
        API_LEVEL_30(R.layout.activity_firststart_slide_api_level_30),
        NOTIFICATION(R.layout.activity_firststart_slide_notification);

        public final int layout;

        Slide(int layout) {
            this.layout = layout;
        }
    }

    ;

    private static Slide[] slides = Slide.values();
    private static String TAG = "FirstStartActivity";

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout mDotsLayout;
    private TextView[] mDots;
    private Button mBackButton;
    private Button mNextButton;

    @Inject
    SharedPreferences mPreferences;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        /**
         * Recheck storage permission. If it has been revoked after the user
         * completed the welcome slides, displays the slides again.
         */
        if (!isFirstStart() && PermissionUtil.haveStoragePermission(this) && upgradedToApiLevel30()) {
            startApp();
            return;
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

        // Add bottom dots
        addBottomDots();
        setActiveBottomDot(0);

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

        if (!isFirstStart()) {
            // Skip intro slide
            onBtnNextClick();
        }
    }

    public void onBtnBackClick() {
        int current = mViewPager.getCurrentItem() - 1;
        if (current >= 0) {
            // Move to previous slider.
            mViewPager.setCurrentItem(current);
            if (current == 0) {
                mBackButton.setVisibility(View.GONE);
            }
        }
    }

    public void onBtnNextClick() {
        Slide slide = currentSlide();
        // Check if we are allowed to advance to the next slide.
        switch (slide) {
            case STORAGE:
                // As the storage permission is a prerequisite to run syncthing, refuse to continue without it.
                Boolean storagePermissionsGranted = PermissionUtil.haveStoragePermission(this);
                if (!storagePermissionsGranted) {
                    Toast.makeText(this, R.string.toast_write_storage_permission_required,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                break;
            case API_LEVEL_30:
                if (!upgradedToApiLevel30()) {
                    Toast.makeText(this, R.string.toast_api_level_30_must_reset,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                break;
        }

        int next = mViewPager.getCurrentItem() + 1;
        while (next < slides.length) {
            if (!shouldSkipSlide(slides[next])) {
                mViewPager.setCurrentItem(next);
                mBackButton.setVisibility(View.VISIBLE);
                break;
            }
            next++;
        }
        if (next == slides.length) {
            // Start the app after "mNextButton" was hit on the last slide.
            Log.v(TAG, "User completed first start UI.");
            mPreferences.edit().putBoolean(Constants.PREF_FIRST_START, false).apply();
            startApp();
        }
    }

    private boolean isFirstStart() {
        return mPreferences.getBoolean(Constants.PREF_FIRST_START, true);
    }

    @TargetApi(33)
    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

    }


    private boolean upgradedToApiLevel30() {
        if (mPreferences.getBoolean(Constants.PREF_UPGRADED_TO_API_LEVEL_30, false)) {
            return true;
        }
        if (isFirstStart()) {
            mPreferences.edit().putBoolean(Constants.PREF_UPGRADED_TO_API_LEVEL_30, true).apply();
            return true;
        }
        return false;
    }

    private void upgradeToApiLevel30() {
        File dbDir = new File(this.getFilesDir(), "index-v0.14.0.db");
        if (dbDir.exists()) {
            try {
                FileUtils.deleteQuietly(dbDir);
            } catch (Throwable e) {
                Log.w(TAG, "Deleting database with FileUtils failed", e);
                Util.runShellCommand("rm -r " + dbDir.getAbsolutePath(), false);
                if (dbDir.exists()) {
                    throw new RuntimeException("Failed to delete existing database");
                }
            }
        }
        mPreferences.edit().putBoolean(Constants.PREF_UPGRADED_TO_API_LEVEL_30, true).apply();
    }

    private Slide currentSlide() {
        return slides[mViewPager.getCurrentItem()];
    }

    private boolean shouldSkipSlide(Slide slide) {
        switch (slide) {
            case INTRO:
                return !isFirstStart();
            case NOTIFICATION:
                return isNotificationPermissionGranted();

            case STORAGE:
                return PermissionUtil.haveStoragePermission(this);
            case LOCATION:
                return hasLocationPermission();
            case API_LEVEL_30:
                // Skip if running as root, as that circumvents any Android FS restrictions.
                return upgradedToApiLevel30()
                        || PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Constants.PREF_USE_ROOT, false);
        }
        return false;
    }

    private void addBottomDots() {
        mDots = new TextView[slides.length];
        for (int i = 0; i < mDots.length; i++) {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226;"));
            mDots[i].setTextSize(35);
            mDotsLayout.addView(mDots[i]);
        }
    }

    private void setActiveBottomDot(int currentPage) {
        int colorInactive = MaterialColors.getColor(this, R.attr.colorPrimary, Color.BLUE);
        int colorActive = MaterialColors.getColor(this, R.attr.colorSecondary, Color.BLUE);
        for (TextView mDot : mDots) {
            mDot.setTextColor(colorInactive);
        }
        mDots[currentPage].setTextColor(colorActive);
    }

    //  ViewPager change listener
    ViewPager.OnPageChangeListener mViewPagerPageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            setActiveBottomDot(position);

            // Change the next button text from next to finish on last slide.
            mNextButton.setText(getString((position == slides.length - 1) ? R.string.finish : R.string.cont));
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {

        }
    };

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

            View view = layoutInflater.inflate(slides[position].layout, container, false);

            switch (slides[position]) {
                case NOTIFICATION:
                    Button notificationBtn = (Button) view.findViewById(R.id.btn_notification);
                    notificationBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestNotificationPermission();
                        }
                    });
                    break;

                case INTRO:
                    break;

                case STORAGE:
                    Button btnGrantStoragePerm = (Button) view.findViewById(R.id.btnGrantStoragePerm);
                    btnGrantStoragePerm.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestStoragePermission();
                        }
                    });
                    break;

                case LOCATION:
                    Button btnGrantLocationPerm = (Button) view.findViewById(R.id.btnGrantLocationPerm);
                    btnGrantLocationPerm.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestLocationPermission();
                        }
                    });
                    break;

                case API_LEVEL_30:
                    Button btnResetDatabase = (Button) view.findViewById(R.id.btnResetDatabase);
                    btnResetDatabase.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            upgradeToApiLevel30();
                            onBtnNextClick();
                        }
                    });
                    break;
            }

            container.addView(view);
            return view;
        }

        @Override
        public int getCount() {
            return slides.length;
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
            startActivities(new Intent[]{mainIntent, new Intent(this, WebGuiActivity.class)});
        } else {
            startActivity(mainIntent);
        }
        finish();
    }

    private boolean hasLocationPermission() {
        for (String perm : PermissionUtil.getLocationPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Permission check and request functions
     */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                PermissionUtil.getLocationPermissions(),
                Constants.PermissionRequestType.LOCATION.ordinal());
    }

    @TargetApi(33)
    private void requestNotificationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (Constants.PermissionRequestType.values()[requestCode]) {
            case LOCATION:
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied foreground location permission");
                    break;
                }
                Log.i(TAG, "User granted foreground location permission");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ActivityCompat.requestPermissions(this,
                            PermissionUtil.getLocationPermissions(),
                            Constants.PermissionRequestType.LOCATION_BACKGROUND.ordinal());
                }
                break;
            case LOCATION_BACKGROUND:
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied background location permission");
                    break;
                }
                Log.i(TAG, "User granted background location permission");
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                break;
            case STORAGE:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied WRITE_EXTERNAL_STORAGE permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted WRITE_EXTERNAL_STORAGE permission.");
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
