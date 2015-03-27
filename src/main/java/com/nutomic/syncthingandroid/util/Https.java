package com.nutomic.syncthingandroid.util;

import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/*
 * Wrapper for HTTPS Clients allowing the Syncthing https.pem CA
 *
 */
public class Https {

    private static final String TAG = "HTTPS";

    /**
     * Create a HTTPClient that verifies a custom PEM certificate
     *
     * @param httpsCertPath refers to the filepath of a SSL/TLS PEM certificate.
     */
    public static HttpClient createHttpsClient(String httpsCertPath) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { new CustomX509TrustManager(httpsCertPath) },
                    new SecureRandom());
            HttpClient client = new DefaultHttpClient();
            SSLSocketFactory ssf = new CustomX509TrustManager.CustomSSLSocketFactory(ctx);
            ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            ClientConnectionManager ccm = client.getConnectionManager();
            SchemeRegistry sr = ccm.getSchemeRegistry();
            sr.register(new Scheme("https", ssf, 443));
            return new DefaultHttpClient(ccm,
                    client.getParams());
        } catch (NoSuchAlgorithmException|KeyManagementException|KeyStoreException|
                UnrecoverableKeyException e) {
          Log.w(TAG, e);
        }
        return null;
    }
}
