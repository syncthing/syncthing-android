package com.nutomic.syncthingandroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.nutomic.syncthingandroid.service.GetTask;
import com.nutomic.syncthingandroid.service.SyncthingService;

public class SettingsActivity extends PreferenceActivity {

	private static final String REPORT_ISSUE_KEY = "report_issue";

	private static final String SYNCTHING_VERSION_KEY = "syncthing_version";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.settings);
		final PreferenceScreen screen = getPreferenceScreen();
		final Preference version = screen.findPreference(SYNCTHING_VERSION_KEY);
		new GetTask() {
			@Override
			protected void onPostExecute(String versionName) {
				version.setSummary((versionName != null)
						? versionName
						: getString(R.string.syncthing_version_error));
			}
		}.execute(SyncthingService.SYNCTHING_URL + SyncthingService.PATH_VERSION);
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
