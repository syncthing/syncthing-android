package com.nutomic.syncthingandroid.fragments;

import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.Compression;
import com.nutomic.syncthingandroid.util.TextWatcherAdapter;
import com.nutomic.syncthingandroid.util.Util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static android.text.TextUtils.isEmpty;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static com.nutomic.syncthingandroid.syncthing.SyncthingService.State.ACTIVE;
import static com.nutomic.syncthingandroid.util.Compression.METADATA;

/**
 * Shows device details and allows changing them.
 */
public class DeviceFragment extends Fragment implements
        SyncthingActivity.OnServiceConnectedListener, RestApi.OnReceiveConnectionsListener,
        SyncthingService.OnApiChangeListener, RestApi.OnDeviceIdNormalizedListener,
        View.OnClickListener {

    public static final String EXTRA_DEVICE_ID =
            "com.nutomic.syncthingandroid.fragments.DeviceFragment.DEVICE_ID";

    private static final String TAG = "DeviceSettingsFragment";

    public static final List<String> DYNAMIC_ADDRESS = Collections.singletonList("dynamic");

    private SyncthingService mSyncthingService;

    private RestApi.Device mDevice;

    private View mIdContainer;

    private EditText mIdView;

    private View mQrButton;

    private EditText mNameView;

    private EditText mAddressesView;

    private TextView mCurrentAddressView;

    private TextView mCompressionValueView;

    private SwitchCompat mIntroducerView;

    private TextView mSyncthingVersionView;

    private View mCompressionContainer;

    private boolean mIsCreateMode;

    private boolean mDeviceNeedsToUpdate;

    private final DialogInterface.OnClickListener mCompressionEntrySelectedListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();

            Compression compression = Compression.fromIndex(which);
            // Don't pop the restart dialog unless the value is actually different.
            if (compression != Compression.fromValue(getActivity(), mDevice.compression)) {
                mDeviceNeedsToUpdate = true;

                mDevice.compression = compression.getValue(getActivity());
                mCompressionValueView.setText(compression.getTitle(getActivity()));
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

    private final CompoundButton.OnCheckedChangeListener mIntroducerCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mDevice.introducer != isChecked) {
                mDeviceNeedsToUpdate = true;

                mDevice.introducer = isChecked;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsActivity activity = (SettingsActivity) getActivity();
        mIsCreateMode = activity.getIsCreate();
        activity.registerOnServiceConnectedListener(this);
        activity.setTitle(mIsCreateMode ? R.string.add_device : R.string.edit_device);
        setHasOptionsMenu(true);

        if (mIsCreateMode) {
            if (savedInstanceState != null) {
                mDevice = (RestApi.Device) savedInstanceState.getSerializable("device");
            }
            if (mDevice == null) {
                initDevice();
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
        outState.putSerializable("device", mDevice);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_device, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mIdContainer = view.findViewById(R.id.idContainer);
        mIdView = (EditText) view.findViewById(R.id.id);
        mQrButton = view.findViewById(R.id.qrButton);
        mNameView = (EditText) view.findViewById(R.id.name);
        mAddressesView = (EditText) view.findViewById(R.id.addresses);
        mCurrentAddressView = (TextView) view.findViewById(R.id.currentAddress);
        mCompressionContainer = view.findViewById(R.id.compressionContainer);
        mCompressionValueView = (TextView) view.findViewById(R.id.compressionValue);
        mIntroducerView = (SwitchCompat) view.findViewById(R.id.introducer);
        mSyncthingVersionView = (TextView) view.findViewById(R.id.syncthingVersion);

        mQrButton.setOnClickListener(this);
        mCompressionContainer.setOnClickListener(this);

        if (!mIsCreateMode) {
            prepareEditMode();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mIdView.removeTextChangedListener(mIdTextWatcher);
        mNameView.removeTextChangedListener(mNameTextWatcher);
        mAddressesView.removeTextChangedListener(mAddressesTextWatcher);
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    /**
     * Sets version and current address of the device.
     * <p/>
     * NOTE: This is only called once on startup, should be called more often to properly display
     * version/address changes.
     */
    @Override
    public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
        boolean viewsExist = mSyncthingVersionView != null && mCurrentAddressView != null;
        if (viewsExist && connections.containsKey(mDevice.deviceID)) {
            mCurrentAddressView.setVisibility(VISIBLE);
            mSyncthingVersionView.setVisibility(VISIBLE);
            mCurrentAddressView.setText(connections.get(mDevice.deviceID).address);
            mSyncthingVersionView.setText(connections.get(mDevice.deviceID).clientVersion);
        }
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != ACTIVE) {
            getActivity().finish();
            return;
        }

        if (!mIsCreateMode) {
            List<RestApi.Device> devices = mSyncthingService.getApi().getDevices(false);
            mDevice = null;
            for (RestApi.Device device : devices) {
                if (device.deviceID.equals(
                        getActivity().getIntent().getStringExtra(EXTRA_DEVICE_ID))) {
                    mDevice = device;
                    break;
                }
            }
            if (mDevice == null) {
                Log.w(TAG, "Device not found in API update, maybe it was deleted?");
                getActivity().finish();
                return;
            }
        }

        mSyncthingService.getApi().getConnections(this);

        updateViewsAndSetListeners();
    }

    private void updateViewsAndSetListeners() {
        // Update views
        mIdView.setText(mDevice.deviceID);
        mNameView.setText(mDevice.name);
        mAddressesView.setText(displayableAddresses());
        mCompressionValueView.setText(Compression.fromValue(getActivity(), mDevice.compression).getTitle(getActivity()));
        mIntroducerView.setChecked(mDevice.introducer);

        // Keep state updated
        mIdView.addTextChangedListener(mIdTextWatcher);
        mNameView.addTextChangedListener(mNameTextWatcher);
        mAddressesView.addTextChangedListener(mAddressesTextWatcher);
        mIntroducerView.setOnCheckedChangeListener(mIntroducerCheckedChangeListener);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.device_settings, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreateMode);
        menu.findItem(R.id.share_device_id).setVisible(!mIsCreateMode);
        menu.findItem(R.id.remove).setVisible(!mIsCreateMode);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (isEmpty(mDevice.deviceID)) {
                    Toast.makeText(getActivity(), R.string.device_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                mSyncthingService.getApi().editDevice(mDevice, getActivity(), this);
                getActivity().finish();
                return true;
            case R.id.share_device_id:
                shareDeviceId(getActivity(), mDevice.deviceID);
                return true;
            case R.id.remove:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.remove_device_confirm)
                        .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> mSyncthingService.getApi().deleteDevice(mDevice, getActivity()))
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

    private void initDevice() {
        mDevice = new RestApi.Device();
        mDevice.name = "";
        mDevice.deviceID = getActivity().getIntent().getStringExtra(EXTRA_DEVICE_ID);
        mDevice.addresses = DYNAMIC_ADDRESS;
        mDevice.compression = METADATA.getValue(getActivity());
        mDevice.introducer = false;
    }

    private void prepareEditMode() {
        getActivity().getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        Drawable dr = ContextCompat.getDrawable(getActivity(), R.drawable.ic_content_copy_black_24dp);
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
            mSyncthingService.getApi().editDevice(mDevice, getActivity(), this);
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
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.compression)
                    .setSingleChoiceItems(R.array.compress_entries,
                            Compression.fromValue(getActivity(), mDevice.compression).getIndex(),
                            mCompressionEntrySelectedListener)
                    .show();
        } else if (v.equals(mQrButton)){
            IntentIntegrator integrator = new IntentIntegrator(DeviceFragment.this);
            integrator.initiateScan();
        } else if (v.equals(mIdContainer)) {
            Util.copyDeviceId(getActivity(), mDevice.deviceID);
        }
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
}
