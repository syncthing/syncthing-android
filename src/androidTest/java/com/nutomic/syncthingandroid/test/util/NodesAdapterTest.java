package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.test.TestContext;
import com.nutomic.syncthingandroid.util.NodesAdapter;
import com.nutomic.syncthingandroid.util.ReposAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class NodesAdapterTest extends AndroidTestCase {

    private NodesAdapter mAdapter;

    private RestApi.Node mNode = new RestApi.Node();

    private RestApi.Connection mConnection = new RestApi.Connection();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAdapter = new NodesAdapter(getContext());
        mNode.Addresses = "127.0.0.1:12345";
        mNode.Name = "the node";
        mNode.NodeID = "123-456-789";

        mConnection.Completion = 100;
        mConnection.InBits = 1048576;
        mConnection.OutBits = 1073741824;

    }

    @MediumTest
    public void testGetViewNoConnections() {
        mAdapter.add(Arrays.asList(mNode));
        View v = mAdapter.getView(0, null, null);

        assertEquals(mNode.Name, ((TextView) v.findViewById(R.id.name)).getText());
        assertEquals(getContext().getString(R.string.node_disconnected),
                ((TextView) v.findViewById(R.id.status)).getText().toString());
        assertFalse(((TextView) v.findViewById(R.id.status)).getText().equals(""));
        assertFalse(((TextView) v.findViewById(R.id.download)).getText().equals(""));
        assertFalse(((TextView) v.findViewById(R.id.upload)).getText().equals(""));
    }

    @MediumTest
    public void testGetViewConnections() {
        mAdapter.add(Arrays.asList(mNode));
        mAdapter.onReceiveConnections(
                new HashMap<String, RestApi.Connection>() {{ put(mNode.NodeID, mConnection); }});
        View v = mAdapter.getView(0, null, null);

        assertEquals(getContext().getString(R.string.node_up_to_date),
                ((TextView) v.findViewById(R.id.status)).getText().toString());
        assertEquals("1 Mb/s", ((TextView) v.findViewById(R.id.download)).getText().toString());
        assertEquals("1 Gb/s", ((TextView) v.findViewById(R.id.upload)).getText().toString());
    }

}
