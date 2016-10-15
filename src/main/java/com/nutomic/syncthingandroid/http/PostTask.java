package com.nutomic.syncthingandroid.http;

import android.util.Log;
import android.util.Pair;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

/**
 * Sends a POST request to the Syncthing API.
 */
public class PostTask extends RestTask<String, Void, Boolean> {

    private static final String TAG = "PostTask";

    public static final String URI_CONFIG = "/rest/system/config";
    public static final String URI_SCAN   = "/rest/db/scan";

    public PostTask(URL url, String path, String httpsCertPath, String apiKey,
                    OnSuccessListener<Boolean> listener) {
        super(url, path, httpsCertPath, apiKey, listener);
    }

    /**
     * For {@link #URI_CONFIG}, params[0] must contain the config.
     *
     * For {@link #URI_SCAN}, params[0] must contain the folder, and params[1] the subfolder.
     */
    @Override
    protected Pair<Boolean, Boolean> doInBackground(String... params) {
        try {
            HttpsURLConnection connection = (mPath.equals(URI_SCAN))
                    ? openConnection("folder", params[0], "sub", params[1])
                    : openConnection();
            connection.setRequestMethod("POST");
            Log.v(TAG, "Calling Rest API at " + connection.getURL());

            if (mPath.equals(URI_CONFIG)) {
                OutputStream os = connection.getOutputStream();
                os.write(params[0].getBytes("UTF-8"));
            }
            connection.connect();
            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            String result = "";
            while ((line = br.readLine()) != null) {
                result += line;
            }
            br.close();
            Log.v(TAG, "API call result: " + result);
            return new Pair<>(true, true);
        } catch (IOException e) {
            Log.w(TAG, "Failed to call rest api", e);
            return new Pair<>(false, null);
        }
    }

}
