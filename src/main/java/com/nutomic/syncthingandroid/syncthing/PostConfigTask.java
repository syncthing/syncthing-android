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
 * Sends a new config to {@link #URI_CONFIG}.
 */
public class PostConfigTask extends AsyncTask<String, Void, Boolean> {

    private static final String TAG = "PostConfigTask";

    public static final String URI_CONFIG = "/rest/system/config";

    private String mHttpsCertPath;

    public PostConfigTask(String httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    /**
     * params[0] Syncthing hostname
     * params[1] Syncthing API key
     * params[2] The new config
     */
    @Override
    protected Boolean doInBackground(String... params) {
        String fullUri = params[0] + URI_CONFIG;
        Log.v(TAG, "Calling Rest API at " + fullUri);

        HttpClient httpclient = Https.createHttpsClient(mHttpsCertPath);
        HttpPost post = new HttpPost(fullUri);
        post.addHeader(new BasicHeader(RestApi.HEADER_API_KEY, params[1]));

        try {
            post.setEntity(new StringEntity(params[2], HTTP.UTF_8));
            Log.v(TAG, "API call parameters: " + params[2]);
            httpclient.execute(post);
        } catch (IOException|IllegalArgumentException e) {
            Log.w(TAG, "Failed to call Rest API at " + fullUri, e);
            return false;
        }
        return true;
    }

}
