package com.nutomic.syncthingandroid.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;

import com.nutomic.syncthingandroid.service.Constants;

/**
 * Provides a themed instance of AppCompatActivity.
 */
public class ThemedAppCompatActivity extends AppCompatActivity {

    private static final String LIGHT_THEME = "1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Load theme.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Integer prefAppTheme = Integer.parseInt(prefs.getString(Constants.PREF_APP_THEME, LIGHT_THEME));
        AppCompatDelegate.setDefaultNightMode(prefAppTheme);
        super.onCreate(savedInstanceState);
    }

}
