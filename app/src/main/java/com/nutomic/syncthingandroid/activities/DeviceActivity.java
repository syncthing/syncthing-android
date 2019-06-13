package com.nutomic.syncthingandroid.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Compression;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;
import com.nutomic.syncthingandroid.util.Util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static android.text.TextUtils.isEmpty;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static com.nutomic.syncthingandroid.service.SyncthingService.State.ACTIVE;
import static com.nutomic.syncthingandroid.util.Compression.METADATA;

/**
 * Shows device details and allows changing them.
 */
public class DeviceActivity extends SyncthingActivity implements View.OnClickListener {

    public static final String EXTRA_NOTIFICATION_ID =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.NOTIFICATION_ID";
    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.DEVICE_ID";
    public static final String EXTRA_DEVICE_NAME =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.DEVICE_NAME";
    public static final String EXTRA_IS_CREATE =
            "com.nutomic.syncthingandroid.activities.DeviceActivity.IS_CREATE";

    private static final String TAG = "DeviceSettingsFragment";
    private static final String IS_SHOWING_DISCARD_DIALOG = "DISCARD_FOLDER_DIALOG_STATE";
    private static final String IS_SHOWING_COMPRESSION_DIALOG = "COMPRESSION_FOLDER_DIALOG_STATE";
    private static final String IS_SHOWING_DELETE_DIALOG = "DELETE_FOLDER_DIALOG_STATE";

    private static final List<String> DYNAMIC_ADDRESS = Collections.singletonList("dynamic");

    private Device mDevice;

    private View mIdContainer;

    private EditText mIdView;

    private View mQrButton;

    private EditText mNameView;

    private EditText mAddressesView;

    private TextView mCurrentAddressView;

    private TextView mCompressionValueView;

    private SwitchCompat mIntroducerView;

    private SwitchCompat mDevicePaused;

    private TextView mSyncthingVersionView;

    private View mCompressionContainer;

    private boolean mIsCreateMode;

    private boolean mDeviceNeedsToUpdate;

    private Dialog mDeleteDialog;
    private Dialog mDiscardDialog;
    private Dialog mCompressionDialog;

    private final DialogInterface.OnClickListener mCompressionEntrySelectedListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            Compression compression = Compression.fromIndex(which);
            // Don't pop the restart dialog unless the value is actually different.
            if (compression != Compression.fromValue(DeviceActivity.this, mDevice.compression)) {
                mDeviceNeedsToUpdate = true;

                mDevice.compression = compression.getValue(DeviceActivity.this);
                mCompressionValueView.setText(compression.getTitle(DeviceActivity.this));
            }
        }
    };

    private final TextWatcher mIdTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(mDevice.deviceID)) {
                mDeviceNeedsToUpdate = true;
                mDevice.deviceID = s.toString();
            }
        }
    };

    private final TextWatcher mNameTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(mDevice.name)) {
                mDeviceNeedsToUpdate = true;
                mDevice.name = s.toString();
            }
        }
    };

    private final TextWatcher mAddressesTextWatcher = new TextWatcherAdapter() {
        @Override
        public void afterTextChanged(Editable s) {
            if (!s.toString().equals(displayableAddresses())) {
                mDeviceNeedsToUpdate = true;
                mDevice.addresses = persistableAddresses(s);
            }
        }
    };

    private final CompoundButton.OnCheckedChangeListener mCheckedListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.introducer:
                    mDevice.introducer = isChecked;
                    mDeviceNeedsToUpdate = true;
                    break;
                case R.id.devicePause:
                    mDevice.paused = isChecked;
                    mDeviceNeedsToUpdate = true;
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_device);

        mIsCreateMode = getIntent().getBooleanExtra(EXTRA_IS_CREATE, false);
        registerOnServiceConnectedListener(this::onServiceConnected);
        setTitle(mIsCreateMode ? R.string.add_device : R.string.edit_device);

        mIdContainer = findViewById(R.id.idContainer);
        mIdView = findViewById(R.id.id);
        mQrButton = findViewById(R.id.qrButton);
        mNameView = findViewById(R.id.name);
        mAddressesView = findViewById(R.id.addresses);
        mCurrentAddressView = findViewById(R.id.currentAddress);
        mCompressionContainer = findViewById(R.id.compressionContainer);
        mCompressionValueView = findViewById(R.id.compressionValue);
        mIntroducerView = findViewById(R.id.introducer);
        mDevicePaused = findViewById(R.id.devicePause);
        mSyncthingVersionView = findViewById(R.id.syncthingVersion);

        mQrButton.setOnClickListener(this);
        mCompressionContainer.setOnClickListener(this);

        if (savedInstanceState != null){
            if (mDevice == null) {
                mDevice = new Gson().fromJson(savedInstanceState.getString("device"), Device.class);
            }
            restoreDialogStates(savedInstanceState);
        }
        if (mIsCreateMode) {
           if (mDevice == null) {
                initDevice();
            }
        }
        else {
            prepareEditMode();
        }
    }

    private void restoreDialogStates(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean(IS_SHOWING_COMPRESSION_DIALOG)){
            showCompressionDialog();
        }

        if (savedInstanceState.getBoolean(IS_SHOWING_DELETE_DIALOG)){
            showDeleteDialog();
        }

        if (mIsCreateMode){
            if (savedInstanceState.getBoolean(IS_SHOWING_DISCARD_DIALOG)){
                showDiscardDialog();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SyncthingService syncthingService = getService();
        if (syncthingService != null) {
            syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
            syncthingService.unregisterOnServiceStateChangeListener(this::onServiceStateChange);
        }
        mIdView.removeTextChangedListener(mIdTextWatcher);
        mNameView.removeTextChangedListener(mNameTextWatcher);
        mAddressesView.removeTextChangedListener(mAddressesTextWatcher);
    }

    @Override
    public void onPause() {
        super.onPause();

        // We don't want to update every time a TextView's character changes,
        // so we hold off until the view stops being visible to the user.
        if (mDeviceNeedsToUpdate) {
            updateDevice();
        }
    }

    /**
     * Save current settings in case we are in create mode and they aren't yet stored in the config.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("device", new Gson().toJson(mDevice));
        if (mIsCreateMode){
            outState.putBoolean(IS_SHOWING_DISCARD_DIALOG, mDiscardDialog != null && mDiscardDialog.isShowing());
            Util.dismissDialogSafe(mDiscardDialog, this);
        }

        outState.putBoolean(IS_SHOWING_COMPRESSION_DIALOG, mCompressionDialog != null && mCompressionDialog.isShowing());
        Util.dismissDialogSafe(mCompressionDialog, this);

        outState.putBoolean(IS_SHOWING_DELETE_DIALOG, mDeleteDialog != null && mDeleteDialog.isShowing());
        Util.dismissDialogSafe(mDeleteDialog, this);
    }

    private void onServiceConnected() {
        Log.v(TAG, "onServiceConnected");
        SyncthingService syncthingService = (SyncthingService) getService();
        syncthingService.getNotificationHandler().cancelConsentNotification(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, 0));
        syncthingService.registerOnServiceStateChangeListener(this::onServiceStateChange);
    }

    /**
     * Sets version and current address of the device.
     * <p/>
     * NOTE: This is only called once on startup, should be called more often to properly display
     * version/address changes.
     */
    private void onReceiveConnections(Connections connections) {
        boolean viewsExist = mSyncthingVersionView != null && mCurrentAddressView != null;
        if (viewsExist && connections.connections.containsKey(mDevice.deviceID)) {
            mCurrentAddressView.setVisibility(VISIBLE);
            mSyncthingVersionView.setVisibility(VISIBLE);
            mCurrentAddressView.setText(connections.connections.get(mDevice.deviceID).address);
            mSyncthingVersionView.setText(connections.connections.get(mDevice.deviceID).clientVersion);
        }
    }

    private void onServiceStateChange(SyncthingService.State currentState) {
        if (currentState != ACTIVE) {
            finish();
            return;
        }

        if (!mIsCreateMode) {
            List<Device> devices = getApi().getDevices(false);
            mDevice = null;
            for (Device device : devices) {
                if (device.deviceID.equals(getIntent().getStringExtra(EXTRA_DEVICE_ID))) {
                    mDevice = device;
                    break;
                }
            }
            if (mDevice == null) {
                Log.w(TAG, "Device not found in API update, maybe it was deleted?");
                finish();
                return;
            }
        }

        getApi().getConnections(this::onReceiveConnections);

        updateViewsAndSetListeners();
    }

    private void updateViewsAndSetListeners() {
        mIdView.removeTextChangedListener(mIdTextWatcher);
        mNameView.removeTextChangedListener(mNameTextWatcher);
        mAddressesView.removeTextChangedListener(mAddressesTextWatcher);
        mIntroducerView.setOnCheckedChangeListener(null);
        mDevicePaused.setOnCheckedChangeListener(null);

        // Update views
        mIdView.setText(mDevice.deviceID);
        mNameView.setText(mDevice.name);
        mAddressesView.setText(displayableAddresses());
        mCompressionValueView.setText(Compression.fromValue(this, mDevice.compression).getTitle(this));
        mIntroducerView.setChecked(mDevice.introducer);
        mDevicePaused.setChecked(mDevice.paused);

        // Keep state updated
        mIdView.addTextChangedListener(mIdTextWatcher);
        mNameView.addTextChangedListener(mNameTextWatcher);
        mAddressesView.addTextChangedListener(mAddressesTextWatcher);
        mIntroducerView.setOnCheckedChangeListener(mCheckedListener);
        mDevicePaused.setOnCheckedChangeListener(mCheckedListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreateMode);
        menu.findItem(R.id.share_device_id).setVisible(!mIsCreateMode);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (isEmpty(mDevice.deviceID)) {
                    Toast.makeText(this, R.string.device_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                getApi().addDevice(mDevice, error ->
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show());
                finish();
                return true;
            case R.id.share_device_id:
                shareDeviceId(this, mDevice.deviceID);
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
                .setMessage(R.string.remove_device_confirm)
                .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
                    getApi().removeDevice(mDevice.deviceID);
                    finish();
                })
                .setNegativeButton(android.R.string.no, null)
                .create();
    }

    /**
     * Receives value of scanned QR code and sets it as device ID.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            mDevice.deviceID = scanResult.getContents();
            mIdView.setText(mDevice.deviceID);
        }
    }

    private void initDevice() {
        mDevice = new Device();
        mDevice.name = getIntent().getStringExtra(EXTRA_DEVICE_NAME);
        mDevice.deviceID = getIntent().getStringExtra(EXTRA_DEVICE_ID);
        mDevice.addresses = DYNAMIC_ADDRESS;
        mDevice.compression = METADATA.getValue(this);
        mDevice.introducer = false;
        mDevice.paused = false;
    }

    private void prepareEditMode() {
        getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Drawable dr = ContextCompat.getDrawable(this, R.drawable.ic_content_copy_24dp);
        mIdView.setCompoundDrawablesWithIntrinsicBounds(null, null, dr, null);
        mIdView.setEnabled(false);
        mQrButton.setVisibility(GONE);

        mIdContainer.setOnClickListener(this);
    }

    /**
     * Sends the updated device info if in edit mode.
     */
    private void updateDevice() {
        if (!mIsCreateMode && mDeviceNeedsToUpdate && mDevice != null) {
            getApi().editDevice(mDevice);
        }
    }

    private List<String> persistableAddresses(CharSequence userInput) {
        return isEmpty(userInput)
                ? DYNAMIC_ADDRESS
                : Arrays.asList(userInput.toString().split(" "));
    }

    private String displayableAddresses() {
        List<String> list = DYNAMIC_ADDRESS.equals(mDevice.addresses)
                ? DYNAMIC_ADDRESS
                : mDevice.addresses;
        return TextUtils.join(" ", list);
    }

    @Override
    public void onClick(View v) {
        if (v.equals(mCompressionContainer)) {
            showCompressionDialog();
        } else if (v.equals(mQrButton)){
            IntentIntegrator integrator = new IntentIntegrator(DeviceActivity.this);
            integrator.initiateScan();
        } else if (v.equals(mIdContainer)) {
            Util.copyDeviceId(this, mDevice.deviceID);
        }
    }

    private void showCompressionDialog(){
        mCompressionDialog = createCompressionDialog();
        mCompressionDialog.show();
    }

    private Dialog createCompressionDialog(){
        return Util.getAlertDialogBuilder(this)
                .setTitle(R.string.compression)
                .setSingleChoiceItems(R.array.compress_entries,
                        Compression.fromValue(this, mDevice.compression).getIndex(),
                        mCompressionEntrySelectedListener)
                .create();
    }

    /**
     * Shares the given device ID via Intent. Must be called from an Activity.
     */
    private void shareDeviceId(Context context, String id) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, id);
        context.startActivity(Intent.createChooser(
                shareIntent, context.getString(R.string.send_device_id_to)));
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
}
