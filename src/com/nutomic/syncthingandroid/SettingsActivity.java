package com.nutomic.syncthingandroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.nutomic.syncthingandroid.R;

public class SettingsActivity extends PreferenceActivity {

	private static final String REPORT_ISSUE_KEY = "report_issue";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
	}


	/**
	 * Opens issue tracker when that preference is clicked.
	 */
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
										 Preference preference) {
		if (REPORT_ISSUE_KEY.equals(preference.getKey())) {
			startActivity(new Intent(Intent.ACTION_VIEW,
					Uri.parse(getString(R.string.issue_tracker_url))));
		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}
}
