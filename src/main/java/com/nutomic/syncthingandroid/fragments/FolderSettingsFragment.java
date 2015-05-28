package com.nutomic.syncthingandroid.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.preference.PreferenceFragment;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.ExtendedCheckBoxPreference;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Shows folder details and allows changing them.
 */
public class FolderSettingsFragment extends PreferenceFragment
        implements SyncthingActivity.OnServiceConnectedListener,
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        SyncthingService.OnApiChangeListener {

    private static final int DIRECTORY_REQUEST_CODE = 234;

    /**
     * The ID of the folder to be edited. To be used with {@link com.nutomic.syncthingandroid.activities.SettingsActivity#EXTRA_IS_CREATE}
     * set to false.
     */
    public static final String EXTRA_REPO_ID = "folder_id";

    private static final String TAG = "FolderSettingsFragment";

    private static final String KEY_NODE_SHARED = "device_shared";

    private SyncthingService mSyncthingService;

    private RestApi.Folder mFolder;

    private EditTextPreference mFolderId;

    private Preference mDirectory;

    private CheckBoxPreference mFolderMaster;

    private PreferenceScreen mDevices;

    private CheckBoxPreference mVersioning;

    private EditTextPreference mVersioningKeep;

    private boolean mIsCreate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsActivity activity = (SettingsActivity) getActivity();
        activity.registerOnServiceConnectedListener(this);
        mIsCreate = activity.getIsCreate();
        setHasOptionsMenu(true);

        if (mIsCreate) {
            addPreferencesFromResource(R.xml.folder_settings_create);
        } else {
            addPreferencesFromResource(R.xml.folder_settings_edit);
        }

        mFolderId = (EditTextPreference) findPreference("folder_id");
        mFolderId.setOnPreferenceChangeListener(this);
        mDirectory = findPreference("directory");
        mDirectory.setOnPreferenceClickListener(this);
        mFolderMaster = (CheckBoxPreference) findPreference("folder_master");
        mFolderMaster.setOnPreferenceChangeListener(this);
        mDevices = (PreferenceScreen) findPreference("devices");
        mDevices.setOnPreferenceClickListener(this);
        mVersioning = (CheckBoxPreference) findPreference("versioning");
        mVersioning.setOnPreferenceChangeListener(this);
        mVersioningKeep = (EditTextPreference) findPreference("versioning_keep");
        mVersioningKeep.setOnPreferenceChangeListener(this);

        if (mIsCreate) {
            if (savedInstanceState != null) {
                mFolder = (RestApi.Folder) savedInstanceState.getSerializable("folder");
            }
            if (mFolder == null) {
                mFolder = new RestApi.Folder();
                mFolder.id = "";
                mFolder.path = "";
                mFolder.rescanIntervalS = 259200; // Scan every 3 days (in case inotify dropped some changes)
                mFolder.deviceIds = new ArrayList<>();
                mFolder.versioning = new RestApi.Versioning();
            }
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("folder", mFolder);
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE) {
            getActivity().finish();
            return;
        }

        if (mIsCreate) {
            getActivity().setTitle(R.string.create_folder);
        } else {
            RestApi.Folder folder = null;
            getActivity().setTitle(R.string.edit_folder);
            List<RestApi.Folder> folders = mSyncthingService.getApi().getFolders();
            for (int i = 0; i < folders.size(); i++) {
                if (folders.get(i).id.equals(
                        getActivity().getIntent().getStringExtra(EXTRA_REPO_ID))) {
                    folder = folders.get(i);
                    break;
                }
            }
            if (folder == null) {
                Log.w(TAG, "Folder not found in API update");
                getActivity().finish();
                return;
            }
            mFolder = folder;
        }

        mFolderId.setText(mFolder.id);
        mFolderId.setSummary(mFolder.id);
        mDirectory.setSummary(mFolder.path);
        mFolderMaster.setChecked(mFolder.readOnly);
        List<RestApi.Device> devicesList = mSyncthingService.getApi().getDevices(false);
        for (RestApi.Device n : devicesList) {
            ExtendedCheckBoxPreference cbp = new ExtendedCheckBoxPreference(getActivity(), n);
            // Calling addPreference later causes it to change the checked state.
            mDevices.addPreference(cbp);
            cbp.setTitle(n.name);
            cbp.setKey(KEY_NODE_SHARED);
            cbp.setOnPreferenceChangeListener(FolderSettingsFragment.this);
            cbp.setChecked(false);
            for (String n2 : mFolder.deviceIds) {
                if (n2.equals(n.deviceID)) {
                    cbp.setChecked(true);
                }
            }
        }
        mVersioning.setChecked(mFolder.versioning instanceof RestApi.SimpleVersioning);
        if (mVersioning.isChecked()) {
            mVersioningKeep.setText(mFolder.versioning.getParams().get("keep"));
            mVersioningKeep.setSummary(mFolder.versioning.getParams().get("keep"));
            mVersioningKeep.setEnabled(true);
        } else {
            mVersioningKeep.setEnabled(false);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.folder_settings, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreate);
        menu.findItem(R.id.delete).setVisible(!mIsCreate);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (mFolder.id.length() > 64 || !mFolder.id.matches("[a-zA-Z0-9-_\\.]+")) {
                    Toast.makeText(getActivity(), R.string.folder_id_invalid, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                if (mFolder.path.equals("")) {
                    Toast.makeText(getActivity(), R.string.folder_path_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                mSyncthingService.getApi().editFolder(mFolder, true, getActivity());
                return true;
            case R.id.delete:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.delete_folder_confirm)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                mSyncthingService.getApi().deleteFolder(mFolder, getActivity());
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case android.R.id.home:
                getActivity().finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        if (preference instanceof EditTextPreference) {
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

        if (preference.equals(mFolderId)) {
            mFolder.id = (String) o;
            folderUpdated();
            return true;
        } else if (preference.equals(mDirectory)) {
            mFolder.path = (String) o;
            folderUpdated();
            return true;
        } else if (preference.equals(mFolderMaster)) {
            mFolder.readOnly = (Boolean) o;
            folderUpdated();
            return true;
        } else if (preference.getKey().equals(KEY_NODE_SHARED)) {
            ExtendedCheckBoxPreference pref = (ExtendedCheckBoxPreference) preference;
            RestApi.Device device = (RestApi.Device) pref.getObject();
            if ((Boolean) o) {
                mFolder.deviceIds.add(device.deviceID);
            } else {
                Iterator<String> it = mFolder.deviceIds.iterator();
                while (it.hasNext()) {
                    String n = it.next();
                    if (n.equals(device.deviceID)) {
                        it.remove();
                    }
                }
            }
            folderUpdated();
            return true;
        } else if (preference.equals(mVersioning)) {
            mVersioningKeep.setEnabled((Boolean) o);
            if ((Boolean) o) {
                RestApi.SimpleVersioning v = new RestApi.SimpleVersioning();
                mFolder.versioning = v;
                v.setParams(5);
                mVersioningKeep.setText("5");
                mVersioningKeep.setSummary("5");
            } else {
                mFolder.versioning = new RestApi.Versioning();
            }
            folderUpdated();
            return true;
        } else if (preference.equals(mVersioningKeep)) {
            try {
                ((RestApi.SimpleVersioning) mFolder.versioning)
                        .setParams(Integer.parseInt((String) o));
                folderUpdated();
                return true;
            } catch (NumberFormatException e) {
                Log.w(TAG, "invalid versioning option: "+ o);
            }
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.equals(mDirectory)) {
            Intent intent = new Intent(getActivity(), FolderPickerActivity.class);
            if (mFolder.path.length() > 0) {
                intent.putExtra(FolderPickerActivity.EXTRA_INITIAL_DIRECTORY, mFolder.path);
            }
            startActivityForResult(intent, DIRECTORY_REQUEST_CODE);
        } else if (preference.equals(mDevices) &&
                mSyncthingService.getApi().getDevices(false).isEmpty()) {
            Toast.makeText(getActivity(), R.string.no_devices, Toast.LENGTH_SHORT)
                    .show();
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == DIRECTORY_REQUEST_CODE) {
            mFolder.path = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            mDirectory.setSummary(mFolder.path);
            folderUpdated();
        }
    }

    private void folderUpdated() {
        if (!mIsCreate) {
            mSyncthingService.getApi().editFolder(mFolder, false, getActivity());
        }
    }

}
