package com.vintro.wsplanner.network;

import android.content.Context;
import android.content.Intent;
import android.appwidget.AppWidgetManager;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.utils.Logger;

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
    public static final String fileUrlStart = "https://puw.wspa.pl/pluginfile.php/249798/mod_folder/content/0/Informatyka%20-%20studia%20I%20stopnia%20-%20st%20";
    public static final String fileUrlEnd = "%20-%20semestr%20zimowy.xlsx?forcedownload=1";

    public static String getFileUrl(Context context, Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        int course = PreferencesManager.getCoursePref(context, intent);
        String url = fileUrlStart + "I".repeat(course) + fileUrlEnd;
        Logger.d("PUW.getFileUrl", "Getting file url for widget " + widgetId + ", url: " + url);
        return url;
    }

    private static OkHttpClient getClient() {
        Map<String, List<Cookie>> cookieStore = new HashMap<>();

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : List.of();
                    }
                })
                .build();
        return client;
    }

    public static OkHttpClient login(Intent intent, Context context) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        Logger.d("PUW.login", "Logging in for widget " + widgetId);

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
            if (!loginResponse.isSuccessful()) {
                Logger.e("PUW.loginWithClient", "Logging in not 200, code: " + loginResponse.code());
                return -1;
            }
            if (!loginResponse.request().url().toString().equals(PUW.homeUrl)) {
                Logger.w("PUW.loginWithClient", "Logging in not successful, response: " + loginResponse.toString());
                return 0;
            }
        } catch (Exception e) {
            Logger.e("PUW.loginWithClient", "Logging in error, client: " + client + ", error:" + e.toString());
            return -1;
        }
        return 1;
    }

    public static ResponseBody downloadFile(Intent workIntent, Context context) {
        OkHttpClient client = PUW.login(workIntent, context);
        if (client == null) {
            Logger.e("PUW.downloadFile", "Downloading file error: got null from PUW.login, intent: " + workIntent);
            return null;
        }

        String fileUrl = PUW.getFileUrl(context, workIntent);
        Request fileRequest = new Request.Builder()
                .url(fileUrl)
                .build();

        try {
            Logger.d("PUW.downloadFile", "Downloading file: " + fileUrl);
            Response fileResponse = client.newCall(fileRequest).execute();

            if (!fileResponse.isSuccessful()) {
                Logger.e("PUW.downloadFile", "Downloading error: " + fileResponse.toString());
                fileResponse.close();
                return null;
            }

            ResponseBody file = fileResponse.body();

            if (file == null || file.contentLength() == 0) {
                Logger.e("PUW.downloadFile", "File body is null");
                fileResponse.close();
                return null;
            }

            Logger.d("PUW.downloadFile", "File downloaded, size: " + file.contentLength());
            return file;

        } catch (Exception e) {
            Logger.e("PUW.downloadFile", "Downloading error: " + e.getMessage());
            return null;
        }
    }
}
