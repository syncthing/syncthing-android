package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.documentfile.provider.DocumentFile;

import android.os.Environment;
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

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.databinding.FragmentFolderBinding;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.FileUtils;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static androidx.core.view.MarginLayoutParamsCompat.setMarginEnd;
import static androidx.core.view.MarginLayoutParamsCompat.setMarginStart;
import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.nutomic.syncthingandroid.service.SyncthingService.State.ACTIVE;

/**
 * Shows folder details and allows changing them.
 */
public class FolderActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnServiceStateChangeListener {

    public static final String EXTRA_NOTIFICATION_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.NOTIFICATION_ID";
    public static final String EXTRA_IS_CREATE =
            "com.nutomic.syncthingandroid.activities.FolderActivity.IS_CREATE";
    public static final String EXTRA_FOLDER_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_ID";
    public static final String EXTRA_FOLDER_LABEL =
            "com.nutomic.syncthingandroid.activities.FolderActivity.FOLDER_LABEL";
    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.activities.FolderActivity.DEVICE_ID";

    private static final String TAG = "FolderActivity";

    private static final String IS_SHOWING_DELETE_DIALOG = "DELETE_FOLDER_DIALOG_STATE";
    private static final String IS_SHOW_DISCARD_DIALOG = "DISCARD_FOLDER_DIALOG_STATE";

    private static final int FILE_VERSIONING_DIALOG_REQUEST = 3454;
    private static final int PULL_ORDER_DIALOG_REQUEST = 3455;
    private static final int FOLDER_TYPE_DIALOG_REQUEST =3456;
    private static final int CHOOSE_FOLDER_REQUEST = 3459;

    private static final String FOLDER_MARKER_NAME = ".stfolder";
    private static final String IGNORE_FILE_NAME = ".stignore";

    private Folder mFolder;
    private Uri mFolderUri = null;
    // Indicates the result of the write test to mFolder.path on dialog init or after a path change.
    Boolean mCanWriteToPath = false;

    private FragmentFolderBinding binding;

    private boolean mIsCreateMode;
    private boolean mFolderNeedsToUpdate = false;

    private Dialog mDeleteDialog;
    private Dialog mDiscardDialog;

    private Folder.Versioning mVersioning;

    private final TextWatcher mTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            mFolder.label        = binding.label.getText().toString();
            mFolder.id           = binding.id.getText().toString();
            // binding.directoryTextView must not be handled here as it's handled by {@link onActivityResult}
            mFolderNeedsToUpdate = true;
        }
    };

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.fileWatcher:
                    mFolder.fsWatcherEnabled = isChecked;
                    mFolderNeedsToUpdate = true;
                    break;
                case R.id.folderPause:
                    mFolder.paused = isChecked;
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
        binding = FragmentFolderBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        setTitle(mIsCreateMode ? R.string.create_folder : R.string.edit_folder);
        registerOnServiceConnectedListener(this);

        binding.directoryTextView.setOnClickListener(view -> onPathViewClick());

        findViewById(R.id.folderTypeContainer).setOnClickListener(v -> showFolderTypeDialog());
        findViewById(R.id.pullOrderContainer).setOnClickListener(v -> showPullOrderDialog());
        findViewById(R.id.versioningContainer).setOnClickListener(v -> showVersioningDialog());
        binding.editIgnores.setOnClickListener(v -> editIgnores());

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
            binding.label.requestFocus();
            binding.editIgnores.setEnabled(false);
        }
        else {
            // Prepare edit mode.
            binding.id.clearFocus();
            binding.id.setFocusable(false);
            binding.id.setEnabled(false);
            binding.directoryTextView.setEnabled(false);
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

    /**
     * Invoked after user clicked on the directoryTextView label.
     */
    @SuppressLint("InlinedAPI")
    private void onPathViewClick() {
        // This has to be android.net.Uri as it implements a Parcelable.
        android.net.Uri externalFilesDirUri = FileUtils.getExternalFilesDirUri(FolderActivity.this);

        // Display storage access framework directory picker UI.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (externalFilesDirUri != null) {
            intent.putExtra("android.provider.extra.INITIAL_URI", externalFilesDirUri);
        }
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        try {
            startActivityForResult(intent, CHOOSE_FOLDER_REQUEST);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "onPathViewClick exception, falling back to built-in FolderPickerActivity.", e);
            startActivityForResult(FolderPickerActivity.createIntent(this, mFolder.path, null),
                FolderPickerActivity.DIRECTORY_REQUEST_CODE);
        }
    }

    private void editIgnores() {
        try {
            File ignoreFile = new File(mFolder.path, IGNORE_FILE_NAME);
            if (!ignoreFile.exists() && !ignoreFile.createNewFile()) {
                Toast.makeText(this, R.string.create_ignore_file_error, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_EDIT);
            Uri uri = Uri.fromFile(ignoreFile);
            intent.setDataAndType(uri, "text/plain");
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            startActivity(intent);
        } catch (IOException e) {
            Log.w(TAG, e);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, e);
            Toast.makeText(this, R.string.edit_ignore_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void showFolderTypeDialog() {
        if (TextUtils.isEmpty(mFolder.path)) {
            Toast.makeText(this, R.string.folder_path_required, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        if (!mCanWriteToPath) {
            /**
             * Do not handle the click as the children in the folder type layout are disabled
             * and an explanation is already given on the UI why the only allowed folder type
             * is "sendonly".
            */
            Toast.makeText(this, R.string.folder_path_readonly, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        // The user selected folder path is writeable, offer to choose from all available folder types.
        Intent intent = new Intent(this, FolderTypeDialogActivity.class);
        intent.putExtra(FolderTypeDialogActivity.EXTRA_FOLDER_TYPE, mFolder.type);
        startActivityForResult(intent, FOLDER_TYPE_DIALOG_REQUEST);
    }

    private void showPullOrderDialog() {
        Intent intent = new Intent(this, PullOrderDialogActivity.class);
        intent.putExtra(PullOrderDialogActivity.EXTRA_PULL_ORDER, mFolder.order);
        startActivityForResult(intent, PULL_ORDER_DIALOG_REQUEST);
    }

    private void showVersioningDialog() {
        Intent intent = new Intent(this, VersioningDialogActivity.class);
        intent.putExtras(getVersioningBundle());
        startActivityForResult(intent, FILE_VERSIONING_DIALOG_REQUEST);
    }

    private Bundle getVersioningBundle() {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry: mFolder.versioning.params.entrySet()){
            bundle.putString(entry.getKey(), entry.getValue());
        }

        if (TextUtils.isEmpty(mFolder.versioning.type)){
            bundle.putString("type", "none");
        } else{
            bundle.putString("type", mFolder.versioning.type);
        }

        return bundle;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService syncthingService = getService();
        if (syncthingService != null) {
            syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
            syncthingService.unregisterOnServiceStateChangeListener(this::onServiceStateChange);
        }
        binding.label.removeTextChangedListener(mTextWatcher);
        binding.id.removeTextChangedListener(mTextWatcher);
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
        Util.dismissDialogSafe(mDeleteDialog, this);

        if (mIsCreateMode){
            outState.putBoolean(IS_SHOW_DISCARD_DIALOG, mDiscardDialog != null && mDiscardDialog.isShowing());
            Util.dismissDialogSafe(mDiscardDialog, this);
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onServiceConnected() {
        Log.v(TAG, "onServiceConnected");
        SyncthingService syncthingService = (SyncthingService) getService();
        syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        syncthingService.registerOnServiceStateChangeListener(this);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
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
            checkWriteAndUpdateUI();
        }
        if (getIntent().hasExtra(EXTRA_DEVICE_ID)) {
            mFolder.addDevice(getIntent().getStringExtra(EXTRA_DEVICE_ID));
            mFolderNeedsToUpdate = true;
        }

        attemptToApplyVersioningConfig();

        updateViewsAndSetListeners();
    }

    // If the FolderActivity gets recreated after the VersioningDialogActivity is closed, then the result from the VersioningDialogActivity will be received before
    // the mFolder variable has been recreated, so the versioning config will be stored in the mVersioning variable until the mFolder variable has been
    // recreated in the onServiceStateChange(). This has been observed to happen after the screen orientation has changed while the VersioningDialogActivity was open.
    private void attemptToApplyVersioningConfig() {
        if (mFolder != null && mVersioning != null){
            mFolder.versioning = mVersioning;
            mVersioning = null;
        }
    }

    private void updateViewsAndSetListeners() {
        binding.label.removeTextChangedListener(mTextWatcher);
        binding.id.removeTextChangedListener(mTextWatcher);
        binding.fileWatcher.setOnCheckedChangeListener(null);
        binding.folderPause.setOnCheckedChangeListener(null);

        // Update views
        binding.label.setText(mFolder.label);
        binding.id.setText(mFolder.id);
        updateFolderTypeDescription();
        updatePullOrderDescription();
        updateVersioningDescription();
        binding.fileWatcher.setChecked(mFolder.fsWatcherEnabled);
        binding.folderPause.setChecked(mFolder.paused);
        List<Device> devicesList = getApi().getDevices(false);

        binding.devicesContainer.removeAllViews();
        if (devicesList.isEmpty()) {
            addEmptyDeviceListView();
        } else {
            for (Device n : devicesList) {
                addDeviceViewAndSetListener(n, getLayoutInflater());
            }
        }

        // Keep state updated
        binding.label.addTextChangedListener(mTextWatcher);
        binding.id.addTextChangedListener(mTextWatcher);
        binding.fileWatcher.setOnCheckedChangeListener(mCheckedListener);
        binding.folderPause.setOnCheckedChangeListener(mCheckedListener);
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
                if (mFolderUri != null) {
                    /**
                     * Normally, syncthing takes care of creating the ".stfolder" marker.
                     * This fails on newer android versions if the syncthing binary only has
                     * readonly access on the path and the user tries to configure a
                     * sendonly folder. To fix this, we'll precreate the marker using java code.
                     * We also create an empty file in the marker directory, to hopefully keep
                     * it alive in the face of overzealous disk cleaner apps.
                     */
                    DocumentFile dfFolder = DocumentFile.fromTreeUri(this, mFolderUri);
                    if (dfFolder != null) {
                        Log.v(TAG, "Creating new directory " + mFolder.path + File.separator + FOLDER_MARKER_NAME);
                        DocumentFile marker = dfFolder.createDirectory(FOLDER_MARKER_NAME);
                        marker.createFile("text/plain", "empty");
                    }
                }
                getApi().createFolder(mFolder);
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
        return Util.getAlertDialogBuilder(this)
                .setMessage(R.string.remove_folder_confirm)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    RestApi restApi = getApi();
                    if (restApi != null) {
                        restApi.removeFolder(mFolder.id);
                    }
                    mFolderNeedsToUpdate = false;
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && requestCode == CHOOSE_FOLDER_REQUEST) {
            mFolderUri = data.getData();
            if (mFolderUri == null) {
                return;
            }
            // Get the folder path unix style, e.g. "/storage/0000-0000/DCIM"
            String targetPath = FileUtils.getAbsolutePathFromSAFUri(FolderActivity.this, mFolderUri);
            if (targetPath != null) {
                targetPath = Util.formatPath(targetPath);
            }
            if (targetPath == null || TextUtils.isEmpty(targetPath) || (targetPath.equals(File.separator))) {
                mFolder.path = "";
                mFolderUri = null;
                checkWriteAndUpdateUI();
                // Show message to the user suggesting to select a folder on internal or external storage.
                Toast.makeText(this, R.string.toast_invalid_folder_selected, Toast.LENGTH_LONG).show();
                return;
            }
            mFolder.path = FileUtils.cutTrailingSlash(targetPath);
            Log.v(TAG, "onActivityResult/CHOOSE_FOLDER_REQUEST: Got directory path '" + mFolder.path + "'");
            checkWriteAndUpdateUI();
            // Postpone sending the config changes using syncthing REST API.
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == FolderPickerActivity.DIRECTORY_REQUEST_CODE) {
            mFolder.path = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            checkWriteAndUpdateUI();
            // Postpone sending the config changes using syncthing REST API.
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == FILE_VERSIONING_DIALOG_REQUEST) {
            updateVersioning(data.getExtras());
        } else if (resultCode == Activity.RESULT_OK && requestCode == FOLDER_TYPE_DIALOG_REQUEST) {
            mFolder.type = data.getStringExtra(FolderTypeDialogActivity.EXTRA_RESULT_FOLDER_TYPE);
            updateFolderTypeDescription();
            mFolderNeedsToUpdate = true;
        } else if (resultCode == Activity.RESULT_OK && requestCode == PULL_ORDER_DIALOG_REQUEST) {
            mFolder.order = data.getStringExtra(PullOrderDialogActivity.EXTRA_RESULT_PULL_ORDER);
            updatePullOrderDescription();
            mFolderNeedsToUpdate = true;
        }
    }

    /**
     * Prerequisite: mFolder.path must be non-empty
     */
    private void checkWriteAndUpdateUI() {
        binding.directoryTextView.setText(mFolder.path);
        if (TextUtils.isEmpty(mFolder.path)) {
            return;
        }

        /**
         * Check if the permissions we have on that folder is readonly or readwrite.
         * Access level readonly: folder can only be configured "sendonly".
         * Access level readwrite: folder can be configured "sendonly" or "sendreceive".
         */
        mCanWriteToPath = Util.nativeBinaryCanWriteToPath(FolderActivity.this, mFolder.path);
        if(!mCanWriteToPath){
           final File externalStorageDirectory = Environment.getExternalStorageDirectory();
           mCanWriteToPath = Util.nativeBinaryCanWriteToPath2(externalStorageDirectory, mFolder.path);
        }
        if (mCanWriteToPath) {
            binding.accessExplanationView.setText(R.string.folder_path_readwrite);
            binding.folderType.setEnabled(true);
            binding.editIgnores.setEnabled(true);
            if (mIsCreateMode) {
                /**
                 * Suggest folder type FOLDER_TYPE_SEND_RECEIVE for folders to be created
                 * because the user most probably intentionally chose a special folder like
                 * "[storage]/Android/data/com.nutomic.syncthingandroid/files"
                 * or enabled root mode thus having write access.
                 */
                mFolder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;
                updateFolderTypeDescription();
            }
        } else {
            // Force "sendonly" folder.
            binding.accessExplanationView.setText(R.string.folder_path_readonly);
            binding.folderType.setEnabled(false);
            binding.editIgnores.setEnabled(false);
            mFolder.type = Constants.FOLDER_TYPE_SEND_ONLY;
            updateFolderTypeDescription();
        }
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
        mFolder.fsWatcherEnabled = true;
        mFolder.fsWatcherDelayS = 10;
        /**
         * Folder rescan interval defaults to 3600s as it is the default in
         * syncthing when the file watcher is enabled and a new folder is created.
         */
        mFolder.rescanIntervalS = 3600;
        mFolder.paused = false;
        mFolder.type = Constants.FOLDER_TYPE_SEND_RECEIVE;
        mFolder.versioning = new Folder.Versioning();
    }

    private void addEmptyDeviceListView() {
        int height = (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, height);
        int dividerInset = getResources().getDimensionPixelOffset(R.dimen.material_divider_inset);
        int contentInset = getResources().getDimensionPixelOffset(R.dimen.abc_action_bar_content_inset_material);
        setMarginStart(params, dividerInset);
        setMarginEnd(params, contentInset);
        TextView emptyView = new TextView(binding.devicesContainer.getContext());
        emptyView.setGravity(CENTER_VERTICAL);
        emptyView.setText(R.string.devices_list_empty);
        binding.devicesContainer.addView(emptyView, params);
    }

    private void addDeviceViewAndSetListener(Device device, LayoutInflater inflater) {
        inflater.inflate(R.layout.item_device_form, binding.devicesContainer);
        MaterialSwitch deviceView = (MaterialSwitch) binding.devicesContainer.getChildAt(binding.devicesContainer.getChildCount()-1);
        deviceView.setOnCheckedChangeListener(null);
        deviceView.setChecked(mFolder.getDevice(device.deviceID) != null);
        deviceView.setText(device.getDisplayName());
        deviceView.setTag(device);
        deviceView.setOnCheckedChangeListener(mCheckedListener);
    }

    private void updateFolder() {
        if (!mIsCreateMode) {
            /**
             * RestApi is guaranteed not to be null as {@link onServiceStateChange}
             * immediately finishes this activity if SyncthingService shuts down.
             */
            getApi().updateFolder(mFolder);
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
        return Util.getAlertDialogBuilder(this)
                .setMessage(R.string.dialog_discard_changes)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    private void updateVersioning(Bundle arguments) {
        if (mFolder != null){
            mVersioning = mFolder.versioning;
        } else {
            mVersioning = new Folder.Versioning();
        }

        String type = arguments.getString("type");
        arguments.remove("type");

        if (type.equals("none")){
            mVersioning = new Folder.Versioning();
        } else {
            for (String key : arguments.keySet()) {
                mVersioning.params.put(key, arguments.getString(key));
            }
            mVersioning.type = type;
        }

        attemptToApplyVersioningConfig();
        updateVersioningDescription();
        mFolderNeedsToUpdate = true;
    }

    private void updateFolderTypeDescription() {
        if (mFolder == null) {
            return;
        }

        switch (mFolder.type) {
            case Constants.FOLDER_TYPE_SEND_RECEIVE:
                setFolderTypeDescription(getString(R.string.folder_type_sendreceive),
                        getString(R.string.folder_type_sendreceive_description));
                break;
            case Constants.FOLDER_TYPE_SEND_ONLY:
                setFolderTypeDescription(getString(R.string.folder_type_sendonly),
                        getString(R.string.folder_type_sendonly_description));
                break;
            case Constants.FOLDER_TYPE_RECEIVE_ONLY:
                setFolderTypeDescription(getString(R.string.folder_type_receiveonly),
                        getString(R.string.folder_type_receiveonly_description));
                break;
        }
    }

    private void setFolderTypeDescription(String type, String description) {
        binding.folderType.setText(type);
        binding.folderTypeDescription.setText(description);
    }

    private void updatePullOrderDescription() {
        if (mFolder == null) {
            return;
        }

        if (TextUtils.isEmpty(mFolder.order)) {
            setPullOrderDescription(getString(R.string.pull_order_type_random),
                    getString(R.string.pull_order_type_random_description));
            return;
        }

        switch (mFolder.order) {
            case "random":
                setPullOrderDescription(getString(R.string.pull_order_type_random),
                        getString(R.string.pull_order_type_random_description));
                break;
            case "alphabetic":
                setPullOrderDescription(getString(R.string.pull_order_type_alphabetic),
                        getString(R.string.pull_order_type_alphabetic_description));
                break;
            case "smallestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_smallestFirst),
                        getString(R.string.pull_order_type_smallestFirst_description));
                break;
            case "largestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_largestFirst),
                        getString(R.string.pull_order_type_largestFirst_description));
                break;
            case "oldestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_oldestFirst),
                        getString(R.string.pull_order_type_oldestFirst_description));
                break;
            case "newestFirst":
                setPullOrderDescription(getString(R.string.pull_order_type_newestFirst),
                        getString(R.string.pull_order_type_newestFirst_description));
                break;
        }
    }

    private void setPullOrderDescription(String type, String description) {
        binding.pullOrderType.setText(type);
        binding.pullOrderDescription.setText(description);
    }

    private void updateVersioningDescription() {
        if (mFolder == null){
            return;
        }

        if (TextUtils.isEmpty(mFolder.versioning.type)) {
            setVersioningDescription(getString(R.string.none), "");
            return;
        }

        switch (mFolder.versioning.type) {
            case "simple":
                setVersioningDescription(getString(R.string.type_simple),
                        getString(R.string.simple_versioning_info, mFolder.versioning.params.get("keep")));
                break;
            case "trashcan":
                setVersioningDescription(getString(R.string.type_trashcan),
                        getString(R.string.trashcan_versioning_info, mFolder.versioning.params.get("cleanoutDays")));
                break;
            case "staggered":
                int maxAge = (int) TimeUnit.SECONDS.toDays(Long.valueOf(mFolder.versioning.params.get("maxAge")));
                setVersioningDescription(getString(R.string.type_staggered),
                        getString(R.string.staggered_versioning_info, maxAge, mFolder.versioning.params.get("versionsPath")));
                break;
            case "external":
                setVersioningDescription(getString(R.string.type_external),
                        getString(R.string.external_versioning_info, mFolder.versioning.params.get("command")));
                break;
        }
    }

    private void setVersioningDescription(String type, String description) {
        binding.versioningType.setText(type);
        binding.versioningDescription.setText(description);
    }
}
