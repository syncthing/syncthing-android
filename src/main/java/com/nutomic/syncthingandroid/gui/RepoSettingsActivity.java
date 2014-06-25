package com.nutomic.syncthingandroid.gui;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.util.ExtendedCheckBoxPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows repo details and allows changing them.
 */
public class RepoSettingsActivity extends PreferenceActivity
		implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
		SyncthingService.OnApiChangeListener {

	public static final String ACTION_CREATE = "create";

	public static final String ACTION_EDIT = "edit";

	public static final String KEY_REPO_ID = "repository_id";

	private static final String KEY_NODE_SHARED = "node_shared";

	private SyncthingService mSyncthingService;

	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mSyncthingService.registerOnApiChangeListener(RepoSettingsActivity.this);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	private RestApi.Repository mRepo;

	private EditTextPreference mRepositoryId;

	private EditTextPreference mDirectory;

	private CheckBoxPreference mRepositoryMaster;

	private PreferenceScreen mNodes;

	private CheckBoxPreference mVersioning;

	private EditTextPreference mVersioningKeep;

	private Preference mDelete;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getIntent().getAction().equals(ACTION_CREATE)) {
			addPreferencesFromResource(R.xml.repo_settings_create);
		}
		else if (getIntent().getAction().equals(ACTION_EDIT)) {
			addPreferencesFromResource(R.xml.repo_settings_edit);
		}

		mRepositoryId = (EditTextPreference) findPreference("repository_id");
		mRepositoryId.setOnPreferenceChangeListener(this);
		mDirectory = (EditTextPreference) findPreference("directory");
		mDirectory.setOnPreferenceChangeListener(this);
		mRepositoryMaster = (CheckBoxPreference) findPreference("repository_master");
		mRepositoryMaster.setOnPreferenceChangeListener(this);
		mNodes = (PreferenceScreen) findPreference("nodes");
		mVersioning = (CheckBoxPreference) findPreference("versioning");
		mVersioning.setOnPreferenceChangeListener(this);
		mVersioningKeep = (EditTextPreference) findPreference("versioning_keep");
		mVersioningKeep.setOnPreferenceChangeListener(this);
		if (getIntent().getAction().equals(ACTION_EDIT)) {
			mDelete = findPreference("delete");
			mDelete.setOnPreferenceClickListener(this);
		}

		bindService(new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onApiChange(boolean isAvailable) {
		if (!isAvailable) {
			finish();
			return;
		}

		if (getIntent().getAction().equals(ACTION_CREATE)) {
			setTitle(R.string.create_repo);
			mRepo = new RestApi.Repository();
			mRepo.ID = "";
			mRepo.Directory = "";
			mRepo.Nodes = new ArrayList<RestApi.Node>();
			mRepo.Versioning = new RestApi.Versioning();
		}
		else if (getIntent().getAction().equals(ACTION_EDIT)) {
			setTitle(R.string.edit_repo);
			List<RestApi.Repository> repos = mSyncthingService.getApi().getRepos();
			for (int i = 0; i < repos.size(); i++) {
				if (repos.get(i).ID.equals(getIntent().getStringExtra(KEY_REPO_ID))) {
					mRepo = repos.get(i);
					break;
				}
			}
		}

		mRepositoryId.setText(mRepo.ID);
		mRepositoryId.setSummary(mRepo.ID);
		mDirectory.setText(mRepo.Directory);
		mDirectory.setSummary(mRepo.Directory);
		mRepositoryMaster.setChecked(mRepo.ReadOnly);
		List<RestApi.Node> nodesList = mSyncthingService.getApi().getNodes();
		for (RestApi.Node n : nodesList) {
			ExtendedCheckBoxPreference cbp =
					new ExtendedCheckBoxPreference(RepoSettingsActivity.this, n);
			cbp.setTitle(n.Name);
			cbp.setKey(KEY_NODE_SHARED);
			cbp.setOnPreferenceChangeListener(RepoSettingsActivity.this);
			cbp.setChecked(false);
			for (RestApi.Node n2 : mRepo.Nodes) {
				if (n2.NodeID.equals(n.NodeID)) {
					cbp.setChecked(true);
				}
			}
			mNodes.addPreference(cbp);
		}
		mVersioning.setChecked(mRepo.Versioning instanceof RestApi.SimpleVersioning);
		if (mVersioning.isChecked()) {
			mVersioningKeep.setText(mRepo.Versioning.getParams().get("keep"));
			mVersioningKeep.setSummary(mRepo.Versioning.getParams().get("keep"));
			mVersioningKeep.setEnabled(true);
		}
		else {
			mVersioningKeep.setEnabled(false);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.repo_settings_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.create).setVisible(getIntent().getAction().equals(ACTION_CREATE));
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.create:
				if (mRepo.ID.equals("")) {
					Toast.makeText(this, R.string.repo_id_required, Toast.LENGTH_LONG).show();
					return true;
				}
				if (mRepo.Directory.equals("")) {
					Toast.makeText(this, R.string.repo_path_required, Toast.LENGTH_LONG).show();
					return true;
				}
				mSyncthingService.getApi().editRepo(mRepo, true);
				finish();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object o) {
		if (preference instanceof EditTextPreference) {
			EditTextPreference pref = (EditTextPreference) preference;
			pref.setSummary((String) o);
		}

		if (preference.equals(mRepositoryId)) {
			mRepo.ID = (String) o;
			repositoryUpdated();
			return true;
		}
		else if (preference.equals(mDirectory)) {
			mRepo.Directory = (String) o;
			repositoryUpdated();
			return true;
		}
		else if (preference.equals(mRepositoryMaster)) {
			mRepo.ReadOnly = (Boolean) o;
			repositoryUpdated();
			return true;
		}
		else if (preference.getKey().equals(KEY_NODE_SHARED)) {
			ExtendedCheckBoxPreference pref = (ExtendedCheckBoxPreference) preference;
			RestApi.Node node = (RestApi.Node) pref.getObject();
			if ((Boolean) o) {
				mRepo.Nodes.add(node);
			}
			else {
				for (RestApi.Node n : mRepo.Nodes) {
					if (n.NodeID.equals(node.NodeID)) {
						mRepo.Nodes.remove(n);
					}
				}
			}
			repositoryUpdated();
			return true;
		}
		else if (preference.equals(mVersioning)) {
			mVersioningKeep.setEnabled((Boolean) o);
			if ((Boolean) o) {
				RestApi.SimpleVersioning v = new RestApi.SimpleVersioning();
				mRepo.Versioning = v;
				v.setParams(5);
				mVersioningKeep.setText("5");
				mVersioningKeep.setSummary("5");
			}
			else {
				mRepo.Versioning = new RestApi.Versioning();
			}
			repositoryUpdated();
			return true;
		}
		else if (preference.equals(mVersioningKeep)) {
			((RestApi.SimpleVersioning) mRepo.Versioning)
					.setParams(Integer.parseInt((String) o));
			repositoryUpdated();
			return true;
		}

		return false;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.equals(mDelete)) {
			new AlertDialog.Builder(this)
					.setMessage(R.string.delete_repo_confirm)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mSyncthingService.getApi().deleteRepo(mRepo);
							finish();
						}
					})
					.setNegativeButton(android.R.string.no, null)
					.show();
			return true;
		}
		return false;
	}

	private void repositoryUpdated() {
		if (getIntent().getAction().equals(ACTION_EDIT)) {
			mSyncthingService.getApi().editRepo(mRepo, false);
		}
	}

}
