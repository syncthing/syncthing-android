package com.nutomic.syncthingandroid.syncthing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;

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

    /**
     * Path to the native, integrated syncthing binary, relative to the data folder
     */
    private static final String BINARY_NAME = "lib/libsyncthing.so";

    private Handler mHandler;

    private Context mContext;

    public SyncthingRunnable(Context context) {
        mContext = context;
        mHandler = new Handler();
    }

    @Override
    public void run() {
        DataOutputStream dos = null;
        int ret = 1;
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("sh");
            dos = new DataOutputStream(process.getOutputStream());
            // Set home directory to data folder for syncthing to use.
            dos.writeBytes("HOME=" + mContext.getFilesDir() + " ");
            // Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
            dos.writeBytes(mContext.getApplicationInfo().dataDir + "/" + BINARY_NAME + " " +
                    "-home " + mContext.getFilesDir() + "\n");
            dos.writeBytes("exit\n");
            dos.flush();

            log(process.getInputStream());

            ret = process.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
        } finally {
            try {
                dos.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close shell stream", e);
            }
            process.destroy();
            final int retVal = ret;
            if (ret != 0) {
                Log.w(TAG_NATIVE, "Syncthing binary crashed with error code " + Integer.toString(retVal));
                postCrashDialog(retVal);
            }
        }
    }

    /**
     * Displays a dialog with an info message and the return value.
     *
     * @param retVal
     */
    private void postCrashDialog(final int retVal) {
        mHandler.post(new Runnable() {
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(mContext)
                        .setTitle(R.string.binary_crashed_title)
                        .setMessage(mContext.getString(R.string.binary_crashed_message, retVal))
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface,
                                                        int i) {
                                        System.exit(0);
                                    }
                                }
                        )
                        .create();
                dialog.getWindow()
                        .setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                dialog.show();
            }
        });
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
