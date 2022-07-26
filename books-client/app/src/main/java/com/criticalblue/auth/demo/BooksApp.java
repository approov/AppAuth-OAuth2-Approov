package com.criticalblue.auth.demo;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;

import androidx.annotation.ColorRes;

import android.util.Log;

import com.criticalblue.auth.demo.auth.AuthRepo;
import com.criticalblue.auth.demo.books.BooksRepo;
import com.google.common.io.BaseEncoding;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

        Log.i(TAG, "Creating BooksApp");

        authRepo = new AuthRepo(this);
        Log.i(TAG, "Auth repo created");

        booksRepo = new BooksRepo(this, authRepo);
        Log.i(TAG, "Books service created");

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
        ApproovService.initialize(getApplicationContext(), getString(R.string.approov_config));

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV RUNTIME SECRETS ***
        ApproovService.addSubstitutionQueryParam("key");
    }

    /**
     * Returns a client for http requests.
     *
     * @return an http client.
     */
    public static OkHttpClient getHttpClient() {
        // *** COMMENT THE LINE BELOW FOR APPROOV ***
        //return new OkHttpClient.Builder().build();

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
        return ApproovService.getOkHttpClient();
    }

    public AuthRepo getAuthRepo() {
        return authRepo;
    }

    public BooksRepo getBooksRepo() {
        return booksRepo;
    }

    public String getSignature() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (packageInfo == null
                    || packageInfo.signatures == null
                    || packageInfo.signatures.length == 0
                    || packageInfo.signatures[0] == null) {
                return null;
            }
            return signatureDigest(packageInfo.signatures[0]);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static String signatureDigest(Signature sig) {
        byte[] signature = sig.toByteArray();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] digest = md.digest(signature);
            return BaseEncoding.base16().lowerCase().encode(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public int getColorValue(@ColorRes int color) {
        return getColor(color);
    }

    /**
     * Returns an image downloader for http requests.
     *
     * @return an http downloader.
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

    public RequestCreator loadImage(String url) {
        // Images links returned by the Google API are http and the image will not load, because the
        // call will be redirected to https.
        Uri uri = Uri.parse(url);
        url = "https://" + uri.getEncodedAuthority() + uri.getEncodedPath() + "?" + uri.getEncodedQuery();
        Log.w(TAG, url);
        return getImageDownloader().load(url);
    }
}

