package com.nutomic.syncthingandroid.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;

import eu.chainfire.libsuperuser.Shell;

public class Util {

    private static final String TAG = "SyncthingUtil";

    private Util() {
    }

    /**
     * Copies the given device ID to the clipboard (and shows a Toast telling about it).
     *
     * @param id The device ID to copy.
     */
    public static void copyDeviceId(Context context, String id) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(context.getString(R.string.device_id), id);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, R.string.device_id_copied_to_clipboard, Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * Converts a number of bytes to a human readable file size (eg 3.5 GiB).
     * <p>
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableFileSize(Context context, long bytes) {
        final String[] units = context.getResources().getStringArray(R.array.file_size_units);
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Converts a number of bytes to a human readable transfer rate in bytes per second
     * (eg 100 KiB/s).
     * <p>
     * Based on http://stackoverflow.com/a/5599842
     */
    public static String readableTransferRate(Context context, long bits) {
        final String[] units = context.getResources().getStringArray(R.array.transfer_rate_units);
        long bytes = bits / 8;
        if (bytes <= 0) return "0 " + units[0];
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(bytes / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * <<<<<<< HEAD
     * Normally an application's data directory is only accessible by the corresponding application.
     * Therefore, every file and directory is owned by an application's user and group. When running Syncthing as root,
     * it writes to the application's data directory. This leaves files and directories behind which are owned by root having 0600.
     * Moreover, those acitons performed as root changes a file's type in terms of SELinux.
     * A subsequent start of Syncthing will fail due to insufficient permissions.
     * Hence, this method fixes the owner, group and the files' type of the data directory.
     *
     * @return true if the operation was successfully performed. False otherwise.
     */
    public static boolean fixAppDataPermissions(Context context) {
        // We can safely assume that root magic is somehow available, because readConfig and saveChanges check for
        // read and write access before calling us.
        // Be paranoid :) and check if root is available.
        // Ignore the 'use_root' preference, because we might want to fix ther permission
        // just after the root option has been disabled.
        if (!Shell.SU.available()) {
            Log.e(TAG, "Root is not available. Cannot fix permissions.");
            return false;
        }

        String packageName;
        ApplicationInfo appInfo;
        try {
            packageName = context.getPackageName();
            appInfo = context.getPackageManager().getApplicationInfo(packageName, 0);

        } catch (NameNotFoundException e) {
            // This should not happen!
            // One should always be able to retrieve the application info for its own package.
            Log.w(TAG, "Error getting current package name", e);
            return false;
        }
        Log.d(TAG, "Uid of '" + packageName + "' is " + appInfo.uid);

        // Get private app's "files" dir residing in "/data/data/[packageName]".
        String dir = context.getFilesDir().getAbsolutePath();
        String cmd = "chown -R " + appInfo.uid + ":" + appInfo.uid + " " + dir + "; ";
        // Running Syncthing as root might change a file's or directories type in terms of SELinux.
        // Leaving them as they are, the Android service won't be able to access them.
        // At least for those files residing in an application's data folder.
        // Simply reverting the type to its default should do the trick.
        cmd += "restorecon -R " + dir + "\n";
        Log.d(TAG, "Running: '" + cmd);
        int exitCode = runShellCommand(cmd, true);
        if (exitCode == 0) {
            Log.i(TAG, "Fixed app data permissions on '" + dir + "'.");
        } else {
            Log.w(TAG, "Failed to fix app data permissions on '" + dir + "'. Result: " +
                Integer.toString(exitCode));
        }
        return exitCode == 0;
    }

    /**
     * Returns if the syncthing binary would be able to write a file into
     * the given folder given the configured access level.
     */
    public static boolean nativeBinaryCanWriteToPath(Context context, String absoluteFolderPath) {
        final String TOUCH_FILE_NAME = ".stwritetest";
        Boolean useRoot = false;
        Boolean prefUseRoot = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(Constants.PREF_USE_ROOT, false);
        if (prefUseRoot && Shell.SU.available()) {
            useRoot = true;
        }

        // Write permission test file.
        String touchFile = absoluteFolderPath + "/" + TOUCH_FILE_NAME;
        int exitCode = runShellCommand("echo \"\" > \"" + touchFile + "\"\n", useRoot);
        if (exitCode != 0) {
            String error;
            switch (exitCode) {
                case 1:
                    error = "Permission denied";
                    break;
                default:
                    error = "Shell execution failed";
            }
            Log.i(TAG, "Failed to write test file '" + touchFile +
                "', " + error);
            return false;
        }

        // Detected we have write permission.
        Log.i(TAG, "Successfully wrote test file '" + touchFile + "'");

        // Remove test file.
        if (runShellCommand("rm \"" + touchFile + "\"\n", useRoot) != 0) {
            // This is very unlikely to happen, so we have less error handling.
            Log.i(TAG, "Failed to remove test file");
        }
        return true;
    }

    /**
     * Run command in a shell and return the exit code.
     */
    public static int runShellCommand(String cmd, Boolean useRoot) {
        // Assume "failure" exit code if an error is caught.
        int exitCode = 255;
        Process shellProc = null;
        DataOutputStream shellOut = null;
        try {
            shellProc = Runtime.getRuntime().exec((useRoot) ? "su" : "sh");
            shellOut = new DataOutputStream(shellProc.getOutputStream());
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(shellOut));
            Log.d(TAG, "runShellCommand: " + cmd);
            bufferedWriter.write(cmd);
            bufferedWriter.flush();
            shellOut.close();
            shellOut = null;
            exitCode = shellProc.waitFor();
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "runShellCommand: Exception", e);
        } finally {
            try {
                if (shellOut != null) {
                    shellOut.close();
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to close shell stream", e);
            }
            if (shellProc != null) {
                shellProc.destroy();
            }
        }
        return exitCode;
    }

    /**
     * Make sure that dialog is showing and activity is valid before dismissing dialog, to prevent
     * various crashes.
     */
    public static void dismissDialogSafe(Dialog dialog, Activity activity) {
        if (dialog == null || !dialog.isShowing())
            return;

        if (activity.isFinishing())
            return;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN && activity.isDestroyed())
            return;

        dialog.dismiss();
    }

    /**
     * Format a path properly.
     *
     * @param path String containing the path that needs formatting.
     * @return formatted file path as a string.
     */
    public static String formatPath(String path) {
        return new File(path).toURI().normalize().getPath();
    }

    /**
     * @return a themed AlertDialog builder.
     */
    public static AlertDialog.Builder getAlertDialogBuilder(Context context)
    {
        return new AlertDialog.Builder(context, R.style.Theme_Syncthing_Dialog);
    }
}
