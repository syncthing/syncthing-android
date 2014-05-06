package com.nutomic.syncthingandroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

public class WebGuiActivity extends Activity {

	private static final String TAG = "WebGuiActivity";

	/**
	 * URL of the local syncthing web UI.
	 *
	 * TODO: read this out from native code.
	 */
	private static final String SYNCTHING_URL = "http://127.0.0.1:8080";

	private WebView mWebView;
	private ProgressBar mLoadingView;

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

		mLoadingView = (ProgressBar) findViewById(R.id.loading);
		mLoadingView.setIndeterminate(true);
		mWebView = (WebView) findViewById(R.id.webview);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(new WebViewClient());
		mWebView.setWebViewClient(mWebViewClient);
		mWebView.loadUrl(SYNCTHING_URL);
	}
	
}
