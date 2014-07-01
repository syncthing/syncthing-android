package com.nutomic.syncthingandroid.gui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

import java.util.List;
import java.util.Map;

/**
 * Shows node details and allows changing them.
 */
public class NodeSettingsActivity extends PreferenceActivity implements
		Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
		RestApi.OnReceiveConnectionsListener, SyncthingService.OnApiChangeListener {

	public static final String ACTION_CREATE = "create";

	public static final String ACTION_EDIT = "edit";

	public static final String KEY_NODE_ID = "node_id";

	private SyncthingService mSyncthingService;

	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mSyncthingService.registerOnApiChangeListener(NodeSettingsActivity.this);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	private RestApi.Node mNode;

	private EditTextPreference mNodeId;

	private EditTextPreference mName;

	private EditTextPreference mAddresses;

	private Preference mVersion;

	private Preference mCurrentAddress;

	private Preference mDelete;

	@Override
	@SuppressLint("AppCompatMethod")
	@TargetApi(11)
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}

		if (getIntent().getAction().equals(ACTION_CREATE)) {
			addPreferencesFromResource(R.xml.node_settings_create);
		}
		else if (getIntent().getAction().equals(ACTION_EDIT)) {
			addPreferencesFromResource(R.xml.node_settings_edit);
		}

		mNodeId = (EditTextPreference) findPreference("node_id");
		mNodeId.setOnPreferenceChangeListener(this);
		mName = (EditTextPreference) findPreference("name");
		mName.setOnPreferenceChangeListener(this);
		mAddresses = (EditTextPreference) findPreference("addresses");
		mAddresses.setOnPreferenceChangeListener(this);
		if (getIntent().getAction().equals(ACTION_EDIT)) {
			mVersion = findPreference("version");
			mVersion.setSummary("?");
			mCurrentAddress = findPreference("current_address");
			mDelete = findPreference("delete");
			mCurrentAddress.setSummary("?");
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
			setTitle(R.string.create_node);
			mNode = new RestApi.Node();
			mNode.Name = "";
			mNode.NodeID = "";
			mNode.Addresses = "dynamic";
		}
		else if (getIntent().getAction().equals(ACTION_EDIT)) {
			setTitle(R.string.edit_node);
			List<RestApi.Node> nodes = mSyncthingService.getApi().getNodes();
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i).NodeID.equals(getIntent().getStringExtra(KEY_NODE_ID))) {
					mNode = nodes.get(i);
					break;
				}
			}
		}
		mSyncthingService.getApi().getConnections(NodeSettingsActivity.this);

		mNodeId.setText(mNode.NodeID);
		mNodeId.setSummary(mNode.NodeID);
		mName.setText((mNode.Name));
		mName.setSummary(mNode.Name);
		mAddresses.setText(mNode.Addresses);
		mAddresses.setSummary(mNode.Addresses);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.node_settings, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.create).setVisible(getIntent().getAction().equals(ACTION_CREATE));
		menu.findItem(R.id.share_node_id).setVisible(getIntent().getAction().equals(ACTION_EDIT));
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.create:
				if (mNode.NodeID.equals("")) {
					Toast.makeText(this, R.string.node_id_required, Toast.LENGTH_LONG).show();
					return true;
				}
				if (mNode.Name.equals("")) {
					Toast.makeText(this, R.string.node_name_required, Toast.LENGTH_LONG).show();
					return true;
				}
				mSyncthingService.getApi().editNode(mNode, true);
				finish();
				return true;
			case R.id.share_node_id:
				RestApi.shareNodeId(this, mNode.NodeID);
				return true;
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
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

		if (preference.equals(mNodeId)) {
			mNode.NodeID = (String) o;
			nodeUpdated();
			return true;
		}
		else if (preference.equals(mName)) {
			mNode.Name = (String) o;
			nodeUpdated();
			return true;
		}
		else if (preference.equals(mAddresses)) {
			mNode.Addresses = (String) o;
			nodeUpdated();
			return true;
		}
		return false;
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if (preference.equals(mDelete)) {
			new AlertDialog.Builder(this)
					.setMessage(R.string.delete_node_confirm)
					.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mSyncthingService.getApi().deleteNode(mNode);
							finish();
						}
					})
					.setNegativeButton(android.R.string.no, null)
					.show();
			return true;
		}
		return false;
	}

	/**
	 * Sets version and current address of the node.
	 *
	 * NOTE: This is only called once on startup, should be called more often to properly display
	 *       version/address changes.
	 */
	@Override
	public void onReceiveConnections(Map<String, RestApi.Connection> connections) {
		if (connections.containsKey(mNode.NodeID)) {
			mVersion.setSummary(connections.get(mNode.NodeID).ClientVersion);
			mCurrentAddress.setSummary(connections.get(mNode.NodeID).Address);
		}
	}

	/**
	 * Sends the updated node info if in edit mode.
	 */
	private void nodeUpdated() {
		if (getIntent().getAction().equals(ACTION_EDIT)) {
			mSyncthingService.getApi().editNode(mNode, false);
		}
	}

}
