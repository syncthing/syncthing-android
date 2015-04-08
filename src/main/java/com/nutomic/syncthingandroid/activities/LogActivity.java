package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import android.support.v4.view.MenuItemCompat;
import android.widget.ScrollView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Shows the log information from Syncthing.
 */
public class LogActivity extends SyncthingActivity {

    private final static String TAG = "LogActivity";

    private TextView mLog;
    private boolean mSyncthingLog = true;
    private AsyncTask mFetchLogTask;
    private ScrollView mScrollView;
    private Intent mShareIntent;

    /**
     * Initialize Log.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.log_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState != null) {
            mSyncthingLog = savedInstanceState.getBoolean("syncthingLog");
        }

        mLog = (TextView) findViewById(R.id.log);
        mScrollView = (ScrollView) findViewById(R.id.scroller);

        updateLog();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("syncthingLog", mSyncthingLog);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.log_list, menu);

        // Add the share button
        MenuItem shareItem = menu.findItem(R.id.menu_share);
        if (mSyncthingLog) {
            shareItem.setTitle(R.string.log_android_title);
        } else {
            shareItem.setTitle(R.string.log_syncthing_title);
        }
        ShareActionProvider actionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(shareItem);
        mShareIntent = new Intent();
        mShareIntent.setAction(Intent.ACTION_SEND);
        mShareIntent.setType("text/plain");
        mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, mLog.getText());
        actionProvider.setShareIntent(mShareIntent);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switch_logs:
                mSyncthingLog = !mSyncthingLog;
                if (mSyncthingLog) {
                    item.setTitle(R.string.log_android_title);
                } else {
                    item.setTitle(R.string.log_syncthing_title);
                }
                updateLog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void scrollToBottom() {
        mScrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.scrollTo(0, mLog.getBottom());
            }
        });
    }

    private void updateLog() {
        if (mFetchLogTask != null)
            mFetchLogTask.cancel(true);
        mLog.setText("Retrieving logs...");
        mFetchLogTask = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                return getLog(mSyncthingLog);
            }
            @Override
            protected void onPostExecute(String log) {
                mLog.setText(log);
                if (mShareIntent != null)
                    mShareIntent.putExtra(android.content.Intent.EXTRA_TEXT, log);
                scrollToBottom();
            }
        }.execute();
    }

    private String getLog(final boolean syncthingLog) {
        Process process = null;
        DataOutputStream pOut = null;
        try {
            ProcessBuilder pb;
            if (syncthingLog) {
                pb = new ProcessBuilder("/system/bin/logcat", "-t", "300", "-s", "SyncthingNativeCode");
            } else {
                pb = new ProcessBuilder("/system/bin/logcat", "-t", "300", "'*'");
            }
            pb.redirectErrorStream(true);
            process = pb.start();
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()), 8192);
            StringBuilder log = new StringBuilder();
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
                log.append(System.getProperty("line.separator"));
            }
            return log.toString();
        } catch (IOException e) {
            Log.w(TAG, "Error reading Android log", e);
        } finally {
            try {
                if (pOut != null) {
                    pOut.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to close shell stream", e);
            }
            if (process != null) {
                process.destroy();
            }
        }
        return "";
    }

}
