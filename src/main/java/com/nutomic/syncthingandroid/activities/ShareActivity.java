package com.nutomic.syncthingandroid.activities;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shares incoming files to syncthing folders.
 */
public class ShareActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener {

    private static final String TAG = "ShareActivity";

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE || getApi() == null)
            return;

        List<Folder> folders = getApi().getFolders();

        ArrayAdapter<Folder> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, folders);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = (Spinner) findViewById(R.id.folders);
        sItems.setAdapter(adapter);
    }

    @Override
    public void onServiceConnected() {
        getService().registerOnApiChangeListener(this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        registerOnServiceConnectedListener(this);

        Spinner mFoldersSpinner = (Spinner) findViewById(R.id.folders);
        Button mShareButton = (Button) findViewById(R.id.share_button);
        Button mCancelButton = (Button) findViewById(R.id.cancel_button);
        EditText mShareName = (EditText) findViewById(R.id.name);
        TextView mShareTitle = (TextView) findViewById(R.id.namesTitle);

        // TODO: add support for EXTRA_TEXT (notes, memos sharing)
        ArrayList<Uri> extrasToCopy = new ArrayList<>();
        if (getIntent().getAction().equals(Intent.ACTION_SEND)) {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null)
                extrasToCopy.add(uri);
        } else if (getIntent().getAction().equals(Intent.ACTION_SEND_MULTIPLE)) {
            ArrayList<Uri> extras = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (extras != null)
                extrasToCopy = extras;
        }

        if (extrasToCopy.isEmpty()) {
            Toast.makeText(this, getString(R.string.nothing_share), Toast.LENGTH_SHORT).show();
            finish();
        }

        Map<Uri, String> files = new HashMap<>();
        for (Uri sourceUri : extrasToCopy) {
            String displayName = getDisplayNameForUri(sourceUri);
            if (displayName == null) {
                displayName = generateDisplayName();
            }
            files.put(sourceUri, displayName);
        }

        mShareName.setText(TextUtils.join("\n", files.values()));
        if (files.size() > 1) {
            mShareName.setFocusable(false);
            mShareName.setKeyListener(null);
        }
        mShareTitle.setText(getResources().getQuantityString(R.plurals.file_name_title,
                files.size() > 1 ? 2 : 1));
        mShareButton.setOnClickListener(view -> {
            if (files.size() == 1)
                files.entrySet().iterator().next().setValue(mShareName.getText().toString());
            Folder folder = (Folder) mFoldersSpinner.getSelectedItem();
            new CopyFilesTask(files, folder).execute();
        });
        mCancelButton.setOnClickListener(view -> finish());
    }

    // Taken from ownCloud Android
    // (GNU GPL v2.0 https://github.com/owncloud/android/blob/master/LICENSE.txt)
    /**
     * Generate file name for new file.
     */
    private String generateDisplayName() {
        Date date = new Date(System.currentTimeMillis());
        DateFormat df = DateFormat.getDateTimeInstance();
        return String.format(getResources().getString(R.string.file_name_template),
                df.format(date));
    }

    /**
     * Get file name from uri.
     */
    private String getDisplayNameForUri(Uri uri) {
        String displayName;

        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            displayName = uri.getLastPathSegment();
        } else {
            displayName = getDisplayNameFromContentResolver(uri);
            if (displayName == null) {
                // last chance to have a name
                displayName = uri.getLastPathSegment().replaceAll("\\s", "");
            }

            // Add best possible extension
            int index = displayName.lastIndexOf(".");
            if (index == -1 || MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(displayName.substring(index + 1)) == null) {
                String mimeType = this.getContentResolver().getType(uri);
                String extension = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(mimeType);
                if (extension != null) {
                    displayName += "." + extension;
                }
            }
        }

        // Replace path separator characters to avoid inconsistent paths
        return displayName != null ? displayName.replaceAll("/", "-") : null;
    }

    /**
     * Get file name from content uri (content://).
     */
    private String getDisplayNameFromContentResolver(Uri uri) {
        String displayName = null;
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            String displayNameColumn;
            if (mimeType.startsWith("image/")) {
                displayNameColumn = MediaStore.Images.ImageColumns.DISPLAY_NAME;
            } else if (mimeType.startsWith("video/")) {
                displayNameColumn = MediaStore.Video.VideoColumns.DISPLAY_NAME;

            } else if (mimeType.startsWith("audio/")) {
                displayNameColumn = MediaStore.Audio.AudioColumns.DISPLAY_NAME;

            } else {
                displayNameColumn = MediaStore.Files.FileColumns.DISPLAY_NAME;
            }

            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(
                        uri,
                        new String[]{displayNameColumn},
                        null,
                        null,
                        null
                );
                if (cursor != null) {
                    cursor.moveToFirst();
                    displayName = cursor.getString(cursor.getColumnIndex(displayNameColumn));
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not retrieve display name for " + uri.toString(), e);
                // nothing else, displayName keeps null
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return displayName;
    }

    private class CopyFilesTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog mProgress;
        private Map<Uri, String> mFiles;
        private Folder mFolder;
        private int mCopied = 0, mIgnored = 0;

        CopyFilesTask(Map<Uri, String> files, Folder folder) {
            this.mFiles = files;
            this.mFolder = folder;
        }

        protected void onPreExecute() {
            mProgress = ProgressDialog.show(ShareActivity.this, null,
                    getString(R.string.copy_progress), true);
        }

        protected Boolean doInBackground(Void... params) {
            boolean isError = false;
            for (Map.Entry<Uri, String> entry : mFiles.entrySet()) {
                InputStream inputStream = null;
                String outPath = mFolder.path + entry.getValue();
                try {
                    File outFile = new File(outPath);
                    if (outFile.isFile()) {
                        mIgnored++;
                        continue;
                    }
                    inputStream = getContentResolver().openInputStream(entry.getKey());
                    Files.asByteSink(outFile).writeFrom(inputStream);
                    mCopied++;
                } catch (FileNotFoundException e) {
                    Log.e(TAG, String.format("Can't find input file \"%s\" to copy",
                            entry.getKey()), e);
                    isError = true;
                } catch (IOException e) {
                    Log.e(TAG, String.format("IO exception during file \"%s\" sharing",
                            entry.getKey()), e);
                    isError = true;
                } finally {
                    try {
                        if (inputStream != null)
                            inputStream.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Exception on input/output stream close", e);
                    }
                }
            }
            return isError;
        }

        protected void onPostExecute(Boolean isError) {
            mProgress.dismiss();
            Toast.makeText(ShareActivity.this, mIgnored > 0 ?
                    getResources().getQuantityString(R.plurals.copy_success_partially, mCopied,
                            mCopied, mFolder.label, mIgnored) :
                    getResources().getQuantityString(R.plurals.copy_success, mCopied, mCopied,
                            mFolder.label),
                    Toast.LENGTH_LONG).show();
            if (isError) {
                Toast.makeText(ShareActivity.this, getString(R.string.copy_exception),
                        Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    }
}
