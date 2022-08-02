package com.criticalblue.auth.demo.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;

import android.util.Log;

import com.criticalblue.auth.demo.BooksApp;
import com.criticalblue.auth.demo.R;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.AuthorizationServiceDiscovery;
import net.openid.appauth.ClientAuthentication;
import net.openid.appauth.ClientSecretPost;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;
import net.openid.appauth.connectivity.ConnectionBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_CODE_EXCHANGE_FINISH;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_CODE_EXCHANGE_START;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_LOGIN_FAILURE;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_LOGIN_START;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_LOGIN_SUCCESS;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_LOGOUT_START;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_LOGOUT_SUCCESS;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_SERVICE_DISCOVERY_FINISH;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_SERVICE_DISCOVERY_START;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_USER_AUTH_FINISH;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_USER_AUTH_START;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_USER_INFO_FINISH;
import static com.criticalblue.auth.demo.auth.AuthEvent.AUTH_USER_INFO_START;

import javax.net.ssl.HttpsURLConnection;

// *** APPROOV DEPENDENCIES ***
import io.approov.service.httpsurlconn.ApproovException;
import io.approov.service.httpsurlconn.ApproovNetworkException;
import io.approov.service.httpsurlconn.ApproovRejectionException;

public class AuthRepo {
    BooksApp app;
    String clientId = null;
    String redirectUri = null;
    String authScope = null;

    private final String TAG = AuthRepo.class.getSimpleName();
    private final Semaphore loginLock;
    private final AuthorizationService authService;
    private AuthLoginListener loginListener = null;
    private AuthState authState = null;
    private String userInfoUrl = null;

    public AuthRepo(BooksApp app) {
        this.app = app;
        loginLock = new Semaphore(1);

        // *** COMMENT THE TWO LINES BELOW FOR APPROOV ***
        AppAuthConfiguration configuration = new AppAuthConfiguration.Builder().build();
        authService = new AuthorizationService(app, configuration);

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
        // authService = new AuthorizationService(app, ApproovCustomConnection());
    }

    /**
     * All the calls made by AppAuth will be secured by using the Approov Managed Trust Roots
     * feature to protect the TLS channel against MitM attacks.
     *
     * @link https://approov.io/docs/latest/approov-usage-documentation/#managed-trust-roots
     *
     * @return The AppAuthConfiguration with a HttpsURLConnection secured by Approov
     */
    private AppAuthConfiguration ApproovCustomConnection() {
        return new AppAuthConfiguration.Builder()
                .setConnectionBuilder(new ConnectionBuilder() {
                    @NonNull
                    @Override
                    public HttpsURLConnection openConnection(@NonNull Uri uri) throws IOException {
                        URL url = new URL(uri.toString());
                        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

                        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
                        // io.approov.service.httpsurlconn.ApproovService.addApproov(connection);

                        return connection;
                    }
                })
                .build();
    }

    /**
     * Exchanges the OAuth2 Authorization code by an Access and ID token.
     *
     * Defaults to use the OAuth2 Mobile FLow, but if you are integrating Approov then you need to
     * switch to use instead the OAuth2 Web Flow, that allows to provide a client_secret, that is
     * securely retrieved from the Approov cloud service just-in-time of being used to make request.
     *
     * @param resp An instance of AuthorizationResponse with the response from the OAuth2 screen.
     */
    private void performCodeExchangeRequest(AuthorizationResponse resp) {
        // *** COMMENT THE LINE BELOW FOR APPROOV ***
        performMobileFlowTokenRequest(resp);

        // *** UNCOMMENT THE LINE BELOW FOR APPROOV ***
        // performWebFlowTokenRequest(resp);
    }

    /**
     * Approov allows to fetch secrets just in time for being used to make API calls, provided that
     * the mobile app passes the attestation, thus no more hardcoded secrets on the app code.
     *
     * @link https://approov.io/docs/latest/approov-usage-documentation/#secure-strings
     *
     * @return The client secret retrieved from the Approov Cloud service
     * @throws AuthException When fails the Mobile App Attestation or no network connectivity
     */
    private String fetchJustInTimeClientSecret() throws AuthException {
        String clientSecret;

        try {
            clientSecret = io.approov.service.httpsurlconn.ApproovService.fetchSecureString("client_secret", null);
        } catch (ApproovRejectionException e) {
            Log.e(TAG, "ApproovRejectionException - REASON: " + e.getRejectionReasons() + " - ARC: " + e.getARC());
            throw new AuthException("Failed the Mobile App Attestation.");
        } catch (ApproovNetworkException e) {
            Log.w(TAG, "ApproovNetworkException() - REASON: " + e.getMessage());
            throw new AuthException("No network connectivity - Please try again.");
        } catch (ApproovException e) {
            Log.e(TAG, "ApproovException() - REASON: " + e.getMessage());
            throw new AuthException("Unable to perform the Mobile App Attestation.");
        }

        if (clientSecret == null) {
            throw new AuthException("Unable to start the OAuth2 Authorization Code exchange.");
        }

        return clientSecret;
    }

    public boolean isConfigured() {
        return (authState != null &&
                authState.getAuthorizationServiceConfiguration() != null &&
                clientId != null &&
                redirectUri != null &&
                authScope != null);
    }

    public boolean isAuthorized() {
        return (authState != null && authState.isAuthorized());
    }

    private void lockLogins() {
        try {
            loginLock.acquire();
        } catch (InterruptedException ex) {
            throw new RuntimeException("Unexpected interrupt", ex);
        }
    }

    private void unlockLogins() {
        loginLock.release();
    }

    public void login(AuthLoginListener loginListener) {
        Log.i(TAG, "login called");

        lockLogins();

        if (isAuthorized()) {
            unlockLogins();
            return;
        }

        this.loginListener = loginListener;
        loginListener.onStart(AuthRepo.this, AUTH_LOGIN_START);

        initOauth2Flow();
    }

    private void initOauth2Flow() {
        Log.i(TAG, "---> Fetch Authorization config");

        String discoveryEndpoint = app.getString(R.string.discovery_endpoint);
        Log.i(TAG, "discoveryEndpoint: " + discoveryEndpoint);

        Uri discoveryUri = Uri.parse(discoveryEndpoint);
        loginListener.onEvent(AuthRepo.this, AUTH_SERVICE_DISCOVERY_START);

        AuthorizationServiceConfiguration.fetchFromUrl(discoveryUri, this::finishServiceDiscovery);
    }

    private void finishServiceDiscovery(AuthorizationServiceConfiguration config, AuthorizationException ex) {
        if (config == null) {
            failLogin(new AuthException("Failed to retrieve authorization service discovery document"));
            return;
        }

        authState = new AuthState(config);
        AuthorizationServiceDiscovery discovery = config.discoveryDoc;

        if (discovery != null) {
            userInfoUrl = app.getString(R.string.user_info_endpoint);
        }

        validateUserInfoUrl();

        loginListener.onEvent(AuthRepo.this, AUTH_SERVICE_DISCOVERY_FINISH);

        Log.i(TAG, "---> Service Discovery Config");
        Log.i(TAG, "  authorization endpoint: " + Objects.requireNonNull(authState.getAuthorizationServiceConfiguration()).authorizationEndpoint);
        Log.i(TAG, "  token endpoint: " + authState.getAuthorizationServiceConfiguration().tokenEndpoint);
        Log.i(TAG, "  user info endpoint: " + userInfoUrl);

        setClientConfig();
        startUserAuth();
    }

    private void validateUserInfoUrl() {
        try {
            new URL(userInfoUrl);
            if (!userInfoUrl.endsWith("/")) userInfoUrl += "/";
        } catch (MalformedURLException urlEx) {
            userInfoUrl = null;
        }
    }

    private void setClientConfig() {
        clientId = app.getString(R.string.client_id);
        redirectUri = app.getString(R.string.redirect_uri);
        authScope = app.getString(R.string.authorization_scope);

        Log.i(TAG, "---> Client config");
        Log.i(TAG, "  client id: " + clientId);
        Log.i(TAG, "  redirect uri: " + redirectUri);
        Log.i(TAG, "  auth scope: " + authScope);
    }

    private void startUserAuth() {
        Log.i(TAG, "---> Starting user auth");

        loginListener.onEvent(AuthRepo.this, AUTH_USER_AUTH_START);

        // may need to do this off UI thread?

        AuthorizationRequest.Builder authRequestBuilder = new AuthorizationRequest.Builder(
                Objects.requireNonNull(authState.getAuthorizationServiceConfiguration()),
                clientId,
                ResponseTypeValues.CODE,
                Uri.parse(redirectUri))
                .setScope(authScope);
        AuthorizationRequest authRequest = authRequestBuilder.build();

        CustomTabsIntent.Builder intentBuilder =
                authService.createCustomTabsIntentBuilder(authRequest.toUri());
        intentBuilder.setToolbarColor(app.getColorValue(R.color.colorAccent));
        CustomTabsIntent authIntent = intentBuilder.build();

        Intent intent = authService.getAuthorizationRequestIntent(authRequest, authIntent);

        loginListener.onUserAgentRequest(AuthRepo.this, intent);
    }

    public void notifyUserAgentResponse(Intent data, int returnCode) {
        if (returnCode != BooksApp.RC_AUTH) {
            failLogin(new AuthException("User authorization was cancelled"));
            return;
        }

        AuthorizationResponse resp = AuthorizationResponse.fromIntent(data);
        AuthorizationException ex = AuthorizationException.fromIntent(data);

        if (resp == null) {
            failLogin(new AuthException("User authorization failed"));
        } else {
            authState.update(resp, ex);
            finishUserAuth();
        }
    }

    private void finishUserAuth() {
        Log.i(TAG, "---> Finishing user auth");

        loginListener.onEvent(AuthRepo.this, AUTH_USER_AUTH_FINISH);

        startCodeExchange();
    }

    private void startCodeExchange() {
        Log.i(TAG, "---> Starting code exchange");

        loginListener.onEvent(AuthRepo.this, AUTH_CODE_EXCHANGE_START);

        AuthorizationResponse resp = authState.getLastAuthorizationResponse();

        performCodeExchangeRequest(resp);
    }

    private void performMobileFlowTokenRequest(AuthorizationResponse resp) {
        if (resp != null) {
            authService.performTokenRequest(
                    resp.createTokenExchangeRequest(), this::onTokenRequestCompleted);
        }
    }

    private void performWebFlowTokenRequest(AuthorizationResponse resp) {
        ClientAuthentication clientAuth = null;

        try {
            clientAuth = new ClientSecretPost(fetchJustInTimeClientSecret());
        } catch (AuthException e) {
            Log.e(TAG, e.toString());
            failLogin(e);
            return;
        }

        if (resp != null) {
            authService.performTokenRequest(
                    resp.createTokenExchangeRequest(), clientAuth, this::onTokenRequestCompleted);
        }
    }

    private void onTokenRequestCompleted(TokenResponse resp, AuthorizationException ex) {
        if (resp == null) {
            Log.e(TAG, "onTokenRequestCompleted(): " + ex.getMessage());
            failLogin(new AuthException(ex.getMessage()));
            return;
        }

        authState.update(resp, ex);
        finishCodeExchange();
    }

    private void finishCodeExchange() {
        Log.i(TAG, "---> Finishing code exchange");

        loginListener.onEvent(AuthRepo.this, AUTH_CODE_EXCHANGE_FINISH);

        startUserInfo();
    }

    private void startUserInfo() {
        Log.i(TAG, "---> Starting user info");

        loginListener.onEvent(AuthRepo.this, AUTH_USER_INFO_START);
        fetchUserInfo(this::onUserInfoCompleted);
    }

    private void onUserInfoCompleted(UserInfo userInfo, AuthException ex) {
        if (userInfo == null) Log.i(TAG, "Unable to obtain user info.");

        finishUserInfo();
    }

    private void finishUserInfo() {
        Log.i(TAG, "---> Finishing user info");

        loginListener.onEvent(AuthRepo.this, AUTH_USER_INFO_FINISH);

        finishLogin();
    }

    private void failLogin(AuthException ex) {
        Log.i(TAG, "Failing login");

        loginListener.onFailure(AuthRepo.this, AUTH_LOGIN_FAILURE, ex);

        unlockLogins();
    }

    private void finishLogin() {
        Log.i(TAG, "---> Finishing login");

        loginListener.onSuccess(AuthRepo.this, AUTH_LOGIN_SUCCESS);

        unlockLogins();
    }

    private UserInfo userInfo;

    private UserInfoAPI createUserInfoAPI() {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient authClient = new OkHttpClient().newBuilder()
                .addInterceptor(getAccessTokenInterceptor())
                .addInterceptor(logger)
                .build();

        Gson gson = new GsonBuilder().setLenient().create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(userInfoUrl)
                .client(authClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        return retrofit.create(UserInfoAPI.class);
    }

    class UserInfoTask extends AsyncTask<Void, Void, UserInfo> {
        private final UserInfoCallback callback;

        UserInfoTask(UserInfoCallback callback) {
            this.callback = callback;
        }

        protected UserInfo doInBackground(Void... params) {
            UserInfoAPI userInfoAPI = createUserInfoAPI();
            Call<UserInfoResult> call = userInfoAPI.getUserInfo();
            userInfo = null;

            try {
                Response<UserInfoResult> response = call.execute();

                Log.i(TAG, "---> User Info Request URL: " + call.request().url());

                if (response.isSuccessful()) {
                    UserInfoResult result = response.body();
                    if (result != null) {
                        userInfo = new UserInfo(result.getFamilyName(), result.getGivenName(),
                                result.getPicture());
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }

            return userInfo;
        }

        protected void onPostExecute(UserInfo userInfo) {
            if (userInfo == null) {
                callback.call(null, new AuthException("Unable to retrieve user info"));
                return;
            }

            callback.call(userInfo, null);
        }
    }

    public void fetchUserInfo(UserInfoCallback callback) {
        if (callback == null) throw new RuntimeException("fetchUserInfo: null callback");

        if (!isAuthorized()) {
            callback.call(null, new AuthException("Not authorized"));
            return;
        }

        new UserInfoTask(callback).execute();
    }

    public UserInfo getUserInfo() {
        if (!isAuthorized()) return null;

        if (userInfo != null) return userInfo;

        CountDownLatch fetchComplete = new CountDownLatch(1);

        fetchUserInfo((userInfo, ex) -> {
            AuthRepo.this.userInfo = userInfo;
            fetchComplete.countDown();
        });

        boolean complete;
        try {
            complete = fetchComplete.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            complete = false;
        }
        if (!complete) userInfo = null;

        return userInfo;
    }

    public void logout(AuthLogoutListener logoutListener) {
        lockLogins();

        if (!isAuthorized()) {
            unlockLogins();
            return;
        }

        logoutListener.onStart(AuthRepo.this, AUTH_LOGOUT_START);

        if (isConfigured()) {
            authState = new AuthState(Objects.requireNonNull(authState.getAuthorizationServiceConfiguration()));
        } else {
            authState = null;
            clientId = null;
            redirectUri = null;
            userInfoUrl = null;
        }

        userInfo = null;

        logoutListener.onSuccess(AuthRepo.this, AUTH_LOGOUT_SUCCESS);

        unlockLogins();
    }

    private String accessToken;

    // dangerous; do not call on UI thread.
    private String getAccessToken() {
        if (!isAuthorized()) {
            return null;
        }

        CountDownLatch actionComplete = new CountDownLatch(1);

        authState.performActionWithFreshTokens(authService, (String authToken,
                                                             String idToken,
                                                             AuthorizationException ex) -> {
            accessToken = authToken;
            actionComplete.countDown();
        });

        boolean complete;
        try {
            complete = actionComplete.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            complete = false;
        }
        if (!complete) accessToken = null;

        String token = accessToken;
        this.accessToken = null;

        return token;
    }

    public Interceptor getAccessTokenInterceptor() {
        return chain -> {
            Request request = chain.request();

            Log.i(TAG, "---> Token Interceptor: " + request.url());

            request = request.newBuilder()
                    .header("Authorization", "Bearer " + getAccessToken())
                    .build();

            return chain.proceed(request);
        };
    }
}
