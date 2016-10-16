package com.nutomic.syncthingandroid.http;


import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Polls to load the web interface, until we receive http status 200.
 */
public class PollWebGuiAvailableTask extends RestTask<Void, Void> {

    private static final String TAG = "PollWebGuiAvailableTask";

    /**
     * Interval in ms, at which connections to the web gui are performed on first start
     * to find out if it's online.
     */
    private static final long WEB_GUI_POLL_INTERVAL = 100;

    public PollWebGuiAvailableTask(URL url, String httpsCertPath, String apiKey,
                                   OnSuccessListener listener) {
        super(url, "", httpsCertPath, apiKey, listener);
    }

    @Override
    protected Pair<Boolean, String> doInBackground(Void... aVoid) {
        int status = 0;
        do {
            try {
                HttpsURLConnection connection = openConnection();
                connection.connect();
                status = connection.getResponseCode();
            } catch (IOException e) {
                // We catch this in every call, as long as the service is not online, so we ignore and continue.
                try {
                    Thread.sleep(WEB_GUI_POLL_INTERVAL);
                } catch (InterruptedException e2) {
                    Log.w(TAG, "Failed to sleep", e2);
                }
            }
        } while (status != HttpsURLConnection.HTTP_OK);
        return new Pair<>(true, null);
    }

}
