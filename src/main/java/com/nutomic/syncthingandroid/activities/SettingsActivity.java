package com.nutomic.syncthingandroid.activities;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.DeviceFragment;
import com.nutomic.syncthingandroid.fragments.FolderFragment;
import com.nutomic.syncthingandroid.fragments.SettingsFragment;

/**
 * General Activity used by all PreferenceFragments.
 */
public class SettingsActivity extends SyncthingActivity {

    public static final String ACTION_APP_SETTINGS_FRAGMENT = "app_settings_fragment";

    public static final String ACTION_NODE_SETTINGS_FRAGMENT = "device_settings_fragment";

    public static final String ACTION_REPO_SETTINGS_FRAGMENT = "folder_settings_fragment";

    /**
     * Must be set for {@link #ACTION_NODE_SETTINGS_FRAGMENT} and
     * {@link #ACTION_REPO_SETTINGS_FRAGMENT} to determine if an existing folder/device should be
     * edited or a new one created.
     */
    public static final String EXTRA_IS_CREATE = "create";

    private Fragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        FragmentManager fm = getFragmentManager();
        if (savedInstanceState != null) {
            mFragment = fm.getFragment(savedInstanceState,
                    savedInstanceState.getString("fragment_name"));
        } else if (getIntent().getAction() != null) {
            switch (getIntent().getAction()) {
                case ACTION_APP_SETTINGS_FRAGMENT:
                    setTitle(R.string.settings_title);
                    mFragment = new SettingsFragment();
                    break;
                case ACTION_NODE_SETTINGS_FRAGMENT:
                    mFragment = new DeviceFragment();
                    if (!getIntent().hasExtra(EXTRA_IS_CREATE)) {
                        throw new IllegalArgumentException("EXTRA_IS_CREATE must be set");
                    }
                    break;
                case ACTION_REPO_SETTINGS_FRAGMENT:
                    mFragment = new FolderFragment();
                    if (!getIntent().hasExtra(EXTRA_IS_CREATE)) {
                        throw new IllegalArgumentException("EXTRA_IS_CREATE must be set");
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "You must provide the requested fragment type as an extra.");
            }
        } else {
            setTitle(R.string.settings_title);
            mFragment = new SettingsFragment();
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

    public boolean getIsCreate() {
        return getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
    }
}
