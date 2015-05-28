package com.nutomic.syncthingandroid.syncthing;

import android.os.AsyncTask;
import android.util.Log;

import com.nutomic.syncthingandroid.util.Https;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;

import java.io.IOException;

/**
 * Performs a POST request with no parameters to the URL in uri[0]  with the path in uri[1].
 */
public class PostTask extends AsyncTask<String, Void, Boolean> {

    private static final String TAG = "PostTask";

    public static final String URI_CONFIG = "/rest/system/config";
    public static final String URI_SCAN   = "/rest/db/scan";

    private String mHttpsCertPath;

    public PostTask(String httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    /**
     * params[0] Syncthing hostname
     * params[1] URI to call
     * params[2] Syncthing API key
     * params[3] The request content (optional)
     */
    @Override
    protected Boolean doInBackground(String... params) {
        String fullUri = params[0] + params[1];
        Log.v(TAG, "Calling Rest API at " + fullUri);

        HttpClient httpclient = Https.createHttpsClient(mHttpsCertPath);
        HttpPost post = new HttpPost(fullUri);
        post.addHeader(new BasicHeader(RestApi.HEADER_API_KEY, params[2]));

        try {
            if (params.length > 3) {
                post.setEntity(new StringEntity(params[3], HTTP.UTF_8));
                Log.v(TAG, "API call parameters: " + params[3]);
            }
            httpclient.execute(post);
        } catch (IOException|IllegalArgumentException e) {
            Log.w(TAG, "Failed to call Rest API at " + fullUri, e);
            return false;
        }
        return true;
    }

}
