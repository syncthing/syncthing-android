package com.nutomic.syncthingandroid.syncthing;


import android.os.AsyncTask;
import android.util.Log;

import com.nutomic.syncthingandroid.util.Https;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.conn.HttpHostConnectException;

import java.io.IOException;

/**
 * Polls SYNCTHING_URL until it returns HTTP status OK, then calls all listeners
 * in mOnWebGuiAvailableListeners and clears it.
 */
public abstract class PollWebGuiAvailableTask extends AsyncTask<String, Void, Void> {

    private static final String TAG = "PollWebGuiAvailableTask";

    /**
     * Interval in ms, at which connections to the web gui are performed on first start
     * to find out if it's online.
     */
    private static final long WEB_GUI_POLL_INTERVAL = 100;

    private String mHttpsCertPath;

    public PollWebGuiAvailableTask(String httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    /**
     * @param @url The URL of the web GUI (eg 127.0.0.1:8384).
     */
    @Override
    protected Void doInBackground(String... url) {
        int status = 0;
        HttpClient httpclient = Https.createHttpsClient(mHttpsCertPath);
        HttpHead head = new HttpHead(url[0]);
        do {
            try {
                Thread.sleep(WEB_GUI_POLL_INTERVAL);
                HttpResponse response = httpclient.execute(head);
                // NOTE: status is not really needed, as HttpHostConnectException is thrown
                // earlier.
                status = response.getStatusLine().getStatusCode();
            } catch (HttpHostConnectException e) {
                // We catch this in every call, as long as the service is not online,
                // so we ignore and continue.
            } catch (IOException|InterruptedException|IllegalArgumentException e) {
                Log.w(TAG, "Failed to poll for web interface", e);
            }
        } while (status != HttpStatus.SC_OK && status != HttpStatus.SC_UNAUTHORIZED);
        return null;
    }

}
