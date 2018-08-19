package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.Manifest;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
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
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.lang.ref.WeakReference;
import javax.inject.Inject;

public class FirstStartActivity extends Activity {

    private static String TAG = "FirstStartActivity";
    private static final int REQUEST_COARSE_LOCATION = 141;
    private static final int REQUEST_WRITE_STORAGE = 142;

    private static class Slide {
        public int layout;
        public int dotColorActive;
        public int dotColorInActive;

        Slide (int layout, int dotColorActive, int dotColorInActive) {
            this.layout = layout;
            this.dotColorActive = dotColorActive;
            this.dotColorInActive = dotColorInActive;
        }
    }

    private Slide[] mSlides;
    /**
     * Initialize the slide's ViewPager position to "-1" so it will never
     * trigger any action in {@link #onBtnNextClick} if the slide is not
     * shown.
     */
    private int mSlidePosStoragePermission = -1;
    private int mSlidePosKeyGeneration = -1;

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout mDotsLayout;
    private TextView[] mDots;
    private Button mBackButton;
    private Button mNextButton;

    @Inject SharedPreferences mPreferences;

    /**
     * Handles activity behaviour depending on prerequisites.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        /**
         * Check if prerequisites to run the app are still in place.
         * If anything mandatory is missing, the according welcome slide(s) will be shown.
         */
        Boolean showSlideStoragePermission = !haveStoragePermission();
        Boolean showSlideLocationPermission = !haveLocationPermission();
        Boolean showSlideKeyGeneration = !Constants.getConfigFile(this).exists();

        /**
         * If we don't have to show slides for mandatory prerequisites,
         * start directly into MainActivity.
         */
        if (!showSlideStoragePermission && !showSlideKeyGeneration) {
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

        // Add welcome slides to be shown.
        int[] colorsActive = getResources().getIntArray(R.array.array_dot_active);
        int[] colorsInactive = getResources().getIntArray(R.array.array_dot_inactive);
        int slideIndex = 0;
        mSlides = new Slide[
                1 +
                (showSlideStoragePermission ? 1 : 0) +
                (showSlideLocationPermission ? 1 : 0) +
                (showSlideKeyGeneration ? 1 : 0)
        ];
        mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_intro, colorsActive[0], colorsInactive[0]);
        if (showSlideStoragePermission) {
            mSlidePosStoragePermission = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_storage_permission, colorsActive[1], colorsInactive[1]);
        }
        if (showSlideLocationPermission) {
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_location_permission, colorsActive[2], colorsInactive[2]);
        }
        if (showSlideKeyGeneration) {
            mSlidePosKeyGeneration = slideIndex;
            mSlides[slideIndex++] = new Slide(R.layout.activity_firststart_key_generation, colorsActive[3], colorsInactive[3]);
        }

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
        if (mViewPager.getCurrentItem() == mSlidePosStoragePermission) {
            // As the storage permission is a prerequisite to run syncthing, refuse to continue without it.
            if (!haveStoragePermission()) {
                Toast.makeText(this, R.string.toast_write_storage_permission_required,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        int current = getItem(+1);
        if (current < mSlides.length) {
            // Move to next slide.
            mViewPager.setCurrentItem(current);
            mBackButton.setVisibility(View.VISIBLE);
            if (current == mSlidePosKeyGeneration) {
                onKeyGenerationSlideShown();
            }
        } else {
            // Start the app after "mNextButton" was hit on the last slide.
            Log.v(TAG, "User completed first start UI.");
            mPreferences.edit().putBoolean(Constants.PREF_FIRST_START, false).apply();
            startApp();
        }
    }

    private void addBottomDots(int currentPage) {
        mDots = new TextView[mSlides.length];

        mDotsLayout.removeAllViews();
        for (int i = 0; i < mDots.length; i++) {
            mDots[i] = new TextView(this);
            mDots[i].setText(Html.fromHtml("&#8226;"));
            mDots[i].setTextSize(35);
            mDots[i].setTextColor(mSlides[currentPage].dotColorInActive);
            mDotsLayout.addView(mDots[i]);
        }

        if (mDots.length > 0)
            mDots[currentPage].setTextColor(mSlides[currentPage].dotColorActive);
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
            mNextButton.setText(getString((position == mSlides.length - 1) ? R.string.finish : R.string.cont));
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

            View view = layoutInflater.inflate(mSlides[position].layout, container, false);

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
            return mSlides.length;
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
        Intent mainIntent = new Intent(this, MainActivity.class);

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
    private boolean haveLocationPermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_COARSE_LOCATION);
    }

    private boolean haveStoragePermission() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_WRITE_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_COARSE_LOCATION:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "User denied ACCESS_COARSE_LOCATION permission.");
                } else {
                    Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "User granted ACCESS_COARSE_LOCATION permission.");
                }
                break;
            case REQUEST_WRITE_STORAGE:
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

    /**
     * Perform secure key generation in an AsyncTask.
     */
    private void onKeyGenerationSlideShown() {
        mNextButton.setVisibility(View.GONE);
        KeyGenerationTask keyGenerationTask = new KeyGenerationTask(this);
        keyGenerationTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Sets up the initial configuration and generates secure keys.
     */
    private static class KeyGenerationTask extends AsyncTask<Void, Void, Void> {
        private WeakReference<FirstStartActivity> refFirstStartActivity;
        ConfigXml configXml;

        KeyGenerationTask(FirstStartActivity context) {
            refFirstStartActivity = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            FirstStartActivity firstStartActivity = refFirstStartActivity.get();
            if (firstStartActivity == null) {
                cancel(true);
                return null;
            }
            try {
                configXml = new ConfigXml(firstStartActivity);
            } catch (ConfigXml.OpenConfigException e) {
                TextView keygenStatus = firstStartActivity.findViewById(R.id.key_generation_status);
                keygenStatus.setText(firstStartActivity.getString(R.string.config_create_failed));
                cancel(true);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // Get a reference to the activity if it is still there.
            FirstStartActivity firstStartActivity = refFirstStartActivity.get();
            if (firstStartActivity == null) {
                return;
            }
            TextView keygenStatus = (TextView) firstStartActivity.findViewById(R.id.key_generation_status);
            keygenStatus.setText(firstStartActivity.getString(R.string.key_generation_success));
            Button nextButton = (Button) firstStartActivity.findViewById(R.id.btn_next);
            nextButton.setVisibility(View.VISIBLE);
        }
    }

}
