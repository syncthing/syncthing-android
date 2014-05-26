package com.nutomic.syncthingandroid.syncthing;

import android.content.Context;

import com.nutomic.syncthingandroid.R;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener {

	private Context mContext;

	private String mVersion;

	public RestApi(Context context) {
		mContext = context;
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
		}.execute(GetTask.URI_VERSION);
	}

	public String getVersion() {
		return mVersion;
	}

	public void shutdown() {
		new PostTask().execute(PostTask.URI_SHUTDOWN);
	}

}
