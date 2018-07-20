package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.Manifest;
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

import javax.inject.Inject;

public class FirstStartActivity extends Activity implements Button.OnClickListener {

    private static String TAG = "FirstStartActivity";
    private static final int REQUEST_WRITE_STORAGE = 142;

    private ViewPager mViewPager;
    private ViewPagerAdapter mViewPagerAdapter;
    private LinearLayout dotsLayout;
    private TextView[] dots;
    private int[] layouts;
    private Button btnBack;
    private Button btnNext;

    @Inject SharedPreferences mPreferences;

    /**
     * Handles activity behaviour depending on {@link #isFirstStart()} and {@link #haveStoragePermission()}.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getApplication()).component().inject(this);

        if (!isFirstStart() && false) {
            startApp();
            return;
        }

        // Make notification bar transparent (API level 21+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // Show first start welcome wizard UI.
        setContentView(R.layout.activity_first_start);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);
        dotsLayout = (LinearLayout) findViewById(R.id.layoutDots);
        btnBack = (Button) findViewById(R.id.btn_back);
        btnNext = (Button) findViewById(R.id.btn_next);

        mViewPager.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Consume the event to prevent swiping through the slides.
                v.performClick();
                return true;
            }
        });

        // layouts of all welcome sliders
        // add few more layouts if you want
        layouts = new int[]{
                R.layout.activity_firststart_slide1,
                R.layout.activity_firststart_slide2,
                R.layout.activity_firststart_slide3};

        // adding bottom dots
        addBottomDots(0);

        // making notification bar transparent
        changeStatusBarColor();

        mViewPagerAdapter = new ViewPagerAdapter();
        mViewPager.setAdapter(mViewPagerAdapter);
        mViewPager.addOnPageChangeListener(mViewPagerPageChangeListener);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = getItem(-1);
                if (current >= 0) {
                    // Move to previous slider.
                    mViewPager.setCurrentItem(current);
                    if (current == 0) {
                        btnBack.setVisibility(View.GONE);
                    }
                }
            }
        });

        btnNext.setOnClickListener(this);
    }

    private void addBottomDots(int currentPage) {
        dots = new TextView[layouts.length];

        int[] colorsActive = getResources().getIntArray(R.array.array_dot_active);
        int[] colorsInactive = getResources().getIntArray(R.array.array_dot_inactive);

        dotsLayout.removeAllViews();
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new TextView(this);
            dots[i].setText(Html.fromHtml("&#8226;"));
            dots[i].setTextSize(35);
            dots[i].setTextColor(colorsInactive[currentPage]);
            dotsLayout.addView(dots[i]);
        }

        if (dots.length > 0)
            dots[currentPage].setTextColor(colorsActive[currentPage]);
    }

    private int getItem(int i) {
        return mViewPager.getCurrentItem() + i;
    }

    //  viewpager change listener
    ViewPager.OnPageChangeListener mViewPagerPageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            addBottomDots(position);

            // Change the next button text from next to finish on last slide.
            btnNext.setText(getString((position == layouts.length - 1) ? R.string.finish : R.string.cont));
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

            View view = layoutInflater.inflate(layouts[position], container, false);
            container.addView(view);

            return view;
        }

        @Override
        public int getCount() {
            return layouts.length;
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



















    private boolean isFirstStart() {
        return mPreferences.getBoolean("first_start", true);
    }

    private void startApp() {
        if (!haveStoragePermission()) {
            requestStoragePermission();
            /**
             * startApp will be called in {@link #onRequestPermissionsResult()}
             * after permission was granted.
             */
            return;
        }

        boolean isFirstStart = isFirstStart();
        if (isFirstStart) {
            Log.v(TAG, "User completed first start UI.");
            mPreferences.edit().putBoolean("first_start", false).apply();
        }

        // In case start_into_web_gui option is enabled, start both activities so that back
        // navigation works as expected.
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_KEY_GENERATION_IN_PROGRESS, isFirstStart);
        Intent webIntent = new Intent(this, WebGuiActivity.class);
        if (mPreferences.getBoolean("start_into_web_gui", false)) {
            startActivities(new Intent[] {mainIntent, webIntent});
        } else {
            startActivity(mainIntent);
        }
        finish();
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
    public void onClick(View v) {
        // Start the app when "next" was hit on the last slide
        int current = getItem(+1);
        if (current < layouts.length) {
            // Move to next slide
            mViewPager.setCurrentItem(current);
            btnBack.setVisibility(View.VISIBLE);
        } else {
            startApp();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.toast_write_storage_permission_required,
                            Toast.LENGTH_LONG).show();
                    this.finish();
                } else {
                    startApp();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}
