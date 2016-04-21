package com.nutomic.syncthingandroid.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.nutomic.syncthingandroid.R;

/**
 * An activity that onPostCreate will look for a Toolbar in the layout
 * and bind it as the activity's actionbar with reasonable defaults. <br/>
 * The Toolbar must exist in the content view and have an id of R.id.toolbar.<br/>
 * Trying to call getSupportActionBar before this Activity's onPostCreate will cause a crash.
 */
public abstract class ToolbarBindingActivity extends AppCompatActivity {

    private static final String TAG = "ToolbarBindingActivity";

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar == null)
            return;

        try {
            setSupportActionBar(toolbar);
        } catch (NoClassDefFoundError e) {
            // Workaround for crash on Samsung 4.2 devices.
            // This should be fixed in support library 24.0.0
            // https://code.google.com/p/android/issues/detail?id=78377#c364
            // https://github.com/syncthing/syncthing-android/issues/591
            Log.w(TAG, e);
        }
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
