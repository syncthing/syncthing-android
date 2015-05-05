package com.nutomic.syncthingandroid.test.util;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.View;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.util.DevicesAdapter;

import java.util.Arrays;
import java.util.HashMap;

public class DevicesAdapterTest extends AndroidTestCase {

    private DevicesAdapter mAdapter;

    private RestApi.Device mDevice = new RestApi.Device();

    private RestApi.Connection mConnection = new RestApi.Connection();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAdapter = new DevicesAdapter(getContext());
        mDevice.addresses = "127.0.0.1:12345";
        mDevice.name = "the device";
        mDevice.deviceID = "123-456-789";

        mConnection.completion = 100;
        mConnection.inBits = 1048576;
        mConnection.outBits = 1073741824;

    }

    @MediumTest
    public void testGetViewNoConnections() {
        mAdapter.add(Arrays.asList(mDevice));
        View v = mAdapter.getView(0, null, null);

        assertEquals(mDevice.name, ((TextView) v.findViewById(R.id.name)).getText());
        assertEquals(getContext().getString(R.string.device_disconnected),
                ((TextView) v.findViewById(R.id.status)).getText().toString());
        assertFalse(((TextView) v.findViewById(R.id.status)).getText().equals(""));
        assertFalse(((TextView) v.findViewById(R.id.download)).getText().equals(""));
        assertFalse(((TextView) v.findViewById(R.id.upload)).getText().equals(""));
    }

    @MediumTest
    public void testGetViewConnections() {
        mAdapter.add(Arrays.asList(mDevice));
        mAdapter.onReceiveConnections(
                new HashMap<String, RestApi.Connection>() {{ put(mDevice.deviceID, mConnection); }});
        View v = mAdapter.getView(0, null, null);

        assertEquals(getContext().getString(R.string.device_up_to_date),
                ((TextView) v.findViewById(R.id.status)).getText().toString());
        assertEquals("1 Mib/s", ((TextView) v.findViewById(R.id.download)).getText().toString());
        assertEquals("1 Gib/s", ((TextView) v.findViewById(R.id.upload)).getText().toString());
    }

}
