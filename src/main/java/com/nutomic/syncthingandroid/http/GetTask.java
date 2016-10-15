package com.nutomic.syncthingandroid.http;

import android.util.Log;
import android.util.Pair;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

/**
 * Performs a GET request to the Syncthing API
 * Performs a GET request with no parameters to the URL in uri[0] with the path in uri[1] and
 * returns the result as a String.
 */
public class GetTask extends RestTask<String, Void, String> {

    private static final String TAG = "GetTask";

    public static final String URI_CONFIG      = "/rest/system/config";
    public static final String URI_VERSION     = "/rest/system/version";
    public static final String URI_SYSTEM      = "/rest/system/status";
    public static final String URI_CONNECTIONS = "/rest/system/connections";
    public static final String URI_MODEL       = "/rest/db/status";
    public static final String URI_DEVICEID    = "/rest/svc/deviceid";
    public static final String URI_REPORT      = "/rest/svc/report";
    public static final String URI_EVENTS      = "/rest/events";

    public GetTask(URL url, String path, String httpsCertPath, String apiKey,
                   OnSuccessListener<String> listener) {
        super(url, path, httpsCertPath, apiKey, listener);
    }

    /**
     * @param params Keys and values for the query string.
     */
    @Override
    protected Pair<Boolean, String> doInBackground(String... params) {
        try {
            HttpsURLConnection connection = openConnection(params);
            Log.v(TAG, "Calling Rest API at " + connection.getURL());
            connection.connect();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            String result = "";
            while ((line = br.readLine()) != null) {
                result += line;
            }
            br.close();
            Log.v(TAG, "API call result: " + result);
            return new Pair<>(true, result);
        } catch (IOException e) {
            Log.w(TAG, "Failed to call rest api", e);
            return new Pair<>(false, null);
        }
    }

}
