package com.nutomic.syncthingandroid.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.fragments.dialog.KeepVersionsDialogFragment;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.RestApi.SimpleVersioning;
import com.nutomic.syncthingandroid.syncthing.RestApi.Versioning;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;

import java.util.ArrayList;
import java.util.List;

import static android.support.v4.view.MarginLayoutParamsCompat.setMarginEnd;
import static android.support.v4.view.MarginLayoutParamsCompat.setMarginStart;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.nutomic.syncthingandroid.syncthing.SyncthingService.State.ACTIVE;
import static com.nutomic.syncthingandroid.util.DpConverter.dp;
import static java.lang.String.valueOf;

/**
 * Shows folder details and allows changing them.
 */
public class FolderFragment extends Fragment
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener {

    /**
     * The ID of the folder to be edited. To be used with {@link com.nutomic.syncthingandroid.activities.SettingsActivity#EXTRA_IS_CREATE}
     * set to false.
     */
    public static final String EXTRA_REPO_ID = "folder_id";

    private static final int DIRECTORY_REQUEST_CODE = 234;

    private static final String TAG = "EditFolderFragment";

    public static final String KEEP_VERSIONS_DIALOG_TAG = "KeepVersionsDialogFragment";

    private SyncthingService mSyncthingService;

    private RestApi.Folder mFolder;

    private EditText mIdView;

    private TextView mPathView;

    private SwitchCompat mFolderMasterView;

    private ViewGroup mDevicesContainer;

    private TextView mVersioningKeepView;

    private boolean mIsCreateMode;

    private KeepVersionsDialogFragment mKeepVersionsDialogFragment = new KeepVersionsDialogFragment();

    private TextWatcher mIdTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mFolder.id = s.toString();
            updateRepo();
        }
    };

    private TextWatcher mPathTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mFolder.path = s.toString();
        }
    };

    private CompoundButton.OnCheckedChangeListener mMasterCheckedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            mFolder.readOnly = isChecked;
            updateRepo();
        }
    };

    private CompoundButton.OnCheckedChangeListener mOnShareChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            RestApi.Device device = (RestApi.Device) view.getTag();
            if (isChecked) {
                mFolder.deviceIds.add(device.deviceID);
            } else {
                mFolder.deviceIds.remove(device.deviceID);
            }
            updateRepo();
        }
    };

    private KeepVersionsDialogFragment.OnValueChangeListener mOnValueChangeListener = new KeepVersionsDialogFragment.OnValueChangeListener() {
        @Override
        public void onValueChange(int intValue) {
            if (intValue == 0) {
                mFolder.versioning = new Versioning();
                mVersioningKeepView.setText(R.string.off);
            } else {
                mFolder.versioning = new SimpleVersioning();
                ((SimpleVersioning) mFolder.versioning).setParams(intValue);
                mVersioningKeepView.setText(valueOf(intValue));
            }
            updateRepo();
        }
    };

    private View.OnClickListener mPathViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getActivity(), FolderPickerActivity.class);
            if (mFolder.path.length() > 0) {
                intent.putExtra(FolderPickerActivity.EXTRA_INITIAL_DIRECTORY, mFolder.path);
            }
            startActivityForResult(intent, DIRECTORY_REQUEST_CODE);
        }
    };

    private View.OnClickListener mVersioningContainerClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mKeepVersionsDialogFragment.show(getFragmentManager(), KEEP_VERSIONS_DIALOG_TAG);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsActivity activity = (SettingsActivity) getActivity();
        mIsCreateMode = activity.getIsCreate();
        activity.setTitle(mIsCreateMode ? R.string.create_folder : R.string.edit_folder);
        activity.registerOnServiceConnectedListener(this);
        setHasOptionsMenu(true);

        if (mIsCreateMode) {
            if (savedInstanceState != null) {
                mFolder = (RestApi.Folder) savedInstanceState.getSerializable("folder");
            }
            if (mFolder == null) {
                initFolder();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSyncthingService != null) {
            mSyncthingService.unregisterOnApiChangeListener(this);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_folder, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mIdView = (EditText) view.findViewById(R.id.id);
        mPathView = (TextView) view.findViewById(R.id.directory);
        mFolderMasterView = (SwitchCompat) view.findViewById(R.id.master);
        mVersioningKeepView = (TextView) view.findViewById(R.id.versioningKeep);
        mDevicesContainer = (ViewGroup) view.findViewById(R.id.devicesContainer);

        mPathView.setOnClickListener(mPathViewClickListener);
        view.findViewById(R.id.versioningContainer).setOnClickListener(mVersioningContainerClickListener);

        if (!mIsCreateMode) {
            prepareEditMode();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mIdView.removeTextChangedListener(mIdTextWatcher);
        mPathView.removeTextChangedListener(mPathTextWatcher);
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != ACTIVE) {
            getActivity().finish();
            return;
        }

        if (!mIsCreateMode) {
            List<RestApi.Folder> folders = mSyncthingService.getApi().getFolders();
            String passedId = getActivity().getIntent().getStringExtra(EXTRA_REPO_ID);
            for (RestApi.Folder currentFolder : folders) {
                if (currentFolder.id.equals(passedId)) {
                    mFolder = currentFolder;
                    break;
                }
            }
            if (mFolder == null) {
                Log.w(TAG, "Folder not found in API update");
                getActivity().finish();
                return;
            }
        }

        updateViewsAndSetListeners();
    }

    private void updateViewsAndSetListeners() {
        // Update views
        mIdView.setText(mFolder.id);
        mPathView.setText(mFolder.path);
        mFolderMasterView.setChecked(mFolder.readOnly);
        List<RestApi.Device> devicesList = mSyncthingService.getApi().getDevices(false);

        if (devicesList.isEmpty()) {
            addEmptyDeviceListView();
        } else {
            for (RestApi.Device n : devicesList) {

                addDeviceViewAndSetListener(n, LayoutInflater.from(getActivity()));
            }
        }

        boolean versioningEnabled = mFolder.versioning instanceof SimpleVersioning;
        int versions = 0;
        if (versioningEnabled) {
            versions = Integer.valueOf(mFolder.versioning.getParams().get("keep"));
            mVersioningKeepView.setText(valueOf(versions));
        } else {
            mVersioningKeepView.setText(R.string.off);
        }
        mKeepVersionsDialogFragment.setValue(versions);

        // Keep state updated
        mIdView.addTextChangedListener(mIdTextWatcher);
        mPathView.addTextChangedListener(mPathTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(mMasterCheckedListener);
        mKeepVersionsDialogFragment.setOnValueChangeListener(mOnValueChangeListener);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.folder_settings, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreateMode);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (TextUtils.isEmpty(mFolder.id)) {
                    Toast.makeText(getActivity(), R.string.folder_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                if (TextUtils.isEmpty(mFolder.path)) {
                    Toast.makeText(getActivity(), R.string.folder_path_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                mSyncthingService.getApi().editFolder(mFolder, true, getActivity());
                getActivity().finish();
                return true;
            case R.id.remove:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.remove_folder_confirm)
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == DIRECTORY_REQUEST_CODE) {
            mFolder.path = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            mPathView.setText(mFolder.path);
            updateRepo();
        }
    }

    private void initFolder() {
        mFolder = new RestApi.Folder();
        mFolder.id = "";
        mFolder.path = "";
        mFolder.rescanIntervalS = 259200; // Scan every 3 days (in case inotify dropped some changes)
        mFolder.deviceIds = new ArrayList<>();
        mFolder.versioning = new Versioning();
    }

    private void prepareEditMode() {
        mIdView.clearFocus();
        mIdView.setFocusable(false);
        mIdView.setEnabled(false);
        mPathView.setEnabled(false);
    }

    private void addEmptyDeviceListView() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, dp(48, getActivity()));
        int dividerInset = getResources().getDimensionPixelOffset(R.dimen.material_divider_inset);
        int contentInset = getResources().getDimensionPixelOffset(R.dimen.abc_action_bar_content_inset_material);
        setMarginStart(params, dividerInset);
        setMarginEnd(params, contentInset);
        TextView emptyView = new TextView(mDevicesContainer.getContext());
        emptyView.setGravity(CENTER_VERTICAL);
        emptyView.setText(R.string.devices_list_empty);
        mDevicesContainer.addView(emptyView, params);
    }

    private void addDeviceViewAndSetListener(RestApi.Device device, LayoutInflater inflater) {
        inflater.inflate(R.layout.item_device_form, mDevicesContainer);
        SwitchCompat deviceView = (SwitchCompat) mDevicesContainer.getChildAt(mDevicesContainer.getChildCount()-1);
        deviceView.setChecked(mFolder.deviceIds.contains(device.deviceID));
        deviceView.setText(RestApi.getDeviceDisplayName(device));
        deviceView.setTag(device);
        deviceView.setOnCheckedChangeListener(mOnShareChangeListener);
    }

    private void updateRepo() {
        if (!mIsCreateMode) {
            mSyncthingService.getApi().editFolder(mFolder, false, getActivity());
        }
    }
}
