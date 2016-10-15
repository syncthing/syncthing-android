package com.nutomic.syncthingandroid.http;


import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import com.nutomic.syncthingandroid.syncthing.RestApi;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public abstract class RestTask<Params, Progress, Result> extends
        AsyncTask<Params, Progress, Pair<Boolean, Result>> {

    private static final String TAG = "RestTask";

    public interface OnSuccessListener<Result> {
        public void onSuccess(Result result);
    }

    private final URL mUrl;
    protected final String mPath;
    private final String mHttpsCertPath;
    private final String mApiKey;
    private final OnSuccessListener<Result> mListener;

    public RestTask(URL url, String path, String httpsCertPath, String apiKey,
                    OnSuccessListener<Result> listener) {
        mUrl           = url;
        mPath          = path;
        mHttpsCertPath = httpsCertPath;
        mApiKey        = apiKey;
        mListener      = listener;
    }

    protected HttpsURLConnection openConnection(String... params) throws IOException {
        Uri.Builder uriBuilder = Uri.parse(mUrl.toString())
                .buildUpon()
                .path(mPath);
        for (int paramCounter = 0; paramCounter + 1 < params.length; ) {
            uriBuilder.appendQueryParameter(params[paramCounter++], params[paramCounter++]);
        }
        URL url = new URL(uriBuilder.build().toString());

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestProperty(RestApi.HEADER_API_KEY, mApiKey);
        connection.setHostnameVerifier((h, s) -> true);
        connection.setSSLSocketFactory(getSslSocketFactory());
        return connection;
    }

    protected void onPostExecute(Pair<Boolean, Result> result) {
        if (mListener == null)
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
