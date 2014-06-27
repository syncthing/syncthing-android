package com.nutomic.syncthingandroid.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Stack;

/**
 * Activity that allows selecting a directory in the local file system.
 */
public class FolderPickerActivity extends ActionBarActivity
		implements AdapterView.OnItemClickListener, SyncthingService.OnApiChangeListener {

	private static final String TAG = "FolderPickerActivity";

	public static final String EXTRA_INITIAL_DIRECTORY = "initial_directory";

	public static final String EXTRA_RESULT_DIRECTORY = "result_directory";

	private ListView mListView;

	private FileAdapter mAdapter;

	private File mLocation;

	private SyncthingService mSyncthingService;

	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mSyncthingService.registerOnApiChangeListener(FolderPickerActivity.this);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.folder_picker_activity);
		mListView = (ListView) findViewById(android.R.id.list);
		mListView.setOnItemClickListener(this);
		mListView.setEmptyView(findViewById(android.R.id.empty));
		mAdapter = new FileAdapter(this);
		mListView.setAdapter(mAdapter);

		mLocation = new File(getIntent().getStringExtra(EXTRA_INITIAL_DIRECTORY));
		refresh();

		bindService(new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.folder_picker, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.create_folder:
				final EditText et = new EditText(this);
				AlertDialog dialog = new AlertDialog.Builder(this)
						.setTitle(R.string.create_folder)
						.setView(et)
						.setPositiveButton(android.R.string.ok,
								new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								createFolder(et.getText().toString());
							}
						})
						.setNegativeButton(android.R.string.cancel, null)
						.create();
				dialog.setOnShowListener(new DialogInterface.OnShowListener() {
					@Override
					public void onShow(DialogInterface dialogInterface) {
						((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
								.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
					}
				});
				dialog.show();
				return true;
			case R.id.select:
				Intent intent = new Intent()
						.putExtra(EXTRA_RESULT_DIRECTORY, mLocation.getAbsolutePath());
				setResult(Activity.RESULT_OK, intent);
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
    }

	/**
	 * Creates a new folder with the given name and enters it.
	 */
	private void createFolder(String name) {
		File newFolder = new File(mLocation, name);
		newFolder.mkdir();
		mLocation = newFolder;
		refresh();
	}

	/**
	 * Refreshes the ListView to show the contents of the folder in {@code }mLocation.peek()}.
	 */
	private void refresh() {
		mAdapter.clear();
		File[] contents = mLocation.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory();
			}
		});
		Arrays.sort(contents);
		for (File f : contents) {
			mAdapter.add(f);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
		mLocation = mAdapter.getItem(i);
		refresh();
	}

	private class FileAdapter extends ArrayAdapter<File> {

		public FileAdapter(Context context) {
			super(context, android.R.layout.simple_list_item_1);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater) getContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				convertView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
			}

			TextView title = (TextView) convertView.findViewById(android.R.id.text1);
			title.setText(getItem(position).getName());
			return convertView;
		}
	}

	@Override
	public void onBackPressed() {
		if (!mLocation.equals(Environment.getExternalStorageDirectory())) {
			mLocation = mLocation.getParentFile();
			refresh();
		}
		else {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}

	@Override
	public void onApiChange(boolean isAvailable) {
		if (!isAvailable) {
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}

}
