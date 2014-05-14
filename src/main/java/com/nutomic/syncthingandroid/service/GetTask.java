package com.nutomic.syncthingandroid.service;


import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Performs a GET request with no parameters to the URL in uri[0] and
 * returns the result as a String.
 */
public class GetTask extends AsyncTask<String, Void, String> {

	private static final String TAG = "GetTask";

	/**
	 * URI to call to get version name.
	 */
	public static final String URI_VERSION = SyncthingService.SYNCTHING_URL + "/rest/version";

    @Override
	 protected String doInBackground(String... uri) {
		HttpClient httpclient = new DefaultHttpClient();
		HttpGet get = new HttpGet(uri[0]);
		String responseString = null;
		try {
			HttpResponse response = httpclient.execute(get);
			HttpEntity entity = response.getEntity();

			if (entity != null) {

				// A Simple JSON Response Read
				InputStream is = entity.getContent();

				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line;
				String result = "";
				while((line = br.readLine()) != null) {
					result += line;
				}
				br.close();
				return result;
			}
		}
		catch (IOException e) {
			Log.w(TAG, "Failed to call Rest API at " + uri[0], e);
		}
		return null;
	}
}
