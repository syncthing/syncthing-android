package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.BuildConfig;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import eu.chainfire.libsuperuser.Shell;

/**
 * Runs the syncthing binary from command line, and prints its output to logcat.
 *
 * @see <a href="http://docs.syncthing.net/users/syncthing.html">Command Line Docs</a>
 */
public class SyncthingRunnable implements Runnable {

    private static final String TAG = "SyncthingRunnable";

    private static final String TAG_NATIVE = "SyncthingNativeCode";

    private static final String TAG_NICE = "SyncthingRunnableIoNice";

    private static final String TAG_KILL = "SyncthingRunnableKill";

    public static final String UNIT_TEST_PATH = "was running";

    private static final AtomicReference<Process> mSyncthing = new AtomicReference<>();

    private final Context mContext;

    private String mSyncthingBinary;

    private String[] mCommand;

    private String mErrorLog;

    public enum Command {
        generate, // Generate keys, a config file and immediately exit.
        main,     // Run the main Syncthing application.
        reset,    // Reset Syncthing's indexes
    }

    /**
     * Constructs instance.
     *
     * @param command Which type of Syncthing command to execute.
     */
    public SyncthingRunnable(Context context, Command command) {
        mContext = context;
        mSyncthingBinary = mContext.getApplicationInfo().dataDir + "/" + SyncthingService.BINARY_NAME;
        switch (command) {
            case generate:
                mCommand = new String[]{ mSyncthingBinary, "-generate", mContext.getFilesDir().toString() };
                break;
            case main:
                mCommand = new String[]{ mSyncthingBinary, "-home", mContext.getFilesDir().toString(), "-no-browser" };
                break;
            case reset:
                mCommand = new String[]{ mSyncthingBinary, "-home", mContext.getFilesDir().toString(), "-reset" };
                break;
            default:
                Log.w(TAG, "Unknown command option");
        }
    }

    /**
     * Constructs instance.
     *
     * @param manualCommand The exact command to be executed on the shell. Used for tests only.
     */
    public SyncthingRunnable(Context context, String[] manualCommand) {
        mContext = context;
        mSyncthingBinary = mContext.getApplicationInfo().dataDir + "/" + SyncthingService.BINARY_NAME;
        mCommand = manualCommand;
    }

    @Override
    public void run() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        int ret = 1;
        // Make sure Syncthing is executable
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "500", mSyncthingBinary);
            Process p = pb.start();
            p.waitFor();
        } catch (IOException|InterruptedException e) {
            Log.w(TAG, "Failed to chmod Syncthing", e);
        }
        // Loop Syncthing
        Process process = null;
        // Potential fix for #498, keep the CPU running while native binary is running
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = useWakeLock()
                ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
                : null;
        try {
            if (wakeLock != null)
                wakeLock.acquire();
            ProcessBuilder pb = (useRoot())
                    ? new ProcessBuilder("su", "-c", TextUtils.join(" ", mCommand))
                    : new ProcessBuilder(mCommand);

            Map<String, String> env = pb.environment();
            // Set home directory to data folder for web GUI folder picker.
            env.put("HOME", Environment.getExternalStorageDirectory().getAbsolutePath());
            env.put("STTRACE", sp.getString("sttrace", ""));
            env.put("STNORESTART", "1");
            env.put("STNOUPGRADE", "1");
            env.put("STGUIAUTH", sp.getString("gui_user", "") + ":" +
                    sp.getString("gui_password", ""));
            process = pb.start();
            mSyncthing.set(process);

            mErrorLog = "";
            Thread lInfo = log(process.getInputStream(), Log.INFO, true);
            Thread lWarn = log(process.getErrorStream(), Log.WARN, true);

            niceSyncthing();

            ret = process.waitFor();
            mSyncthing.set(null);
            lInfo.join();
            lWarn.join();

            switch (ret) {
                case 0:
                case 2:
                case 4:
                    // Valid exit codes, ignored.
                    break;
                case 3:
                    // Restart if that was requested via Rest API call.
                    Log.i(TAG, "Restarting syncthing");
                    mContext.startService(new Intent(mContext, SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESTART));
                    break;
                case 137:
                    // Ignore SIGKILL that we use to stop Syncthing.
                    break;
                case 1:
                    // fallthrough
                default:
                    // Report Syncthing crashes, using Exception in debug mode or log in release mode.
                    String message = "Syncthing binary crashed with error code " +
                            Integer.toString(ret) + ", output:\n" + mErrorLog;
                    if (BuildConfig.DEBUG)
                        throw new RuntimeException(message);
                    else
                        Log.e(TAG, message);
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
        } finally {
            if (wakeLock != null)
                wakeLock.release();
            if (process != null)
                process.destroy();
        }
    }

    /**
     * Returns true if root is available and enabled in settings.
     */
    private boolean useRoot() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        return sp.getBoolean(SyncthingService.PREF_USE_ROOT, false) && Shell.SU.available();
    }

    /**
     * Returns true if the experimental setting for using wake locks has been enabled in settings.
     */
    private boolean useWakeLock() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        return sp.getBoolean(SyncthingService.PREF_USE_WAKE_LOCK, false);
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
                    nice = Runtime.getRuntime().exec((useRoot()) ? "su" : "sh");
                    niceOut = new DataOutputStream(nice.getOutputStream());
                    niceOut.writeBytes("set `ps | grep libsyncthing.so`\n");
                    niceOut.writeBytes("ionice $2 be 7\n"); // best-effort, low priority
                    niceOut.writeBytes("exit\n");
                    log(nice.getErrorStream(), Log.WARN, false);
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
     * Try a SIGINT first, then try again with SIGKILL.
     */
    public void killSyncthing() {
        for (int i = 0; i < 2; i++) {
            Process ps = null;
            DataOutputStream psOut = null;
            try {
                ps = Runtime.getRuntime().exec((useRoot()) ? "su" : "sh");
                psOut = new DataOutputStream(ps.getOutputStream());
                psOut.writeBytes("ps | grep libsyncthing.so\n");
                psOut.writeBytes("exit\n");
                psOut.flush();
                ps.waitFor();
                InputStreamReader isr = new InputStreamReader(ps.getInputStream(), "UTF-8");
                BufferedReader br = new BufferedReader(isr);
                String id;
                while ((id = br.readLine()) != null) {
                    id = id.trim().split("\\s+")[1];
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
    private void killProcessId(String id, boolean force) {
        Process kill = null;
        DataOutputStream killOut = null;
        try {
            kill = Runtime.getRuntime().exec((useRoot()) ? "su" : "sh");
            killOut = new DataOutputStream(kill.getOutputStream());
            if (!force) {
                killOut.writeBytes("kill -SIGINT " + id + "\n");
                killOut.writeBytes("sleep 1\n");
            } else {
                killOut.writeBytes("sleep 3\n");
                killOut.writeBytes("kill -SIGKILL " + id + "\n");
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
     * @param saveLog True if the log should be stored to {@link #mErrorLog}.
     */
    private Thread log(final InputStream is, final int priority, final boolean saveLog) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                    BufferedReader br = new BufferedReader(isr);
                    String line;
                    while ((line = br.readLine()) != null) {
                        Log.println(priority, TAG_NATIVE, line);

                        if (saveLog)
                            mErrorLog += line + "\n";
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed to read Syncthing's command line output", e);
                }
            }
        });
        t.start();
        return t;
    }

}
