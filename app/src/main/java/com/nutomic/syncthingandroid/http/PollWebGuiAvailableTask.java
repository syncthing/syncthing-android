package com.nutomic.syncthingandroid.http;


import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.VolleyError;

import java.net.ConnectException;
import java.net.URL;
import java.util.Collections;

/**
 * Polls to load the web interface, until it is available.
 */
public class PollWebGuiAvailableTask extends ApiRequest {

    private static final String TAG = "PollWebGuiAvailableTask";
    /**
     * Interval in ms, at which connections to the web gui are performed on first start
     * to find out if it's online.
     */
    private static final long WEB_GUI_POLL_INTERVAL = 100;

    private final Handler mHandler = new Handler();

    private OnSuccessListener mListener;

    public PollWebGuiAvailableTask(Context context, URL url, String apiKey,
                                   OnSuccessListener listener) {
        super(context, url, "", apiKey);
        Log.i(TAG, "Starting to poll for web gui availability");
        mListener = listener;
        performRequest();
    }

    public void cancelRequestsAndCallback() {
        mListener = null;
    }

    private void performRequest() {
        Uri uri = buildUri(Collections.emptyMap());
        connect(Request.Method.GET, uri, null, this::onSuccess, this::onError);
    }

    private void onSuccess(String result) {
        if (mListener != null) {
            mListener.onSuccess(result);
        } else {
            Log.v(TAG, "Cancelled callback and outstanding requests");
        }
    }

    private void onError(VolleyError error) {
        if (mListener != null) {
            mHandler.postDelayed(this::performRequest, WEB_GUI_POLL_INTERVAL);
            Throwable cause = error.getCause();
            if (cause == null || cause.getClass().equals(ConnectException.class)) {
                Log.v(TAG, "Polling web gui");
            } else {
                Log.w(TAG, "Unexpected error while polling web gui", error);
            }
        } else {
            Log.v(TAG, "Cancelled callback and outstanding requests");
        }
    }

}
