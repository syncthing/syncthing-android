package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.dialog.KeepVersionsDialogFragment;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;

import java.util.List;

import static android.support.v4.view.MarginLayoutParamsCompat.setMarginEnd;
import static android.support.v4.view.MarginLayoutParamsCompat.setMarginStart;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.nutomic.syncthingandroid.service.SyncthingService.State.ACTIVE;
import static java.lang.String.valueOf;

/**
 * Shows folder details and allows changing them.
 */
public class FolderActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener {

    public static final String EXTRA_IS_CREATE =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.IS_CREATE";
    public static final String EXTRA_FOLDER_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_ID";
    public static final String EXTRA_FOLDER_LABEL =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_LABEL";
    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.DEVICE_ID";

    private static final int DIRECTORY_REQUEST_CODE = 234;

    private static final String TAG = "EditFolderFragment";

    public static final String KEEP_VERSIONS_DIALOG_TAG = "KeepVersionsDialogFragment";

    private Folder mFolder;

    private EditText mLabelView;
    private EditText mIdView;
    private TextView mPathView;
    private SwitchCompat mFolderMasterView;
    private ViewGroup mDevicesContainer;
    private TextView mVersioningKeepView;

    private boolean mIsCreateMode;
    private boolean mFolderNeedsToUpdate;

    private final KeepVersionsDialogFragment mKeepVersionsDialogFragment = new KeepVersionsDialogFragment();

    private final TextWatcher mTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mFolder.label        = mLabelView.getText().toString();
            mFolder.id           = mIdView.getText().toString();
            mFolder.path         = mPathView.getText().toString();
            mFolderNeedsToUpdate = true;
        }
    };

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.master:
                    mFolder.type = (isChecked) ? "readonly" : "readwrite";
                    mFolderNeedsToUpdate = true;
                    break;
                case R.id.device_toggle:
                    Device device = (Device) view.getTag();
                    if (isChecked) {
                        mFolder.addDevice(device.deviceID);
                    } else {
                        mFolder.removeDevice(device.deviceID);
                    }
                    mFolderNeedsToUpdate = true;
                    break;
            }
        }
    };

    private final KeepVersionsDialogFragment.OnValueChangeListener mOnValueChangeListener =
            new KeepVersionsDialogFragment.OnValueChangeListener() {
        @Override
        public void onValueChange(int intValue) {
            if (intValue == 0) {
                mFolder.versioning = new Folder.Versioning();
                mVersioningKeepView.setText(R.string.off);
            } else {
                mFolder.versioning.type = "simple";
                mFolder.versioning.params.put("keep", valueOf(intValue));
                mVersioningKeepView.setText(valueOf(intValue));
            }
            mFolderNeedsToUpdate = true;
        }
    };

    private final View.OnClickListener mPathViewClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(FolderActivity.this, FolderPickerActivity.class);
            if (!TextUtils.isEmpty(mFolder.path)) {
                intent.putExtra(FolderPickerActivity.EXTRA_INITIAL_DIRECTORY, mFolder.path);
            }
            startActivityForResult(intent, DIRECTORY_REQUEST_CODE);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_folder);

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        setTitle(mIsCreateMode ? R.string.create_folder : R.string.edit_folder);
        registerOnServiceConnectedListener(this);

        mLabelView = (EditText) findViewById(R.id.label);
        mIdView = (EditText) findViewById(R.id.id);
        mPathView = (TextView) findViewById(R.id.directory);
        mFolderMasterView = (SwitchCompat) findViewById(R.id.master);
        mVersioningKeepView = (TextView) findViewById(R.id.versioningKeep);
        mDevicesContainer = (ViewGroup) findViewById(R.id.devicesContainer);

        mPathView.setOnClickListener(mPathViewClickListener);
        findViewById(R.id.versioningContainer).setOnClickListener(v ->
                mKeepVersionsDialogFragment.show(getFragmentManager(), KEEP_VERSIONS_DIALOG_TAG));

        if (mIsCreateMode) {
            if (savedInstanceState != null) {
                mFolder = new Gson().fromJson(savedInstanceState.getString("folder"), Folder.class);
            }
            if (mFolder == null) {
                initFolder();
            }
            // Open keyboard on label view in edit mode.
            mLabelView.requestFocus();
        }
        else {
            prepareEditMode();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (getService() != null) {
            getService().unregisterOnApiChangeListener(this);
        }
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mPathView.removeTextChangedListener(mTextWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();

        // We don't want to update every time a TextView's character changes,
        // so we hold off until the view stops being visible to the user.
        if (mFolderNeedsToUpdate) {
            updateFolder();
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("folder", new Gson().toJson(mFolder));
    }

    @Override
    public void onServiceConnected() {
        getService().registerOnApiChangeListener(this);
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != ACTIVE) {
            finish();
            return;
        }

        if (!mIsCreateMode) {
            List<Folder> folders = getApi().getFolders();
            String passedId = getIntent().getStringExtra(EXTRA_FOLDER_ID);
            mFolder = null;
            for (Folder currentFolder : folders) {
                if (currentFolder.id.equals(passedId)) {
                    mFolder = currentFolder;
                    break;
                }
            }
            if (mFolder == null) {
                Log.w(TAG, "Folder not found in API update, maybe it was deleted?");
                finish();
                return;
            }
        }

        updateViewsAndSetListeners();
    }

    private void updateViewsAndSetListeners() {
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mPathView.removeTextChangedListener(mTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(null);
        mKeepVersionsDialogFragment.setOnValueChangeListener(null);

        // Update views
        mLabelView.setText(mFolder.label);
        mIdView.setText(mFolder.id);
        mPathView.setText(mFolder.path);
        mFolderMasterView.setChecked(Objects.equal(mFolder.type, "readonly"));
        List<Device> devicesList = getApi().getDevices(false);

        mDevicesContainer.removeAllViews();
        if (devicesList.isEmpty()) {
            addEmptyDeviceListView();
        } else {
            for (Device n : devicesList) {
                addDeviceViewAndSetListener(n, getLayoutInflater());
            }
        }

        boolean versioningEnabled = Objects.equal(mFolder.versioning.type, "simple");
        int versions = 0;
        if (versioningEnabled) {
            versions = Integer.valueOf(mFolder.versioning.params.get("keep"));
            mVersioningKeepView.setText(valueOf(versions));
        } else {
            mVersioningKeepView.setText(R.string.off);
        }
        mKeepVersionsDialogFragment.setValue(versions);

        // Keep state updated
        mLabelView.addTextChangedListener(mTextWatcher);
        mIdView.addTextChangedListener(mTextWatcher);
        mPathView.addTextChangedListener(mTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(mCheckedListener);
        mKeepVersionsDialogFragment.setOnValueChangeListener(mOnValueChangeListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.folder_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreateMode);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (TextUtils.isEmpty(mFolder.id)) {
                    Toast.makeText(this, R.string.folder_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                if (TextUtils.isEmpty(mFolder.path)) {
                    Toast.makeText(this, R.string.folder_path_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                getApi().addFolder(mFolder);
                finish();
                return true;
            case R.id.remove:
                new AlertDialog.Builder(this)
                        .setMessage(R.string.remove_folder_confirm)
                        .setPositiveButton(android.R.string.yes, (dialogInterface, i) ->
                                getApi().removeFolder(mFolder.id))
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case android.R.id.home:
                onBackPressed();
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
            mFolderNeedsToUpdate = true;
        }
    }

    private void initFolder() {
        mFolder = new Folder();
        mFolder.id = getIntent().getStringExtra(EXTRA_FOLDER_ID);
        mFolder.label = getIntent().getStringExtra(EXTRA_FOLDER_LABEL);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Scan every 3 days (in case inotify dropped some changes)
            mFolder.rescanIntervalS = 259200;
        }
        else {
            // FileObserver does not work correctly on Android Marshmallow.
            // Nougat seems to have the same problem in emulator, but we should check this again.
            // https://code.google.com/p/android/issues/detail?id=189231
            mFolder.rescanIntervalS = 60;
        }
        mFolder.versioning = new Folder.Versioning();
        String deviceId = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        if (deviceId != null) {
            mFolder.addDevice(deviceId);
        }
    }

    private void prepareEditMode() {
        mIdView.clearFocus();
        mIdView.setFocusable(false);
        mIdView.setEnabled(false);
        mPathView.setEnabled(false);
    }

    private void addEmptyDeviceListView() {
        int height = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, height);
        int dividerInset = getResources().getDimensionPixelOffset(R.dimen.material_divider_inset);
        int contentInset = getResources().getDimensionPixelOffset(R.dimen.abc_action_bar_content_inset_material);
        setMarginStart(params, dividerInset);
        setMarginEnd(params, contentInset);
        TextView emptyView = new TextView(mDevicesContainer.getContext());
        emptyView.setGravity(CENTER_VERTICAL);
        emptyView.setText(R.string.devices_list_empty);
        mDevicesContainer.addView(emptyView, params);
    }

    private void addDeviceViewAndSetListener(Device device, LayoutInflater inflater) {
        inflater.inflate(R.layout.item_device_form, mDevicesContainer);
        SwitchCompat deviceView = (SwitchCompat) mDevicesContainer.getChildAt(mDevicesContainer.getChildCount()-1);
        deviceView.setChecked(mFolder.getDevice(device.deviceID) != null);
        deviceView.setText(device.getDisplayName());
        deviceView.setTag(device);
        deviceView.setOnCheckedChangeListener(mCheckedListener);
    }

    private void updateFolder() {
        if (!mIsCreateMode) {
            getApi().editFolder(mFolder);
        }
    }

    @Override
    public void onBackPressed() {
        if (mIsCreateMode) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.dialog_discard_changes)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
        else {
            super.onBackPressed();
        }
    }
}
