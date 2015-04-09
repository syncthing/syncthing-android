package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.RestApi;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebGuiActivity extends SyncthingActivity
        implements SyncthingService.OnWebGuiAvailableListener {

    private static final String TAG = "WebGuiActivity";

    private WebView mWebView;

    private View mLoadingView;

    private X509Certificate mCaCert;

    /**
     * Hides the loading screen and shows the WebView once it is partially loaded.
     */
    private final WebViewClient mWebViewClient = new WebViewClient() {

        /**
         * Catch (self-signed) SSL errors and test if they correspond to Syncthing's certificate.
         */
        @Override
        public void onReceivedSslError (WebView view, SslErrorHandler handler, SslError error) {
            try {
                // Uses reflection to access the private mX509Certificate field of SslCertificate
                SslCertificate sslCert = error.getCertificate();
                Field f = sslCert.getClass().getDeclaredField("mX509Certificate");
                f.setAccessible(true);
                X509Certificate cert = (X509Certificate)f.get(sslCert);
                if (cert == null) {
                    Log.w(TAG, "X509Certificate reference invalid");
                    handler.cancel();
                    return;
                }
                cert.verify(mCaCert.getPublicKey());
                handler.proceed();
            } catch (NoSuchFieldException|IllegalAccessException|CertificateException|
                    NoSuchAlgorithmException|InvalidKeyException|NoSuchProviderException|
                    SignatureException e) {
                Log.w(TAG, e);
                handler.cancel();
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
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

        loadCaCert();

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
     *
     * Sets the X-API-Key (HEADER_API_KEY) header for authorization
     */
    @Override
    public void onWebGuiAvailable() {
        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put(RestApi.HEADER_API_KEY, getService().getApi().getApiKey());
        mWebView.loadUrl(getService().getWebGuiUrl(), extraHeaders);
    }

    /**
     * Reads the SyncthingService.HTTPS_CERT_FILE Ca Cert key  and loads it in memory
     */
    private void loadCaCert() {
        InputStream inStream = null;
        try {
            String httpsCertPath = getFilesDir() + "/" + SyncthingService.HTTPS_CERT_FILE;
            inStream = new FileInputStream(httpsCertPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            mCaCert = (X509Certificate)
                    cf.generateCertificate(inStream);
        } catch (FileNotFoundException|CertificateException e) {
            throw new IllegalArgumentException("Untrusted Certificate");
        } finally {
            try {
                if (inStream != null)
                    inStream.close();
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }
    }
}
