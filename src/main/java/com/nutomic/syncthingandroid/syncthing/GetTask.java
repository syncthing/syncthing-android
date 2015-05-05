package com.nutomic.syncthingandroid.syncthing;

import android.os.AsyncTask;
import android.util.Log;

import com.nutomic.syncthingandroid.util.Https;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;

/**
 * Performs a GET request with no parameters to the URL in uri[0] with the path in uri[1] and
 * returns the result as a String.
 */
public class GetTask extends AsyncTask<String, Void, String> {

    private static final String TAG = "GetTask";

    public static final String URI_CONFIG = "/rest/system/config";

    public static final String URI_VERSION = "/rest/system/version";

    public static final String URI_SYSTEM = "/rest/system/status";

    public static final String URI_CONNECTIONS = "/rest/system/connections";

    public static final String URI_MODEL = "/rest/db/status";

    public static final String URI_DEVICEID = "/rest/svc/deviceid";

    private String mHttpsCertPath;

    public GetTask(String httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    /**
     * params[0] Syncthing hostname
     * params[1] URI to call
     * params[2] Syncthing API key
     * params[3] optional parameter key
     * params[4] optional parameter value
     */
    @Override
    protected String doInBackground(String... params) {
        // Retry at most 10 times before failing
        for (int i = 0; i < 10; i++) {
            String fullUri = params[0] + params[1];
            HttpClient httpclient = Https.createHttpsClient(mHttpsCertPath);
            if (params.length == 5) {
                LinkedList<NameValuePair> urlParams = new LinkedList<>();
                urlParams.add(new BasicNameValuePair(params[3], params[4]));
                fullUri += "?" + URLEncodedUtils.format(urlParams, HTTP.UTF_8);
            }
            HttpGet get = new HttpGet(fullUri);
            get.addHeader(new BasicHeader(RestApi.HEADER_API_KEY, params[2]));

            try {
                HttpResponse response = httpclient.execute(get);
                HttpEntity entity = response.getEntity();

                if (entity != null) {
                    InputStream is = entity.getContent();

                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String line;
                    String result = "";
                    while ((line = br.readLine()) != null) {
                        result += line;
                    }
                    br.close();
                    return result;
                }
            } catch (IOException|IllegalArgumentException e) {
                Log.w(TAG, "Failed to call Rest API at " + fullUri, e);
            }
            try {
                // Don't push the API too hard
                Thread.sleep(500 * i);
            } catch (InterruptedException e) {
                Log.w(TAG, e);
            }
            Log.w(TAG, "Retrying GetTask Rest API call ("+(i+1)+"/10)");
        }
        return null;
    }

}
