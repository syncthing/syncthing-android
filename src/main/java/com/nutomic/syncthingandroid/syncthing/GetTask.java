package com.nutomic.syncthingandroid.syncthing;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;

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

	public static final String URI_CONFIG = "/rest/config";

	public static final String URI_VERSION = "/rest/version";

	public static final String URI_SYSTEM = "/rest/system";

	public static final String URI_CONNECTIONS = "/rest/connections";

	public static final String URI_MODEL = "/rest/model";

	public static final String URI_NODEID = "/rest/nodeid";

	/**
	 * params[0] Syncthing hostname
	 * params[1] URI to call
	 * params[2] Syncthing API key
	 * params[3] optional parameter key
	 * params[4] optional parameter value
	 */
	@Override
	protected String doInBackground(String... params) {
		String fullUri = params[0] + params[1];
		HttpClient httpclient = new DefaultHttpClient();
		if (params.length == 5) {
			LinkedList<NameValuePair> urlParams = new LinkedList<NameValuePair>();
			urlParams.add(new BasicNameValuePair(params[3], params[4]));
			fullUri += "?" + URLEncodedUtils.format(urlParams, "utf-8");
		}
		HttpGet get = new HttpGet(fullUri);
		get.addHeader(new BasicHeader("X-API-Key", params[2]));

		try {
			HttpResponse response = httpclient.execute(get);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
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
			Log.w(TAG, "Failed to call Rest API at " + fullUri, e);
		}
		return null;
	}

}
