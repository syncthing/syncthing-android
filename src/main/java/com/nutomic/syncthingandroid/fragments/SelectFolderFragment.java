package com.nutomic.syncthingandroid.fragments;

import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SelectFolderFragment extends FoldersFragment {

    private static final String TAG = "SelectFolderFragment";
    private ArrayList<Uri> mFiles;
    private boolean copying = false;

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        // Currently only handle one copy request per intent. So the UI will remain responsive
        // but won't really do anything after the first click.
        if (!copying) {
            copying = true;
            RestApi.Folder destFolder = getItemFolder(i);
            CopyFilesTaskParams copyFilesTaskParams = new CopyFilesTaskParams(mFiles, destFolder);
            new CopyFilesTask().execute(copyFilesTaskParams);
        }
    }

    public void setFiles(ArrayList<Uri> files) {
        mFiles = files;
    }

    private static class CopyFilesTaskParams {
        public final List<Uri> sourceUris;
        public final RestApi.Folder destFolder;
        CopyFilesTaskParams(List<Uri> sourceUris, RestApi.Folder destFolder) {
            this.sourceUris = sourceUris;
            this.destFolder = destFolder;
        }
    }

    private class CopyFilesTask extends AsyncTask<CopyFilesTaskParams, Void, Void> {
        @Override
        protected Void doInBackground(CopyFilesTaskParams... params) {
            List<Uri> sourceUris = params[0].sourceUris;
            RestApi.Folder destFolder = params[0].destFolder;
            for (Uri uri : sourceUris) {
                File sourceFile = new File(getRealPathFromURI(uri));
                File destFile = new File(destFolder.path, sourceFile.getName());
                try {
                    copyFile(sourceFile, destFile);
                }
                catch (IOException e) {
                    Log.e(TAG, "Failed copying file", e);
                }
            }

            SyncthingActivity activity = (SyncthingActivity) getActivity();
            activity.getApi().onFolderFileChange(destFolder.id, destFolder.path);
            activity.finish();
            return null;
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

        // Copied from http://stackoverflow.com/a/9293885/943814
        private void copyFile(File src, File dst) throws IOException {
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
    }
}

