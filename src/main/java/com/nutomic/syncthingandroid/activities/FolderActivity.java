package com.nutomic.syncthingandroid.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Objects;
import com.google.gson.Gson;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.dialog.VersioningDialogFragment;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;

import java.util.List;
import java.util.Random;
import java.util.Map;

import static android.support.v4.view.MarginLayoutParamsCompat.setMarginEnd;
import static android.support.v4.view.MarginLayoutParamsCompat.setMarginStart;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.nutomic.syncthingandroid.service.SyncthingService.State.ACTIVE;

/**
 * Shows folder details and allows changing them.
 */
public class FolderActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener, VersioningDialogFragment.VersioningDialogInterface {

    public static final String EXTRA_IS_CREATE =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.IS_CREATE";
    public static final String EXTRA_FOLDER_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_ID";
    public static final String EXTRA_FOLDER_LABEL =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_LABEL";
    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.DEVICE_ID";

    private static final String TAG = "EditFolderFragment";

    private static final String IS_SHOWING_DELETE_DIALOG = "DELETE_FOLDER_DIALOG_STATE";
    private static final String IS_SHOW_DISCARD_DIALOG = "DISCARD_FOLDER_DIALOG_STATE";

    private Folder mFolder;

    private EditText mLabelView;
    private EditText mIdView;
    private TextView mPathView;
    private SwitchCompat mFolderMasterView;
    private ViewGroup mDevicesContainer;
    private TextView mVersioningDescriptionView;

    private boolean mIsCreateMode;
    private boolean mFolderNeedsToUpdate;

    private Dialog mDeleteDialog;
    private Dialog mDiscardDialog;

    private final VersioningDialogFragment mVersioningDialogFragment = new VersioningDialogFragment();

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_folder);

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        setTitle(mIsCreateMode ? R.string.create_folder : R.string.edit_folder);
        registerOnServiceConnectedListener(this);

        mLabelView = (EditText) findViewById(R.id.label);
        mIdView = (EditText) findViewById(R.id.id);
        mPathView = (TextView) findViewById(R.id.fragment_folder_path);
        mFolderMasterView = (SwitchCompat) findViewById(R.id.master);
        mVersioningDescriptionView = (TextView) findViewById(R.id.versioningDescription);
        mDevicesContainer = (ViewGroup) findViewById(R.id.devicesContainer);

        findViewById(R.id.versioningContainer).setOnClickListener(v -> showVersioningDialog());

        if (mIsCreateMode) {
            if (savedInstanceState != null) {
                mFolder = new Gson().fromJson(savedInstanceState.getString("folder"), Folder.class);
                if (savedInstanceState.getBoolean(IS_SHOW_DISCARD_DIALOG)){
                    showDiscardDialog();
                }
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

        if (savedInstanceState != null){
            if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)){
                showDeleteDialog();
            }
        }

        if (savedInstanceState != null){
            if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)){
                showDeleteDialog();
            }
        }
    }

    private void showVersioningDialog() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        mVersioningDialogFragment.setArguments(getVersioningBundle());
        mVersioningDialogFragment.show(transaction, "versioningDialog");
    }

    private Bundle getVersioningBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry: mFolder.versioning.params.entrySet()){
            bundle.putString(entry.getKey(), entry.getValue());
        }
        bundle.putString("type", mFolder.versioning.type);
        return bundle;
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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_SHOWING_DELETE_DIALOG, mDeleteDialog != null && mDeleteDialog.isShowing());
        if (mDeleteDialog != null) {
            mDeleteDialog.cancel();
        }

        if (mIsCreateMode){
            outState.putBoolean(IS_SHOW_DISCARD_DIALOG, mDiscardDialog != null && mDiscardDialog.isShowing());
            if(mDiscardDialog != null){
                mDiscardDialog.cancel();
            }
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
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
        if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            mFolder.addDevice(getIntent().getStringExtra(EXTRA_DEVICE_ID));
            mFolderNeedsToUpdate = true;
        }

        updateViewsAndSetListeners();
    }

    private void updateViewsAndSetListeners() {
        mLabelView.removeTextChangedListener(mTextWatcher);
        mIdView.removeTextChangedListener(mTextWatcher);
        mPathView.removeTextChangedListener(mTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(null);

        // Update views
        mLabelView.setText(mFolder.label);
        mIdView.setText(mFolder.id);
        mPathView.setText(mFolder.path);
        mVersioningDescriptionView.setText(getVersioningDescription());
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

        // Keep state updated
        mLabelView.addTextChangedListener(mTextWatcher);
        mIdView.addTextChangedListener(mTextWatcher);
        mPathView.addTextChangedListener(mTextWatcher);
        mFolderMasterView.setOnCheckedChangeListener(mCheckedListener);
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
                showDeleteDialog();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showDeleteDialog(){
        mDeleteDialog = createDeleteDialog();
        mDeleteDialog.show();
    }

    private Dialog createDeleteDialog(){
        return new AlertDialog.Builder(this)
                .setMessage(R.string.remove_folder_confirm)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    getApi().removeFolder(mFolder.id);
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
    }

    private String generateRandomFolderId() {
        char[] chars = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append("-");
            }
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        return sb.toString();
    }
    private void initFolder() {
        mFolder = new Folder();
        mFolder.id = (getIntent().hasExtra(EXTRA_FOLDER_ID))
                ? getIntent().getStringExtra(EXTRA_FOLDER_ID)
                : generateRandomFolderId();
        mFolder.label = getIntent().getStringExtra(EXTRA_FOLDER_LABEL);
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
            // Scan every 3 days (in case inotify dropped some changes)
            mFolder.rescanIntervalS = 259200;
        }
        else {
            // FileObserver is broken on Marshmallow.
            // https://github.com/syncthing/syncthing-android/issues/787
            mFolder.rescanIntervalS = 60;
        }
        mFolder.versioning = new Folder.Versioning();
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
            showDiscardDialog();
        }
        else {
            super.onBackPressed();
        }
    }

    private void showDiscardDialog(){
        mDiscardDialog = createDiscardDialog();
        mDiscardDialog.show();
    }

    private Dialog createDiscardDialog() {
        return new AlertDialog.Builder(this)
                .setMessage(R.string.dialog_discard_changes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    @Override
    public void onDialogClosed(Bundle arguments) {
        updateVersioning(arguments);
    }

    private void updateVersioning(Bundle arguments) {
        String type = arguments.getString("type");
        arguments.remove("type");

        if (type.equals("none")){
            mFolder.versioning = new Folder.Versioning();
        }else {
            mFolder.versioning.type = type;

            for (String key : arguments.keySet()) {
                mFolder.versioning.params.put(key, arguments.getString(key));
            }
        }
        mVersioningDescriptionView.setText(getVersioningDescription());
        mFolderNeedsToUpdate = true;
    }

    private String getVersioningDescription() {
        String description = getString(R.string.none);

        if (mFolder == null || mFolder.versioning.type == null){
            return description;
        }

        switch (mFolder.versioning.type) {
            case "simple":
                description = getString(R.string.type) + ": " + getString(R.string.simple) + "\n"
                        + getString(R.string.keep_versions) +": " + mFolder.versioning.params.get("keep");
                break;
            case "trashcan":
                description = getString(R.string.type) + ": " + getString(R.string.trashcan) + "\n"
                        + getString(R.string.clean_out_after) +": " + mFolder.versioning.params.get("cleanoutDays");
                break;
            case "staggered":
                int maxAge = getMaxAgeInDays();
                description = getString(R.string.type) + ": " + getString(R.string.staggered) + "\n"
                        + getString(R.string.maximum_age) + ": " + (maxAge == 0 ? getString(R.string.never_delete) : maxAge) + "\n"
                        + getString(R.string.versions_path)+ ": " + mFolder.versioning.params.get("versionsPath");
                break;
            case "external":
                description = getString(R.string.type) + ": " + getString(R.string.external) + "\n"
                        + getString(R.string.command) + ": " + mFolder.versioning.params.get("command");
                break;
        }

        return description;
    }

    //MaxAge is stored in seconds, since Syncthing requires it in seconds, but it is displayed in days. Hence the conversion below.
    private int getMaxAgeInDays() {
        int secInADay = 86400;

        return Integer.valueOf(mFolder.versioning.params.get("maxAge"))/secInADay;
    }
}
