package com.nutomic.syncthingandroid.activities;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class ShareActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener {
    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE || getApi() == null)
            return;

        List<Folder> folders = getApi().getFolders();

        ArrayAdapter<Folder> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, folders);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = (Spinner) findViewById(R.id.folders_spinner);
        sItems.setAdapter(adapter);
    }

    @Override
    public void onServiceConnected() {
        getService().registerOnApiChangeListener(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        registerOnServiceConnectedListener(this);

        Spinner mFoldersSpinner = (Spinner) findViewById(R.id.folders_spinner);
        Button mShareButton = (Button) findViewById(R.id.share_button);
        EditText mShareName = (EditText) findViewById(R.id.share_name);

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
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            finish();
        }

        Map<String, String> files = new HashMap<>();
        for (Uri sourceUri : extrasToCopy) {
            String displayName = getDisplayNameForUri(sourceUri);
            if (displayName == null) {
                displayName = generateDisplayName();
            }
            String path = null;
            if (ContentResolver.SCHEME_CONTENT.equals(sourceUri.getScheme())) {
                path = getPath(sourceUri);
            } else if (ContentResolver.SCHEME_FILE.equals(sourceUri.getScheme())) {
                path = sourceUri.getPath();
            }

            files.put(path, displayName);
        }

        mShareName.setText(TextUtils.join("\n", files.values()));
        if (files.size() > 1)
            mShareName.setEnabled(false);

        mShareButton.setOnClickListener(view -> {
            if (files.size() == 1)
                files.entrySet().iterator().next().setValue(mShareName.getText().toString());
            Folder folder = (Folder) mFoldersSpinner.getSelectedItem();
            int copied = 0;
            for (Map.Entry<String, String> entry : files.entrySet()) {
                FileChannel source = null;
                FileChannel destination = null;
                try {
                    source = new FileInputStream(entry.getKey()).getChannel();
                    destination = new FileOutputStream(folder.path+entry.getValue()).getChannel();
                    if (source != null) {
                        destination.transferFrom(source, 0, source.size());
                    }
                    if (source != null) {
                        source.close();
                    }
                    destination.close();
                    copied++;
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Exception", Toast.LENGTH_SHORT).show();
                }
            }
            Toast.makeText(this, copied+" files copied to folder \""+folder.label+"\"",
                Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    // copyright owncloud
    private String generateDisplayName() {
        Date date = new Date(System.currentTimeMillis());
        DateFormat df = DateFormat.getDateTimeInstance();
        return "new_file-" + df.format(date);
    }

    private String getDisplayNameForUri(Uri uri) {

        if (uri == null) {
            throw new IllegalArgumentException("Received NULL!");
        }

        String displayName = null;

        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            displayName = uri.getLastPathSegment();     // ready to return

        } else {
            // content: URI

            displayName = getDisplayNameFromContentResolver(uri);

            try {
                if (displayName == null) {
                    // last chance to have a name
                    displayName = uri.getLastPathSegment().replaceAll("\\s", "");
                }

                // Add best possible extension
                int index = displayName.lastIndexOf(".");
                if (index == -1 || MimeTypeMap.getSingleton().
                        getMimeTypeFromExtension(displayName.substring(index + 1)) == null) {
                    String mimeType = this.getContentResolver().getType(uri);
                    String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    if (extension != null) {
                        displayName += "." + extension;
                    }
                }

            } catch (Exception e) {
                //Log_OC.e(TAG, "No way to get a display name for " + uri.toString());
            }
        }

        // Replace path separator characters to avoid inconsistent paths
        return displayName.replaceAll("/", "-");
    }


    private String getDisplayNameFromContentResolver(Uri uri) {
        String displayName = null;
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            String displayNameColumn;
            if (mimeType.toLowerCase().startsWith("image/")) {
                displayNameColumn = MediaStore.Images.ImageColumns.DISPLAY_NAME;

            } else if (mimeType.toLowerCase().startsWith("video/")) {
                displayNameColumn = MediaStore.Video.VideoColumns.DISPLAY_NAME;

            } else if (mimeType.toLowerCase().startsWith("audio/")) {
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
                //Log_OC.e(TAG, "Could not retrieve display name for " + uri.toString());
                // nothing else, displayName keeps null

            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return displayName;
    }
}

