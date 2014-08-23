package com.nutomic.syncthingandroid.test.syncthing;

import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.nutomic.syncthingandroid.syncthing.GetTask;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.IOException;

public class GetTaskTest extends AndroidTestCase {

    private MockWebServer mServer;

    private static final String RESPONSE = "the response";

    private static final String API_KEY = "the key";

    private static final String PARAM_KEY_ONE = "first-param";

    private static final String PARAM_VALUE_ONE = "first param value";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mServer = new MockWebServer();
        mServer.enqueue(new MockResponse().setBody(RESPONSE));
        mServer.play();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // TODO: causes problems, see https://github.com/square/okhttp/issues/1033
        //mServer.shutdown();
    }

    @MediumTest
    public void testGetNoParams() throws IOException, InterruptedException {
        new GetTask() {
            @Override
            protected void onPostExecute(String s) {
                assertEquals(RESPONSE, s);
            }
        }.execute(mServer.getUrl("").toString(), GetTask.URI_CONFIG, API_KEY);
        RecordedRequest request = mServer.takeRequest();
        assertEquals(API_KEY, request.getHeader(RestApi.HEADER_API_KEY));
        Uri uri = Uri.parse(request.getPath());
        assertEquals(GetTask.URI_CONFIG, uri.getPath());
    }

    @MediumTest
    public void testGetParams() throws IOException, InterruptedException {
        new GetTask() {
            @Override
            protected void onPostExecute(String s) {
                assertEquals(RESPONSE, s);
            }
        }.execute(mServer.getUrl("").toString(), GetTask.URI_CONFIG, API_KEY, PARAM_KEY_ONE,
                PARAM_VALUE_ONE);
        RecordedRequest request = mServer.takeRequest();
        assertEquals(API_KEY, request.getHeader(RestApi.HEADER_API_KEY));
        Uri uri = Uri.parse(request.getPath());
        assertEquals(GetTask.URI_CONFIG, uri.getPath());
        assertEquals(PARAM_VALUE_ONE, uri.getQueryParameter(PARAM_KEY_ONE));
    }

}
