package com.nutomic.syncthingandroid.gui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;
import com.nutomic.syncthingandroid.syncthing.SyncthingServiceBinder;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebGuiActivity extends ActionBarActivity implements SyncthingService.OnWebGuiAvailableListener {

	private WebView mWebView;

	private View mLoadingView;

	private SyncthingService mSyncthingService;

	private final ServiceConnection mSyncthingServiceConnection = new ServiceConnection() {

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
	private final WebViewClient mWebViewClient = new WebViewClient() {

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

		setContentView(R.layout.web_gui_activity);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		mLoadingView = findViewById(R.id.loading);
		ProgressBar pb = (ProgressBar) mLoadingView.findViewById(R.id.progress);
		pb.setIndeterminate(true);

		mWebView = (WebView) findViewById(R.id.webview);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(mWebViewClient);

		bindService(new Intent(this, SyncthingService.class),
				mSyncthingServiceConnection, Context.BIND_AUTO_CREATE);
	}

	/**
	 * Loads and shows WebView, hides loading view.
	 */
	@Override
	public void onWebGuiAvailable() {
		mWebView.loadUrl(mSyncthingService.getApi().getUrl());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unbindService(mSyncthingServiceConnection);
	}
	
}
