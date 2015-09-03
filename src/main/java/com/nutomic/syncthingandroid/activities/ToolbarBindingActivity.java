package com.nutomic.syncthingandroid.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.nutomic.syncthingandroid.R;

/**
 * An activity that onPostCreate will look for a Toolbar in the layout
 * and bind it as the activity's actionbar with reasonable defaults. <br/>
 * The Toolbar must exist in the content view and have an id of R.id.toolbar.<br/>
 * Trying to call getSupportActionBar before this Activity's onPostCreate will cause a crash.
 */
public class ToolbarBindingActivity extends AppCompatActivity {

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            //noinspection ConstantConditions
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
}
