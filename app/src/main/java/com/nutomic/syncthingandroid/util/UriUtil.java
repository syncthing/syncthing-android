package com.nutomic.syncthingandroid.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.util.DocumentsContract;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;

/**
 * Utility for getting the absolute file path from an URI.
 */
public class UriUtil {
    private static final String TAG = "UriUtil";

    public static String getAbsolutePathFromUri2(final Context context, final Uri uri) {
        final String id = DocumentsContract.getDocumentId(uri);
        if (TextUtils.isEmpty(id)) {
            return "";
        }
        if (id.startsWith("raw:")) {
            return id.replaceFirst("raw:", "");
        }
        try {
            final Uri contentUri = ContentUris.withAppendedId(
                Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            return getDataColumn(context, contentUri, null, null);
        } catch (NumberFormatException e) {
            Log.e(TAG, "getAbsolutePathFromUri2: Downloads provider returned unexpected uri " + uri.toString(), e);
            return null;
        }
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     * source https://stackoverflow.com/a/20559175
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getAbsolutePathFromUri(final Context context, final Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
            DocumentsContract.isDocumentUri(context, uri)) {
            // DocumentProvider
            if (isExternalStorageDocument(uri)) {
                // ExternalStorageProvider
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // Handle non-primary volumes like the external storage.
                String resolvedPath = "";
                File[] possibleExtSdComposites = context.getExternalFilesDirs(null);
                for (File f : possibleExtSdComposites) {
                    // Reset final path
                    resolvedPath = "";

                    // Construct list of folders
                    ArrayList<String> extSdSplit = new ArrayList<>(Arrays.asList(f.getPath().split("/")));

                    // Look for folder "<your_application_id>"
                    int idx = extSdSplit.indexOf(context.getPackageName());

                    // ASSUMPTION: Expected to be found at depth 2 (in this case ExtSdCard's root is /storage/0000-0000/) - e.g. /storage/0000-0000/Android/data/<your_application_id>/files
                    ArrayList<String> hierarchyList = new ArrayList<>(extSdSplit.subList(0, idx - 2));

                    // Construct list containing full possible path to the file
                    hierarchyList.add(split[1]);
                    String possibleFilePath = TextUtils.join("/", hierarchyList);

                    // If file is found --> success
                    if (idx != -1 && new File(possibleFilePath).exists()) {
                        resolvedPath = possibleFilePath;
                        break;
                    }
                }

                if (!resolvedPath.equals("")) {
                    return resolvedPath;
                } else {
                    return null;
                }
            } else if (isDownloadsDocument(uri)) {
                // DownloadsProvider
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                // MediaProvider
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // MediaStore (and general)
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // File
            return uri.getPath();
        }
        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    private static String getDataColumn(Context context, Uri uri, String selection,
            String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
}
