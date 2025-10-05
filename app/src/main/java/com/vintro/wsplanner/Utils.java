package com.vintro.wsplanner;

import android.content.Context;
import android.content.SharedPreferences;

public class Utils {
    public static final String baseUrl = "https://puw.wspa.pl/";
    public static final String loginUrl = baseUrl + "login/index.php";
    public static final String fileUrl = "https://puw.wspa.pl/pluginfile.php/249798/mod_folder/content/0/Informatyka%20-%20studia%20I%20stopnia%20-%20st%20III%20-%20semestr%20zimowy.xlsx?forcedownload=1";
    public static final String outputFileName = "plan.xlsx";
    public static final String prefsName = "com.vintro.wsplanner.GetPlanWidget";
    public static final String prefKey = "plan_widget_";
    public static final String prefKeyLogin = prefKey  + "_login";
    public static final String prefKeyPassword = prefKey  + "_password";

    public static void savePrefs(Context context, int widgetId, String login, String password) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, 0).edit();
        prefs.putString(prefKeyLogin + widgetId, login);
        prefs.putString(prefKeyPassword + widgetId, password);
        prefs.apply();
    }

    public static String getLoginPref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, 0);
        String login = prefs.getString(prefKeyLogin + widgetId, null);
        return login;
    }

    public static String getPasswordPref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, 0);
        String password = prefs.getString(prefKeyPassword + widgetId, null);
        return password;
    }

    public static void deletePrefs(Context context, int widgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, 0).edit();
        prefs.remove(prefKeyLogin + widgetId);
        prefs.remove(prefKeyPassword + widgetId);
        prefs.apply();
    }
}
