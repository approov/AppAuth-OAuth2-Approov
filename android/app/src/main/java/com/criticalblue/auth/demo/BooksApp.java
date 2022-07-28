package com.criticalblue.auth.demo;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.ColorRes;

import com.criticalblue.auth.demo.auth.AuthRepo;
import com.criticalblue.auth.demo.books.BooksRepo;

import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import okhttp3.OkHttpClient;

// *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
import io.approov.service.okhttp.ApproovService;

public class BooksApp extends Application {
    private final String TAG = BooksApp.class.getSimpleName();

    public final static int RC_FAIL = 0;
    public final static int RC_AUTH = 100;

    private AuthRepo authRepo;

    private BooksRepo booksRepo;

    @Override
    public void onCreate() {
        super.onCreate();

        authRepo = new AuthRepo(this);
        booksRepo = new BooksRepo(this, authRepo);

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
        // Used to secure the API requests to the Google books API
        ApproovService.initialize(getApplicationContext(), getString(R.string.approov_config));

        // Used to secure the OAuth2 flow with the AppAuth package
        io.approov.service.httpsurlconn.ApproovService.initialize(getApplicationContext(), "");
    }

    /**
     * Returns a client for HTTPS requests.
     *
     * @return an http client.
     */
    public static OkHttpClient getHttpClient() {
        // *** COMMENT THE LINE BELOW FOR APPROOV ***
        //return new OkHttpClient.Builder().build();

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
        return ApproovService.getOkHttpClient();
    }

    /**
     * Builds a Picasso image downloader with an OkHttpClient secured with the Approov Managed Trust
     * Roots feature to protect the TLS channel against MitM attacks.
     *
     * @link https://approov.io/docs/latest/approov-usage-documentation/#managed-trust-roots
     *
     * @return a Picasso downloader secured by Approov.
     */
    public Picasso getImageDownloader() {
        OkHttpClient okHttpClient = getHttpClient();

        return new Picasso.Builder(getApplicationContext())
                .downloader(new OkHttp3Downloader(okHttpClient))
                .listener((picasso, uri, exception) -> {
                    Log.w(TAG, "FAILED TO LOAD IMAGE: " + uri.toString());
                    Log.e(TAG, exception.toString());
                })
                .build();
    }

    /**
     * Loads an image with the http:// protocol upgraded to the https:// protocol.
     *
     * The OkHttpClient being used is secured by Approov.
     *
     * @param url The url to download the image
     * @return A RequestCreator instance protected by Approov
     */
    public RequestCreator loadImage(String url) {
        // Images links returned by the Google API are http and the image will not load, because the
        // call will be redirected to https.
        Uri uri = Uri.parse(url);
        url = "https://" + uri.getEncodedAuthority() + uri.getEncodedPath() + "?" + uri.getEncodedQuery();
        Log.w(TAG, url);
        return getImageDownloader().load(url);
    }

    public AuthRepo getAuthRepo() {
        return authRepo;
    }

    public BooksRepo getBooksRepo() {
        return booksRepo;
    }

    public int getColorValue(@ColorRes int color) {
        return getColor(color);
    }
}

