package com.nutomic.syncthingandroid.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
// import android.util.Log;

import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RunConditionMonitor;
import com.nutomic.syncthingandroid.util.JobUtils;

/**
 * SyncTriggerJobService to be scheduled by the JobScheduler.
 * See {@link JobUtils#scheduleSyncTriggerServiceJob} for more details.
 */
@RequiresApi(21)
public class SyncTriggerJobService extends JobService {
    private static final String TAG = "SyncTriggerJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        // Log.v(TAG, "onStartJob: Job fired.");
        Context context = getApplicationContext();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        Intent intent = new Intent(RunConditionMonitor.ACTION_SYNC_TRIGGER_FIRED);
        localBroadcastManager.sendBroadcast(intent);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
