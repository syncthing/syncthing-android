package com.nutomic.syncthingandroid.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays information about the local device.
 */
public class LocalDeviceInfoFragment extends Fragment
        implements RestApi.OnReceiveSystemInfoListener, RestApi.OnReceiveConnectionsListener {

    private TextView mDeviceId;

    private TextView mCpuUsage;

    private TextView mRamUsage;

    private TextView mDownload;

    private TextView mUpload;

    private TextView mAnnounceServer;

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
        mActivity.supportInvalidateOptionsMenu();
    }

    public void onDrawerClosed() {
        mTimer.cancel();
        mTimer = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.local_device_info_fragment, container, false);
        mDeviceId = (TextView) view.findViewById(R.id.device_id);
        mCpuUsage = (TextView) view.findViewById(R.id.cpu_usage);
        mRamUsage = (TextView) view.findViewById(R.id.ram_usage);
        mDownload = (TextView) view.findViewById(R.id.download);
        mUpload = (TextView) view.findViewById(R.id.upload);
        mAnnounceServer = (TextView) view.findViewById(R.id.announce_server);

        return view;
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
        if (mActivity.getApi() != null) {
            mActivity.getApi().getSystemInfo(this);
            mActivity.getApi().getConnections(this);
        }
    }

    /**
     * Populates views with status received via {@link RestApi#getSystemInfo}.
     */
    @Override
    public void onReceiveSystemInfo(RestApi.SystemInfo info) {
        if (getActivity() == null)
            return;

        mDeviceId.setText(info.myID);
        mDeviceId.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mActivity.getApi().copyDeviceId(mDeviceId.getText().toString());
                view.performClick();
                return true;
            }
        });
        mCpuUsage.setText(new DecimalFormat("0.00").format(info.cpuPercent) + "%");
        mRamUsage.setText(RestApi.readableFileSize(mActivity, info.sys));
        if (info.extAnnounceConnected == info.extAnnounceTotal) {
            mAnnounceServer.setText(android.R.string.ok);
            mAnnounceServer.setTextColor(getResources().getColor(R.color.text_green));
        } else {
            mAnnounceServer.setText(Integer.toString(info.extAnnounceConnected) + "/" +
                    Integer.toString(info.extAnnounceTotal));
            mAnnounceServer.setTextColor(getResources().getColor(R.color.text_red));
        }
    }

    /**
     * Populates views with status received via {@link RestApi#getConnections}.
     */
    @Override
    public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
        RestApi.Connection c = connections.get(RestApi.LOCAL_DEVICE_CONNECTIONS);
        mDownload.setText(RestApi.readableTransferRate(mActivity, c.InBits));
        mUpload.setText(RestApi.readableTransferRate(mActivity, c.OutBits));
    }

    /**
     * Shares the local device ID when "share" is clicked.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share_device_id:
                RestApi.shareDeviceId(getActivity(), mDeviceId.getText().toString());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
