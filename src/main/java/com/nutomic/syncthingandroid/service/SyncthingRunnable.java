package com.nutomic.syncthingandroid.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Map;
import java.util.HashMap;
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
    private static final String BINARY_NAME = "libsyncthing.so";
    private static final int LOG_FILE_MAX_LINES = 10;
    private static final int NOTIFICATION_ID_CRASH = 9;

    private static final AtomicReference<Process> mSyncthing = new AtomicReference<>();
    private final Context mContext;
    private final String mSyncthingBinary;
    private String[] mCommand;
    private final File mLogFile;
    private final SharedPreferences mPreferences;
    private final boolean mUseRoot;

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
        mSyncthingBinary = mContext.getApplicationInfo().nativeLibraryDir + "/" + BINARY_NAME;
        mLogFile = new File(mContext.getExternalFilesDir(null), "syncthing.log");
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mUseRoot = mPreferences.getBoolean(SyncthingService.PREF_USE_ROOT, false) && Shell.SU.available();
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
        mSyncthingBinary = mContext.getApplicationInfo().nativeLibraryDir + "/" + BINARY_NAME;
        mCommand = manualCommand;
        mLogFile = new File(mContext.getExternalFilesDir(null), "syncthing.log");
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mUseRoot = false;
    }

    @Override
    public void run() {
        trimLogFile();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        int ret;
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

            HashMap<String, String> targetEnv = buildEnvironment(sp);
            process = setupAndLaunch(targetEnv);

            mSyncthing.set(process);

            Thread lInfo = log(process.getInputStream(), Log.INFO, true);
            Thread lWarn = log(process.getErrorStream(), Log.WARN, true);

            niceSyncthing();

            ret = process.waitFor();
            mSyncthing.set(null);
            lInfo.join();
            lWarn.join();

            switch (ret) {
                case 0:
                case 137:
                    // Syncthing was shut down (via API or SIGKILL), do nothing.
                    break;
                case 1:
                    // Syncthing is already running, kill it and try again.
                    killSyncthing();
                    //fallthrough
                case 3:
                    // Restart was requested via Rest API call.
                    Log.i(TAG, "Restarting syncthing");
                    mContext.startService(new Intent(mContext, SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESTART));
                    break;
                default:
                    Log.w(TAG, "Syncthing has crashed (exit code " + ret + ")");
                    if (mPreferences.getBoolean("notify_crashes", false)) {
                        // Show notification to inform user about crash.
                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(mLogFile), "text/plain");
                        Notification n = new NotificationCompat.Builder(mContext)
                                .setContentTitle(mContext.getString(R.string.notification_crash_title))
                                .setContentText(mContext.getString(R.string.notification_crash_text))
                                .setSmallIcon(R.drawable.ic_stat_notify)
                                .setContentIntent(PendingIntent.getActivity(mContext, 0, intent, 0))
                                .setAutoCancel(true)
                                .build();
                        NotificationManager nm = (NotificationManager)
                                mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                        nm.notify(NOTIFICATION_ID_CRASH, n);
                    }
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

    private void putCustomEnvironmentVariables(Map<String, String> environment, SharedPreferences sp) {
        String customEnvironment = sp.getString("environment_variables", null);
        if (TextUtils.isEmpty(customEnvironment))
            return;

        for (String e : customEnvironment.split(" ")) {
            String[] e2 = e.split("=");
            environment.put(e2[0], e2[1]);
        }
    }

    /**
     * Returns true if the experimental setting for using wake locks has been enabled in settings.
     */
    private boolean useWakeLock() {
        return mPreferences.getBoolean(SyncthingService.PREF_USE_WAKE_LOCK, false);
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
                    nice = Runtime.getRuntime().exec((mUseRoot) ? "su" : "sh");
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
                ps = Runtime.getRuntime().exec((mUseRoot) ? "su" : "sh");
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
            kill = Runtime.getRuntime().exec((mUseRoot) ? "su" : "sh");
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
     * @param saveLog True if the log should be stored to {@link #mLogFile}.
     */
    private Thread log(final InputStream is, final int priority, final boolean saveLog) {
        Thread t = new Thread(() -> {
            try {
                InputStreamReader isr = new InputStreamReader(is, Charsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    Log.println(priority, TAG_NATIVE, line);

                    if (saveLog) {
                        Files.append(line + "\n", mLogFile, Charsets.UTF_8);
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to read Syncthing's command line output", e);
            }
        });
        t.start();
        return t;
    }

    /**
     * Only keep last {@link #LOG_FILE_MAX_LINES} lines in log file, to avoid bloat.
     */
    private void trimLogFile() {
        try {
            LineNumberReader lnr = new LineNumberReader(new FileReader(mLogFile));
            lnr.skip(Long.MAX_VALUE);

            int lineCount = lnr.getLineNumber();
            lnr.close();

            File tempFile = new File(mContext.getExternalFilesDir(null), "syncthing.log.tmp");

            BufferedReader reader = new BufferedReader(new FileReader(mLogFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String currentLine;
            int startFrom = lineCount - LOG_FILE_MAX_LINES;
            for (int i = 0; (currentLine = reader.readLine()) != null; i++) {
                if (i > startFrom) {
                    writer.write(currentLine + "\n");
                }
            }
            writer.close();
            reader.close();
            tempFile.renameTo(mLogFile);
        } catch (IOException e) {
            Log.w(TAG, "Failed to trim log file", e);
        }
    }

    private HashMap<String, String> buildEnvironment(SharedPreferences sp) {
        HashMap<String, String> targetEnv = new HashMap<String, String>();
        // Set home directory to data folder for web GUI folder picker.
        targetEnv.put("HOME", Environment.getExternalStorageDirectory().getAbsolutePath());
        targetEnv.put("STTRACE", sp.getString("sttrace", ""));
        File externalFilesDir = mContext.getExternalFilesDir(null);
        if (externalFilesDir != null)
            targetEnv.put("STGUIASSETS", externalFilesDir.getAbsolutePath() + "/gui");
        targetEnv.put("STNORESTART", "1");
        targetEnv.put("STNOUPGRADE", "1");
        // Disable hash benchmark for faster startup.
        // https://github.com/syncthing/syncthing/issues/4348
        targetEnv.put("STHASHING", "minio");
        if (sp.getBoolean("use_tor", false)) {
            targetEnv.put("all_proxy", "socks5://localhost:9050");
            targetEnv.put("ALL_PROXY_NO_FALLBACK", "1");
        }
        if (sp.getBoolean("use_legacy_hashing", false))
            targetEnv.put("STHASHING", "standard");
        putCustomEnvironmentVariables(targetEnv, sp);
        return targetEnv;
    }

    private Process setupAndLaunch(HashMap<String, String> env) throws IOException {
        Process process = null;

        ProcessBuilder pb = (mUseRoot)
                ? new ProcessBuilder("su")
                : new ProcessBuilder(mCommand);

        if (mUseRoot) {
            process = pb.start();
            // The su binary prohibits the inheritance of environment variables.
            // Even with --preserve-environment the environment gets messed up.
            // We therefore start a root shell, and set all the environment variables manually.
            DataOutputStream suOut = new DataOutputStream(process.getOutputStream());
            for (Map.Entry<String, String> entry : env.entrySet()) {
                suOut.writeBytes(String.format("export %s=\"%s\"\n", entry.getKey(), entry.getValue()));
            }
            suOut.flush();
            // Exec into Synchting, because we want to wait for Syncthing.
            // Exec will replace the su process by Syncthing.
            suOut.writeBytes("exec " + TextUtils.join(" ", mCommand) + "\n");
            suOut = null;
        } else {
            pb.environment().putAll(env);
            process = pb.start();
        }
        return process;
    }
}
