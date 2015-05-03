package com.nutomic.syncthingandroid.syncthing;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

import java.io.IOException;

/**
 * Performs a POST request with no parameters to the URL in uri[0]  with the path in uri[1].
 */
public class PostTask extends AsyncTask<String, Void, Boolean> {

    private static final String TAG = "PostTask";

    public static final String URI_CONFIG = "/rest/system/config";

    public static final String URI_SCAN = "/rest/db/scan";

    /**
     * params[0] Syncthing hostname
     * params[1] URI to call
     * params[2] Syncthing API key
     * params[3] The request content (optional)
     */
    @Override
    protected Boolean doInBackground(String... params) {
        String fullUri = params[0] + params[1];
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost post = new HttpPost(fullUri);
        post.addHeader(new BasicHeader(RestApi.HEADER_API_KEY, params[2]));

        try {
            if (params.length > 3) {
                post.setEntity(new StringEntity(params[3], HTTP.UTF_8));
            }
            httpclient.execute(post);
        } catch (IOException e) {
            Log.w(TAG, "Failed to call Rest API at " + fullUri, e);
            return false;
        }
        return true;
    }

}
