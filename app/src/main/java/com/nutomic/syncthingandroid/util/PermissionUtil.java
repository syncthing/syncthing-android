package com.nutomic.syncthingandroid.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class PermissionUtil {
    private PermissionUtil() {}

    /**
     * Returns the location permissions required to access wifi SSIDs depending
     * on the respective Android version.
     */
    public static String[] getLocationPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { // before android 9
            return new String[]{
                Manifest.permission.ACCESS_COARSE_LOCATION,
            };
        }
        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
        };
    }

    public static boolean haveStoragePermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        int permissionState = ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }
}
