package com.nutomic.syncthingandroid.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.util.Util;

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
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

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
    private static final int LOG_FILE_MAX_LINES = 10;

    private static final AtomicReference<Process> mSyncthing = new AtomicReference<>();
    private final Context mContext;
    private final File mSyncthingBinary;
    private String[] mCommand;
    private final File mLogFile;
    @Inject SharedPreferences mPreferences;
    private final boolean mUseRoot;
    @Inject NotificationHandler mNotificationHandler;

    public enum Command {
        deviceid,           // Output the device ID to the command line.
        generate,           // Generate keys, a config file and immediately exit.
        main,               // Run the main Syncthing application.
        resetdatabase,      // Reset Syncthing's database
        resetdeltas,        // Reset Syncthing's delta indexes
    }

    /**
     * Constructs instance.
     *
     * @param command Which type of Syncthing command to execute.
     */
    public SyncthingRunnable(Context context, Command command) {
        ((SyncthingApp) context.getApplicationContext()).component().inject(this);
        mContext = context;
        mSyncthingBinary = Constants.getSyncthingBinary(mContext);
        mLogFile = Constants.getLogFile(mContext);

        // Get preferences relevant to starting syncthing core.
        mUseRoot = mPreferences.getBoolean(Constants.PREF_USE_ROOT, false) && Shell.SU.available();
        switch (command) {
            case deviceid:
                mCommand = new String[]{ mSyncthingBinary.getPath(), "-home", mContext.getFilesDir().toString(), "--device-id" };
                break;
            case generate:
                mCommand = new String[]{ mSyncthingBinary.getPath(), "-generate", mContext.getFilesDir().toString(), "-logflags=0" };
                break;
            case main:
                mCommand = new String[]{ mSyncthingBinary.getPath(), "-home", mContext.getFilesDir().toString(), "-no-browser", "-logflags=0" };
                break;
            case resetdatabase:
                mCommand = new String[]{ mSyncthingBinary.getPath(), "-home", mContext.getFilesDir().toString(), "-reset-database", "-logflags=0" };
                break;
            case resetdeltas:
                mCommand = new String[]{ mSyncthingBinary.getPath(), "-home", mContext.getFilesDir().toString(), "-reset-deltas", "-logflags=0" };
                break;
            default:
                throw new InvalidParameterException("Unknown command option");
        }
    }

    @Override
    public void run() {
        run(false);
    }

    @SuppressLint("WakelockTimeout")
    public String run(boolean returnStdOut) {
        trimLogFile();
        int ret;
        String capturedStdOut = "";
        // Make sure Syncthing is executable
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "500", mSyncthingBinary.getPath());
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
            increaseInotifyWatches();

            HashMap<String, String> targetEnv = buildEnvironment();
            process = setupAndLaunch(targetEnv);

            mSyncthing.set(process);

            Thread lInfo = null;
            Thread lWarn = null;
            if (returnStdOut) {
                BufferedReader br = null;
                try {
                    br = new BufferedReader(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));
                    String line;
                    while ((line = br.readLine()) != null) {
                        Log.println(Log.INFO, TAG_NATIVE, line);
                        capturedStdOut = capturedStdOut + line + "\n";
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed to read Syncthing's command line output", e);
                } finally {
                    if (br != null)
                        br.close();
                }
            } else {
                lInfo = log(process.getInputStream(), Log.INFO, true);
                lWarn = log(process.getErrorStream(), Log.WARN, true);
            }

            niceSyncthing();

            ret = process.waitFor();
            Log.i(TAG, "Syncthing exited with code " + ret);
            mSyncthing.set(null);
            if (lInfo != null)
                lInfo.join();
            if (lWarn != null)
                lWarn.join();

            switch (ret) {
                case 0:
                case 137:
                    // Syncthing was shut down (via API or SIGKILL), do nothing.
                    break;
                case 1:
                    Log.w(TAG, "Another Syncthing instance is already running, requesting restart via SyncthingService intent");
                    //fallthrough
                case 3:
                    // Restart was requested via Rest API call.
                    Log.i(TAG, "Restarting syncthing");
                    mContext.startService(new Intent(mContext, SyncthingService.class)
                            .setAction(SyncthingService.ACTION_RESTART));
                    break;
                default:
                    Log.w(TAG, "Syncthing has crashed (exit code " + ret + ")");
                    mNotificationHandler.showCrashedNotification(R.string.notification_crash_title, false);
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Failed to execute syncthing binary or read output", e);
        } finally {
            if (wakeLock != null)
                wakeLock.release();
            if (process != null)
                process.destroy();
        }
        return capturedStdOut;
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
        return mPreferences.getBoolean(Constants.PREF_USE_WAKE_LOCK, false);
    }

    /**
     * Look for running libsyncthing.so processes and return an array
     * containing the PIDs of found instances.
     */
    private List<String> getSyncthingPIDs() {
        List<String> syncthingPIDs = new ArrayList<String>();
        Process ps = null;
        DataOutputStream psOut = null;
        BufferedReader br = null;
        try {
            ps = Runtime.getRuntime().exec((mUseRoot) ? "su" : "sh");
            psOut = new DataOutputStream(ps.getOutputStream());
            psOut.writeBytes("ps\n");
            psOut.writeBytes("exit\n");
            psOut.flush();
            ps.waitFor();
            br = new BufferedReader(new InputStreamReader(ps.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(Constants.FILENAME_SYNCTHING_BINARY)) {
                    String syncthingPID = line.trim().split("\\s+")[1];
                    Log.v(TAG, "getSyncthingPIDs: Found process PID [" + syncthingPID + "]");
                    syncthingPIDs.add(syncthingPID);
                }
            }
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "Failed to list Syncthing processes", e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
                if (psOut != null) {
                    psOut.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to close psOut stream", e);
            }
            if (ps != null) {
                ps.destroy();
            }
        }
        return syncthingPIDs;
    }

    /**
     * Root-only: Temporarily increase "fs.inotify.max_user_watches"
     * as Android has a default limit of 8192 watches.
     * Manually run "sysctl fs.inotify" in a root shell terminal to check current limit.
     */
    private void increaseInotifyWatches() {
        if (!mUseRoot || !Shell.SU.available()) {
            Log.i(TAG, "increaseInotifyWatches: Root is not available. Cannot increase inotify limit.");
            return;
        }
        int exitCode = Util.runShellCommand("sysctl -n -w fs.inotify.max_user_watches=131072\n", true);
        Log.i(TAG, "increaseInotifyWatches: sysctl returned " + Integer.toString(exitCode));
    }

    /**
     * Look for a running libsyncthing.so process and nice its IO.
     */
    private void niceSyncthing() {
        if (!mUseRoot || !Shell.SU.available()) {
            Log.i(TAG_NICE, "Root is not available. Cannot nice syncthing.");
            return;
        }

        List<String> syncthingPIDs = getSyncthingPIDs();
        if (syncthingPIDs.isEmpty()) {
            Log.i(TAG_NICE, "Found no running instances of " + Constants.FILENAME_SYNCTHING_BINARY);
            return;
        }

        // Ionice all running syncthing processes.
        for (String syncthingPID : syncthingPIDs) {
            // Set best-effort, low priority using ionice.
            int exitCode = Util.runShellCommand("/system/bin/ionice " + syncthingPID + " be 7\n", true);
            Log.i(TAG_NICE, "ionice returned " + Integer.toString(exitCode) +
                " on " + Constants.FILENAME_SYNCTHING_BINARY);
        }
    }

    public interface OnSyncthingKilled {
        void onKilled();
    }
    /**
     * Look for running libsyncthing.so processes and kill them.
     * Try a SIGINT first, then try again with SIGKILL.
     */
    public void killSyncthing() {
        for (int i = 0; i < 2; i++) {
            List<String> syncthingPIDs = getSyncthingPIDs();
            if (syncthingPIDs.isEmpty()) {
                Log.d(TAG, "killSyncthing: Found no more running instances of " + Constants.FILENAME_SYNCTHING_BINARY);
                break;
            }

            int exitCode;
            for (String syncthingPID : syncthingPIDs) {
                if (i > 0) {
                    // Force termination of the process by sending SIGKILL.
                    SystemClock.sleep(3000);
                    exitCode = Util.runShellCommand("kill -SIGKILL " + syncthingPID + "\n", mUseRoot);
                } else {
                    exitCode = Util.runShellCommand("kill -SIGINT " + syncthingPID + "\n", mUseRoot);
                    SystemClock.sleep(1000);
                }
                if (exitCode == 0) {
                    Log.d(TAG, "Killed Syncthing process " + syncthingPID);
                } else {
                    Log.w(TAG, "Failed to kill Syncthing process " + syncthingPID +
                        " exit code " + Integer.toString(exitCode));
                }
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
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(is, Charsets.UTF_8));
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
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.w(TAG, "log: Failed to close bufferedReader", e);
                }
            }
        });
        t.start();
        return t;
    }

    /**
     * Only keep last {@link #LOG_FILE_MAX_LINES} lines in log file, to avoid bloat.
     */
    private void trimLogFile() {
        if (!mLogFile.exists())
            return;

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

    private HashMap<String, String> buildEnvironment() {
        HashMap<String, String> targetEnv = new HashMap<>();
        // Set home directory to data folder for web GUI folder picker.
        targetEnv.put("HOME", Environment.getExternalStorageDirectory().getAbsolutePath());
        targetEnv.put("STTRACE", TextUtils.join(" ",
                        mPreferences.getStringSet(Constants.PREF_DEBUG_FACILITIES_ENABLED, new HashSet<>())));
        File externalFilesDir = mContext.getExternalFilesDir(null);
        if (externalFilesDir != null)
            targetEnv.put("STGUIASSETS", externalFilesDir.getAbsolutePath() + "/gui");
        targetEnv.put("STNORESTART", "1");
        targetEnv.put("STNOUPGRADE", "1");
        // Disable hash benchmark for faster startup.
        // https://github.com/syncthing/syncthing/issues/4348
        targetEnv.put("STHASHING", "minio");
        if (mPreferences.getBoolean(Constants.PREF_USE_TOR, false)) {
            targetEnv.put("all_proxy", "socks5://localhost:9050");
            targetEnv.put("ALL_PROXY_NO_FALLBACK", "1");
        } else {
            String socksProxyAddress = mPreferences.getString(Constants.PREF_SOCKS_PROXY_ADDRESS, "");
            if (!socksProxyAddress.equals("")) {
                targetEnv.put("all_proxy", socksProxyAddress);
            }

            String httpProxyAddress = mPreferences.getString(Constants.PREF_HTTP_PROXY_ADDRESS, "");
            if (!httpProxyAddress.equals("")) {
                targetEnv.put("http_proxy", httpProxyAddress);
                targetEnv.put("https_proxy", httpProxyAddress);
            }
        }
        if (mPreferences.getBoolean("use_legacy_hashing", false))
            targetEnv.put("STHASHING", "standard");
        putCustomEnvironmentVariables(targetEnv, mPreferences);
        return targetEnv;
    }

    private Process setupAndLaunch(HashMap<String, String> env) throws IOException {
        if (mUseRoot) {
            ProcessBuilder pb = new ProcessBuilder("su");
            Process process = pb.start();
            // The su binary prohibits the inheritance of environment variables.
            // Even with --preserve-environment the environment gets messed up.
            // We therefore start a root shell, and set all the environment variables manually.
            DataOutputStream suOut = new DataOutputStream(process.getOutputStream());
            for (Map.Entry<String, String> entry : env.entrySet()) {
                suOut.writeBytes(String.format("export %s=\"%s\"\n", entry.getKey(), entry.getValue()));
            }
            suOut.flush();
            // Exec will replace the su process image by Syncthing as execlp in C does.
            // Without using exec, the process will drop to the root shell as soon as Syncthing terminates like a normal shell does.
            // If we did not use exec, we would wait infinitely for the process to terminate (ret = process.waitFor(); in run()).
            // With exec the whole process terminates when Syncthing exits.
            suOut.writeBytes("exec " + TextUtils.join(" ", mCommand) + "\n");
            // suOut.flush has to be called to fix issue - #1005 Endless loader after enabling "Superuser mode"
            suOut.flush();
            return process;
        } else {
            ProcessBuilder pb = new ProcessBuilder(mCommand);
            pb.environment().putAll(env);
            return pb.start();
        }
    }
}
