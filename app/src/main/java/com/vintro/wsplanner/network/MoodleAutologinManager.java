package com.vintro.wsplanner.network;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// manages moodle mobile webservice authentication and one-time autologin browser redirects
public class MoodleAutologinManager {
    private static final String TAG = "MoodleAutologinManager";

    private static final String MOODLE_HOST = "puw.wspa.pl";
    private static final String TOKEN_ENDPOINT = "https://puw.wspa.pl/login/token.php";
    private static final String REST_ENDPOINT = "https://puw.wspa.pl/webservice/rest/server.php";
    private static final String DEFAULT_AUTOLOGIN_URL = "https://puw.wspa.pl/admin/tool/mobile/autologin.php";
    public static final String DEFAULT_HOME_URL = "https://puw.wspa.pl/my/";

    private static final String SERVICE_MOBILE_APP = "moodle_mobile_app";
    private static final String WS_SITE_INFO = "core_webservice_get_site_info";
    private static final String WS_GET_AUTOLOGIN_KEY = "tool_mobile_get_autologin_key";
    private static final String USER_AGENT_MOODLE_MOBILE = "MoodleMobile";

    private static final String ERROR_LOCKOUT = "autologinkeygenerationlockout";
    private static final String ERROR_INVALID_TOKEN = "invalidtoken";
    private static final String ERROR_ACCESS_EXCEPTION = "accessexception";

    private static final ExecutorService executor = AppExecutors.io();
    private static final Handler mainHandler = AppExecutors.mainHandler();
    private static final Object tokenLock = new Object();

    private static final class TokenPair {
        final String token;
        final String privateToken;
        final long userId;

        TokenPair(String token, String privateToken, long userId) {
            this.token = token;
            this.privateToken = privateToken;
            this.userId = userId;
        }
    }

    private static final class AutologinKeyResult {
        final boolean success;
        final String key;
        final String autologinUrl;
        final String errorCode;

        AutologinKeyResult(boolean success, String key, String autologinUrl, String errorCode) {
            this.success = success;
            this.key = key;
            this.autologinUrl = autologinUrl;
            this.errorCode = errorCode;
        }
    }

    public static final class ResolutionResult {
        @NonNull
        public final Uri uri;
        public final boolean usedAutologin;
        @NonNull
        public final String reason;

        ResolutionResult(@NonNull Uri uri, boolean usedAutologin, @NonNull String reason) {
            this.uri = uri;
            this.usedAutologin = usedAutologin;
            this.reason = reason;
        }
    }

    // asynchronously fetch and cache moodle mobile tokens and userid after login
    public static void prefetchAuthTokensAsync(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                Logger.d(TAG + ".prefetch", "Skipped token prefetch: device is offline");
                return;
            }
            Logger.i(TAG + ".prefetch", "Starting background Moodle mobile token prefetch");
            TokenPair pair = ensureTokensAndUserId(appContext, false);
            if (pair != null) {
                Logger.i(TAG + ".prefetch", "Moodle mobile tokens and userid cached successfully");
            } else {
                Logger.w(TAG + ".prefetch", "Moodle mobile token prefetch did not complete; will retry lazily on click");
            }
        });
    }

    // resolve autologin url on background thread and open external browser safely from an activity
    public static void openUrlWithAutologin(
            @NonNull Activity activity,
            @Nullable String targetUrl,
            @Nullable Runnable onFinished
    ) {
        Context appContext = activity.getApplicationContext();
        executor.execute(() -> {
            ResolutionResult result = resolveAutologinUriSync(appContext, targetUrl);
            mainHandler.post(() -> {
                if (onFinished != null) {
                    onFinished.run();
                }
                if (activity.isFinishing() || activity.isDestroyed()) {
                    Logger.w(TAG + ".openUrlWithAutologin", "Activity no longer active, aborting browser launch");
                    return;
                }
                launchExternalBrowser(activity, result);
            });
        });
    }

    // resolve autologin url on background thread and open external browser safely from a fragment
    public static void openUrlWithAutologin(
            @NonNull Fragment fragment,
            @Nullable String targetUrl,
            @Nullable Runnable onFinished
    ) {
        Activity hostActivity = fragment.getActivity();
        if (hostActivity == null) {
            if (onFinished != null) {
                mainHandler.post(onFinished);
            }
            return;
        }
        Context appContext = hostActivity.getApplicationContext();
        executor.execute(() -> {
            ResolutionResult result = resolveAutologinUriSync(appContext, targetUrl);
            mainHandler.post(() -> {
                if (onFinished != null) {
                    onFinished.run();
                }
                Activity currentActivity = fragment.getActivity();
                if (!fragment.isAdded() || currentActivity == null || currentActivity.isFinishing() || currentActivity.isDestroyed()) {
                    Logger.w(TAG + ".openUrlWithAutologin", "Fragment no longer attached or active, aborting browser launch");
                    return;
                }
                launchExternalBrowser(currentActivity, result);
            });
        });
    }

    // build one-time autologin uri synchronously on a background thread with full graceful fallback
    @NonNull
    public static ResolutionResult resolveAutologinUriSync(@NonNull Context context, @Nullable String rawTargetUrl) {
        Context appContext = context.getApplicationContext();
        String safeTargetUrl = sanitizeTargetUrl(rawTargetUrl);
        Uri fallbackUri = Uri.parse(safeTargetUrl);

        String targetSummary = describeSafeUrl(safeTargetUrl);
        Logger.i(TAG + ".resolve", "Preparing PUW browser redirect for target=" + targetSummary);

        if (!isMoodleAutologinHost(safeTargetUrl)) {
            Logger.d(TAG + ".resolve", "Target host is not " + MOODLE_HOST + ", opening directly without autologin");
            return new ResolutionResult(fallbackUri, false, "external_host");
        }

        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            Logger.w(TAG + ".resolve", "Device offline, falling back to direct target URL: " + targetSummary);
            return new ResolutionResult(fallbackUri, false, "offline");
        }

        TokenPair credentials = ensureTokensAndUserId(appContext, false);
        if (credentials == null) {
            Logger.w(TAG + ".resolve", "No valid Moodle mobile tokens available, falling back to direct URL: " + targetSummary);
            return new ResolutionResult(fallbackUri, false, "missing_tokens");
        }

        AutologinKeyResult keyResult = requestAutologinKey(credentials.token, credentials.privateToken);
        if (keyResult.success) {
            Uri autologinUri = buildAutologinUri(keyResult.autologinUrl, credentials.userId, keyResult.key, safeTargetUrl);
            if (autologinUri != null) {
                String maskedLogin = Logger.maskSensitiveData(PreferencesManager.getGlobalLoginPref(appContext));
                Logger.i(TAG + ".resolve", "Autologin URI built successfully (user=" + maskedLogin + ", target=" + targetSummary + ")");
                return new ResolutionResult(autologinUri, true, "autologin_success");
            }
            Logger.w(TAG + ".resolve", "Failed to build valid autologin URI, falling back to direct URL");
            return new ResolutionResult(fallbackUri, false, "invalid_autologin_uri");
        }

        // handle 6-minute server rate limit silently without showing error
        if (ERROR_LOCKOUT.equalsIgnoreCase(keyResult.errorCode)) {
            Logger.i(TAG + ".resolve", "Moodle server returned autologinkeygenerationlockout (6-min limit); opening target directly: " + targetSummary);
            return new ResolutionResult(fallbackUri, false, ERROR_LOCKOUT);
        }

        // handle expired or revoked token with a single silent refresh attempt
        if (ERROR_INVALID_TOKEN.equalsIgnoreCase(keyResult.errorCode)
                || ERROR_ACCESS_EXCEPTION.equalsIgnoreCase(keyResult.errorCode)) {
            Logger.w(TAG + ".resolve", "Moodle rejected cached token (errorcode=" + keyResult.errorCode + "), attempting one-time silent refresh");
            TokenPair refreshed = ensureTokensAndUserId(appContext, true);
            if (refreshed != null) {
                AutologinKeyResult retryResult = requestAutologinKey(refreshed.token, refreshed.privateToken);
                if (retryResult.success) {
                    Uri autologinUri = buildAutologinUri(retryResult.autologinUrl, refreshed.userId, retryResult.key, safeTargetUrl);
                    if (autologinUri != null) {
                        String maskedLogin = Logger.maskSensitiveData(PreferencesManager.getGlobalLoginPref(appContext));
                        Logger.i(TAG + ".resolve", "Autologin URI built successfully after token refresh (user=" + maskedLogin + ", target=" + targetSummary + ")");
                        return new ResolutionResult(autologinUri, true, "autologin_after_refresh");
                    }
                } else if (ERROR_LOCKOUT.equalsIgnoreCase(retryResult.errorCode)) {
                    Logger.i(TAG + ".resolve", "Lockout returned after token refresh; opening target directly: " + targetSummary);
                    return new ResolutionResult(fallbackUri, false, ERROR_LOCKOUT);
                } else {
                    Logger.w(TAG + ".resolve", "Retry after token refresh failed (errorcode=" + retryResult.errorCode + "), clearing stored tokens");
                    PreferencesManager.clearMoodleAuthTokens(appContext);
                    return new ResolutionResult(fallbackUri, false, "retry_failed_" + retryResult.errorCode);
                }
            } else {
                Logger.w(TAG + ".resolve", "Silent token refresh failed or credentials unavailable, clearing stored tokens");
                PreferencesManager.clearMoodleAuthTokens(appContext);
                return new ResolutionResult(fallbackUri, false, "token_refresh_failed");
            }
        }

        Logger.w(TAG + ".resolve", "Autologin key request failed (errorcode=" + keyResult.errorCode + "), falling back to direct URL: " + targetSummary);
        return new ResolutionResult(fallbackUri, false, "fallback_" + keyResult.errorCode);
    }

    // open resolved uri in the external system browser without logging sensitive query parameters
    public static void launchExternalBrowser(@NonNull Context context, @NonNull ResolutionResult result) {
        try {
            Logger.i(TAG + ".launchExternalBrowser", "Launching external browser (autologin=" + result.usedAutologin + ", reason=" + result.reason + ")");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, result.uri);
            if (!(context instanceof Activity)) {
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            Logger.e(TAG + ".launchExternalBrowser", "No browser app found to handle PUW URL: " + Logger.formatException(e));
        } catch (Exception e) {
            Logger.e(TAG + ".launchExternalBrowser", "Failed to launch external browser: " + Logger.formatException(e));
        }
    }

    // ensure encrypted token, privatetoken, and userid are present; optionally force refresh
    @Nullable
    private static TokenPair ensureTokensAndUserId(@NonNull Context appContext, boolean forceRefresh) {
        synchronized (tokenLock) {
            String cachedToken = forceRefresh ? null : PreferencesManager.getMoodleToken(appContext);
            String cachedPrivateToken = forceRefresh ? null : PreferencesManager.getMoodlePrivateToken(appContext);
            long cachedUserId = forceRefresh ? -1L : PreferencesManager.getMoodleUserId(appContext);

            if (isNonEmpty(cachedToken) && isNonEmpty(cachedPrivateToken) && cachedUserId > 0) {
                Logger.d(TAG + ".ensureTokens", "Using cached encrypted Moodle tokens and userid");
                return new TokenPair(cachedToken, cachedPrivateToken, cachedUserId);
            }

            if (forceRefresh) {
                PreferencesManager.clearMoodleAuthTokens(appContext);
            }

            String token = cachedToken;
            String privateToken = cachedPrivateToken;

            if (!isNonEmpty(token) || !isNonEmpty(privateToken)) {
                String login = PreferencesManager.getGlobalLoginPref(appContext);
                String password = PreferencesManager.getGlobalPasswordPref(appContext);
                if (!isNonEmpty(login) || !isNonEmpty(password)) {
                    Logger.w(TAG + ".ensureTokens", "Global credentials missing, cannot acquire Moodle mobile tokens");
                    return null;
                }

                String[] fetched = fetchTokens(login, password);
                if (fetched == null) {
                    return null;
                }
                token = fetched[0];
                privateToken = fetched[1];
                PreferencesManager.saveMoodleTokens(appContext, token, privateToken);
            }

            long userId = cachedUserId;
            if (userId <= 0) {
                userId = fetchUserId(token);
                if (userId <= 0) {
                    // if site info failed with invalid token, clear stale tokens
                    PreferencesManager.clearMoodleAuthTokens(appContext);
                    return null;
                }
                PreferencesManager.setMoodleUserId(appContext, userId);
            }

            return new TokenPair(token, privateToken, userId);
        }
    }

    // step 1: obtain moodle mobile token and privatetoken
    @Nullable
    private static String[] fetchTokens(@NonNull String username, @NonNull String password) {
        Logger.d(TAG + ".fetchTokens", "Requesting Moodle mobile token for user=" + Logger.maskSensitiveData(username));
        OkHttpClient client = PUW.getApiClient();

        RequestBody body = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("service", SERVICE_MOBILE_APP)
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Logger.w(TAG + ".fetchTokens", "Token endpoint returned HTTP " + response.code());
                return null;
            }
            String raw = response.body().string();
            JSONObject json = new JSONObject(raw);
            if (json.has("error") || json.has("errorcode")) {
                String errorCode = json.optString("errorcode", "unknown_error");
                Logger.w(TAG + ".fetchTokens", "Token request rejected by server (errorcode=" + errorCode + ")");
                return null;
            }
            String token = json.optString("token", "").trim();
            String privateToken = json.optString("privatetoken", "").trim();
            if (token.isEmpty() || privateToken.isEmpty()) {
                Logger.w(TAG + ".fetchTokens", "Token response incomplete (hasToken=" + !token.isEmpty() + ", hasPrivateToken=" + !privateToken.isEmpty() + ")");
                return null;
            }
            Logger.i(TAG + ".fetchTokens", "Moodle mobile token and privatetoken acquired successfully");
            return new String[]{token, privateToken};
        } catch (Exception e) {
            Logger.e(TAG + ".fetchTokens", "Exception requesting Moodle tokens: " + Logger.formatException(e));
            return null;
        }
    }

    // step 2: obtain numeric moodle userid via core_webservice_get_site_info
    private static long fetchUserId(@NonNull String token) {
        Logger.d(TAG + ".fetchUserId", "Requesting Moodle userid via " + WS_SITE_INFO);
        OkHttpClient client = PUW.getApiClient();

        RequestBody body = new FormBody.Builder()
                .add("wstoken", token)
                .add("wsfunction", WS_SITE_INFO)
                .add("moodlewsrestformat", "json")
                .build();

        Request request = new Request.Builder()
                .url(REST_ENDPOINT)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Logger.w(TAG + ".fetchUserId", "Site info endpoint returned HTTP " + response.code());
                return -1L;
            }
            String raw = response.body().string();
            JSONObject json = new JSONObject(raw);
            if (json.has("exception") || json.has("errorcode")) {
                String errorCode = json.optString("errorcode", "unknown_error");
                Logger.w(TAG + ".fetchUserId", "Site info request failed (errorcode=" + errorCode + ")");
                return -1L;
            }
            long userId = json.optLong("userid", -1L);
            if (userId <= 0) {
                Logger.w(TAG + ".fetchUserId", "Site info response did not contain valid numeric userid");
                return -1L;
            }
            Logger.i(TAG + ".fetchUserId", "Moodle userid fetched successfully");
            return userId;
        } catch (Exception e) {
            Logger.e(TAG + ".fetchUserId", "Exception fetching Moodle userid: " + Logger.formatException(e));
            return -1L;
        }
    }

    // step 3: request one-time 60s autologin key strictly at click time
    @NonNull
    private static AutologinKeyResult requestAutologinKey(@NonNull String token, @NonNull String privateToken) {
        Logger.d(TAG + ".requestKey", "Requesting one-time autologin key via " + WS_GET_AUTOLOGIN_KEY);
        OkHttpClient client = PUW.getApiClient();

        RequestBody body = new FormBody.Builder()
                .add("wstoken", token)
                .add("wsfunction", WS_GET_AUTOLOGIN_KEY)
                .add("privatetoken", privateToken)
                .add("moodlewsrestformat", "json")
                .build();

        Request request = new Request.Builder()
                .url(REST_ENDPOINT)
                .header("User-Agent", USER_AGENT_MOODLE_MOBILE)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                Logger.w(TAG + ".requestKey", "Autologin key endpoint returned HTTP " + response.code());
                return new AutologinKeyResult(false, null, null, "http_" + response.code());
            }
            String raw = response.body().string();
            JSONObject json = new JSONObject(raw);
            if (json.has("exception") || json.has("errorcode")) {
                String errorCode = json.optString("errorcode", "unknown_error").trim();
                Logger.w(TAG + ".requestKey", "Autologin key endpoint returned errorcode=" + errorCode);
                return new AutologinKeyResult(false, null, null, errorCode);
            }

            String key = json.optString("key", "").trim();
            if (key.isEmpty()) {
                Logger.w(TAG + ".requestKey", "Autologin key missing in response JSON");
                return new AutologinKeyResult(false, null, null, "empty_key");
            }

            String autologinUrl = json.optString("autologinurl", "").trim();
            if (!isSafeAutologinEndpoint(autologinUrl)) {
                autologinUrl = DEFAULT_AUTOLOGIN_URL;
            }
            Logger.d(TAG + ".requestKey", "One-time autologin key received from server");
            return new AutologinKeyResult(true, key, autologinUrl, "");
        } catch (Exception e) {
            Logger.e(TAG + ".requestKey", "Exception requesting autologin key: " + Logger.formatException(e));
            return new AutologinKeyResult(false, null, null, "network_exception");
        }
    }

    // step 4: assemble final autologin uri with userid, one-time key, and target url
    @Nullable
    private static Uri buildAutologinUri(
            @NonNull String autologinUrl,
            long userId,
            @NonNull String oneTimeKey,
            @NonNull String targetUrl
    ) {
        try {
            return Uri.parse(autologinUrl).buildUpon()
                    .appendQueryParameter("userid", String.valueOf(userId))
                    .appendQueryParameter("key", oneTimeKey)
                    .appendQueryParameter("urltogo", targetUrl)
                    .build();
        } catch (Exception e) {
            Logger.e(TAG + ".buildAutologinUri", "Failed to build autologin URI: " + Logger.formatException(e));
            return null;
        }
    }

    @NonNull
    public static String sanitizeTargetUrl(@Nullable String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return DEFAULT_HOME_URL;
        }
        String trimmed = rawUrl.trim();
        try {
            Uri uri = Uri.parse(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(scheme)
                    && host != null
                    && (host.equalsIgnoreCase(MOODLE_HOST) || host.toLowerCase(Locale.ROOT).endsWith(".wspa.pl"))) {
                return trimmed;
            }
            Logger.w(TAG + ".sanitizeTargetUrl", "Blocked untrusted target URL host=" + host + ", falling back to default PUW home");
        } catch (Exception e) {
            Logger.w(TAG + ".sanitizeTargetUrl", "Malformed target URL, falling back to default PUW home");
        }
        return DEFAULT_HOME_URL;
    }

    private static boolean isMoodleAutologinHost(@NonNull String url) {
        try {
            Uri uri = Uri.parse(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && MOODLE_HOST.equalsIgnoreCase(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSafeAutologinEndpoint(@Nullable String url) {
        if (!isNonEmpty(url)) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && MOODLE_HOST.equalsIgnoreCase(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    // return non-sensitive host and path summary safe for diagnostics logs
    @NonNull
    private static String describeSafeUrl(@NonNull String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() != null ? uri.getHost() : "";
            String path = uri.getPath() != null ? uri.getPath() : "/";
            String courseId = uri.getQueryParameter("id");
            if (courseId != null && !courseId.isEmpty()) {
                return host + path + "?id=" + courseId;
            }
            return host + path;
        } catch (Exception e) {
            return MOODLE_HOST;
        }
    }

    private static boolean isNonEmpty(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
