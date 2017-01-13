package com.nutomic.syncthingandroid.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.SyncthingService;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class ShareActivity extends SyncthingActivity
        implements SyncthingActivity.OnServiceConnectedListener, SyncthingService.OnApiChangeListener {
    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (currentState != SyncthingService.State.ACTIVE)
            return;

        Toast.makeText(this, "test", Toast.LENGTH_SHORT).show();
        if (getApi() == null)
            return;

        Toast.makeText(this, "test1", Toast.LENGTH_SHORT).show();
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Toast.makeText(this, action, Toast.LENGTH_SHORT).show();
        Toast.makeText(this, type, Toast.LENGTH_SHORT).show();
        Object test = TextUtils.join(",", intent.getExtras().keySet());
        Toast.makeText(this, test != null ? test.toString() : "null", Toast.LENGTH_LONG).show();
        Uri test1 = (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
        Toast.makeText(this, test1 != null ? test1.toString() : "null", Toast.LENGTH_SHORT).show();

        List<Folder> folders = getApi().getFolders();

        List<String> spinnerArray = new ArrayList<String>();
        for(Folder item : folders){
            Toast.makeText(this, item.label, Toast.LENGTH_LONG).show();
            Toast.makeText(this, item.id, Toast.LENGTH_LONG).show();
            spinnerArray.add(item.label);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = (Spinner) findViewById(R.id.spinner2);
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

        /*// Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);
        populateAutoComplete();

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);*/
    }
}

