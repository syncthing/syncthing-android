package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;
import android.util.Log;

import com.nutomic.syncthingandroid.R;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener {

	private static final String TAG = "RestApi";

	private String mVersion;

	private String mUrl;

	private String mApiKey;

	public RestApi(String url, String apiKey) {
		mUrl = url;
		mApiKey = apiKey;
	}

	/**
	 * Returns the full URL of the web gui.
	 */
	public String getUrl() {
		return mUrl;
	}

	@Override
	public void onWebGuiAvailable() {
		new GetTask() {
			@Override
			protected void onPostExecute(String version) {
				mVersion = version;
				Log.i(TAG, "Syncthing version is " + mVersion);
			}
		}.execute(mUrl, GetTask.URI_VERSION, mApiKey);
	}

	/**
	 * Returns the version name, or a (text) error message on failure.
	 */
	public String getVersion() {
		return mVersion;
	}

	/**
	 * Stops syncthing. You should probably use SyncthingService.stopService() instead.
	 */
	public void shutdown() {
		new PostTask().execute(mUrl, PostTask.URI_SHUTDOWN, mApiKey);
	}

}
