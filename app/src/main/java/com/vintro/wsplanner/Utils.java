package com.vintro.wsplanner;

import static android.content.Context.MODE_PRIVATE;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

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

public class Utils {
    public static final String baseUrl = "https://puw.wspa.pl/";
    public static final String loginUrl = baseUrl + "login/index.php";
    public static final String homeUrl = baseUrl + "my/";
    public static final String fileUrl = "https://puw.wspa.pl/pluginfile.php/249798/mod_folder/content/0/Informatyka%20-%20studia%20I%20stopnia%20-%20st%20III%20-%20semestr%20zimowy.xlsx?forcedownload=1";
    public static final String outputFileName = "plan.xlsx";
    public static final String prefsName = "com.vintro.wsplanner.GetPlanWidget";
    public static final String prefKey = "plan_widget_";
    public static final String prefKeyLogin = prefKey  + "_login";
    public static final String prefKeyPassword = prefKey  + "_password";

    public static void animateAlpha(View v, float alpha) {
        v.animate().alpha(alpha).setDuration(300).start();
    }

    public static void savePrefs(Context context, int widgetId, String login, String password) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        prefs.putString(prefKeyLogin + widgetId, login);
        prefs.putString(prefKeyPassword + widgetId, password);
        prefs.apply();
    }

    public static String getLoginPref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        String login = prefs.getString(prefKeyLogin + widgetId, null);
        return login;
    }

    public static String getPasswordPref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        String password = prefs.getString(prefKeyPassword + widgetId, null);
        return password;
    }

    public static void deletePrefs(Context context, int widgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        prefs.remove(prefKeyLogin + widgetId);
        prefs.remove(prefKeyPassword + widgetId);
        prefs.apply();
    }

    public static OkHttpClient login(Intent intent, Context context) {
        OkHttpClient client = getClient();

        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        String login = getLoginPref(context, widgetId);
        String password = getPasswordPref(context, widgetId);

        int response = loginWithClient(client, login, password);
        return response == 1 ? client : null;
    }

    public static int checkLogin(String login, String password) {
        OkHttpClient client = getClient();
        return loginWithClient(client, login, password);
    }

    private static int loginWithClient(OkHttpClient client, String login, String password) {
        RequestBody formBody = new FormBody.Builder()
                .add("username", login)
                .add("password", password)
                .build();

        Request loginRequest = new Request.Builder()
                .url(Utils.loginUrl)
                .post(formBody)
                .build();

        try (Response loginResponse = client.newCall(loginRequest).execute()) {
            Log.d("Utils.loginWithClient", "Loginning, answer: " + loginResponse.toString());
            if (!loginResponse.isSuccessful()) {
                Log.e("Utils.loginWithCLient", "Loginning not 200, code: " + loginResponse.code());
                return -1;
            }
            if (!loginResponse.request().url().toString().equals(Utils.homeUrl)) {
                Log.e("Utils.loginWithClient", "Loginning not successful, response: " + loginResponse.toString());
                return 0;
            }
        } catch (Exception e) {
            Log.e("Utils.loginWithClient", "Loginning error, client: " + client + ", error:" + e.toString());
            return -1;
        }
        return 1;
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
}
