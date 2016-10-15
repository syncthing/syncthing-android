package com.nutomic.syncthingandroid.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.views.WifiSsidPreference;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.List;
import java.util.TreeSet;

import eu.chainfire.libsuperuser.Shell;

public class SettingsFragment extends PreferenceFragment
        implements SyncthingActivity.OnServiceConnectedListener,
        SyncthingService.OnApiChangeListener, Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    private static final String TAG = "SettingsFragment";

    private static final String SYNCTHING_OPTIONS_KEY = "syncthing_options";
    private static final String SYNCTHING_GUI_KEY     = "syncthing_gui";
    private static final String DEVICE_NAME_KEY       = "deviceName";
    private static final String USAGE_REPORT_ACCEPTED = "urAccepted";
    private static final String ADDRESS               = "address";
    private static final String USER                  = "user";
    // Note that this preference is seperate from the syncthing config value. While Syncthing
    // stores a password hash, we store the plaintext password in the Android preferences.
    private static final String PASSWORD              = "web_gui_password";
    private static final String EXPORT_CONFIG         = "export_config";
    private static final String IMPORT_CONFIG         = "import_config";
    private static final String STTRACE               = "sttrace";
    private static final String SYNCTHING_RESET       = "streset";
    private static final String SYNCTHING_VERSION_KEY = "syncthing_version";
    private static final String APP_VERSION_KEY       = "app_version";

    private CheckBoxPreference mAlwaysRunInBackground;
    private CheckBoxPreference mSyncOnlyCharging;
    private CheckBoxPreference mSyncOnlyWifi;
    private WifiSsidPreference mSyncOnlyOnSSIDs;
    private CheckBoxPreference mUseRoot;
    private CheckBoxPreference mKeepWakelock;
    private PreferenceScreen mOptionsScreen;
    private PreferenceScreen mGuiScreen;
    private SyncthingService mSyncthingService;

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        boolean enabled = currentState == SyncthingService.State.ACTIVE;
        mOptionsScreen.setEnabled(enabled);
        mGuiScreen.setEnabled(enabled);
        mUseRoot.setEnabled(enabled);
        mKeepWakelock.setEnabled(enabled);

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
                        value = api.getLocalDevice().name;
                        break;
                    case USAGE_REPORT_ACCEPTED:
                        int setting = api.getUsageReportAccepted();
                        value = Boolean.toString(setting == RestApi.USAGE_REPORTING_ACCEPTED);
                        break;
                    default:
                        value = api.getValue(RestApi.TYPE_OPTIONS, pref.getKey());
                }
                applyPreference(pref, value);
            }

            Preference address = mGuiScreen.findPreference(ADDRESS);
            address.setOnPreferenceChangeListener(this);
            applyPreference(address, api.getValue(RestApi.TYPE_GUI, ADDRESS));

            Preference user = mGuiScreen.findPreference(USER);
            user.setOnPreferenceChangeListener(this);
            applyPreference(user, api.getValue(RestApi.TYPE_GUI, USER));

            Preference password = mGuiScreen.findPreference(PASSWORD);
            password.setOnPreferenceChangeListener(this);
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
        final PreferenceScreen screen = getPreferenceScreen();
        mAlwaysRunInBackground = (CheckBoxPreference)
                findPreference(SyncthingService.PREF_ALWAYS_RUN_IN_BACKGROUND);
        mSyncOnlyCharging = (CheckBoxPreference)
                findPreference(SyncthingService.PREF_SYNC_ONLY_CHARGING);
        mSyncOnlyWifi = (CheckBoxPreference) findPreference(SyncthingService.PREF_SYNC_ONLY_WIFI);
        mSyncOnlyOnSSIDs = (WifiSsidPreference) findPreference(SyncthingService.PREF_SYNC_ONLY_WIFI_SSIDS);
        mSyncOnlyOnSSIDs.setDefaultValue(new TreeSet<String>()); // default to empty list
        mUseRoot = (CheckBoxPreference) findPreference(SyncthingService.PREF_USE_ROOT);
        mKeepWakelock = (CheckBoxPreference) findPreference(SyncthingService.PREF_USE_WAKE_LOCK);
        Preference appVersion = screen.findPreference(APP_VERSION_KEY);
        mOptionsScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_OPTIONS_KEY);
        mGuiScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_GUI_KEY);
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
        mSyncOnlyOnSSIDs.setOnPreferenceChangeListener(this);
        mUseRoot.setOnPreferenceClickListener(this);
        mKeepWakelock.setOnPreferenceClickListener(this);
        screen.findPreference(EXPORT_CONFIG).setOnPreferenceClickListener(this);
        screen.findPreference(IMPORT_CONFIG).setOnPreferenceClickListener(this);
        screen.findPreference(SYNCTHING_RESET).setOnPreferenceClickListener(this);
        sttrace.setOnPreferenceChangeListener(this);
    }

    /**
     * Enables or disables {@link #mUseRoot} preference depending whether root is available.
     */
    private class TestRootTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            return Shell.SU.available();
        }

        @Override
        protected void onPostExecute(Boolean haveRoot) {
            if (haveRoot) {
                mSyncthingService.getApi().requireRestart(getActivity());
                mUseRoot.setChecked(true);
            } else {
                Toast.makeText(getActivity(), R.string.toast_root_denied, Toast.LENGTH_SHORT)
                        .show();
            }
        }
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
        boolean requireRestart = false;

        if (preference.equals(mAlwaysRunInBackground)) {
            boolean value = (Boolean) o;
            preference.setSummary((value)
                    ? R.string.always_run_in_background_enabled
                    : R.string.always_run_in_background_disabled);
            mSyncOnlyCharging.setEnabled(value);
            mSyncOnlyWifi.setEnabled(value);
            mSyncOnlyOnSSIDs.setEnabled(mSyncOnlyWifi.isChecked());
            // Uncheck items when disabled, so it is clear they have no effect.
            if (!value) {
                mSyncOnlyCharging.setChecked(false);
                mSyncOnlyWifi.setChecked(false);
            }
        } else if (preference.equals(mSyncOnlyWifi)) {
            mSyncOnlyOnSSIDs.setEnabled((Boolean) o);
        } else if (preference.getKey().equals(DEVICE_NAME_KEY)) {
            Device old = mSyncthingService.getApi().getLocalDevice();
            Device updated = new Device();
            updated.addresses = old.addresses;
            updated.compression = old.compression;
            updated.deviceID = old.deviceID;
            updated.introducer = old.introducer;
            updated.name = (String) o;
            mSyncthingService.getApi().editDevice(updated, getActivity(), null);
        } else if (preference.getKey().equals(USAGE_REPORT_ACCEPTED)) {
            int setting = ((Boolean) o)
                    ? RestApi.USAGE_REPORTING_ACCEPTED
                    : RestApi.USAGE_REPORTING_DENIED;
            mSyncthingService.getApi().setUsageReportAccepted(setting, getActivity());
        } else if (mOptionsScreen.findPreference(preference.getKey()) != null) {
            boolean isArray = preference.getKey().equals("listenAddress") ||
                    preference.getKey().equals("globalAnnounceServers");
            mSyncthingService.getApi().setValue(RestApi.TYPE_OPTIONS, preference.getKey(), o,
                    isArray, getActivity());
        } else if (preference.getKey().equals(ADDRESS)) {
            mSyncthingService.getApi().setValue(
                    RestApi.TYPE_GUI, preference.getKey(), o, false, getActivity());
        } else if (preference.getKey().equals(USER)) {
            mSyncthingService.getApi().setValue(
                    RestApi.TYPE_GUI, preference.getKey(), o, false, getActivity());
        } else if (preference.getKey().equals(PASSWORD)) {
            mSyncthingService.getApi().setValue(
                    RestApi.TYPE_GUI, "password", o, false, getActivity());
        }

        // Avoid any code injection.
        if (preference.getKey().equals(STTRACE)) {
            if (((String) o).matches("[0-9a-z, ]*"))
                requireRestart = true;
            else {
                Toast.makeText(getActivity(), R.string.toast_invalid_sttrace, Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        if (requireRestart)
            mSyncthingService.getApi().requireRestart(getActivity());

        return true;
    }

    /**
     * Changes the owner of syncthing files so they can be accessed without root.
     */
    private class ChownFilesRunnable implements Runnable {
        @Override
        public void run() {
            String f = getActivity().getFilesDir().getAbsolutePath();
            List<String> out = Shell.SU.run("chown -R --reference=" + f + " " + f);
            Log.i(TAG, "Changed owner of syncthing files, output: " + out);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        switch (preference.getKey()) {
            case SyncthingService.PREF_USE_ROOT:
                if (mUseRoot.isChecked()) {
                    // Only check preference after root was granted.
                    mUseRoot.setChecked(false);
                    new TestRootTask().execute();
                } else {
                    new Thread(new ChownFilesRunnable()).start();
                    mSyncthingService.getApi().requireRestart(getActivity());
                }
                return true;
            case SyncthingService.PREF_USE_WAKE_LOCK:
                mSyncthingService.getApi().requireRestart(getActivity());
                return true;
            case EXPORT_CONFIG:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.dialog_confirm_export)
                        .setPositiveButton(android.R.string.yes,
                                (dialog, which) -> {
                                    mSyncthingService.exportConfig();
                                    Toast.makeText(getActivity(),
                                            getString(R.string.config_export_successful,
                                            SyncthingService.EXPORT_PATH), Toast.LENGTH_LONG).show();
                                })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case IMPORT_CONFIG:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.dialog_confirm_import)
                        .setPositiveButton(android.R.string.yes,
                                (dialog, which) -> {
                                    if (mSyncthingService.importConfig()) {
                                        Toast.makeText(getActivity(),
                                                getString(R.string.config_imported_successful),
                                                Toast.LENGTH_SHORT).show();
                                        // No need to restart, as we shutdown to import the config, and
                                        // then have to start Syncthing again.
                                    } else {
                                        Toast.makeText(getActivity(),
                                                getString(R.string.config_import_failed,
                                                SyncthingService.EXPORT_PATH), Toast.LENGTH_LONG).show();
                                    }
                                })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case SYNCTHING_RESET:
                final Intent intent = new Intent(getActivity(), SyncthingService.class)
                        .setAction(SyncthingService.ACTION_RESET);

                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.streset_title)
                        .setMessage(R.string.streset_question)
                        .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                            getActivity().startService(intent);
                            Toast.makeText(getActivity(), R.string.streset_done, Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
                        })
                        .show();
                return true;
            default:
                return false;
        }
    }

}
