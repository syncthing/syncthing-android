package com.nutomic.syncthingandroid.util;

import android.annotation.SuppressLint;
import android.util.Log;

import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;


/*
 * TrustManager allowing the Syncthing https.pem CA
 *
 * Based on http://stackoverflow.com/questions/16719959#16759793
 *
 */
public class CustomX509TrustManager implements X509TrustManager {

    private static final String TAG = "CustomX509TrustManager";

    /**
     * Taken from: http://janis.peisenieks.lv/en/76/english-making-an-ssl-connection-via-android/
     *
     */
    public static class CustomSSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public CustomSSLSocketFactory(SSLContext context)
                throws KeyManagementException, NoSuchAlgorithmException,
                KeyStoreException, UnrecoverableKeyException {
            super(null);
            sslContext = context;
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port,
                                   boolean autoClose) throws IOException {
            return sslContext.getSocketFactory().createSocket(socket, host, port,
                    autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

    private final String mHttpsCertPath;

    public CustomX509TrustManager(String httpsCertPath) {
        mHttpsCertPath = httpsCertPath;
    }

    @Override
    @SuppressLint("TrustAllX509TrustManager")
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
    }

    /**
     * Verifies certs against public key of the local syncthing instance
     */
    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                                   String authType) throws CertificateException {
        InputStream inStream = null;
        try {
            inStream = new FileInputStream(mHttpsCertPath);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate)
                    cf.generateCertificate(inStream);
            for (X509Certificate cert : certs) {
                cert.verify(ca.getPublicKey());
            }
        } catch (FileNotFoundException |NoSuchAlgorithmException|InvalidKeyException|
                NoSuchProviderException |SignatureException e) {
            throw new CertificateException("Untrusted Certificate!", e);
        } finally {
            try {
                if (inStream != null)
                    inStream.close();
            } catch (IOException e) {
                Log.w(TAG, e);
            }
        }
    }
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
