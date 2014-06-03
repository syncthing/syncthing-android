package com.nutomic.syncthingandroid.gui;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.text.InputType;
import android.view.MenuItem;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

public class SettingsActivity extends PreferenceActivity
		implements Preference.OnPreferenceChangeListener {

	private static final String SYNCTHING_OPTIONS_KEY = "syncthing_options";

	private static final String SYNCTHING_GUI_KEY = "syncthing_gui";

	private static final String SYNCTHING_VERSION_KEY = "syncthing_version";

	private Preference mVersion;

	private PreferenceScreen mOptionsScreen;

	private PreferenceScreen mGuiScreen;

	private SyncthingService mSyncthingService;

	/**
	 * Binds to service and sets syncthing preferences from Rest API.
	 */
	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mVersion.setSummary(mSyncthingService.getApi().getVersion());

			for (int i = 0; i < mOptionsScreen.getPreferenceCount(); i++) {
				Preference pref = mOptionsScreen.getPreference(i);
				pref.setOnPreferenceChangeListener(SettingsActivity.this);
				String value = mSyncthingService.getApi()
						.getValue(RestApi.TYPE_OPTIONS, pref.getKey());
				applyPreference(pref, value);
			}

			for (int i = 0; i < mGuiScreen.getPreferenceCount(); i++) {
				Preference pref = mGuiScreen.getPreference(i);
				pref.setOnPreferenceChangeListener(SettingsActivity.this);
				String value = mSyncthingService.getApi()
						.getValue(RestApi.TYPE_GUI, pref.getKey());
				applyPreference(pref, value);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	/**
	 * Applies the given value to the preference.
	 *
	 * If pref is an EditTextPreference, setText is used and the value shown as summary. If pref is
	 * a CheckBoxPreference, setChecked is used (by parsing value as Boolean).
	 */
	private void applyPreference(Preference pref, String value) {
		if (pref instanceof EditTextPreference) {
			((EditTextPreference) pref).setText(value);
			pref.setSummary(value);
		}
		else if (pref instanceof CheckBoxPreference) {
			((CheckBoxPreference) pref).setChecked(Boolean.parseBoolean(value));
		}
	}

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
		PreferenceScreen screen = getPreferenceScreen();
		mVersion = screen.findPreference(SYNCTHING_VERSION_KEY);
		mOptionsScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_OPTIONS_KEY);
		mGuiScreen = (PreferenceScreen) screen.findPreference(SYNCTHING_GUI_KEY);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
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

	/**
	 * Sends the updated value to {@link }RestApi}, and sets it as the summary
	 * for EditTextPreference.
	 */
	@Override
	public boolean onPreferenceChange(Preference preference, Object o) {
		if (preference instanceof EditTextPreference) {
			String value = (String) o;
			preference.setSummary(value);
			EditTextPreference etp = (EditTextPreference) preference;
			if (etp.getEditText().getInputType() == InputType.TYPE_CLASS_NUMBER) {
				o = Integer.parseInt((String) o);
			}
		}

		if (mOptionsScreen.findPreference(preference.getKey()) != null) {
			mSyncthingService.getApi().setValue(RestApi.TYPE_OPTIONS, preference.getKey(), o,
					preference.getKey().equals("ListenAddress"));
			return true;
		}
		else if (mGuiScreen.findPreference(preference.getKey()) != null) {
			mSyncthingService.getApi().setValue(RestApi.TYPE_GUI, preference.getKey(), o, false);
			return true;
		}
		return false;
	}
}
