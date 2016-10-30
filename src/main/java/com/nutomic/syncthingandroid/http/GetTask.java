package com.nutomic.syncthingandroid.http;

import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import com.google.common.base.Optional;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

/**
 * Performs a GET request to the Syncthing API
 * Performs a GET request with no parameters to the URL in uri[0] with the path in uri[1] and
 * returns the result as a String.
 */
public class GetTask extends RestTask<Void, Void> {

    private static final String TAG = "GetTask";

    public static final String URI_CONFIG      = "/rest/system/config";
    public static final String URI_VERSION     = "/rest/system/version";
    public static final String URI_SYSTEM      = "/rest/system/status";
    public static final String URI_CONNECTIONS = "/rest/system/connections";
    public static final String URI_MODEL       = "/rest/db/status";
    public static final String URI_DEVICEID    = "/rest/svc/deviceid";
    public static final String URI_REPORT      = "/rest/svc/report";
    public static final String URI_EVENTS      = "/rest/events";

    private final Map<String, String> mParams;

    public GetTask(URL url, String path, String httpsCertPath, String apiKey,
                   @Nullable Map<String, String> params, OnSuccessListener listener) {
        super(url, path, httpsCertPath, apiKey, listener);
        mParams = Optional.fromNullable(params).or(Collections.emptyMap());
    }

    @Override
    protected Pair<Boolean, String> doInBackground(Void... aVoid) {
        try {
            HttpsURLConnection connection = openConnection(mParams);
            Log.v(TAG, "Calling Rest API at " + connection.getURL());
            return connect(connection);
        } catch (IOException e) {
            Log.w(TAG, "Failed to call rest api", e);
            return new Pair<>(false, null);
        }
    }

}
