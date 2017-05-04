package com.nutomic.syncthingandroid.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.model.Connections;
import com.nutomic.syncthingandroid.model.SystemInfo;
import com.nutomic.syncthingandroid.model.SystemVersion;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays information about the local device.
 */
public class DrawerFragment extends Fragment implements View.OnClickListener {

    private TextView mDeviceId;
    private TextView mCpuUsage;
    private TextView mRamUsage;
    private TextView mDownload;
    private TextView mUpload;
    private TextView mAnnounceServer;
    private TextView mVersion;
    private TextView mExitButton;

    private Timer mTimer;

    private MainActivity mActivity;

    public void onDrawerOpened() {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateGui();
            }

        }, 0, SyncthingService.GUI_UPDATE_INTERVAL);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateExitButtonVisibility();
    }

    public void onDrawerClosed() {
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onDrawerClosed();
    }

    /**
     * Populates views and menu.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drawer, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mDeviceId       = (TextView) view.findViewById(R.id.device_id);
        mCpuUsage       = (TextView) view.findViewById(R.id.cpu_usage);
        mRamUsage       = (TextView) view.findViewById(R.id.ram_usage);
        mDownload       = (TextView) view.findViewById(R.id.download);
        mUpload         = (TextView) view.findViewById(R.id.upload);
        mAnnounceServer = (TextView) view.findViewById(R.id.announce_server);
        mVersion        = (TextView) view.findViewById(R.id.version);
        mExitButton     = (TextView) view.findViewById(R.id.drawerActionExit);

        view.findViewById(R.id.drawerActionWebGui)
                .setOnClickListener(this);
        view.findViewById(R.id.drawerActionShareId)
                .setOnClickListener(this);
        view.findViewById(R.id.drawerActionRestart)
                .setOnClickListener(this);
        view.findViewById(R.id.drawerActionSettings)
                .setOnClickListener(this);
        mExitButton.setOnClickListener(this);

        updateExitButtonVisibility();
    }

    private void updateExitButtonVisibility() {
        boolean alwaysInBackground = SyncthingService.alwaysRunInBackground(getActivity());
        mExitButton.setVisibility(alwaysInBackground ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (MainActivity) getActivity();

        if (savedInstanceState != null && savedInstanceState.getBoolean("active")) {
            onDrawerOpened();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("active", mTimer != null);
    }

    /**
     * Invokes status callbacks.
     */
    private void updateGui() {
        if (mActivity.getApi() == null || getActivity() == null || getActivity().isFinishing())
            return;
        mActivity.getApi().getSystemInfo(this::onReceiveSystemInfo);
        mActivity.getApi().getSystemVersion(this::onReceiveSystemVersion);
        mActivity.getApi().getConnections(this::onReceiveConnections);
    }

    /**
     * This will not do anything if gui updates are already scheduled.
     */
    public void requestGuiUpdate() {
        if (mTimer == null) {
            updateGui();
        }
    }

    /**
     * Populates views with status received via {@link RestApi#getSystemInfo}.
     */
    public void onReceiveSystemInfo(SystemInfo info) {
        if (getActivity() == null)
            return;

        mDeviceId.setText(info.myID);
        mDeviceId.setOnClickListener(v -> Util.copyDeviceId(getActivity(), mDeviceId.getText().toString()));
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(2);
        mCpuUsage.setText(percentFormat.format(info.cpuPercent / 100));
        mRamUsage.setText(Util.readableFileSize(mActivity, info.sys));
        int announceTotal = info.discoveryMethods;
        int announceConnected =
                announceTotal - Optional.fromNullable(info.discoveryErrors).transform(Map::size).or(0);
        mAnnounceServer.setText(String.format(Locale.getDefault(), "%1$d/%2$d",
                                              announceConnected, announceTotal));
        int color = (announceConnected > 0)
                ? R.color.text_green
                : R.color.text_red;
        mAnnounceServer.setTextColor(ContextCompat.getColor(getContext(), color));
    }

    /**
     * Populates views with status received via {@link RestApi#getSystemInfo}.
     */
    public void onReceiveSystemVersion(SystemVersion info) {
        if (getActivity() == null)
            return;

        mVersion.setText(info.version);
    }

    /**
     * Populates views with status received via {@link RestApi#getConnections}.
     */
    private void onReceiveConnections(Connections connections) {
        Connections.Connection c = connections.total;
        mDownload.setText(Util.readableTransferRate(mActivity, c.inBits));
        mUpload.setText(Util.readableTransferRate(mActivity, c.outBits));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.drawerActionWebGui:
                startActivity(new Intent(mActivity, WebGuiActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionShareId:
                Intent i = new Intent(android.content.Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(android.content.Intent.EXTRA_TEXT, mDeviceId.getText());
                startActivity(Intent.createChooser(i, "Share device ID with"));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionSettings:
                startActivity(new Intent(mActivity, SettingsActivity.class));
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionRestart:
                mActivity.showRestartDialog();
                mActivity.closeDrawer();
                break;
            case R.id.drawerActionExit:
                mActivity.stopService(new Intent(mActivity, SyncthingService.class));
                mActivity.finish();
                mActivity.closeDrawer();
                break;
        }
    }
}
