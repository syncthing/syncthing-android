package com.nutomic.syncthingandroid.http;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.ImageView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public abstract class ApiRequest {

    private static final String TAG = "ApiRequest";

    /**
     * The name of the HTTP header used for the syncthing API key.
     */
    private static final String HEADER_API_KEY = "X-API-Key";

    public interface OnSuccessListener {
        public void onSuccess(String result);
    }

    public interface OnImageSuccessListener {
        public void onImageSuccess(Bitmap result);
    }

    public interface OnErrorListener {
        public void onError(VolleyError error);
    }

    private static RequestQueue sVolleyQueue;

    private RequestQueue getVolleyQueue() {
        if (sVolleyQueue == null) {
            Context context = mContext.getApplicationContext();
            sVolleyQueue = Volley.newRequestQueue(context, new NetworkStack());
        }
        return sVolleyQueue;
    }

    private final Context mContext;
    private final URL mUrl;
    protected final String mPath;
    private final String mHttpsCertPath;
    private final String mApiKey;

    public ApiRequest(Context context, URL url, String path, String httpsCertPath, String apiKey) {
        mContext = context;
        mUrl           = url;
        mPath          = path;
        mHttpsCertPath = httpsCertPath;
        mApiKey        = apiKey;
    }

    protected Uri buildUri(Map<String, String> params) {
        Uri.Builder uriBuilder = Uri.parse(mUrl.toString())
                .buildUpon()
                .path(mPath);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        return uriBuilder.build();
    }

    /**
     * Opens the connection, then returns success status and response string.
     */
    protected void connect(int requestMethod, Uri uri, @Nullable String requestBody,
                           @Nullable OnSuccessListener listener, @Nullable OnErrorListener errorListener) {
        StringRequest request = new StringRequest(requestMethod, uri.toString(), reply -> {
            if (listener != null)
                listener.onSuccess(reply);
        }, error -> {
            if (errorListener != null)
                errorListener.onError(error);

            Log.w(TAG, "Request to " + uri + " failed: " + error.getMessage());
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return ImmutableMap.of(HEADER_API_KEY, mApiKey);
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                return Optional.fromNullable(requestBody).transform(String::getBytes).orNull();
            }
        };
        getVolleyQueue().add(request);
    }

    /**
     * Opens the connection, then returns success status and response bitmap.
     */
    protected void MakeImageRequest(Uri uri, @Nullable OnImageSuccessListener imageListener,
                                    @Nullable OnErrorListener errorListener) {
        ImageRequest imageRequest =  new ImageRequest(uri.toString(), bitmap -> {
            if (imageListener != null) {
                imageListener.onImageSuccess(bitmap);
            }
        }, 0, 0, ImageView.ScaleType.CENTER, Bitmap.Config.RGB_565, volleyError -> {
            if(errorListener != null) {
                errorListener.onError(volleyError);
            }
            Log.d(TAG, "onErrorResponse: " + volleyError);
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return ImmutableMap.of(HEADER_API_KEY, mApiKey);
            }
        };

        getVolleyQueue().add(imageRequest);
    }

    /**
     * Extends {@link HurlStack}, uses {@link #getSslSocketFactory()} and disables hostname
     * verification.
     */
    private class NetworkStack extends HurlStack {

        public NetworkStack() {
            super(null, getSslSocketFactory());
        }
        @Override
        protected HttpURLConnection createConnection(URL url) throws IOException {
            HttpsURLConnection connection = (HttpsURLConnection) super.createConnection(url);
            connection.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
            return connection;
        }
    }

    private SSLSocketFactory getSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new SyncthingTrustManager()},
                    new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            Log.w(TAG, e);
            return null;
        }
    }

    /*
     * TrustManager checking against the local Syncthing instance's https public key.
     *
     * Based on http://stackoverflow.com/questions/16719959#16759793
     */
    private class SyncthingTrustManager implements X509TrustManager {

        private static final String TAG = "SyncthingTrustManager";

        @Override
        @SuppressLint("TrustAllX509TrustManager")
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        /**
         * Verifies certs against public key of the local syncthing instance
         */
        @Override
        public void checkServerTrusted(X509Certificate[] certs,
                                       String authType) throws CertificateException {
            InputStream is = null;
            try {
                is = new FileInputStream(mHttpsCertPath);
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate ca = (X509Certificate) cf.generateCertificate(is);
                for (X509Certificate cert : certs) {
                    cert.verify(ca.getPublicKey());
                }
            } catch (FileNotFoundException | NoSuchAlgorithmException | InvalidKeyException |
                    NoSuchProviderException | SignatureException e) {
                throw new CertificateException("Untrusted Certificate!", e);
            } finally {
                try {
                    if (is != null)
                        is.close();
                } catch (IOException e) {
                    Log.w(TAG, e);
                }
            }
        }
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

}
