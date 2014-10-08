package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.util.FoldersAdapter;

import java.util.ArrayList;
import java.util.Arrays;

public class FoldersAdapterTest extends AndroidTestCase {

    private FoldersAdapter mAdapter;

    private RestApi.Folder mFolder = new RestApi.Folder();

    private RestApi.Model mModel = new RestApi.Model();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAdapter = new FoldersAdapter(getContext());
        mFolder.Path = "/my/dir/";
        mFolder.ID = "id 123";
        mFolder.Invalid = "all good";
        mFolder.DeviceIds = new ArrayList<>();
        mFolder.ReadOnly = false;
        mFolder.Versioning = new RestApi.Versioning();

        mModel.localFiles = 50;
        mModel.globalFiles = 500;
        mModel.localBytes = 1048576;
        mModel.globalBytes = 1073741824;
    }

    @MediumTest
    public void testGetViewNoModel() {
        mAdapter.add(Arrays.asList(mFolder));
        View v = mAdapter.getView(0, null, null);
        assertEquals(mFolder.ID, ((TextView) v.findViewById(R.id.id)).getText());
        assertEquals(mFolder.Path, ((TextView) v.findViewById(R.id.directory)).getText());
        assertEquals(mFolder.Invalid, ((TextView) v.findViewById(R.id.invalid)).getText());
    }

    @MediumTest
    public void testGetViewModel() {
        mAdapter.add(Arrays.asList(mFolder));
        mAdapter.onReceiveModel(mFolder.ID, mModel);
        View v = mAdapter.getView(0, null, null);
        assertFalse(((TextView) v.findViewById(R.id.state)).getText().toString().equals(""));
        String items = ((TextView) v.findViewById(R.id.items)).getText().toString();
        assertTrue(items.contains(Long.toString(mModel.localFiles)));
        assertTrue(items.contains(Long.toString(mModel.localFiles)));
        String size = ((TextView) v.findViewById(R.id.size)).getText().toString();
        assertTrue(size.contains("1 MB"));
        assertTrue(size.contains("1 GB"));
    }

}
