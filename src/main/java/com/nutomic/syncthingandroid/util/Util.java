package com.nutomic.syncthingandroid.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;

import java.text.DecimalFormat;

public class Util {

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
     *
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
     *
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
}
