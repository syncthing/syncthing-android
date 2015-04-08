package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Runs the syncthing binary from command line, and prints its output to logcat.
 */
public class SyncthingRunnable implements Runnable {

    private static final String TAG = "SyncthingRunnable";

    private static final String TAG_NICE = "SyncthingRunnable-Nice";

    private static final String TAG_KILL = "SyncthingRunnable-Kill";

    private static final String TAG_NATIVE = "SyncthingNativeCode";

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
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        DataOutputStream dos = null;
        int ret = 1;
        Process process = null;
        try {
            // Loop to handle syncthing restarts (these always have an error code of 3).
            do {
                ProcessBuilder pb = new ProcessBuilder("sh");
                Map<String, String> env = pb.environment();
                // Set home directory to data folder for web GUI folder picker.
                env.put("HOME", Environment.getExternalStorageDirectory().getAbsolutePath());
                env.put("STTRACE", sp.getString("sttrace", ""));
                env.put("STNORESTART", "1");
                env.put("STNOUPGRADE", "1");
                env.put("STGUIAUTH", sp.getString("gui_user", "") + ":" +
                        sp.getString("gui_password", ""));
                process = pb.start();

                dos = new DataOutputStream(process.getOutputStream());
                // Call syncthing with -home (as it would otherwise use "~/.config/syncthing/".
                dos.writeBytes(mCommand + " -home " + mContext.getFilesDir() + "\n");
                dos.writeBytes("exit\n");
                dos.flush();

                log(process.getInputStream(), Log.INFO);
                log(process.getErrorStream(), Log.WARN);

                niceSyncthing();

                ret = process.waitFor();

                if (ret == 3) {
                    Log.i(TAG, "Restarting syncthing");
                    mContext.startService(new Intent(mContext, SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESTART));
                }
            } while (ret == 3);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
        } finally {
            try {
                if (dos != null)
                    dos.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close shell stream", e);
            }
            if (process != null)
                process.destroy();
            if (ret != 0) {
                Log.e(TAG_NATIVE, "Syncthing binary crashed with error code " +
                        Integer.toString(ret));
            }
        }
    }

    /**
     * Look for a running libsyncthing.so process and nice its IO.
     */
    private void niceSyncthing() {
        new Thread() {
            public void run() {
                Process nice = null;
                DataOutputStream niceOut = null;
                int ret = 1;
                try {
                    Thread.sleep(1000); // Wait a second before getting the pid
                    nice = Runtime.getRuntime().exec("sh");
                    niceOut = new DataOutputStream(nice.getOutputStream());
                    niceOut.writeBytes("set `ps |grep libsyncthing.so`\n");
                    niceOut.writeBytes("ionice $2 be 7\n"); // best-effort, low priority
                    niceOut.writeBytes("exit\n");
                    log(nice.getErrorStream(), Log.WARN);
                    niceOut.flush();
                    ret = nice.waitFor();
                    Log.i(TAG_NICE, "ionice performed on libsyncthing.so");
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG_NICE, "Failed to execute ionice binary", e);
                } finally {
                    try {
                        if (niceOut != null) {
                            niceOut.close();
                        }
                    } catch (IOException e) {
                        Log.w(TAG_NICE, "Failed to close shell stream", e);
                    }
                    if (nice != null) {
                        nice.destroy();
                    }
                    if (ret != 0) {
                        Log.e(TAG_NICE, "Failed to set ionice " + Integer.toString(ret));
                    }
                }
            }
        }.start();
    }

    /**
     * Look for running libsyncthing.so processes and kill them.
     * Try a SIGTERM once, then try again (twice) with SIGKILL.
     *
     */
    public static void killSyncthing() {
        for (int i = 0; i < 2; i++) {
            Process ps = null;
            DataOutputStream psOut = null;
            try {
                ps = Runtime.getRuntime().exec("sh");
                psOut = new DataOutputStream(ps.getOutputStream());
                psOut.writeBytes("ps | grep libsyncthing.so\n");
                psOut.writeBytes("exit\n");
                psOut.flush();
                ps.waitFor();
                InputStreamReader isr = new InputStreamReader(ps.getInputStream());
                BufferedReader br = new BufferedReader(isr);
                String id;
                while ((id = br.readLine()) != null) {
                    killProcessId(id, i > 0);
                }
            } catch (IOException | InterruptedException e) {
                Log.w(TAG_KILL, "Failed list Syncthing processes", e);
            } finally {
                try {
                    if (psOut != null)
                        psOut.close();
                } catch (IOException e) {
                    Log.w(TAG_KILL, "Failed close the psOut stream", e);
                }
                if (ps != null) {
                    ps.destroy();
                }
            }
        }
    }

    /**
     * Kill a given process ID
     *
     * @param force Whether to use a SIGKILL.
     */
    private static void killProcessId(String id, boolean force) {
        Process kill = null;
        DataOutputStream killOut = null;
        try {
            kill = Runtime.getRuntime().exec("sh");
            killOut = new DataOutputStream(kill.getOutputStream());
            if (!force) {
                killOut.writeBytes("kill " + id + "\n");
                killOut.writeBytes("sleep 1\n");
            } else {
                killOut.writeBytes("sleep 3\n");
                killOut.writeBytes("kill -9 " + id + "\n");
            }
            killOut.writeBytes("exit\n");
            killOut.flush();
            kill.waitFor();
            Log.i(TAG_KILL, "Killed Syncthing process "+id);
        } catch (IOException | InterruptedException e) {
            Log.w(TAG_KILL, "Failed to kill process id "+id, e);
        } finally {
            try {
                if (killOut != null)
                    killOut.close();
            } catch (IOException e) {
                Log.w(TAG_KILL, "Failed close the killOut stream", e);}
            if (kill != null) {
                kill.destroy();
            }
        }
    }

    /**
     * Logs the outputs of a stream to logcat and mNativeLog.
     *
     * @param is The stream to log.
     * @param priority The priority level.
     */
    private void log(final InputStream is, final int priority) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        Log.println(priority, TAG_NATIVE, line);
                    }
                } catch (IOException e) {
                    // NOTE: This is sometimes called on shutdown, as
                    // Process.destroy() closes the stream.
                    Log.w(TAG, "Failed to read Syncthing's command line output", e);
                }
            }
        }).start();
    }

}
