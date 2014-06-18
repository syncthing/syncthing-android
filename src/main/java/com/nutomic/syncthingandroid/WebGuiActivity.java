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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebGuiActivity extends Activity implements SyncthingService.OnWebGuiAvailableListener {

	private static final String TAG = "WebGuiActivity";

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

		if (SyncthingService.isFirstStart(this)) {
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
		bindService(
				new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);
	}

	/**
	 * Loads and shows WebView, hides loading view.
	 */
	@Override
	public void onWebGuiAvailable() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mWebView.loadUrl(mSyncthingService.getApi().getUrl());
			}
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		return mSyncthingService != null && mSyncthingService.isWebGuiAvailable();
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
	
}
