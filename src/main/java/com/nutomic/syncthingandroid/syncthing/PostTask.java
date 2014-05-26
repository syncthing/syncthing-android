package com.nutomic.syncthingandroid.syncthing;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;

/**
 * Performs a POST request with no parameters to the URL in uri[0]  with the path in uri[1].
 */
public class PostTask extends AsyncTask<String, Void, Void> {

	private static final String TAG = "PostTask";

	public static final String URI_SHUTDOWN = "/rest/shutdown";

	@Override
	protected Void doInBackground(String... uri) {
		String fullUri = uri[0] + uri[1];
		Log.i(TAG, "Sending POST request to " + fullUri);
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost post = new HttpPost(fullUri);
		String responseString = null;
		try {
			HttpResponse response = httpclient.execute(post);
		}
		catch (IOException e) {
			Log.w(TAG, "Failed to call Rest API at " + fullUri, e);
		}
		return null;
	}

}
