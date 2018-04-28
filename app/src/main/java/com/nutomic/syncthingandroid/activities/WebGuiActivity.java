package com.nutomic.syncthingandroid.activities;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.net.Proxy;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.ConfigXml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * Holds a WebView that shows the web ui of the local syncthing instance.
 */
public class WebGuiActivity extends StateDialogActivity
        implements SyncthingService.OnWebGuiAvailableListener {

    private static final String TAG = "WebGuiActivity";

    private WebView mWebView;

    private View mLoadingView;

    private X509Certificate mCaCert;

    private ConfigXml mConfig;

    /**
     * Hides the loading screen and shows the WebView once it is fully loaded.
     */
    private final WebViewClient mWebViewClient = new WebViewClient() {

        /**
         * Catch errors like the timeout on loading a web page.
         */
         public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
             if (errorCode == WebViewClient.ERROR_TIMEOUT) {
                Log.w(TAG, "Timeout loading locally running web UI. Retrying ...");
                if (setWebViewProxy(mWebView.getContext().getApplicationContext(), "", 0, "localhost|0.0.0.0|127.*|[::1]")) {
                    view.reload();
                }
             }
             super.onReceivedError(view, errorCode, description, failingUrl);
         }

        /**
         * Catch (self-signed) SSL errors and test if they correspond to Syncthing's certificate.
         */
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            try {
                int sdk = android.os.Build.VERSION.SDK_INT;
                if (sdk < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    // The mX509Certificate field is not available for ICS- devices
                    Log.w(TAG, "Skipping certificate check for devices <ICS");
                    handler.proceed();
                    return;
                }
                // Use reflection to access the private mX509Certificate field of SslCertificate
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

        public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
            handler.proceed(mConfig.getUserName(), mConfig.getApiKey());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if(uri.getHost().equals(getService().getWebGuiUrl().getHost())) {
                return false;
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
        }

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

        setContentView(R.layout.activity_web_gui);

        mLoadingView = findViewById(R.id.loading);
        mConfig = new ConfigXml(this);
        loadCaCert();

        mWebView = findViewById(R.id.webview);
        setWebViewProxy(mWebView.getContext().getApplicationContext(), "", 0, "localhost|0.0.0.0|127.*|[::1]");
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.setWebViewClient(mWebViewClient);
        mWebView.clearCache(true);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        super.onServiceConnected(componentName, iBinder);
        getService().registerOnWebGuiAvailableListener(WebGuiActivity.this);
    }

    @Override
    public void onWebGuiAvailable() {
        if (mWebView.getUrl() == null) {
            mWebView.loadUrl(getService().getWebGuiUrl().toString());
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Reads the SyncthingService.HTTPS_CERT_FILE Ca Cert key  and loads it in memory
     */
    private void loadCaCert() {
        InputStream inStream = null;
        try {
            File httpsCertPath = Constants.getHttpsCertFile(this);
            inStream = new FileInputStream(httpsCertPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            mCaCert = (X509Certificate)
                    cf.generateCertificate(inStream);
        } catch (FileNotFoundException|CertificateException e) {
            throw new IllegalArgumentException("Untrusted Certificate", e);
        } finally {
            try {
                if (inStream != null)
                    inStream.close();
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }
    }

    /**
     * Set webview proxy and sites that are not retrieved using proxy.
     * Compatible with KitKat or higher android version.
     * Returns boolean if successful.
     * Source: https://stackoverflow.com/a/26781539
     */
    public static boolean setWebViewProxy(Context appContext, String host, int port, String exclusionList) {
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // Not supported on android version lower than KitKat.
            return false;
        }

        Properties properties = System.getProperties();
        properties.setProperty("http.proxyHost", host);
        properties.setProperty("http.proxyPort", Integer.toString(port));
        properties.setProperty("https.proxyHost", host);
        properties.setProperty("https.proxyPort", Integer.toString(port));
        properties.setProperty("http.nonProxyHosts", exclusionList);
        properties.setProperty("https.nonProxyHosts", exclusionList);

        try {
            Class applictionCls = Class.forName("android.app.Application");
            Field loadedApkField = applictionCls.getDeclaredField("mLoadedApk");
            loadedApkField.setAccessible(true);
            Object loadedApk = loadedApkField.get(appContext);
            Class loadedApkCls = Class.forName("android.app.LoadedApk");
            Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
            receiversField.setAccessible(true);
            ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
            for (Object receiverMap : receivers.values()) {
                for (Object rec : ((ArrayMap) receiverMap).keySet()) {
                    Class clazz = rec.getClass();
                    if (clazz.getName().contains("ProxyChangeListener")) {
                        Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);

                        String CLASS_NAME;
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                            CLASS_NAME = "android.net.ProxyProperties";
                        } else {
                            CLASS_NAME = "android.net.ProxyInfo";
                        }
                        Class cls = Class.forName(CLASS_NAME);
                        Constructor constructor = cls.getConstructor(String.class, Integer.TYPE, String.class);
                        constructor.setAccessible(true);
                        Object proxyProperties = constructor.newInstance(host, port, exclusionList);
                        intent.putExtra("proxy", (Parcelable) proxyProperties);

                        onReceiveMethod.invoke(rec, appContext, intent);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
