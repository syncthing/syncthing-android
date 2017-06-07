package com.nutomic.syncthingandroid.fragments.dialog;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.fragments.NumberPickerFragment;

import java.util.concurrent.TimeUnit;


/**
 * Contains the configuration options for Staggered file versioning.
 */

public class StaggeredVersioningFragment extends Fragment {

    private View mView;

    private Bundle mArguments;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_staggered_versioning, container, false);
        mArguments = getArguments();
        fillArguments();
        updateNumberPicker();
        initiateVersionsPathTextView();
        return mView;
    }

    private void fillArguments() {
        if (missingParameters()) {
            mArguments.putString("maxAge", "0");
            mArguments.putString("versionsPath", "");
        }
    }

    private boolean missingParameters() {
        return !mArguments.containsKey("maxAge");
    }

    //The maxAge parameter is displayed in days but stored in seconds since Syncthing needs it in seconds.
    //A NumberPickerFragment is nested in the fragment_staggered_versioning layout, the values for it are update below.
    private void updateNumberPicker() {
        NumberPickerFragment numberPicker = (NumberPickerFragment) getChildFragmentManager().findFragmentByTag("numberpicker_staggered_versioning");
        numberPicker.updateNumberPicker(100, 0, getMaxAgeInDays());
        numberPicker.setOnValueChangedLisenter((picker, oldVal, newVal) -> updatePreference("maxAge", (String.valueOf(TimeUnit.DAYS.toSeconds(newVal)))));
    }

    private void initiateVersionsPathTextView() {
        TextView directoryTextView = (TextView) mView.findViewById(R.id.fragmentVersionsPath);
        directoryTextView.setText(getVersionsPath());
        directoryTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreference("versionsPath", s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private String getVersionsPath() {
        return mArguments.getString("versionsPath");
    }

    private void updatePreference(String key, String newValue) {
        getArguments().putString(key, newValue);
    }

    private int getMaxAgeInDays() {
        return (int) TimeUnit.SECONDS.toDays(Long.valueOf(mArguments.getString("maxAge")));
    }
}
