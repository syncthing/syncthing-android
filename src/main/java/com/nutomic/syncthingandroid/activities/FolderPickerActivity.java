package com.nutomic.syncthingandroid.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Activity that allows selecting a directory in the local file system.
 */
public class FolderPickerActivity extends SyncthingActivity
        implements AdapterView.OnItemClickListener, SyncthingService.OnApiChangeListener {

    private static final String TAG = "FolderPickerActivity";

    public static final String EXTRA_INITIAL_DIRECTORY = "initial_directory";

    public static final String EXTRA_RESULT_DIRECTORY = "result_directory";

    private ListView mListView;

    private FileAdapter mFilesAdapter;

    private RootsAdapter mRootsAdapter;

    private ArrayList<File> mRootDirectories = new ArrayList();

    /**
     * Location of null means that the list of roots is displayed.
     */
    private File mLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setContentView(R.layout.folder_picker_activity);
        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setEmptyView(findViewById(android.R.id.empty));
        mFilesAdapter = new FileAdapter(this);
        mRootsAdapter = new RootsAdapter(this);
        mListView.setAdapter(mFilesAdapter);

        // Populate roots.
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            mRootDirectories.addAll(Arrays.asList(getExternalFilesDirs(null)));
        }
        mRootDirectories.add(Environment.getExternalStorageDirectory());
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getBoolean("advanced_folder_picker", false)) {
            mRootDirectories.add(new File("/"));
        }

        for (File f : mRootDirectories) {
            if (f == null)
                continue;

            mRootsAdapter.add(f);
        }

        if (getIntent().hasExtra(EXTRA_INITIAL_DIRECTORY)) {
            displayFolder(new File(getIntent().getStringExtra(EXTRA_INITIAL_DIRECTORY)));
        } else {
            displayRoot();
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnApiChangeListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mListView.getAdapter() == mRootsAdapter)
            return true;

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
                                }
                        )
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
            case android.R.id.home:
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
        if (newFolder.mkdir()) {
            displayFolder(newFolder);
        } else {
            Toast.makeText(this, R.string.create_folder_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refreshes the ListView to show the contents of the folder in {@code }mLocation.peek()}.
     */
    private void displayFolder(File folder) {
        mLocation = folder;
        mFilesAdapter.clear();
        File[] contents = mLocation.listFiles();
        Arrays.sort(contents, new Comparator<File>() {
            public int compare(File f1, File f2) {
                if (f1.isDirectory() && f2.isFile())
                    return -1;
                if (f1.isFile() && f2.isDirectory())
                    return 1;
                return f1.getName().compareTo(f2.getName());
            }
        });

        for (File f : contents) {
            mFilesAdapter.add(f);
        }
        mListView.setAdapter(mFilesAdapter);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        ArrayAdapter<File> adapter = (ArrayAdapter<File>) mListView.getAdapter();
        File f = adapter.getItem(i);
        if (f.isDirectory()) {
            displayFolder(f);
            invalidateOptionsMenu();
        }
    }

    private class FileAdapter extends ArrayAdapter<File> {

        public FileAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            TextView title = (TextView) convertView.findViewById(android.R.id.text1);
            File f = getItem(position);
            title.setText(f.getName());
            int textColor = (f.isDirectory())
                    ? android.R.color.primary_text_light
                    : android.R.color.tertiary_text_light;
            title.setTextColor(getContext().getResources().getColor(textColor));

            return convertView;
        }
    }

    private class RootsAdapter extends ArrayAdapter<File> {

        public RootsAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            TextView title = (TextView) convertView.findViewById(android.R.id.text1);
            title.setText(getItem(position).getAbsolutePath());
            return convertView;
        }
    }

    /**
     * Goes up a directory, up to the list of roots if there are multiple roots.
     *
     * If we already are in the list of roots, or if we are directly in the only
     * root folder, we cancel.
     */
    @Override
    public void onBackPressed() {
        if (!mRootDirectories.contains(mLocation) && mLocation != null) {
            displayFolder(mLocation.getParentFile());
        } else if (mRootDirectories.contains(mLocation) && mRootDirectories.size() > 1) {
            displayRoot();
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onApiChange(SyncthingService.State currentState) {
        if (!isFinishing() && currentState != SyncthingService.State.ACTIVE) {
            setResult(Activity.RESULT_CANCELED);
            SyncthingService.showDisabledDialog(this);
            finish();
        }
    }

    /**
     * Displays a list of all available roots, or if there is only one root, the
     * contents of that folder.
     */
    private void displayRoot() {
        mFilesAdapter.clear();
        if (mRootDirectories.size() == 1) {
            displayFolder(mRootDirectories.get(0));
        } else {
            mListView.setAdapter(mRootsAdapter);
            mLocation = null;
        }
        invalidateOptionsMenu();
    }

}
