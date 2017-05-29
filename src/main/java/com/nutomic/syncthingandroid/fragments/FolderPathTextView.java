package com.nutomic.syncthingandroid.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;

/**
 * Simply displays a clickable view that allows for selecting and displaying a folder path.
 * The selected path can be accessed by adding a textwatcher in the parent activity/fragment to fragment_directory_text_view.
 */

public class FolderPathTextView extends Fragment {
    private static final int DIRECTORY_REQUEST_CODE = 234;

    private TextView mFolderPathView;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mFolderPathView = (TextView) inflater.inflate(R.layout.fragment_directory_text_view, container);
        mFolderPathView.setOnClickListener(mFolderPathViewClickListener);

        return mFolderPathView;
    }

    private final View.OnClickListener mFolderPathViewClickListener = v -> {
        Intent intent = new Intent(getActivity(), FolderPickerActivity.class);
        String currentPath = getCurrentPath();
        if (!TextUtils.isEmpty(currentPath)) {
            intent.putExtra(FolderPickerActivity.EXTRA_INITIAL_DIRECTORY, currentPath);
        }
        startActivityForResult(intent, DIRECTORY_REQUEST_CODE);
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (isDirectoryResult(requestCode, resultCode)) {
            updateFolderPath(data);
        }
    }

    private void updateFolderPath(Intent data) {
        mFolderPathView.setText(data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY));
    }

    private String getCurrentPath() {
        return mFolderPathView.getText().toString();
    }

    private boolean isDirectoryResult(int requestCode, int resultCode) {
        return resultCode == Activity.RESULT_OK && requestCode == DIRECTORY_REQUEST_CODE;
    }
}
