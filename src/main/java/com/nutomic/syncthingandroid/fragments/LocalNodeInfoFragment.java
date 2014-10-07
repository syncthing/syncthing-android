package com.nutomic.syncthingandroid.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
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
 * Displays information about the local node.
 */
public class LocalNodeInfoFragment extends Fragment
        implements RestApi.OnReceiveSystemInfoListener, RestApi.OnReceiveConnectionsListener {

    private TextView mNodeId;

    private TextView mCpuUsage;

    private TextView mRamUsage;

    private TextView mDownload;

    private TextView mUpload;

    private TextView mAnnounceServer;

    private Timer mTimer;

    private MainActivity mActivity;

    /**
     * Starts polling for status when opened, stops when closed.
     */
    public class Toggle extends ActionBarDrawerToggle {
        public Toggle(Activity activity, DrawerLayout drawerLayout, int drawerImageRes) {
            super(activity, drawerLayout, drawerImageRes, R.string.app_name, R.string.system_info);
        }

        @Override
        public void onDrawerClosed(View view) {
            super.onDrawerClosed(view);
            mTimer.cancel();
            mTimer = null;
            mActivity.getSupportActionBar().setTitle(R.string.app_name);
            mActivity.supportInvalidateOptionsMenu();
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            super.onDrawerOpened(drawerView);
            LocalNodeInfoFragment.this.onDrawerOpened();
        }
    }

    ;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.local_node_info_fragment, container, false);
        mNodeId = (TextView) view.findViewById(R.id.node_id);
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

    private void onDrawerOpened() {
        // FIXME: never called
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateGui();
            }

        }, 0, SyncthingService.GUI_UPDATE_INTERVAL);
        mActivity.getSupportActionBar().setTitle(R.string.system_info);
        mActivity.supportInvalidateOptionsMenu();
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

        mNodeId.setText(info.myID);
        mNodeId.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mActivity.getApi().copyNodeId(mNodeId.getText().toString());
                view.performClick();
                return true;
            }
        });
        mCpuUsage.setText(new DecimalFormat("0.00").format(info.cpuPercent) + "%");
        mRamUsage.setText(RestApi.readableFileSize(mActivity, info.sys));
        if (info.extAnnounceOK) {
            mAnnounceServer.setText("Online");
            mAnnounceServer.setTextColor(getResources().getColor(R.color.text_green));
        } else {
            mAnnounceServer.setText("Offline");
            mAnnounceServer.setTextColor(getResources().getColor(R.color.text_red));
        }
    }

    /**
     * Populates views with status received via {@link RestApi#getConnections}.
     */
    @Override
    public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
        RestApi.Connection c = connections.get(RestApi.LOCAL_NODE_CONNECTIONS);
        mDownload.setText(RestApi.readableTransferRate(mActivity, c.InBits));
        mUpload.setText(RestApi.readableTransferRate(mActivity, c.OutBits));
    }

    /**
     * Shares the local node ID when "share" is clicked.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share_node_id:
                RestApi.shareNodeId(getActivity(), mNodeId.getText().toString());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
