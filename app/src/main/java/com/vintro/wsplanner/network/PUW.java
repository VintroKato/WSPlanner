package com.vintro.wsplanner.network;

import android.content.Context;
import android.content.Intent;
import android.appwidget.AppWidgetManager;

import androidx.annotation.NonNull;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class PUW {
    public static final String baseUrl = "https://puw.wspa.pl/";
    public static final String loginUrl = baseUrl + "login/index.php";
    public static final String homeUrl = baseUrl + "my/";
    public static final String privateFilesUrl = baseUrl + "user/files.php";
    public static String getFileUrl(Context context, Intent intent, OkHttpClient client) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        String major = PreferencesManager.getMajorPref(context, widgetId);
        DegreeLevel degreeLevel = PreferencesManager.getDegreeLevelPref(context, widgetId);
        StudyMode studyMode = PreferencesManager.getStudyModePref(context, widgetId);
        int year = PreferencesManager.getYearPref(context, widgetId);

        if (major == null || degreeLevel == null || studyMode == null) {
            Logger.w("PUW.getFileUrl", "Missing preferences for widget " + widgetId);
            return null;
        }

        String url = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
        Logger.d("PUW.getFileUrl", "Getting dynamic file url for widget " + widgetId + ", major=" + major + ", degree=" + degreeLevel + ", mode=" + studyMode + ", year=" + year + ", url: " + url);
        return url;
    }

    private static final Map<String, List<Cookie>> globalCookieStore = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile OkHttpClient sharedClient;
    private static volatile String activeSessionUser = null;
    private static volatile long lastLoginTime = 0;
    private static final long SESSION_MAX_AGE_MS = 60 * 60 * 1000; // 1 hour

    private static synchronized OkHttpClient getClient() {
        if (sharedClient == null) {
            sharedClient = new OkHttpClient.Builder()
                    .cookieJar(new CookieJar() {
                        @Override
                        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
                            globalCookieStore.put(url.host(), cookies);
                        }

                        @NonNull
                        @Override
                        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                            List<Cookie> cookies = globalCookieStore.get(url.host());
                            return cookies != null ? cookies : List.of();
                        }
                    })
                    .build();
        }
        return sharedClient;
    }

    private static boolean hasMoodleSessionCookie() {
        List<Cookie> cookies = globalCookieStore.get("puw.wspa.pl");
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (c.name().equalsIgnoreCase("MoodleSession") && !c.value().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized void clearSession() {
        Logger.d("PUW.clearSession", "Clearing global session and cookie store");
        globalCookieStore.clear();
        activeSessionUser = null;
        lastLoginTime = 0;
    }

    public static OkHttpClient globalLogin(Context context) {
        String login = PreferencesManager.getGlobalLoginPref(context);
        String password = PreferencesManager.getGlobalPasswordPref(context);
        Logger.d("PUW.globalLogin", "Executing global login for user: " + Logger.maskSensitiveData(login));
        return getAuthenticatedClient(login, password);
    }

    public static OkHttpClient login(Intent intent, Context context) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        Logger.d("PUW.login", "Executing login for widget " + widgetId);

        String login = PreferencesManager.getLoginPref(context, widgetId);
        String password = PreferencesManager.getPasswordPref(context, widgetId);

        return getAuthenticatedClient(login, password);
    }

    public static synchronized OkHttpClient getAuthenticatedClient(String login, String password) {
        if (login == null || password == null) {
            Logger.w("PUW.getAuthenticatedClient", "Credentials missing (login=" + (login != null) + ", password=" + (password != null) + ")");
            return null;
        }

        OkHttpClient client = getClient();
        long now = System.currentTimeMillis();

        // If session is still fresh (under 1 hour) and we have valid session cookies for this user, reuse session
        if (login.equals(activeSessionUser) && (now - lastLoginTime < SESSION_MAX_AGE_MS) && hasMoodleSessionCookie()) {
            Logger.d("PUW.getAuthenticatedClient", "Reusing active session for user: " + Logger.maskSensitiveData(login));
            return client;
        }

        Logger.d("PUW.getAuthenticatedClient", "Session invalid or expired, performing full authentication for: " + Logger.maskSensitiveData(login));
        int response = loginWithClient(client, login, password);
        if (response == 1) {
            activeSessionUser = login;
            lastLoginTime = now;
            Logger.i("PUW.getAuthenticatedClient", "Authentication successful for user: " + Logger.maskSensitiveData(login));
            return client;
        }
        Logger.w("PUW.getAuthenticatedClient", "Authentication failed for user: " + Logger.maskSensitiveData(login) + ", response code: " + response);
        return null;
    }

    public static int checkLogin(String login, String password) {
        Logger.d("PUW.checkLogin", "Checking credentials for user: " + Logger.maskSensitiveData(login));
        OkHttpClient client = getClient();
        return loginWithClient(client, login, password);
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
            Logger.e("PUW.loginWithClient", "Exception during login request: " + e.getMessage());
            return -1;
        }
    }

    public static ResponseBody downloadFile(Intent workIntent, Context context) {
        Logger.d("PUW.downloadFile", "Starting schedule file download");
        OkHttpClient client = PUW.login(workIntent, context);
        if (client == null) {
            Logger.e("PUW.downloadFile", "Download aborted: could not authenticate with PUW");
            return null;
        }

        String fileUrl = PUW.getFileUrl(context, workIntent, client);
        if (fileUrl == null) {
            Logger.e("PUW.downloadFile", "Download aborted: schedule file URL could not be resolved");
            return null;
        }

        Request fileRequest = new Request.Builder()
                .url(fileUrl)
                .build();

        try {
            Logger.d("PUW.downloadFile", "Executing HTTP GET for schedule file: " + fileUrl);
            Response fileResponse = client.newCall(fileRequest).execute();

            if (!fileResponse.isSuccessful()) {
                Logger.e("PUW.downloadFile", "Download failed with HTTP code: " + fileResponse.code());
                fileResponse.close();
                return null;
            }

            ResponseBody file = fileResponse.body();

            if (file == null || file.contentLength() == 0) {
                Logger.e("PUW.downloadFile", "Downloaded file body is null or empty");
                fileResponse.close();
                return null;
            }

            Logger.d("PUW.downloadFile", "Schedule file downloaded successfully, size: " + file.contentLength() + " bytes");
            return file;

        } catch (Exception e) {
            Logger.e("PUW.downloadFile", "Exception during file download: " + e.getMessage());
            return null;
        }
    }

    public static String getStudentFullName(OkHttpClient client) {
        if (client == null) {
            Logger.e("PUW.getStudentFullName", "OkHttpClient is null");
            // System.out.println("PUW.getStudentFullName: OkHttpClient is null");
            return null;
        }

        // option 1: 'aria-label' from dashboard
        Request homeRequest = new Request.Builder()
                .url(PUW.homeUrl)
                .build();

        try (Response response = client.newCall(homeRequest).execute()) {
            if (response.isSuccessful()) {
                String html = response.body().string();
                Document doc = Jsoup.parse(html);

                Element userDropdown = doc.selectFirst("a.dropdown-toggle.icon-no-margin");

                if (userDropdown != null && userDropdown.hasAttr("aria-label")) {
                    String fullName = userDropdown.attr("aria-label").trim();
                    Logger.d("PUW.getStudentFullName", "Name extracted from dashboard: " + fullName);
                    // System.out.println("PUW.getStudentFullName: Name extracted from dashboard: " + fullName);
                    return fullName;
                } else {
                    Logger.w("PUW.getStudentFullName", "Dropdown element or aria-label not found on dashboard");
                    // System.out.println("PUW.getStudentFullName: Dropdown element or aria-label not found on dashboard");
                }
            }
        } catch (Exception e) {
            Logger.e("PUW.getStudentFullName", "Error fetching home page: " + e.getMessage());
            // System.out.println("PUW.getStudentFullName: Error fetching home page: " + e.getMessage());
        }

        // option 2: if aria-label not found
        Request filesRequest = new Request.Builder()
                .url(privateFilesUrl)
                .build();

        try (Response response = client.newCall(filesRequest).execute()) {
            if (response.isSuccessful()) {
                String html = response.body().string();
                Document doc = Jsoup.parse(html);

                Element h1Element = doc.selectFirst("div.page-header-headings h1");

                if (h1Element != null) {
                    String fullName = h1Element.text().trim();
                    Logger.d("PUW.getStudentFullName", "Name extracted from files.php: " + fullName);
                    // System.out.println("PUW.getStudentFullName: Name extracted from files.php: " + fullName);
                    return fullName;
                } else {
                    Logger.w("PUW.getStudentFullName", "H1 element not found on files.php");
                    // System.out.println("PUW.getStudentFullName: H1 element not found on files.php");
                }
            }
        } catch (Exception e) {
            Logger.e("PUW.getStudentFullName", "Error fetching files page: " + e.getMessage());
            // System.out.println("PUW.getStudentFullName: Error fetching files page: " + e.getMessage());
        }

        return null;
    }
}
