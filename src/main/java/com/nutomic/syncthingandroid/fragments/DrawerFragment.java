package com.nutomic.syncthingandroid.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.WebGuiActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays information about the local device.
 */
public class DrawerFragment extends Fragment implements RestApi.OnReceiveSystemInfoListener,
        RestApi.OnReceiveConnectionsListener, ListView.OnItemClickListener {

    private TextView mDeviceId;

    private TextView mCpuUsage;

    private TextView mRamUsage;

    private TextView mDownload;

    private TextView mUpload;

    private TextView mAnnounceServer;

    private ListView mList;

    private Timer mTimer;

    private MainActivity mActivity;

    /**
     * Displays menu items.
     */
    private class MenuAdapter extends ArrayAdapter<Pair<Integer, Integer>> {

        public MenuAdapter(Context context, List<Pair<Integer, Integer>> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.menu_item, parent, false);
            }

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageResource(getItem(position).first);
            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(getItem(position).second);
            return convertView;
        }
    }

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
        initMenu();
    }

    public void onDrawerClosed() {
        mTimer.cancel();
        mTimer = null;
    }

    /**
     * Populates views and menu.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view       = inflater.inflate(R.layout.drawer_fragment, container, false);
        mDeviceId       = (TextView) view.findViewById(R.id.device_id);
        mCpuUsage       = (TextView) view.findViewById(R.id.cpu_usage);
        mRamUsage       = (TextView) view.findViewById(R.id.ram_usage);
        mDownload       = (TextView) view.findViewById(R.id.download);
        mUpload         = (TextView) view.findViewById(R.id.upload);
        mAnnounceServer = (TextView) view.findViewById(R.id.announce_server);
        mList           = (ListView) view.findViewById(android.R.id.list);

        initMenu();
        mList.setOnItemClickListener(this);

        return view;
    }

    /**
     * Repopulates menu items.
     */
    private void initMenu() {
        MenuAdapter adapter =
                new MenuAdapter(getActivity(), new ArrayList<Pair<Integer, Integer>>());
        adapter.add(new Pair<>(R.drawable.ic_action_share, R.string.share_device_id));
        adapter.add(new Pair<>(R.drawable.ic_menu_browser, R.string.web_gui_title));
        adapter.add(new Pair<>(R.drawable.ic_action_settings, R.string.settings_title));
        adapter.add(new Pair<>(R.drawable.ic_action_donate, R.string.donate));
        if (!SyncthingService.alwaysRunInBackground(getActivity()))
            adapter.add(new Pair<>(R.drawable.ic_menu_close_clear_cancel, R.string.exit));

        mList.setAdapter(adapter);
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
     * Handles menu item clicks.
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        switch (i) {
            case 0:
                RestApi.shareDeviceId(getActivity(), mDeviceId.getText().toString());
                break;
            case 1:
                startActivity(new Intent(mActivity, WebGuiActivity.class));
                mActivity.closeDrawer();
                break;
            case 2:
                startActivity(new Intent(mActivity, SettingsActivity.class)
                        .setAction(SettingsActivity.ACTION_APP_SETTINGS_FRAGMENT));
                mActivity.closeDrawer();
                break;
            case 3:
                startActivity(new Intent(
                        Intent.ACTION_VIEW, Uri.parse(getString(R.string.donate_url))));
                mActivity.closeDrawer();
                break;
            case 4:
                mActivity.getService().getApi().shutdown();
                mActivity.stopService(new Intent(mActivity, SyncthingService.class));
                mActivity.finish();
                mActivity.closeDrawer();
                break;
        }
    }
}
