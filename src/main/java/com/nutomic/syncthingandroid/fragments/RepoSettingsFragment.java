package com.nutomic.syncthingandroid.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.v4.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.FolderPickerActivity;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.util.ExtendedCheckBoxPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows repo details and allows changing them.
 */
public class RepoSettingsFragment extends PreferenceFragment
        implements SyncthingActivity.OnServiceConnectedListener,
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        SyncthingService.OnApiChangeListener {

    private static final int DIRECTORY_REQUEST_CODE = 234;

    /**
     * The ID of the repo to be edited. To be used with {@link com.nutomic.syncthingandroid.activities.SettingsActivity#EXTRA_IS_CREATE}
     * set to false.
     */
    public static final String EXTRA_REPO_ID = "repo_id";

    private static final String KEY_NODE_SHARED = "node_shared";

    private SyncthingService mSyncthingService;

    private RestApi.Repo mRepo;

    private EditTextPreference mRepoId;

    private Preference mDirectory;

    private CheckBoxPreference mRepoMaster;

    private PreferenceScreen mNodes;

    private CheckBoxPreference mVersioning;

    private EditTextPreference mVersioningKeep;

    private boolean mIsCreate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SettingsActivity activity = (SettingsActivity) getActivity();
        activity.registerOnServiceConnectedListener(this);
        mIsCreate = activity.getIsCreate();
        setHasOptionsMenu(true);

        if (mIsCreate) {
            addPreferencesFromResource(R.xml.repo_settings_create);
        } else {
            addPreferencesFromResource(R.xml.repo_settings_edit);
        }

        mRepoId = (EditTextPreference) findPreference("repo_id");
        mRepoId.setOnPreferenceChangeListener(this);
        mDirectory = findPreference("directory");
        mDirectory.setOnPreferenceClickListener(this);
        mRepoMaster = (CheckBoxPreference) findPreference("repo_master");
        mRepoMaster.setOnPreferenceChangeListener(this);
        mNodes = (PreferenceScreen) findPreference("nodes");
        mNodes.setOnPreferenceClickListener(this);
        mVersioning = (CheckBoxPreference) findPreference("versioning");
        mVersioning.setOnPreferenceChangeListener(this);
        mVersioningKeep = (EditTextPreference) findPreference("versioning_keep");
        mVersioningKeep.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        else if (currentState != SyncthingService.State.ACTIVE) {
            getActivity().finish();
            return;
        }

        if (mIsCreate) {
            getActivity().setTitle(R.string.create_repo);
            mRepo = new RestApi.Repo();
            mRepo.ID = "";
            mRepo.Directory = "";
            mRepo.Nodes = new ArrayList<>();
            mRepo.Versioning = new RestApi.Versioning();
        } else {
            getActivity().setTitle(R.string.edit_repo);
            List<RestApi.Repo> repos = mSyncthingService.getApi().getRepos();
            for (int i = 0; i < repos.size(); i++) {
                if (repos.get(i).ID.equals(
                        getActivity().getIntent().getStringExtra(EXTRA_REPO_ID))) {
                    mRepo = repos.get(i);
                    break;
                }
            }
        }

        mRepoId.setText(mRepo.ID);
        mRepoId.setSummary(mRepo.ID);
        mDirectory.setSummary(mRepo.Directory);
        mRepoMaster.setChecked(mRepo.ReadOnly);
        List<RestApi.Node> nodesList = mSyncthingService.getApi().getNodes();
        for (RestApi.Node n : nodesList) {
            ExtendedCheckBoxPreference cbp = new ExtendedCheckBoxPreference(getActivity(), n);
            cbp.setTitle(n.Name);
            cbp.setKey(KEY_NODE_SHARED);
            cbp.setOnPreferenceChangeListener(RepoSettingsFragment.this);
            // FIXME: something wrong here
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
        } else {
            mVersioningKeep.setEnabled(false);
        }
    }

    @Override
    public void onServiceConnected() {
        mSyncthingService = ((SyncthingActivity) getActivity()).getService();
        mSyncthingService.registerOnApiChangeListener(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.repo_settings, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.create).setVisible(mIsCreate);
        menu.findItem(R.id.delete).setVisible(!mIsCreate);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.create:
                if (mRepo.ID.equals("")) {
                    Toast.makeText(getActivity(), R.string.repo_id_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                if (mRepo.Directory.equals("")) {
                    Toast.makeText(getActivity(), R.string.repo_path_required, Toast.LENGTH_LONG)
                            .show();
                    return true;
                }
                mSyncthingService.getApi().editRepo(mRepo, true, getActivity());
                getActivity().finish();
                return true;
            case R.id.delete:
                new AlertDialog.Builder(getActivity())
                        .setMessage(R.string.delete_repo_confirm)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                mSyncthingService.getApi().deleteRepo(mRepo, getActivity());
                                getActivity().finish();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            case android.R.id.home:
                getActivity().finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        if (preference instanceof EditTextPreference) {
            EditTextPreference pref = (EditTextPreference) preference;
            pref.setSummary((String) o);
        }

        if (preference.equals(mRepoId)) {
            mRepo.ID = (String) o;
            repoUpdated();
            return true;
        } else if (preference.equals(mDirectory)) {
            mRepo.Directory = (String) o;
            repoUpdated();
            return true;
        } else if (preference.equals(mRepoMaster)) {
            mRepo.ReadOnly = (Boolean) o;
            repoUpdated();
            return true;
        } else if (preference.getKey().equals(KEY_NODE_SHARED)) {
            ExtendedCheckBoxPreference pref = (ExtendedCheckBoxPreference) preference;
            RestApi.Node node = (RestApi.Node) pref.getObject();
            if ((Boolean) o) {
                mRepo.Nodes.add(node);
            } else {
                for (RestApi.Node n : mRepo.Nodes) {
                    if (n.NodeID.equals(node.NodeID)) {
                        mRepo.Nodes.remove(n);
                    }
                }
            }
            repoUpdated();
            return true;
        } else if (preference.equals(mVersioning)) {
            mVersioningKeep.setEnabled((Boolean) o);
            if ((Boolean) o) {
                RestApi.SimpleVersioning v = new RestApi.SimpleVersioning();
                mRepo.Versioning = v;
                v.setParams(5);
                mVersioningKeep.setText("5");
                mVersioningKeep.setSummary("5");
            } else {
                mRepo.Versioning = new RestApi.Versioning();
            }
            repoUpdated();
            return true;
        } else if (preference.equals(mVersioningKeep)) {
            ((RestApi.SimpleVersioning) mRepo.Versioning)
                    .setParams(Integer.parseInt((String) o));
            repoUpdated();
            return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.equals(mDirectory)) {
            Intent intent = new Intent(getActivity(), FolderPickerActivity.class)
                    .putExtra(FolderPickerActivity.EXTRA_INITIAL_DIRECTORY,
                            (mRepo.Directory.length() != 0)
                                    ? mRepo.Directory
                                    : Environment.getExternalStorageDirectory().getAbsolutePath()
                    );
            startActivityForResult(intent, DIRECTORY_REQUEST_CODE);
        } else if (preference.equals(mNodes) && mSyncthingService.getApi().getNodes().isEmpty()) {
            Toast.makeText(getActivity(), R.string.no_nodes, Toast.LENGTH_SHORT)
                    .show();
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == DIRECTORY_REQUEST_CODE) {
            mRepo.Directory = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DIRECTORY);
            mDirectory.setSummary(mRepo.Directory);
            repoUpdated();
        }
    }

    private void repoUpdated() {
        if (!mIsCreate) {
            mSyncthingService.getApi().editRepo(mRepo, false, getActivity());
        }
    }

}
