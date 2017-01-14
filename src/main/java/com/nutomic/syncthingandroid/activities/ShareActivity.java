package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ShareActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener {
    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE || getApi() == null)
            return;

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Object extras = TextUtils.join(",", intent.getExtras().keySet());
        Uri path = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);

        List<Folder> folders = getApi().getFolders();

        List<String> spinnerArray = new ArrayList<String>();
        for(Folder item : folders){
            spinnerArray.add(item.label);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

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
        mShareButton.setOnClickListener(view -> {
            String label = (String) mFoldersSpinner.getSelectedItem();
            List<Folder> folders = getApi().getFolders();
            Folder folder = null;
            for(Folder item : folders){
                if (item.label.equals(label))
                {
                    folder = item;
                    break;
                }
            }
            if (folder == null)
                return;
            Uri in = (Uri) getIntent().getExtras().get(Intent.EXTRA_STREAM);
            if (in == null)
                return;
            String inFile = getPath(in);
            FileChannel source = null;
            FileChannel destination = null;
            try {
                source = new FileInputStream(inFile).getChannel();
                destination = new FileOutputStream(folder.path+"asd.mp3").getChannel();
                if (source != null) {
                    destination.transferFrom(source, 0, source.size());
                }
                if (source != null) {
                    source.close();
                }
                destination.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
}

