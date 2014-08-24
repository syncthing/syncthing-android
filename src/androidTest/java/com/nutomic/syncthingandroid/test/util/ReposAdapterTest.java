package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.test.TestContext;
import com.nutomic.syncthingandroid.util.ReposAdapter;

import java.util.ArrayList;
import java.util.Arrays;

public class ReposAdapterTest extends AndroidTestCase {

    private ReposAdapter mAdapter;

    private RestApi.Repo mRepo = new RestApi.Repo();

    private RestApi.Model mModel = new RestApi.Model();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAdapter = new ReposAdapter(getContext());
        mRepo.Directory = "/my/dir/";
        mRepo.ID = "id 123";
        mRepo.Invalid = "all good";
        mRepo.Nodes = new ArrayList<>();
        mRepo.ReadOnly = false;
        mRepo.Versioning = new RestApi.Versioning();

        mModel.localFiles = 50;
        mModel.globalFiles = 500;
        mModel.localBytes = 1048576;
        mModel.globalBytes = 1073741824;
    }

    @MediumTest
    public void testGetViewNoModel() {
        mAdapter.add(Arrays.asList(mRepo));
        View v = mAdapter.getView(0, null, null);
        assertEquals(mRepo.ID, ((TextView) v.findViewById(R.id.id)).getText());
        assertEquals(mRepo.Directory, ((TextView) v.findViewById(R.id.directory)).getText());
        assertEquals(mRepo.Invalid, ((TextView) v.findViewById(R.id.invalid)).getText());
    }

    @MediumTest
    public void testGetViewModel() {
        mAdapter.add(Arrays.asList(mRepo));
        mAdapter.onReceiveModel(mRepo.ID, mModel);
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
