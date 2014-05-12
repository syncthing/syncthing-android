package com.nutomic.syncthingandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

public class WebGuiActivity extends Activity {

	private static final String TAG = "WebGuiActivity";

	/**
	 * URL of the local syncthing web UI.
	 *
	 * TODO: read this out from native code.
	 */
	private static final String SYNCTHING_URL = "http://127.0.0.1:8080";

	/**
	 * Folder where syncthing config is stored.
	 *
	 * TODO: do this dynamically
	 */
	private static final String CONFIG_FOLDER = "/data/data/com.nutomic.syncthingandroid/";

	/**
	 * File in the config folder that contains the public key.
	 */
	private static final String CERT_FILE = "cert.pem";

	private WebView mWebView;
	private View mLoadingView;

	/**
	 * Retries loading every second until the web UI becomes available.
	 */
	private WebViewClient mWebViewClient = new WebViewClient() {

		private int mError = 0;

		@Override
		public void onReceivedError(WebView view, int errorCode, String description,
				String failingUrl) {
			mError = errorCode;
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					mError = 0;
					mWebView.loadUrl(SYNCTHING_URL);
				}
			}, 1000);

		}

		@Override
		public void onPageFinished(WebView view, String url) {
			if (mError == 0) {
				mWebView.setVisibility(View.VISIBLE);
				mLoadingView.setVisibility(View.GONE);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startService(new Intent(this, SyncthingService.class));

		setContentView(R.layout.main);

		mLoadingView = findViewById(R.id.loading);
		ProgressBar pb = (ProgressBar) mLoadingView.findViewById(R.id.progress);
		pb.setIndeterminate(true);

		mWebView = (WebView) findViewById(R.id.webview);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(mWebViewClient);
		mWebView.loadUrl(SYNCTHING_URL);

		if (!new File(CONFIG_FOLDER, CERT_FILE).exists()) {
			// First start.
			TextView loadingText = (TextView) mLoadingView.findViewById(R.id.loading_text);
			loadingText.setText(R.string.web_gui_creating_key);
			new AlertDialog.Builder(this)
					.setTitle(R.string.welcome_title)
					.setMessage(R.string.welcome_text)
					.setNeutralButton(android.R.string.ok, null)
					.show();
		}
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
				stopService(new Intent(this, SyncthingService.class));
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
}
