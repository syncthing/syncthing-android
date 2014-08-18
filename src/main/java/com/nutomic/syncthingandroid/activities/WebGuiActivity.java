package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebGuiActivity extends SyncthingActivity implements SyncthingService.OnWebGuiAvailableListener {

    private WebView mWebView;

    private View mLoadingView;

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
     * <p/>
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
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnWebGuiAvailableListener(WebGuiActivity.this);
    }

    /**
     * Loads and shows WebView, hides loading view.
     */
    @Override
    public void onWebGuiAvailable() {
        mWebView.loadUrl(getApi().getUrl());
    }

}
