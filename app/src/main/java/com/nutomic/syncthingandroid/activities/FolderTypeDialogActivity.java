package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;

import java.util.Arrays;
import java.util.List;

public class FolderTypeDialogActivity extends AppCompatActivity {

    public static final String EXTRA_FOLDER_TYPE = "com.github.catfriend1.syncthingandroid.activities.FolderTypeDialogActivity.FOLDER_TYPE";
    public static final String EXTRA_RESULT_FOLDER_TYPE = "com.github.catfriend1.syncthingandroid.activities.FolderTypeDialogActivity.EXTRA_RESULT_FOLDER_TYPE";

    private String selectedType;

    private static final List<String> mTypes = Arrays.asList(
        Constants.FOLDER_TYPE_SEND_RECEIVE,
        Constants.FOLDER_TYPE_SEND_ONLY,
        Constants.FOLDER_TYPE_RECEIVE_ONLY
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_foldertype_dialog);
        if (savedInstanceState == null) {
            selectedType = getIntent().getStringExtra(EXTRA_FOLDER_TYPE);
        }
        initiateFinishBtn();
        initiateSpinner();
    }

    private void initiateFinishBtn() {
        Button finishBtn = findViewById(R.id.finish_btn);
        finishBtn.setOnClickListener(v -> {
            saveConfiguration();
            finish();
        });
    }

    private void saveConfiguration() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT_FOLDER_TYPE, selectedType);
        setResult(Activity.RESULT_OK, intent);
    }

    private void initiateSpinner() {
        Spinner folderTypeSpinner = findViewById(R.id.folderTypeSpinner);
        folderTypeSpinner.setSelection(mTypes.indexOf(selectedType));
        folderTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != mTypes.indexOf(selectedType)) {
                    selectedType = mTypes.get(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // This is not allowed.
            }
        });
    }

    @Override
    public void onBackPressed() {
        saveConfiguration();
        super.onBackPressed();
    }
}
