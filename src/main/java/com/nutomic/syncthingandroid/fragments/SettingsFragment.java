package com.nutomic.syncthingandroid.fragments;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.support.v4.preference.PreferenceFragment;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

public class SettingsFragment extends PreferenceFragment
        implements SyncthingActivity.OnServiceConnectedListener,
        SyncthingService.OnApiChangeListener, Preference.OnPreferenceChangeListener {

    private static final String TAG = "SettingsFragment";

    private static final String SYNCTHING_OPTIONS_KEY = "syncthing_options";

    private static final String SYNCTHING_GUI_KEY = "syncthing_gui";

    private static final String SYNCTHING_VERSION_KEY = "syncthing_version";

    private static final String APP_VERSION_KEY = "app_version";

    private CheckBoxPreference mAlwaysRunInBackground;

    private CheckBoxPreference mSyncOnlyCharging;

    private CheckBoxPreference mSyncOnlyWifi;

    private PreferenceScreen mOptionsScreen;

    private PreferenceScreen mGuiScreen;

    private SyncthingService mSyncthingService;

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        mOptionsScreen.setEnabled(currentState == SyncthingService.State.ACTIVE);
        mGuiScreen.setEnabled(currentState == SyncthingService.State.ACTIVE);

        Preference syncthingVersion = getPreferenceScreen().findPreference(SYNCTHING_VERSION_KEY);
        syncthingVersion.setSummary(mSyncthingService.getApi().getVersion());

        if (currentState == SyncthingService.State.ACTIVE) {
            for (int i = 0; i < mOptionsScreen.getPreferenceCount(); i++) {
                Preference pref = mOptionsScreen.getPreference(i);
                pref.setOnPreferenceChangeListener(SettingsFragment.this);
                String value = mSyncthingService.getApi()
                        .getValue(RestApi.TYPE_OPTIONS, pref.getKey());
                applyPreference(pref, value);
            }

            for (int i = 0; i < mGuiScreen.getPreferenceCount(); i++) {
                Preference pref = mGuiScreen.getPreference(i);
                pref.setOnPreferenceChangeListener(SettingsFragment.this);
                String value = mSyncthingService.getApi()
                        .getValue(RestApi.TYPE_GUI, pref.getKey());
                applyPreference(pref, value);
            }
        }
    }

    /**
     * Applies the given value to the preference.
     *
     * If pref is an EditTextPreference, setText is used and the value shown as summary. If pref is
     * a CheckBoxPreference, setChecked is used (by parsing value as Boolean).
     */
    private void applyPreference(Preference pref, String value) {
        if (pref instanceof EditTextPreference) {
            ((EditTextPreference) pref).setText(value);
            pref.setSummary(value);
        } else if (pref instanceof CheckBoxPreference) {
            ((CheckBoxPreference) pref).setChecked(Boolean.parseBoolean(value));
        }
    }


    /**
     * Loads layout, sets version from Rest API.
     *
     * Manual target API as we manually check if ActionBar is available (for ActionBar back button).
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ((SyncthingActivity) getActivity()).registerOnServiceConnectedListener(this);

        addPreferencesFromResource(R.xml.app_settings);
        PreferenceScreen screen = getPreferenceScreen();
        mAlwaysRunInBackground = (CheckBoxPreference)
                findPreference(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND);
        mSyncOnlyCharging = (CheckBoxPreference)
                findPreference(SyncthingService.PREF_SYNC_ONLY_CHARGING);
        mSyncOnlyWifi = (CheckBoxPreference) findPreference(SyncthingService.PREF_SYNC_ONLY_WIFI);
        Preference appVersion = screen.findPreference(APP_VERSION_KEY);
        try {
            appVersion.setSummary(getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Failed to get app version name");
        }
        mOptionsScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_OPTIONS_KEY);
        mGuiScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_GUI_KEY);

        mAlwaysRunInBackground.setOnPreferenceChangeListener(this);
        mSyncOnlyCharging.setOnPreferenceChangeListener(this);
        mSyncOnlyWifi.setOnPreferenceChangeListener(this);
        // Force summary update and wifi/charging preferences enable/disable.
        onPreferenceChange(mAlwaysRunInBackground, mAlwaysRunInBackground.isChecked());
        Preference sttrace = findPreference("sttrace");
        sttrace.setOnPreferenceChangeListener(this);
        sttrace.setSummary(PreferenceManager
                .getDefaultSharedPreferences(getActivity()).getString("sttrace", ""));
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    /**
     * Handles ActionBar back selected.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(getActivity());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Sends the updated value to {@link }RestApi}, and sets it as the summary
     * for EditTextPreference.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        // Convert new value to integer if input type is number.
        if (preference instanceof EditTextPreference) {
            EditTextPreference etp = (EditTextPreference) preference;
            if (etp.getEditText().getInputType() == InputType.TYPE_CLASS_NUMBER) {
                o = Integer.parseInt((String) o);
            }
        }

        if (preference.equals(mSyncOnlyCharging) || preference.equals(mSyncOnlyWifi)) {
            mSyncthingService.updateState();
        } else if (preference.equals(mAlwaysRunInBackground)) {
            preference.setSummary(((Boolean) o)
                    ? R.string.always_run_in_background_enabled
                    : R.string.always_run_in_background_disabled);
            mSyncOnlyCharging.setEnabled((Boolean) o);
            mSyncOnlyWifi.setEnabled((Boolean) o);

        } else if (mOptionsScreen.findPreference(preference.getKey()) != null) {
            mSyncthingService.getApi().setValue(RestApi.TYPE_OPTIONS, preference.getKey(), o,
                    preference.getKey().equals("ListenAddress"), getActivity());
        } else if (mGuiScreen.findPreference(preference.getKey()) != null) {
            mSyncthingService.getApi().setValue(
                    RestApi.TYPE_GUI, preference.getKey(), o, false, getActivity());
        } else if (preference.getKey().equals("sttrace")) {
            // Avoid any code injection.
            if (!((String) o).matches("[a-z,]*")) {
                Log.w(TAG, "Only a-z and ',' are allowed in STTRACE options");
                return false;
            }
            ((SyncthingActivity) getActivity()).getApi().requireRestart(getActivity());
        }

        // Set the preference value as summary.
        if (preference instanceof EditTextPreference) {
            String value = (o instanceof String)
                    ? (String) o
                    : Integer.toString((Integer) o);
            preference.setSummary(value);
        }

        return true;
    }
}
