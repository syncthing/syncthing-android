package com.nutomic.syncthingandroid.http;


import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;

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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public abstract class RestTask extends
        AsyncTask<Void, Void, Pair<Boolean, String>> {

    private static final String TAG = "RestTask";

    /**
     * The name of the HTTP header used for the syncthing API key.
     */
    private static final String HEADER_API_KEY = "X-API-Key";

    public interface OnSuccessListener {
        public void onSuccess(String result);
    }

    private final URL mUrl;
    protected final String mPath;
    private final String mHttpsCertPath;
    private final String mApiKey;
    private final OnSuccessListener mListener;

    public RestTask(URL url, String path, String httpsCertPath, String apiKey,
                    OnSuccessListener listener) {
        mUrl           = url;
        mPath          = path;
        mHttpsCertPath = httpsCertPath;
        mApiKey        = apiKey;
        mListener      = listener;
    }

    public void execute() {
        super.executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    protected HttpsURLConnection openConnection(Map<String, String> params) throws IOException {
        Uri.Builder uriBuilder = Uri.parse(mUrl.toString())
                .buildUpon()
                .path(mPath);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            uriBuilder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        URL url = new URL(uriBuilder.build().toString());
        Log.v(TAG, "Calling Rest API at " + url);

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty(HEADER_API_KEY, mApiKey);
        connection.setHostnameVerifier((h, s) -> true);
        connection.setSSLSocketFactory(getSslSocketFactory());
        return connection;
    }

    /**
     * Opens the connection, then returns success status and response string.
     */
    protected Pair<Boolean, String> connect(HttpsURLConnection connection) throws IOException {
        connection.connect();
        Pair<Boolean, String> result;
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            Log.i(TAG, "Request to " + connection.getURL() + " failed, code: " + responseCode +
                    ", message: " + responseMessage);
            result = new Pair<>(false, streamToString(connection.getErrorStream()));
        }
        else {
            result = new Pair<>(true, streamToString(connection.getInputStream()));
        }
        connection.disconnect();
        return result;
    }

    private String streamToString(InputStream is) throws IOException {
        ByteSource byteSource = new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
                return is;
            }
        };
        return byteSource.asCharSource(Charsets.UTF_8).read();
    }


    protected void onPostExecute(Pair<Boolean, String> result) {
        if (mListener == null || !result.first)
            return;

        mListener.onSuccess(result.second);
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
