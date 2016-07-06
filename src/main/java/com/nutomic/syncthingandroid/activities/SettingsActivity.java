package com.nutomic.syncthingandroid.activities;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.DeviceFragment;
import com.nutomic.syncthingandroid.fragments.FolderFragment;
import com.nutomic.syncthingandroid.fragments.SettingsFragment;

/**
 * General Activity used by all PreferenceFragments.
 */
public class SettingsActivity extends SyncthingActivity {

    public static final String ACTION_APP_SETTINGS    =
            "com.nutomic.syncthingandroid.activities.SettingsActivity.APP_SETTINGS";
    public static final String ACTION_DEVICE_SETTINGS =
            "com.nutomic.syncthingandroid.activities.SettingsActivity.DEVICE_SETTINGS";
    public static final String ACTION_FOLDER_SETTINGS =
            "com.nutomic.syncthingandroid.activities.SettingsActivity.FOLDER_SETTINGS";

    /**
     * Must be set for {@link #ACTION_DEVICE_SETTINGS} and
     * {@link #ACTION_FOLDER_SETTINGS} to determine if an existing folder/device should be
     * edited or a new one created.
     */
    public static final String EXTRA_IS_CREATE =
            "com.nutomic.syncthingandroid.activities.SettingsActivity.IS_CREATE";

    private Fragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        FragmentManager fm = getFragmentManager();
        if (savedInstanceState != null) {
            mFragment = fm.getFragment(savedInstanceState,
                    savedInstanceState.getString("fragment_name"));
        } else {
            String action = getIntent().getAction();

            if (action == null) {
                action = ACTION_APP_SETTINGS;
            }

            switch (action) {
                case ACTION_APP_SETTINGS:
                    setTitle(R.string.settings_title);
                    mFragment = new SettingsFragment();
                    break;
                case ACTION_DEVICE_SETTINGS:
                    assertHasCreateExtra();
                    mFragment = new DeviceFragment();
                    break;
                case ACTION_FOLDER_SETTINGS:
                    assertHasCreateExtra();
                    mFragment = new FolderFragment();
                    break;
                default:
                    throw new IllegalArgumentException(
                            "You must provide the requested fragment type as an extra.");
            }
        }

        fm.beginTransaction()
                .replace(R.id.content, mFragment)
                .commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String fragmentClassName = mFragment.getClass().getName();
        outState.putString("fragment_name", fragmentClassName);
        FragmentManager fm = getFragmentManager();
        fm.putFragment(outState, fragmentClassName, mFragment);
    }

    private void assertHasCreateExtra() {
        if (!getIntent().hasExtra(EXTRA_IS_CREATE))
            throw new IllegalArgumentException("EXTRA_IS_CREATE must be set");
    }

    public boolean getIsCreate() {
        return getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
    }
}
