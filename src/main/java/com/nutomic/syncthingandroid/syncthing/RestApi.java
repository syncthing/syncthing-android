package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;

import com.nutomic.syncthingandroid.R;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener {

	private Context mContext;

	private String mVersion;

	private String mUrl;

	public RestApi(Context context, String url) {
		mContext = context;
		mUrl = url;
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
			protected void onPostExecute(String versionName) {
				mVersion = (versionName != null)
						? versionName
						: mContext.getString(R.string.syncthing_version_error);
			}
		}.execute(mUrl, GetTask.URI_VERSION);
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
		new PostTask().execute(mUrl, PostTask.URI_SHUTDOWN);
	}

}
