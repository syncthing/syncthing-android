package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;

import java.util.LinkedList;

/**
 * Connects to {@link SyncthingService} and provides access to it.
 */
public abstract class SyncthingActivity extends ToolbarBindingActivity implements ServiceConnection {

    public static final String EXTRA_FIRST_START = "com.nutomic.syncthing-android.SyncthingActivity.FIRST_START";

    private SyncthingService mSyncthingService;
    private AlertDialog mLoadingDialog;

    private final LinkedList<OnServiceConnectedListener> mServiceConnectedListeners = new LinkedList<>();

    /**
     * To be used for Fragments.
     */
    public interface OnServiceConnectedListener {
        void onServiceConnected();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, SyncthingService.class));
    }

    @Override
    protected void onPause() {
        unbindService(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, SyncthingService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissLoadingDialog();
        if (getService() != null) {
            getService().unregisterOnApiChangeListener(this::onApiChange);
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        SyncthingServiceBinder binder = (SyncthingServiceBinder) iBinder;
        mSyncthingService = binder.getService();
        mSyncthingService.registerOnApiChangeListener(this::onApiChange);
        for (OnServiceConnectedListener listener : mServiceConnectedListeners) {
            listener.onServiceConnected();
        }
        mServiceConnectedListeners.clear();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mSyncthingService = null;
    }

    private void onApiChange(SyncthingService.State currentState) {
        switch (currentState) {
            case INIT: // fallthrough
            case STARTING:
                showLoadingDialog();
                break;
            case ACTIVE: // fallthrough
            case DISABLED:
                dismissLoadingDialog();
                break;
        }
    }

    /**
     * Used for Fragments to use the Activity's service connection.
     */
    public void registerOnServiceConnectedListener(OnServiceConnectedListener listener) {
        if (mSyncthingService != null) {
            listener.onServiceConnected();
        } else {
            mServiceConnectedListeners.addLast(listener);
        }
    }

    /**
     * Returns service object (or null if not bound).
     */
    public SyncthingService getService() {
        return mSyncthingService;
    }

    /**
     * Returns RestApi instance, or null if SyncthingService is not yet connected.
     */
    public RestApi getApi() {
        return (getService() != null)
                ? getService().getApi()
                : null;
    }

    /**
     * Shows the loading dialog with the correct text ("creating keys" or "loading").
     */
    private void showLoadingDialog() {
        if (isFinishing() || mLoadingDialog != null)
            return;

        // Try to fix WindowManager$BadTokenException crashes on Android 7.
        // https://stackoverflow.com/q/46030692/1837158
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ActivityManager.RunningAppProcessInfo myProcess = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(myProcess);
            boolean isInBackground =
                    myProcess.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            if (isInBackground)
                return;
        }

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams")
        View dialogLayout = inflater.inflate(R.layout.dialog_loading, null);
        TextView loadingText = (TextView) dialogLayout.findViewById(R.id.loading_text);
        loadingText.setText((getIntent().getBooleanExtra(EXTRA_FIRST_START, false))
                ? R.string.web_gui_creating_key
                : R.string.api_loading);

        mLoadingDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(dialogLayout)
                .show();
    }

    private void dismissLoadingDialog() {
        if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
    }

}
