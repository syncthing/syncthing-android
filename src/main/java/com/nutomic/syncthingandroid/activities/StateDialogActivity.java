package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

/**
 * Handles loading/disabled dialogs.
 */
public abstract class StateDialogActivity extends SyncthingActivity {

    private static final String TAG = "StateDialogActivity";

    private AlertDialog mLoadingDialog;
    private AlertDialog mDisabledDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerOnServiceConnectedListener(() ->
                getService().registerOnApiChangeListener(this::onApiChange));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (getService() != null) {
            getService().unregisterOnApiChangeListener(this::onApiChange);
        }
        dismissDisabledDialog();
    }

    private void onApiChange(SyncthingService.State currentState) {
        switch (currentState) {
            case INIT: // fallthrough
            case STARTING:
                dismissDisabledDialog();
                showLoadingDialog();
                break;
            case ACTIVE:
                dismissDisabledDialog();
                dismissLoadingDialog();
                break;
            case DISABLED:
                dismissLoadingDialog();
                if (!isFinishing()) {
                    showDisabledDialog();
                }
                break;
        }
    }

    private void showDisabledDialog() {
        mDisabledDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.syncthing_disabled_title)
                .setMessage(R.string.syncthing_disabled_message)
                .setPositiveButton(R.string.syncthing_disabled_change_settings,
                        (dialogInterface, i) -> {
                            finish();
                            startActivity(new Intent(this, SettingsActivity.class));
                        }
                )
                .setNegativeButton(R.string.exit,
                        (dialogInterface, i) -> ActivityCompat.finishAffinity(this)
                )
                .setCancelable(false)
                .show();
    }

    private void dismissDisabledDialog() {
        Util.dismissDialogSafe(mDisabledDialog, this);
        mDisabledDialog = null;
    }

    /**
     * Shows the loading dialog with the correct text ("creating keys" or "loading").
     */
    private void showLoadingDialog() {
        if (isFinishing() || mLoadingDialog != null)
            return;

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams")
        View dialogLayout = inflater.inflate(R.layout.dialog_loading, null);
        TextView loadingText = dialogLayout.findViewById(R.id.loading_text);
        loadingText.setText((getIntent().getBooleanExtra(EXTRA_FIRST_START, false))
                ? R.string.web_gui_creating_key
                : R.string.api_loading);

        try {
            mLoadingDialog = new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setView(dialogLayout)
                    .show();
        } catch (RuntimeException e) {
            // Catch and do nothing, workaround for https://stackoverflow.com/q/46030692/1837158
            Log.w(TAG, e);
        }
    }

    private void dismissLoadingDialog() {
        Util.dismissDialogSafe(mLoadingDialog, this);
        mLoadingDialog = null;
    }
}
