package com.vintro.wsplanner.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// manages authenticated moodle web sessions and credential verification against puw
public class PUW {
    public static final String baseUrl = "https://puw.wspa.pl/";
    public static final String loginUrl = baseUrl + "login/index.php";
    public static final String homeUrl = baseUrl + "my/";
    public static final String privateFilesUrl = baseUrl + "user/files.php";

    private static final Map<String, List<Cookie>> globalCookieStore = new ConcurrentHashMap<>();
    private static volatile OkHttpClient sharedClient;
    private static volatile OkHttpClient sharedApiClient;
    private static volatile String activeSessionUser = null;
    private static volatile String activeSessionFingerprint = null;
    private static volatile long lastLoginTime = 0;
    private static final long SESSION_MAX_AGE_MS = 60 * 60 * 1000;

    private static CookieJar createMemoryCookieJar(Map<String, List<Cookie>> store) {
        return new CookieJar() {
            @Override
            public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
                List<Cookie> existing = store.computeIfAbsent(
                        url.host(), k -> new CopyOnWriteArrayList<>()
                );
                for (Cookie newCookie : cookies) {
                    existing.removeIf(c -> c.name().equalsIgnoreCase(newCookie.name()));
                    existing.add(newCookie);
                }
            }

            @NonNull
            @Override
            public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                List<Cookie> cookies = store.get(url.host());
                if (cookies == null || cookies.isEmpty()) {
                    return List.of();
                }
                long now = System.currentTimeMillis();
                cookies.removeIf(c -> c.expiresAt() < now);
                List<Cookie> validCookies = new ArrayList<>();
                for (Cookie c : cookies) {
                    if (c.expiresAt() >= now && c.matches(url)) {
                        validCookies.add(c);
                    }
                }
                return validCookies;
            }
        };
    }

    private static synchronized OkHttpClient getClient() {
        if (sharedClient == null) {
            sharedClient = NetworkUtils.getSharedHttpClient().newBuilder()
                    .cookieJar(createMemoryCookieJar(globalCookieStore))
                    .build();
        }
        return sharedClient;
    }

    // stateless okhttp client sharing connection pool and dispatcher for moodle rest calls
    public static synchronized OkHttpClient getApiClient() {
        if (sharedApiClient == null) {
            sharedApiClient = NetworkUtils.getSharedHttpClient().newBuilder()
                    .cookieJar(CookieJar.NO_COOKIES)
                    .build();
        }
        return sharedApiClient;
    }

    private static boolean hasMoodleSessionCookie() {
        List<Cookie> cookies = globalCookieStore.get("puw.wspa.pl");
        if (cookies != null) {
            long now = System.currentTimeMillis();
            for (Cookie c : cookies) {
                if (c.name().equalsIgnoreCase("MoodleSession") && !c.value().isEmpty() && c.expiresAt() >= now) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String computeCredentialFingerprint(String login, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((login + "\0" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf((login + "\0" + password).hashCode());
        }
    }

    public static synchronized void clearSession() {
        Logger.d("PUW.clearSession", "Clearing global session and cookie store");
        globalCookieStore.clear();
        activeSessionUser = null;
        activeSessionFingerprint = null;
        lastLoginTime = 0;
    }

    // authenticate using global credentials stored in preferences
    public static OkHttpClient globalLogin(Context context) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Logger.w("PUW.globalLogin", "Cannot perform global login: device is offline");
            return null;
        }
        String login = PreferencesManager.getGlobalLoginPref(context);
        String password = PreferencesManager.getGlobalPasswordPref(context);
        Logger.d("PUW.globalLogin", "Executing global login for user: " + Logger.maskSensitiveData(login));
        return getAuthenticatedClient(login, password);
    }

    public static synchronized OkHttpClient getAuthenticatedClient(String login, String password) {
        if (login == null || password == null) {
            Logger.w("PUW.getAuthenticatedClient", "Credentials missing (login=" + (login != null) + ", password=" + (password != null) + ")");
            return null;
        }

        OkHttpClient client = getClient();
        long now = System.currentTimeMillis();
        String fingerprint = computeCredentialFingerprint(login, password);

        // reuse active session only if both username and password fingerprint match
        if (login.equals(activeSessionUser)
                && fingerprint.equals(activeSessionFingerprint)
                && (now - lastLoginTime < SESSION_MAX_AGE_MS)
                && hasMoodleSessionCookie()) {
            Logger.d("PUW.getAuthenticatedClient", "Reusing active session for user: " + Logger.maskSensitiveData(login));
            return client;
        }

        // clear stale cookies before authenticating so an old session cannot interfere
        globalCookieStore.clear();
        Logger.d("PUW.getAuthenticatedClient", "Session invalid or expired, performing full authentication for: " + Logger.maskSensitiveData(login));
        int response = loginWithClient(client, login, password);
        if (response == 1) {
            activeSessionUser = login;
            activeSessionFingerprint = fingerprint;
            lastLoginTime = now;
            Logger.i("PUW.getAuthenticatedClient", "Authentication successful for user: " + Logger.maskSensitiveData(login));
            return client;
        }
        activeSessionUser = null;
        activeSessionFingerprint = null;
        lastLoginTime = 0;
        Logger.w("PUW.getAuthenticatedClient", "Authentication failed for user: " + Logger.maskSensitiveData(login) + ", response code: " + response);
        return null;
    }

    // verify credentials using an isolated cookie jar so existing app sessions are not polluted
    public static int checkLogin(String login, String password) {
        Logger.d("PUW.checkLogin", "Checking credentials for user: " + Logger.maskSensitiveData(login));
        Map<String, List<Cookie>> isolatedStore = new ConcurrentHashMap<>();
        OkHttpClient isolatedClient = NetworkUtils.getSharedHttpClient().newBuilder()
                .cookieJar(createMemoryCookieJar(isolatedStore))
                .build();
        return loginWithClient(isolatedClient, login, password);
    }

    private static int loginWithClient(OkHttpClient client, String login, String password) {
        if (login == null || password == null) {
            Logger.e("PUW.loginWithClient", "Login or password is null");
            return -1;
        }

        Logger.d("PUW.loginWithClient", "Sending login POST request to PUW for user: " + Logger.maskSensitiveData(login));

        RequestBody formBody = new FormBody.Builder()
                .add("username", login)
                .add("password", password)
                .build();

        Request loginRequest = new Request.Builder()
                .url(PUW.loginUrl)
                .post(formBody)
                .build();

        try (Response loginResponse = client.newCall(loginRequest).execute()) {
            Logger.d("PUW.loginWithClient", "Received HTTP response: " + loginResponse.code() + ", targetUrl=" + loginResponse.request().url());
            if (!loginResponse.isSuccessful()) {
                Logger.e("PUW.loginWithClient", "Login request unsuccessful, HTTP code: " + loginResponse.code());
                return -1;
            }
            if (!loginResponse.request().url().toString().equals(PUW.homeUrl)) {
                Logger.w("PUW.loginWithClient", "Login redirected away from homeUrl to: " + loginResponse.request().url());
                return 0;
            }
            Logger.i("PUW.loginWithClient", "Login verified successfully");
            return 1;
        } catch (Exception e) {
            Logger.e("PUW.loginWithClient", "Exception during login request: " + Logger.formatException(e), e);
            return -1;
        }
    }

    // scrape student full name from puw dashboard or files page
    public static String getStudentFullName(OkHttpClient client) {
        if (client == null) {
            Logger.e("PUW.getStudentFullName", "OkHttpClient is null");
            return null;
        }

        Request homeRequest = new Request.Builder()
                .url(PUW.homeUrl)
                .build();

        try (Response response = client.newCall(homeRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String html = response.body().string();
                Document doc = Jsoup.parse(html, PUW.baseUrl);

                Element userDropdown = doc.selectFirst("a.dropdown-toggle.icon-no-margin");

                if (userDropdown != null && userDropdown.hasAttr("aria-label")) {
                    String fullName = userDropdown.attr("aria-label").trim();
                    Logger.d("PUW.getStudentFullName", "Name extracted from dashboard: " + Logger.maskName(fullName));
                    return fullName;
                } else {
                    Logger.w("PUW.getStudentFullName", "Dropdown element or aria-label not found on dashboard");
                }
            }
        } catch (Exception e) {
            Logger.e("PUW.getStudentFullName", "Error fetching home page: " + Logger.formatException(e), e);
        }

        Request filesRequest = new Request.Builder()
                .url(privateFilesUrl)
                .build();

        try (Response response = client.newCall(filesRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String html = response.body().string();
                Document doc = Jsoup.parse(html, PUW.baseUrl);

                Element h1Element = doc.selectFirst("div.page-header-headings h1");

                if (h1Element != null) {
                    String fullName = h1Element.text().trim();
                    Logger.d("PUW.getStudentFullName", "Name extracted from files.php: " + Logger.maskName(fullName));
                    return fullName;
                } else {
                    Logger.w("PUW.getStudentFullName", "H1 element not found on files.php");
                }
            }
        } catch (Exception e) {
            Logger.e("PUW.getStudentFullName", "Error fetching files page: " + Logger.formatException(e), e);
        }

        return null;
    }
}
