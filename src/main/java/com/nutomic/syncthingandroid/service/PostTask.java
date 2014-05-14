package com.nutomic.syncthingandroid;


import android.os.AsyncTask;
import android.util.Log;

import com.nutomic.syncthingandroid.service.SyncthingService;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;

/**
 * Performs a POST request with no parameters to the URL in uri[0].
 */
public class PostTask extends AsyncTask<String, Void, Void> {

	private static final String TAG = "PostTask";

	/**
	 * URI to call for shutdown.
	 */
	public static final String URI_SHUTDOWN = SyncthingService.SYNCTHING_URL + "/rest/shutdown";

	@Override
	protected Void doInBackground(String... uri) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost post = new HttpPost(uri[0]);
		String responseString = null;
		try {
			HttpResponse response = httpclient.execute(post);
		}
		catch (IOException e) {
			Log.w(TAG, "Failed to call Rest API at " + uri[0], e);
		}
		return null;
	}

}
