package com.nutomic.syncthingandroid;


import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Performs a POST request with no parameters to the URL in uri[0].
 */
class RestTask extends AsyncTask<String, Void, Void> {

	private static final String TAG = "RequestTask";

    @Override
    protected Void doInBackground(String... uri) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost httppost = new HttpPost(uri[0]);
		String responseString = null;
		try {
			HttpResponse response = httpclient.execute(httppost);
		}
		catch (IOException e) {
			Log.w(TAG, "Failed to call Rest API", e);
		}
		return null;
    }
}
