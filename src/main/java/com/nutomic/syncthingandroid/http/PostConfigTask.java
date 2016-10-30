package com.nutomic.syncthingandroid.http;

import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;

import javax.net.ssl.HttpsURLConnection;

public class PostConfigTask extends RestTask {

    private static final String TAG = "PostConfigTask";

    private static final String URI_CONFIG = "/rest/system/config";

    private final String mConfig;

    public PostConfigTask(URL url, String httpsCertPath, String apiKey, String config,
                          OnSuccessListener listener) {
        super(url, URI_CONFIG, httpsCertPath, apiKey, listener);
        mConfig = config;
    }

    @Override
    protected Pair<Boolean, String> doInBackground(Void... params) {
        try {
            HttpsURLConnection connection = openConnection(Collections.emptyMap());
            connection.setRequestMethod("POST");
            Log.v(TAG, "Calling Rest API at " + connection.getURL());

            OutputStream os = connection.getOutputStream();
            os.write(mConfig.getBytes("UTF-8"));
            return connect(connection);
        } catch (IOException e) {
            Log.w(TAG, "Failed to call rest api", e);
            return new Pair<>(false, null);
        }
    }

}
