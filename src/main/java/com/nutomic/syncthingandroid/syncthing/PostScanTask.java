package com.nutomic.syncthingandroid.syncthing;

import android.os.AsyncTask;
import android.util.Log;

import com.nutomic.syncthingandroid.util.Https;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.IOException;
import java.util.LinkedList;

/**
 * Performs a POST request to {@link #URI_SCAN} to notify Syncthing of a changed file or folder.
 */
public class PostScanTask extends AsyncTask<String, Void, Void> {

    private static final String TAG = "PostScanTask";

    public static final String URI_SCAN = "/rest/db/scan";

    private final String mHttpsCertPath;

    public PostScanTask(String httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    /**
     * params[0] Syncthing hostname
     * params[1] Syncthing API key
     * params[2] folder parameter (the Syncthing folder to update)
     * params[3] sub parameter (the subfolder to update
     */
    @Override
    protected Void doInBackground(String... params) {
        String fullUri = params[0] + URI_SCAN;

        LinkedList<NameValuePair> urlParams = new LinkedList<>();
        urlParams.add(new BasicNameValuePair("folder", params[2]));
        urlParams.add(new BasicNameValuePair("sub", params[3]));
        fullUri += "?" + URLEncodedUtils.format(urlParams, HTTP.UTF_8);
        Log.v(TAG, "Calling Rest API at " + fullUri);

        // Retry at most 5 times before failing
        for (int i = 0; i < 5; i++) {
            HttpClient httpclient = Https.createHttpsClient(mHttpsCertPath);
            HttpPost post = new HttpPost(fullUri);
            post.addHeader(new BasicHeader(RestApi.HEADER_API_KEY, params[1]));

            if (isCancelled())
                return null;

            try {
                HttpResponse response = httpclient.execute(post);
                if (response.getEntity() != null)
                    return null;
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "Failed to call Rest API at " + fullUri);
            }
            try {
                // Don't push the API too hard
                Thread.sleep(500 * i);
            } catch (InterruptedException e) {
                Log.w(TAG, e);
            }
            Log.w(TAG, "Retrying GetTask Rest API call (" + (i + 1) + "/5)");
        }
        return null;
    }

}
