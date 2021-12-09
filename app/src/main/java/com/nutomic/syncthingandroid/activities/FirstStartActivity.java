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
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.PermissionUtil;

import javax.inject.Inject;

public class FirstStartActivity extends Activity {

    private enum Slide {
        INTRO,
        STORAGE,
        LOCATION,
        API_LEVEL_30,
    };

    private static final Slide[] slideOrder = {
            Slide.INTRO,
            Slide.STORAGE,
            Slide.LOCATION,
            Slide.API_LEVEL_30,
    };
    private static int[] mLayouts = new int[]{
            R.layout.activity_firststart_slide_intro,
            R.layout.activity_firststart_slide_storage,
            R.layout.activity_firststart_slide_location,
            R.layout.activity_firststart_slide_api_level_30,
    };

    private static String TAG = "FirstStartActivity";

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout mDotsLayout;
    private TextView[] mDots;
    private Button mBackButton;
    private Button mNextButton;

    private boolean mResetDatabase = false;

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

        // Add bottom dots
        addBottomDots();
        setActiveBottomDot(0);

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
        while (next < slideOrder.length) {
            if (!shouldSkipSlide(slideOrder[next])) {
                mViewPager.setCurrentItem(next);
                mBackButton.setVisibility(View.VISIBLE);
                break;
            }
            next++;
        }
        if (next == mLayouts.length) {
            // Start the app after "mNextButton" was hit on the last slide.
            Log.v(TAG, "User completed first start UI.");
            mPreferences.edit().putBoolean(Constants.PREF_FIRST_START, false).apply();
            startApp();
        }
    }

    private boolean isFirstStart() {
        return mPreferences.getBoolean(Constants.PREF_FIRST_START, true);
    }

    private boolean upgradedToApiLevel30() {
        return mPreferences.getBoolean(Constants.PREF_UPGRADED_TO_API_LEVEL_30,false);
    }

    private Slide currentSlide() {
        return slideOrder[mViewPager.getCurrentItem()];
    }

    private boolean shouldSkipSlide(Slide slide) {
        switch (slide) {
            case INTRO:
                return !isFirstStart();
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
        mDots = new TextView[mLayouts.length];
        for (int i = 0; i < mDots.length; i++) {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226;"));
            mDots[i].setTextSize(35);
            mDotsLayout.addView(mDots[i]);
        }
    }

    private void setActiveBottomDot(int currentPage) {
        int[] colorsActive = getResources().getIntArray(R.array.array_dot_active);
        int[] colorsInactive = getResources().getIntArray(R.array.array_dot_inactive);
        for (int i = 0; i < mDots.length; i++) {
            mDots[i].setTextColor(colorsInactive[currentPage]);
        }
        mDots[currentPage].setTextColor(colorsActive[currentPage]);
    }

    //  ViewPager change listener
    ViewPager.OnPageChangeListener mViewPagerPageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            setActiveBottomDot(position);

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

            switch (slideOrder[position]) {
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
                            // The main-activity will actually do the db reset.
                            mResetDatabase = true;
                            mPreferences.edit().putBoolean(Constants.PREF_UPGRADED_TO_API_LEVEL_30, true).apply();
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
        mainIntent.putExtra(MainActivity.EXTRA_RESET_DATABASE, mResetDatabase);
        startActivity(mainIntent);
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
