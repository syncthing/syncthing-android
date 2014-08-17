package com.nutomic.syncthingandroid.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.support.v4.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.SettingsActivity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.util.List;
import java.util.Map;

/**
 * Shows node details and allows changing them.
 */
public class NodeSettingsFragment extends PreferenceFragment implements
		SyncthingActivity.OnServiceConnectedListener, Preference.OnPreferenceChangeListener,
		Preference.OnPreferenceClickListener, RestApi.OnReceiveConnectionsListener,
		SyncthingService.OnApiChangeListener, RestApi.OnNodeIdNormalizedListener {

	public static final String EXTRA_NODE_ID = "node_id";

	private static final int SCAN_QR_REQUEST_CODE = 235;

	private SyncthingService mSyncthingService;

	// FIXME: is null
	private RestApi.Node mNode;

	private Preference mNodeId;

	private EditTextPreference mName;

	private EditTextPreference mAddresses;

	private Preference mVersion;

	private Preference mCurrentAddress;

	private boolean mIsCreate;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		((SyncthingActivity) getActivity()).registerOnServiceConnectedListener(this);

		mIsCreate = ((SettingsActivity) getActivity()).getIsCreate();
		setHasOptionsMenu(true);

		if (mIsCreate) {
			addPreferencesFromResource(R.xml.node_settings_create);
		}
		else {
			addPreferencesFromResource(R.xml.node_settings_edit);
		}

		mNodeId = findPreference("node_id");
		mNodeId.setOnPreferenceChangeListener(this);
		mName = (EditTextPreference) findPreference("name");
		mName.setOnPreferenceChangeListener(this);
		mAddresses = (EditTextPreference) findPreference("addresses");
		mAddresses.setOnPreferenceChangeListener(this);
		if (!mIsCreate) {
			mVersion = findPreference("version");
			mVersion.setSummary("?");
			mCurrentAddress = findPreference("current_address");
			mCurrentAddress.setSummary("?");
		}
	}

	@Override
	public void onServiceConnected() {
		mSyncthingService = ((SyncthingActivity) getActivity()).getService();
		mSyncthingService.registerOnApiChangeListener(this);
	}

	@Override
	public void onApiChange(SyncthingService.State currentState) {
		if (currentState != SyncthingService.State.ACTIVE) {
			SyncthingService.showDisabledDialog(getActivity());
			getActivity().finish();
			return;
		}

		if (mIsCreate) {
			getActivity().setTitle(R.string.add_node);
			mNode = new RestApi.Node();
			mNode.Name = "";
			mNode.NodeID = "";
			mNode.Addresses = "dynamic";
			((EditTextPreference) mNodeId).setText(mNode.NodeID);
		}
		else {
			getActivity().setTitle(R.string.edit_node);
			List<RestApi.Node> nodes = mSyncthingService.getApi().getNodes();
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i).NodeID.equals(
						getActivity().getIntent().getStringExtra(EXTRA_NODE_ID))) {
					mNode = nodes.get(i);
					break;
				}
			}
			mNodeId.setOnPreferenceClickListener(this);
		}
		mSyncthingService.getApi().getConnections(NodeSettingsFragment.this);

		mNodeId.setSummary(mNode.NodeID);
		mName.setText((mNode.Name));
		mName.setSummary(mNode.Name);
		mAddresses.setText(mNode.Addresses);
		mAddresses.setSummary(mNode.Addresses);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.node_settings, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.create).setVisible(mIsCreate);
		menu.findItem(R.id.share_node_id).setVisible(!mIsCreate);
		menu.findItem(R.id.delete).setVisible(!mIsCreate);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.create:
				if (mNode.NodeID.equals("")) {
					Toast.makeText(getActivity(), R.string.node_id_required, Toast.LENGTH_LONG)
							.show();
					return true;
				}
				if (mNode.Name.equals("")) {
					Toast.makeText(getActivity(), R.string.node_name_required, Toast.LENGTH_LONG)
							.show();
					return true;
				}
				mSyncthingService.getApi().editNode(mNode, this);
				return true;
			case R.id.share_node_id:
				RestApi.shareNodeId(getActivity(), mNode.NodeID);
				return true;
			case R.id.delete:
				new AlertDialog.Builder(getActivity())
						.setMessage(R.string.delete_node_confirm)
						.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								mSyncthingService.getApi().deleteNode(mNode, getActivity());
								getActivity().finish();
							}
						})
						.setNegativeButton(android.R.string.no, null)
						.show();
				return true;
			case android.R.id.home:
				getActivity().finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
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
		if (preference.equals(mNodeId)) {
			mSyncthingService.getApi().copyNodeId(mNode.NodeID);
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
		if (!mIsCreate) {
			mSyncthingService.getApi().editNode(mNode, this);
		}
	}

	/**
	 * Sends QR code scanning intent when clicking the qrcode icon.
	 */
	public void onClick(View view) {
		Intent intentScan = new Intent("com.google.zxing.client.android.SCAN");
		intentScan.addCategory(Intent.CATEGORY_DEFAULT);
		intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intentScan.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		try {
			startActivityForResult(intentScan, SCAN_QR_REQUEST_CODE);
		}
		catch (ActivityNotFoundException e) {
			Toast.makeText(getActivity(), R.string.no_qr_scanner_installed,
					Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Receives value of scanned QR code and sets it as node ID.
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == SCAN_QR_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
			mNode.NodeID = data.getStringExtra("SCAN_RESULT");
			((EditTextPreference) mNodeId).setText(mNode.NodeID);
			mNodeId.setSummary(mNode.NodeID);
		}
	}

	/**
	 * Callback for {@link RestApi#editNode(RestApi.Node, RestApi.OnNodeIdNormalizedListener)}.
	 * Displays an error message if present, or finishes the Activity on success in edit mode.
	 *
	 * @param normalizedId The normalized node ID, or null on error.
	 * @param error An error message, or null on success.
	 */
	@Override
	public void onNodeIdNormalized(String normalizedId, String error) {
		if (error != null) {
			Toast.makeText(getActivity(), error, Toast.LENGTH_LONG).show();
		}
		else if (mIsCreate) {
			getActivity().finish();
		}
	}

}
