package com.nutomic.syncthingandroid;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

public class SettingsActivity extends PreferenceActivity {

	private static final String REPORT_ISSUE_KEY = "report_issue";

	private static final String SYNCTHING_VERSION_KEY = "syncthing_version";

	private SyncthingService mSyncthingService;

	private ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			final PreferenceScreen screen = getPreferenceScreen();
			final Preference version = screen.findPreference(SYNCTHING_VERSION_KEY);
			version.setSummary(mSyncthingService.getApi().getVersion());
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	/**
	 * Loads layout, sets version from Rest API.
	 *
	 * Manual target API as we manually check if ActionBar is available (for ActionBar back button).
	 */
	@Override
	@TargetApi(11)
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// There is currently no way to get ActionBar in PreferenceActivity on pre-honeycomb with
		// compatibility library, so we'll have to do a version check.
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}

		bindService(new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);

		addPreferencesFromResource(R.xml.settings);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
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

	/**
	 * Handles ActionBar back selected.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				NavUtils.navigateUpFromSameTask(this);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
