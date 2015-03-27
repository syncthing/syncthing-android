package com.nutomic.syncthingandroid.dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;

/**
 * Helper functions to start dialogs on the UI thread using an Async Task.
 */
public class AsyncDialogs {

    /**
     * Dialog to be shown when attempting to start Syncthing while it is disabled according
     * to settings (because the device is not charging or wifi is disconnected).
     */
    public static AsyncTask showDisabledDialog(final Activity activity) {
        AsyncTask a = new AsyncTask() {
            AlertDialog dialog;

            @Override
            protected void onPreExecute() {
                if (activity.isFinishing())
                    return;
                dialog = new AlertDialog.Builder(activity)
                        .setTitle(R.string.syncthing_disabled_title)
                        .setMessage(R.string.syncthing_disabled_message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.syncthing_disabled_change_settings,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        activity.finish();
                                        Intent intent = new Intent(activity, SettingsActivity.class)
                                                .setAction(SettingsActivity.ACTION_APP_SETTINGS_FRAGMENT);
                                        activity.startActivity(intent);
                                    }
                                }
                        )
                        .setNegativeButton(R.string.exit,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        activity.finish();
                                    }
                                }
                        )
                        .show();
            }
            @Override
            protected Object doInBackground(Object[] params) {
                return null;
            }
            @Override
            protected void onCancelled() {
                if (dialog.isShowing())
                    dialog.dismiss();
            }
        };
        return a.execute();
    }

    /**
     * Dialog to be shown when Syncthing is loading and we're waiting for the API.
     */
    public static AsyncTask showLoadingDialog(final Activity activity, final boolean isCreating) {
        AsyncTask a = new AsyncTask() {
            AlertDialog dialog;

            @Override
            protected void onPreExecute() {
                if (activity.isFinishing())
                    return;

                LayoutInflater inflater = activity.getLayoutInflater();
                View dialogLayout = inflater.inflate(R.layout.loading_dialog, null);
                TextView loadingText = (TextView) dialogLayout.findViewById(R.id.loading_text);
                loadingText.setText(isCreating
                        ? R.string.web_gui_creating_key
                        : R.string.api_loading);

                dialog = new AlertDialog.Builder(activity)
                        .setCancelable(false)
                        .setView(dialogLayout)
                        .show();


                // Make sure the welcome dialog is shown on top.
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                if (prefs.getBoolean("first_start", true)) {
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.welcome_title)
                            .setMessage(R.string.welcome_text)
                            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    prefs.edit().putBoolean("first_start", false).commit();
                                }
                            }).show();
                }
            }
            @Override
            protected Object doInBackground(Object[] params) {
                return null;
            }
            @Override
            protected void onCancelled() {
                if (dialog.isShowing())
                    dialog.dismiss();
            }
        };
        return a.execute();
    }


    /**
     * Welcome dialog to be shown when the user launches the app for the first time.
     */
    public static AsyncTask showWelcomeDialog(final Activity activity) {
        AsyncTask a = new AsyncTask() {
            AlertDialog dialog;

            @Override
            protected void onPreExecute() {
                if (activity.isFinishing())
                    return;

            }
            @Override
            protected Object doInBackground(Object[] params) {
                return null;
            }
            @Override
            protected void onCancelled() {
                if (dialog.isShowing())
                    dialog.dismiss();
            }
        };
        return a.execute();
    }
}
