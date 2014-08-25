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
import java.util.Random;

/**
 * Runs the syncthing binary from command line, and prints its output to logcat.
 */
public class SyncthingRunnable implements Runnable {

    private static final String TAG = "SyncthingRunnable";

    private static final String TAG_NATIVE = "SyncthingNativeCode";

    private final Context mContext;

    private final String mCommand;

    private final Handler mHandler;

    private final String mApiKey;

    /**
     * Constructs instance.
     *
     * @param command The exact command to be executed on the shell.
     */
    public SyncthingRunnable(Context context, String command) {
        mContext = context;
        mCommand = command;
        mHandler = new Handler();

        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
            sb.append(chars[random.nextInt(chars.length)]);
        }
        mApiKey = sb.toString();
    }

    public String getApiKey() {
        return mApiKey;
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
            dos.writeBytes("STGUIAPIKEY=" + mApiKey + " ");
            // Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
            dos.writeBytes(mCommand + " -home " + mContext.getFilesDir() + "\n");
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
                Log.w(TAG_NATIVE, "Syncthing binary crashed with error code " +
                        Integer.toString(retVal));
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
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        System.exit(0);
                                    }
                                }
                        )
                        .create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
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
