package com.nutomic.syncthingandroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebGuiActivity extends Activity implements SyncthingService.OnWebGuiAvailableListener {

	private static final String TAG = "WebGuiActivity";

	/**
	 * File in the config folder that contains the public key.
	 */
	private static final String CERT_FILE = "cert.pem";

	/**
	 * File in the config folder that contains configuration.
	 */
	private static final String CONFIG_FILE = "config.xml";

	private WebView mWebView;
	private View mLoadingView;

	private SyncthingService mSyncthingService;

	private ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className, IBinder service) {
			SyncthingServiceBinder binder = (SyncthingServiceBinder) service;
			mSyncthingService = binder.getService();
			mSyncthingService.registerOnWebGuiAvailableListener(WebGuiActivity.this);
		}

		public void onServiceDisconnected(ComponentName className) {
			mSyncthingService = null;
		}
	};

	/**
	 * Hides the loading screen and shows the WebView once it is fully loaded.
	 */
	private WebViewClient mWebViewClient = new WebViewClient() {

		@Override
		public void onPageFinished(WebView view, String url) {
			mWebView.setVisibility(View.VISIBLE);
			mLoadingView.setVisibility(View.GONE);
		}
	};

	/**
	 * Initialize WebView.
	 *
	 * Ignore lint javascript warning as js is loaded only from our known, local service.
	 */
	@Override
	@SuppressLint("SetJavaScriptEnabled")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		mLoadingView = findViewById(R.id.loading);
		ProgressBar pb = (ProgressBar) mLoadingView.findViewById(R.id.progress);
		pb.setIndeterminate(true);

		mWebView = (WebView) findViewById(R.id.webview);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(mWebViewClient);

		// Handle first start.
		File config = new File(getApplicationInfo().dataDir, CONFIG_FILE);
		if (!config.exists()) {
			copyDefaultConfig(config);

			TextView loadingText = (TextView) mLoadingView.findViewById(R.id.loading_text);
			loadingText.setText(R.string.web_gui_creating_key);
			new AlertDialog.Builder(this)
					.setTitle(R.string.welcome_title)
					.setMessage(R.string.welcome_text)
					.setNeutralButton(android.R.string.ok, null)
					.show();
		}

		getApplicationContext().startService(
				new Intent(this, SyncthingService.class));
		getApplicationContext().bindService(
				new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);
	}

	/**
	 * Loads and shows WebView, hides loading view.
	 */
	@Override
	public void onWebGuiAvailable() {
		mWebView.loadUrl(SyncthingService.SYNCTHING_URL);
	}


	@Override
	public void onDestroy() {
		super.onDestroy();
		getApplicationContext().unbindService(mSyncthingServiceConnection);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case R.id.exit:
				// Make sure we unbind first.
				finish();
				getApplicationContext().stopService(new Intent(this, SyncthingService.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Copies the default config file from res/raw/config_default.xml to config.
	 *
	 * @param config: Destination file where the default config should be written.
	 */
	private void copyDefaultConfig(File config) {
		InputStream in = null;
		FileOutputStream out = null;
		try {
			in = getResources().openRawResource(R.raw.config_default);
			out = new FileOutputStream(config);
			byte[] buff = new byte[1024];
			int read = 0;

			while ((read = in.read(buff)) > 0) {
				out.write(buff, 0, read);
			}
		}
		catch (IOException e) {
			Log.w(TAG, "Failed to write config file", e);
			config.delete();
		}
		finally {
			try {
				in.close();
				out.close();
			}
			catch (IOException e) {
				Log.w(TAG, "Failed to close stream while copying config", e);
			}
		}
	}
	
}
