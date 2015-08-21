package com.nutomic.syncthingandroid.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Bundle;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.SelectFolderFragment;

import java.util.ArrayList;

public class ShareToFolderActivity extends SyncthingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_to_folder);
        mFolderFragment = (SelectFolderFragment) getSupportFragmentManager()
                .findFragmentById(R.id.share_to_folder_fragment);
        mFolderFragment.setHasOptionsMenu(false);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        ArrayList<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            uris.add(uri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        mFolderFragment.setFiles(uris);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnApiChangeListener(mFolderFragment);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getService().unregisterOnApiChangeListener(mFolderFragment);
    }

    private SelectFolderFragment mFolderFragment;
}
