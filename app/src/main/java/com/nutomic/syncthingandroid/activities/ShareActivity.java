package com.nutomic.syncthingandroid.activities;

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.io.Files;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.databinding.ActivityShareBinding;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.Util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Shares incoming files to syncthing folders.
 * <p>
 * {@link #getDisplayNameForUri} and {@link #getDisplayNameFromContentResolver} are taken from
 * ownCloud Android {@see https://github.com/owncloud/android/blob/79664304fdb762b2e04f1ac505f50d0923ddd212/src/com/owncloud/android/utils/UriUtils.java#L193}
 */
public class ShareActivity extends StateDialogActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnServiceStateChangeListener {

    private static final String TAG = "ShareActivity";
    private static final String PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER = "previously_selected_syncthing_folder";

    public static final String PREF_FOLDER_SAVED_SUBDIRECTORY = "saved_sub_directory_";

    private TextView mSubDirectoryTextView;

    private Spinner mFoldersSpinner;

    private ActivityShareBinding binding;

    @Inject
    SharedPreferences mPreferences;

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE || getApi() == null)
            return;

        List<Folder> folders = getApi().getFolders();

        // Get the index of the previously selected folder.
        int folderIndex = 0;
        String savedFolderId = mPreferences.getString(PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER, "");
        for (Folder folder : folders) {
            if (folder.id.equals(savedFolderId)) {
                folderIndex = folders.indexOf(folder);
                break;
            }
        }

        ArrayAdapter<Folder> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, folders);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.folders.setAdapter(adapter);
        binding.folders.setSelection(folderIndex);
    }

    @Override
    public void onServiceConnected() {
        getService().registerOnServiceStateChangeListener(this);
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
        ((SyncthingApp) getApplication()).component().inject(this);
        binding = ActivityShareBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        registerOnServiceConnectedListener(this);

        mSubDirectoryTextView = findViewById(R.id.sub_directory_Textview);
        mFoldersSpinner = findViewById(R.id.folders);

        // TODO: add support for EXTRA_TEXT (notes, memos sharing)
        ArrayList<Uri> extrasToCopy = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null)
                extrasToCopy.add(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction())) {
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

        binding.name.setText(TextUtils.join("\n", files.values()));
        if (files.size() > 1) {
            binding.name.setFocusable(false);
            binding.name.setKeyListener(null);
        }
        binding.namesTitle.setText(getResources().getQuantityString(R.plurals.file_name_title,
                files.size() > 1 ? 2 : 1));
        binding.shareButton.setOnClickListener(view -> {
            if (files.size() == 1)
                files.entrySet().iterator().next().setValue(binding.name.getText().toString());
            Folder folder = (Folder) mFoldersSpinner.getSelectedItem();
            File directory = new File(folder.path, getSavedSubDirectory());
            CopyFilesTask mCopyFilesTask = new CopyFilesTask(this, files, folder, directory);
            mCopyFilesTask.execute();
        });

        mFoldersSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSubDirectoryTextView.setText(getSavedSubDirectory());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        binding.browseButton.setOnClickListener(view -> {
            Folder folder = (Folder) mFoldersSpinner.getSelectedItem();
            File initialDirectory = new File(folder.path, getSavedSubDirectory());
            startActivityForResult(FolderPickerActivity.createIntent(getApplicationContext(),
                    initialDirectory.getAbsolutePath(), folder.path),
                    FolderPickerActivity.DIRECTORY_REQUEST_CODE);
        });

        binding.cancelButton.setOnClickListener(view -> finish());
        mSubDirectoryTextView.setText(getSavedSubDirectory());
    }

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

            Cursor cursor = getContentResolver().query(
                    uri,
                    new String[]{displayNameColumn},
                    null,
                    null,
                    null
            );
            if (cursor != null) {
                cursor.moveToFirst();
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(displayNameColumn));
            }
            if (cursor != null) {
                cursor.close();
            }
        }
        return displayName;
    }

    /**
     * Get the previously selected sub directory for the currently selected Syncthing folder.
     */
    private String getSavedSubDirectory() {
        Folder selectedFolder = (Folder) mFoldersSpinner.getSelectedItem();
        String savedSubDirectory = "";

        if (selectedFolder != null) {
            savedSubDirectory = mPreferences.getString(PREF_FOLDER_SAVED_SUBDIRECTORY + selectedFolder.id, "");
        }

        return savedSubDirectory;
    }

    private static class CopyFilesTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<ShareActivity> refShareActivity;
        private ProgressDialog mProgress;
        private final Map<Uri, String> mFiles;
        private final Folder mFolder;
        private final File mDirectory;
        private int mCopied = 0, mIgnored = 0;

        CopyFilesTask(ShareActivity context, Map<Uri, String> files, Folder folder, File directory) {
            refShareActivity = new WeakReference<>(context);
            this.mFiles = files;
            this.mFolder = folder;
            this.mDirectory = directory;
        }

        protected void onPreExecute() {
            // Get a reference to the activity if it is still there.
            ShareActivity shareActivity = refShareActivity.get();
            // shareActivity cannot be null before the task executes.
            mProgress = ProgressDialog.show(shareActivity, null,
                    shareActivity.getString(R.string.copy_progress), true);
        }

        protected Boolean doInBackground(Void... params) {
            // Get a reference to the activity if it is still there.
            ShareActivity shareActivity = refShareActivity.get();
            if (shareActivity == null || shareActivity.isFinishing()) {
                cancel(true);
                return true;
            }

            boolean isError = false;
            for (Map.Entry<Uri, String> entry : mFiles.entrySet()) {
                InputStream inputStream = null;
                try {
                    File outFile = new File(mDirectory, entry.getValue());
                    if (outFile.isFile()) {
                        mIgnored++;
                        continue;
                    }
                    inputStream = shareActivity.getContentResolver().openInputStream(entry.getKey());
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
                    } catch (IOException e) {
                        Log.w(TAG, "Exception on input/output stream close", e);
                    }
                }
            }
            return isError;
        }

        protected void onPostExecute(Boolean isError) {
            // Get a reference to the activity if it is still there.
            ShareActivity shareActivity = refShareActivity.get();
            if (shareActivity == null || shareActivity.isFinishing()) {
                return;
            }
            Util.dismissDialogSafe(mProgress, shareActivity);
            Toast.makeText(shareActivity, mIgnored > 0 ?
                            shareActivity.getResources().getQuantityString(R.plurals.copy_success_partially, mCopied,
                                    mCopied, mFolder.label, mIgnored) :
                            shareActivity.getResources().getQuantityString(R.plurals.copy_success, mCopied, mCopied,
                                    mFolder.label),
                    Toast.LENGTH_LONG).show();
            if (isError) {
                Toast.makeText(shareActivity, shareActivity.getString(R.string.copy_exception),
                        Toast.LENGTH_SHORT).show();
            }
            shareActivity.finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mFoldersSpinner.getSelectedItem() != null) {
            Folder selectedFolder = (Folder) mFoldersSpinner.getSelectedItem();
            mPreferences.edit()
                    .putString(PREF_PREVIOUSLY_SELECTED_SYNCTHING_FOLDER, selectedFolder.id)
                    .apply();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FolderPickerActivity.DIRECTORY_REQUEST_CODE && resultCode == RESULT_OK) {
            Folder selectedFolder = (Folder) mFoldersSpinner.getSelectedItem();
            String folderDirectory = Util.formatPath(selectedFolder.path);
            String subDirectory = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            //Remove the parent directory from the string, so it is only the Sub directory that is displayed to the user.
            subDirectory = subDirectory.replace(folderDirectory, "");
            mSubDirectoryTextView.setText(subDirectory);

            mPreferences
                    .edit().putString(PREF_FOLDER_SAVED_SUBDIRECTORY + selectedFolder.id, subDirectory)
                    .apply();
        }
    }
}
