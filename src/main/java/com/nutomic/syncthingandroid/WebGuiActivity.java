package com.nutomic.syncthingandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class WebGuiActivity extends Activity {

	private static final String TAG = "WebGuiActivity";

	/**
	 * URL of the local syncthing web UI.
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

	/**
	 * File in the config folder that contains configuration.
	 */
	private static final String CONFIG_FILE = "config.xml";

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

		// Handle first start.
		if (!new File(CONFIG_FOLDER, CERT_FILE).exists()) {
			copyDefaultConfig();

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

	/**
	 * Copies the default config file from res/raw/config_default.xml to CONFIG_FOLDER/CONFIG_FILE.
	 */
	private void copyDefaultConfig() {
		File config = new File(CONFIG_FOLDER, CONFIG_FILE);
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
