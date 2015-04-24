package com.nutomic.syncthingandroid.fragments;

import android.content.SharedPreferences;
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
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

public class SettingsFragment extends PreferenceFragment
        implements SyncthingActivity.OnServiceConnectedListener,
        SyncthingService.OnApiChangeListener, Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    private static final String TAG = "SettingsFragment";

    private static final String SYNCTHING_OPTIONS_KEY = "syncthing_options";
    private static final String SYNCTHING_GUI_KEY     = "syncthing_gui";
    private static final String DEVICE_NAME_KEY       = "DeviceName";
    private static final String USAGE_REPORT_ACCEPTED = "URAccepted";
    private static final String ADDRESS               = "Address";
    private static final String GUI_USER              = "gui_user";
    private static final String GUI_PASSWORD          = "gui_password";
    private static final String USER_TLS              = "UseTLS";
    private static final String EXPORT_CONFIG         = "export_config";
    private static final String IMPORT_CONFIG         = "import_config";
    private static final String STTRACE               = "sttrace";

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

        if (currentState == SyncthingService.State.ACTIVE) {
            Preference syncthingVersion = getPreferenceScreen().findPreference(SYNCTHING_VERSION_KEY);
            syncthingVersion.setSummary(mSyncthingService.getApi().getVersion());
            RestApi api = mSyncthingService.getApi();
            for (int i = 0; i < mOptionsScreen.getPreferenceCount(); i++) {
                final Preference pref = mOptionsScreen.getPreference(i);
                pref.setOnPreferenceChangeListener(SettingsFragment.this);
                String value;
                switch (pref.getKey()) {
                    case DEVICE_NAME_KEY:
                        value = api.getLocalDevice().Name;
                        break;
                    case USAGE_REPORT_ACCEPTED:
                        String v = api.getValue(RestApi.TYPE_OPTIONS, pref.getKey());
                        value = (v.equals("1")) ? "true" : "false";
                        break;
                    default:
                        value = api.getValue(RestApi.TYPE_OPTIONS, pref.getKey());
                }
                applyPreference(pref, value);
            }

            Preference address = mGuiScreen.findPreference(ADDRESS);
            applyPreference(address, api.getValue(RestApi.TYPE_GUI, ADDRESS));

            Preference tls = mGuiScreen.findPreference(USER_TLS);
            applyPreference(tls, api.getValue(RestApi.TYPE_GUI, USER_TLS));
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
        mOptionsScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_OPTIONS_KEY);
        mGuiScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_GUI_KEY);
        Preference user = screen.findPreference(GUI_USER);
        Preference password = screen.findPreference(GUI_PASSWORD);
        Preference sttrace = findPreference(STTRACE);

        try {
            appVersion.setSummary(getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Failed to get app version name");
        }

        mAlwaysRunInBackground.setOnPreferenceChangeListener(this);
        mSyncOnlyCharging.setOnPreferenceChangeListener(this);
        mSyncOnlyWifi.setOnPreferenceChangeListener(this);
        screen.findPreference(EXPORT_CONFIG).setOnPreferenceClickListener(this);
        screen.findPreference(IMPORT_CONFIG).setOnPreferenceClickListener(this);
        user.setOnPreferenceChangeListener(this);
        password.setOnPreferenceChangeListener(this);
        // Force summary update and wifi/charging preferences enable/disable.
        onPreferenceChange(mAlwaysRunInBackground, mAlwaysRunInBackground.isChecked());
        sttrace.setOnPreferenceChangeListener(this);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        user.setSummary(sp.getString("gui_user", ""));
        sttrace.setSummary(sp.getString("sttrace", ""));
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSyncthingService.unregisterOnApiChangeListener(this);
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
        if (preference instanceof EditTextPreference && !preference.getKey().equals(GUI_PASSWORD)) {
            EditTextPreference pref = (EditTextPreference) preference;
            if ((pref.getEditText().getInputType() & InputType.TYPE_CLASS_NUMBER) > 0) {
                try {
                    o = Integer.parseInt((String) o);
                    o = o.toString();
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid number: " + o);
                    return false;
                }
            }
            pref.setSummary((String) o);
        }

        if (preference.equals(mSyncOnlyCharging) || preference.equals(mSyncOnlyWifi)) {
            mSyncthingService.updateState();
        } else if (preference.equals(mAlwaysRunInBackground)) {
            preference.setSummary(((Boolean) o)
                    ? R.string.always_run_in_background_enabled
                    : R.string.always_run_in_background_disabled);
            mSyncOnlyCharging.setEnabled((Boolean) o);
            mSyncOnlyWifi.setEnabled((Boolean) o);
        } else if (preference.getKey().equals(DEVICE_NAME_KEY)) {
            RestApi.Device old = mSyncthingService.getApi().getLocalDevice();
            RestApi.Device updated = new RestApi.Device();
            updated.Addresses = old.Addresses;
            updated.Compression = old.Compression;
            updated.DeviceID = old.DeviceID;
            updated.Introducer = old.Introducer;
            updated.Name = (String) o;
            mSyncthingService.getApi().editDevice(updated, getActivity(), null);
        } else if (preference.getKey().equals(USAGE_REPORT_ACCEPTED)) {
            mSyncthingService.getApi().setValue(RestApi.TYPE_OPTIONS, preference.getKey(),
                    ((Boolean) o) ? 1 : 0, false, getActivity());
        } else if (mOptionsScreen.findPreference(preference.getKey()) != null) {
            boolean isArray = preference.getKey().equals("ListenAddress") ||
                    preference.getKey().equals("GlobalAnnServers");
            mSyncthingService.getApi().setValue(RestApi.TYPE_OPTIONS, preference.getKey(), o,
                    isArray, getActivity());
        } else if (preference.getKey().equals(ADDRESS) || preference.getKey().equals(USER_TLS)) {
            mSyncthingService.getApi().setValue(
                    RestApi.TYPE_GUI, preference.getKey(), o, false, getActivity());
        }

        // Avoid any code injection.
        int error = 0;
        if (preference.getKey().equals(STTRACE)) {
            if (((String) o).matches("[a-z, ]*"))
                mSyncthingService.getApi().requireRestart(getActivity());
            else
                error = R.string.toast_invalid_sttrace;
        } else if (preference.getKey().equals(GUI_USER)) {
            String s = (String) o;
            if (!s.contains(":") && !s.contains("'"))
                mSyncthingService.getApi().requireRestart(getActivity());
            else
                error = R.string.toast_invalid_username;
        } else if (preference.getKey().equals(GUI_PASSWORD)) {
            String s = (String) o;
            if (!s.contains(":") && !s.contains("'"))
                mSyncthingService.getApi().requireRestart(getActivity());
            else
                error = R.string.toast_invalid_password;
        }
        if (error != 0) {
            Toast.makeText(getActivity(), error, Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        switch (preference.getKey()) {
            case EXPORT_CONFIG:
                mSyncthingService.exportConfig();
                Toast.makeText(getActivity(), getString(R.string.config_export_successful,
                        SyncthingService.EXPORT_PATH), Toast.LENGTH_LONG).show();
                return true;
            case IMPORT_CONFIG:
                if (mSyncthingService.importConfig()) {
                    Toast.makeText(getActivity(), getString(R.string.config_imported_successful),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), getString(R.string.config_import_failed,
                            SyncthingService.EXPORT_PATH), Toast.LENGTH_LONG).show();
                }
                return true;
            default:
                return false;
        }
    }

}
