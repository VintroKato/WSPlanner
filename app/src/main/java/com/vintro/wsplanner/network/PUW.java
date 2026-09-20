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
            System.out.println("PUW.getFileUrl: Missing preferences for widget " + widgetId);
            return null;
        }

        String url = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
        System.out.println("PUW.getFileUrl: Getting dynamic file url for widget " + widgetId + ", url: " + url);
        return url;
    }

    private static OkHttpClient getClient() {
        Map<String, List<Cookie>> cookieStore = new HashMap<>();

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @NonNull
                    @Override
                    public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : List.of();
                    }
                })
                .build();
        return client;
    }

    public static OkHttpClient globalLogin(Context context) {
        OkHttpClient client = getClient();
        String login = PreferencesManager.getGlobalLoginPref(context);
        String password = PreferencesManager.getGlobalPasswordPref(context);
        int response = loginWithClient(client, login, password);
        return response == 1 ? client : null;
    }

    public static OkHttpClient login(Intent intent, Context context) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        Logger.d("PUW.login", "Logging in for widget " + widgetId);
        // System.out.println("PUW.login: Logging in for widget " + widgetId);

        OkHttpClient client = getClient();

        String login = PreferencesManager.getLoginPref(context, widgetId);
        String password = PreferencesManager.getPasswordPref(context, widgetId);

        int response = loginWithClient(client, login, password);
        return response == 1 ? client : null;
    }

    public static int checkLogin(String login, String password) {
        OkHttpClient client = getClient();
        return loginWithClient(client, login, password);
    }

    private static int loginWithClient(OkHttpClient client, String login, String password) {
        if (login == null || password == null) {
            Logger.e("PUW.loginWithClient", "Login or password is null");
            // System.out.println("PUW.loginWithClient: Login or password is null");
            return -1;
        }

        RequestBody formBody = new FormBody.Builder()
                .add("username", login)
                .add("password", password)
                .build();

        Request loginRequest = new Request.Builder()
                .url(PUW.loginUrl)
                .post(formBody)
                .build();

        try (Response loginResponse = client.newCall(loginRequest).execute()) {
            Logger.d("PUW.loginWithClient", "Logging in, answer: " + loginResponse.toString());
            // System.out.println("PUW.loginWithClient: Logging in, answer: " + loginResponse.toString());
            if (!loginResponse.isSuccessful()) {
                Logger.e("PUW.loginWithClient", "Logging in not 200, code: " + loginResponse.code());
                // System.out.println("PUW.loginWithClient: Logging in not 200, code: " + loginResponse.code());
                return -1;
            }
            if (!loginResponse.request().url().toString().equals(PUW.homeUrl)) {
                Logger.w("PUW.loginWithClient", "Logging in not successful, response: " + loginResponse.toString());
                // System.out.println("PUW.loginWithClient: Logging in not successful, response: " + loginResponse.toString());
                return 0;
            }
        } catch (Exception e) {
            Logger.e("PUW.loginWithClient", "Logging in error, client: " + client + ", error:" + e.toString());
            // System.out.println("PUW.loginWithClient: Logging in error, client: " + client + ", error:" + e.toString());
            return -1;
        }
        return 1;
    }

    public static ResponseBody downloadFile(Intent workIntent, Context context) {
        OkHttpClient client = PUW.login(workIntent, context);
        if (client == null) {
            Logger.e("PUW.downloadFile", "Downloading file error: got null from PUW.login, intent: " + workIntent);
            // System.out.println("PUW.downloadFile: Downloading file error: got null from PUW.login, intent: " + workIntent);
            return null;
        }

        String fileUrl = PUW.getFileUrl(context, workIntent, client);
        if (fileUrl == null) {
            Logger.e("PUW.downloadFile", "File URL could not be resolved");
            return null;
        }

        Request fileRequest = new Request.Builder()
                .url(fileUrl)
                .build();

        try {
            Logger.d("PUW.downloadFile", "Downloading file: " + fileUrl);
            // System.out.println("PUW.downloadFile: Downloading file: " + fileUrl);
            Response fileResponse = client.newCall(fileRequest).execute();

            if (!fileResponse.isSuccessful()) {
                Logger.e("PUW.downloadFile", "Downloading error: " + fileResponse.toString());
                // System.out.println("PUW.downloadFile: Downloading error: " + fileResponse.toString());
                fileResponse.close();
                return null;
            }

            ResponseBody file = fileResponse.body();

            if (file.contentLength() == 0) {
                Logger.e("PUW.downloadFile", "File body is null");
                // System.out.println("PUW.downloadFile: File body is null");
                fileResponse.close();
                return null;
            }

            Logger.d("PUW.downloadFile", "File downloaded, size: " + file.contentLength());
            // System.out.println("PUW.downloadFile: File downloaded, size: " + file.contentLength());
            return file;

        } catch (Exception e) {
            Logger.e("PUW.downloadFile", "Downloading error: " + e.getMessage());
            // System.out.println("PUW.downloadFile: Downloading error: " + e.getMessage());
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
