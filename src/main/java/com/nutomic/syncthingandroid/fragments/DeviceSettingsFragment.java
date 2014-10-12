package com.nutomic.syncthingandroid.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.support.v4.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.List;
import java.util.Map;

/**
 * Shows device details and allows changing them.
 */
public class DeviceSettingsFragment extends PreferenceFragment implements
        SyncthingActivity.OnServiceConnectedListener, Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener, RestApi.OnReceiveConnectionsListener,
        SyncthingService.OnApiChangeListener, RestApi.OnDeviceIdNormalizedListener {

    public static final String EXTRA_NODE_ID = "device_id";

    private static final int SCAN_QR_REQUEST_CODE = 235;

    private SyncthingService mSyncthingService;

    private RestApi.Device mDevice;

    private Preference mDeviceId;

    private EditTextPreference mName;

    private EditTextPreference mAddresses;

    private CheckBoxPreference mCompression;

    private CheckBoxPreference mIntroducer;

    private Preference mVersion;

    private Preference mCurrentAddress;

    private boolean mIsCreate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((SyncthingActivity) getActivity()).registerOnServiceConnectedListener(this);

        mIsCreate = ((SettingsActivity) getActivity()).getIsCreate();
        setHasOptionsMenu(true);

        if (mIsCreate) {
            addPreferencesFromResource(R.xml.device_settings_create);
        } else {
            addPreferencesFromResource(R.xml.device_settings_edit);
        }

        mDeviceId = findPreference("device_id");
        mDeviceId.setOnPreferenceChangeListener(this);
        mName = (EditTextPreference) findPreference("name");
        mName.setOnPreferenceChangeListener(this);
        mAddresses = (EditTextPreference) findPreference("addresses");
        mAddresses.setOnPreferenceChangeListener(this);
        mCompression = (CheckBoxPreference) findPreference("compression");
        mCompression.setOnPreferenceChangeListener(this);
        mIntroducer = (CheckBoxPreference) findPreference("introducer");
        mIntroducer.setOnPreferenceChangeListener(this);
        if (!mIsCreate) {
            mVersion = findPreference("version");
            mVersion.setSummary("?");
            mCurrentAddress = findPreference("current_address");
            mCurrentAddress.setSummary("?");
        }

        if (mIsCreate) {
            if (savedInstanceState != null) {
                mDevice = (RestApi.Device) savedInstanceState.getSerializable("device");
            } else {
                mDevice = new RestApi.Device();
                mDevice.Name = "";
                mDevice.DeviceID = "";
                mDevice.Addresses = "dynamic";
                mDevice.Compression = true;
                ((EditTextPreference) mDeviceId).setText(mDevice.DeviceID);
            }
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("device", mDevice);
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        else if (currentState != SyncthingService.State.ACTIVE) {
            getActivity().finish();
        }

        if (mIsCreate) {
            getActivity().setTitle(R.string.add_device);
        } else {
            getActivity().setTitle(R.string.edit_device);
            List<RestApi.Device> devices = mSyncthingService.getApi().getDevices();
            for (int i = 0; i < devices.size(); i++) {
                if (devices.get(i).DeviceID.equals(
                        getActivity().getIntent().getStringExtra(EXTRA_NODE_ID))) {
                    mDevice = devices.get(i);
                    break;
                }
            }
            mDeviceId.setOnPreferenceClickListener(this);
        }
        mSyncthingService.getApi().getConnections(DeviceSettingsFragment.this);

        mDeviceId.setSummary(mDevice.DeviceID);
        mName.setText((mDevice.Name));
        mName.setSummary(mDevice.Name);
        mAddresses.setText(mDevice.Addresses);
        mAddresses.setSummary(mDevice.Addresses);
        mCompression.setChecked(mDevice.Compression);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.device_settings, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreate);
        menu.findItem(R.id.share_device_id).setVisible(!mIsCreate);
        menu.findItem(R.id.delete).setVisible(!mIsCreate);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (mDevice.DeviceID.equals("")) {
                    Toast.makeText(getActivity(), R.string.device_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                if (mDevice.Name.equals("")) {
                    Toast.makeText(getActivity(), R.string.device_name_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                mSyncthingService.getApi().editDevice(mDevice, getActivity(), this);
                return true;
            case R.id.share_device_id:
                RestApi.shareDeviceId(getActivity(), mDevice.DeviceID);
                return true;
            case R.id.delete:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.delete_device_confirm)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                mSyncthingService.getApi().deleteDevice(mDevice, getActivity());
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case android.R.id.home:
                getActivity().finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        if (preference instanceof EditTextPreference) {
            EditTextPreference pref = (EditTextPreference) preference;
            pref.setSummary((String) o);
        }

        if (preference.equals(mDeviceId)) {
            mDevice.DeviceID = (String) o;
            deviceUpdated();
            return true;
        } else if (preference.equals(mName)) {
            mDevice.Name = (String) o;
            deviceUpdated();
            return true;
        } else if (preference.equals(mAddresses)) {
            mDevice.Addresses = (String) o;
            deviceUpdated();
            return true;
        } else if (preference.equals(mCompression)) {
            mDevice.Compression = (Boolean) o;
            deviceUpdated();
            return true;
        } else if (preference.equals(mIntroducer)) {
            mDevice.Introducer = (Boolean) o;
            deviceUpdated();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.equals(mDeviceId)) {
            mSyncthingService.getApi().copyDeviceId(mDevice.DeviceID);
            return true;
        }
        return false;
    }

    /**
     * Sets version and current address of the device.
     *
     * NOTE: This is only called once on startup, should be called more often to properly display
     * version/address changes.
     */
    @Override
    public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
        if (connections.containsKey(mDevice.DeviceID)) {
            mVersion.setSummary(connections.get(mDevice.DeviceID).ClientVersion);
            mCurrentAddress.setSummary(connections.get(mDevice.DeviceID).Address);
        }
    }

    /**
     * Sends the updated device info if in edit mode.
     */
    private void deviceUpdated() {
        if (!mIsCreate) {
            mSyncthingService.getApi().editDevice(mDevice, getActivity(), this);
        }
    }

    /**
     * Sends QR code scanning intent when clicking the qrcode icon.
     */
    public void onClick(View view) {
        Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
        intentScan.addCategory(Intent.CATEGORY_DEFAULT);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        try {
            startActivityForResult(intentScan, SCAN_QR_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getActivity(), R.string.no_qr_scanner_installed,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Receives value of scanned QR code and sets it as device ID.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCAN_QR_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            mDevice.DeviceID = data.getStringExtra("SCAN_RESULT");
            ((EditTextPreference) mDeviceId).setText(mDevice.DeviceID);
            mDeviceId.setSummary(mDevice.DeviceID);
        }
    }

    /**
     * Callback for {@link RestApi#editDevice}.
     * Displays an error toast if error message present.
     */
    @Override
    public void onDeviceIdNormalized(String normalizedId, String error) {
        if (error != null) {
            Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
        }
    }

}
