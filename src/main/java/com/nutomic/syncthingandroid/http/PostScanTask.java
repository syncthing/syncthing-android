package com.nutomic.syncthingandroid.http;

import android.util.Log;
import android.util.Pair;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class PostScanTask extends RestTask {

    private static final String TAG = "PostScanTask";

    private static final String URI_SCAN = "/rest/db/scan";

    private final String mFolder;
    private final String mSub;

    public PostScanTask(URL url, String httpsCertPath, String apiKey, String folder, String sub) {
        super(url, URI_SCAN, httpsCertPath, apiKey, null);
        mFolder = folder;
        mSub = sub;
    }

    @Override
    protected Pair<Boolean, String> doInBackground(Void... params) {
        try {
            HttpsURLConnection connection = openConnection(ImmutableMap.of("folder", mFolder, "sub", mSub));
            connection.setRequestMethod("POST");
            return connect(connection);
        } catch (IOException e) {
            Log.w(TAG, "Failed to call rest api", e);
            return new Pair<>(false, null);
        }
    }

}
