package com.criticalblue.auth.demo.auth;

import android.content.res.AssetManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import net.openid.appauth.Preconditions;
import net.openid.appauth.connectivity.ConnectionBuilder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SignedConnectionBuilder implements ConnectionBuilder {
    private final String TAG = AuthRepo.class.getSimpleName();

    private static final int CONNECTION_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(15);
    private static final int READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(10);

    private static final String HTTPS_SCHEME = "https";

    private AssetManager assets;

    public SignedConnectionBuilder(AssetManager assets) {
        this.assets = assets;
    }

    @NonNull
    @Override
    public HttpURLConnection openConnection(@NonNull Uri uri) throws IOException {
        Preconditions.checkNotNull(uri, "url must not be null");
        Preconditions.checkArgument(HTTPS_SCHEME.equals(uri.getScheme()),
                "only https connections are permitted");
        HttpsURLConnection conn = (HttpsURLConnection) new URL(uri.toString()).openConnection();
        conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(false);

        // SET UP SSL context which holds signed certs

        SSLContext sslContext;
        TrustManager[] trustManagers;
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            InputStream certInputStream = assets.open("cert.pem");
            BufferedInputStream bis = new BufferedInputStream(certInputStream);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            while (bis.available() > 0) {
                Certificate cert = certificateFactory.generateCertificate(bis);
                keyStore.setCertificateEntry("localhost", cert);
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            trustManagers = trustManagerFactory.getTrustManagers();
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open signed connection: " + e.getMessage());
            return null;
        }

        conn.setSSLSocketFactory(sslContext.getSocketFactory());

        // set up hostname verifier

        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                HostnameVerifier hv =
                        HttpsURLConnection.getDefaultHostnameVerifier();
                Log.d(TAG, "verify hostname: " + hostname);
                return true; // hv.verify(hostname, session);
            }
        };

        conn.setHostnameVerifier(hostnameVerifier);

        // return connection

        return conn;
    }
}