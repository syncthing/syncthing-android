package com.nutomic.syncthingandroid.fragments;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class SelectFolderFragment extends FoldersFragment {

    private static final String TAG = "SelectFolderFragment";

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        for (Uri uri : files) {
            File sourceFile = new File(getRealPathFromURI(uri));
            String path = mAdapter.getItem(i).path;
            File destFile = new File(path, sourceFile.getName());
            try {
                copy(sourceFile, destFile);
            }
            catch (IOException e) {
                Log.e(TAG, "Failed copying file", e);
            }
        }

        getActivity().finish();
    }

    public void setFiles(ArrayList<Uri> files) {
        this.files = files;
    }

    // Copied from http://stackoverflow.com/a/9293885/943814
    public void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    // Copied from http://stackoverflow.com/a/9989900/943814
    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getActivity().getContentResolver()
                .query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    private ArrayList<Uri> files;
}
