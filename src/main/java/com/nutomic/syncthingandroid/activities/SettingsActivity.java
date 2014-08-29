package com.nutomic.syncthingandroid.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.NodeSettingsFragment;
import com.nutomic.syncthingandroid.fragments.RepoSettingsFragment;
import com.nutomic.syncthingandroid.fragments.SettingsFragment;

/**
 * General Activity used by all PreferenceFragments.
 */
public class SettingsActivity extends SyncthingActivity {

    public static final String ACTION_APP_SETTINGS_FRAGMENT = "app_settings_fragment";

    public static final String ACTION_NODE_SETTINGS_FRAGMENT = "node_settings_fragment";

    public static final String ACTION_REPO_SETTINGS_FRAGMENT = "repo_settings_fragment";

    /**
     * Must be set for {@link #ACTION_NODE_SETTINGS_FRAGMENT} and
     * {@link #ACTION_REPO_SETTINGS_FRAGMENT} to determine if an existing repo/node should be
     * edited or a new one created.
     * <p/>
     * If this is false, {@link com.nutomic.syncthingandroid.fragments.RepoSettingsFragment#EXTRA_REPO_ID} or
     * {@link com.nutomic.syncthingandroid.fragments.NodeSettingsFragment#EXTRA_NODE_ID} must be set (according to the selected fragment).
     */
    public static final String EXTRA_IS_CREATE = "create";

    private Fragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState != null) {
            mFragment = fm.getFragment(savedInstanceState,
                    savedInstanceState.getString("fragment_name"));
        } else {
            switch (getIntent().getAction()) {
                case ACTION_APP_SETTINGS_FRAGMENT:
                    setTitle(R.string.settings_title);
                    mFragment = new SettingsFragment();
                    break;
                case ACTION_NODE_SETTINGS_FRAGMENT:
                    mFragment = new NodeSettingsFragment();
                    if (!getIntent().hasExtra(EXTRA_IS_CREATE)) {
                        throw new IllegalArgumentException("EXTRA_IS_CREATE must be set");
                    }
                    break;
                case ACTION_REPO_SETTINGS_FRAGMENT:
                    mFragment = new RepoSettingsFragment();
                    if (!getIntent().hasExtra(EXTRA_IS_CREATE)) {
                        throw new IllegalArgumentException("EXTRA_IS_CREATE must be set");
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "You must provide the requested fragment type as an extra.");
            }
        }

        fm.beginTransaction()
                .replace(android.R.id.content, mFragment)
                .commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String fragmentClassName = mFragment.getClass().getName();
        outState.putString("fragment_name", fragmentClassName);
        FragmentManager fm = getSupportFragmentManager();
        fm.putFragment(outState, fragmentClassName, mFragment);
    }

    public boolean getIsCreate() {
        return getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
    }

    /**
	 * Used for the QR code scanner in NodeSettingsFragment.
     *
     * Instead of the cast, an interface could be used (if there are multiple fragments using this).
     */
    public void onClick(View view) {
        if (mFragment instanceof NodeSettingsFragment) {
            ((NodeSettingsFragment) mFragment).onClick(view);
        }
    }

}
