package com.nutomic.syncthingandroid.syncthing;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Runs the syncthing binary from command line, and prints its output to logcat.
 */
public class SyncthingRunnable implements Runnable {

    private static final String TAG = "SyncthingRunnable";

    private static final String TAG_NATIVE = "SyncthingNativeCode";

    private static final int NOTIFICATION_CRASHED = 3;

    private final Context mContext;

    private String mCommand;

    /**
     * Constructs instance.
     *
     * @param command The exact command to be executed on the shell.
     */
    public SyncthingRunnable(Context context, String command) {
        mContext = context;
        mCommand = command;
    }

    @Override
    public void run() {
        SharedPreferences pm = PreferenceManager.getDefaultSharedPreferences(mContext);
        DataOutputStream dos = null;
        int ret = 1;
        Process process = null;
        try {
            // Loop to handle syncthing restarts (these always have an error code of 3).
            do {
                process = Runtime.getRuntime().exec("sh");
                dos = new DataOutputStream(process.getOutputStream());
                // Set home directory to data folder for syncthing to use.
                dos.writeBytes("HOME=" + mContext.getFilesDir() + " ");
                dos.writeBytes("STTRACE=" + pm.getString("sttrace", "") + " ");
                dos.writeBytes("STNORESTART=1 ");
                // Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
                dos.writeBytes(mCommand + " -home " + mContext.getFilesDir() + "\n");
                dos.writeBytes("exit\n");
                dos.flush();

                log(process.getInputStream());

                ret = process.waitFor();
            } while (ret == 3);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
        } finally {
            try {
                dos.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close shell stream", e);
            }
            process.destroy();
            if (ret != 0) {
                Log.w(TAG_NATIVE, "Syncthing binary crashed with error code " +
                        Integer.toString(ret));
                NotificationCompat.Builder b = new NotificationCompat.Builder(mContext)
                        .setContentTitle(mContext.getString(R.string.binary_crashed_title))
                        .setContentText(mContext.getString(R.string.binary_crashed_message, ret))
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true);
                Notification n = new NotificationCompat.BigTextStyle(b)
                        .bigText(mContext.getString(R.string.binary_crashed_message, ret))
                        .build();
                NotificationManager nm = (NotificationManager)
                        mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(NOTIFICATION_CRASHED, n);
            }
        }
    }

    /**
     * Logs the outputs of a stream to logcat and mNativeLog.
     *
     * @param is The stream to log.
     */
    private void log(final InputStream is) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        Log.i(TAG_NATIVE, line);
                    }
                } catch (IOException e) {
                    // NOTE: This is sometimes called on shutdown, as
                    // Process.destroy() closes the stream.
                    Log.w(TAG, "Failed to read syncthing command line output", e);
                }
            }
        }).start();
    }

}
