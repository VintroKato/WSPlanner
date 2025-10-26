package com.vintro.wsplanner.data.preferences;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.vintro.wsplanner.utils.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;

import dev.spght.encryptedprefs.*;

public class PreferencesManager {
    private static final String prefs_name = "wsplanner_prefs";
    private static final String key_login = "login_";
    private static final String key_password = "password_";
    private static final String key_course = "course_";

    private static SharedPreferences getEncryptedPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    prefs_name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Logger.e("PreferencesManager", "Failed to create encrypted preferences, falling back to regular:" + e);
            return context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE);
        }
    }

    public static void savePrefs(Context context, int widgetId, String login, String password, int course) {
        Logger.d("PreferencesManager.savePrefs", "Saving prefs for widget " + widgetId + ", course: " + course);

        SharedPreferences prefs = getEncryptedPreferences(context);
        prefs.edit()
                .putString(key_login + widgetId, login)
                .putString(key_password + widgetId, password)
                .putInt(key_course + widgetId, course)
                .apply();
    }

    public static String getLoginPref(Context context, int widgetId) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        String login = prefs.getString(key_login + widgetId, null);
        Logger.d("PreferencesManager.getLoginPref", "Getting login for widget " + widgetId);
        return login;
    }

    public static String getPasswordPref(Context context, int widgetId) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        String password = prefs.getString(key_password + widgetId, null);
        Logger.d("PreferencesManager.getPasswordPref", "Getting password for widget " + widgetId + ", result is null: " + (password == null));
        return password;
    }

    public static int getCoursePref(Context context, Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        SharedPreferences prefs = getEncryptedPreferences(context);
        int course = prefs.getInt(key_course + widgetId, 3);
        Logger.d("PreferencesManager.getCoursePref", "Getting course for widget " + widgetId + ", course: " + course);
        return course;
    }

    public static void deletePrefs(Context context, int widgetId) {
        Logger.d("PreferencesManager.deletePrefs", "Deleting prefs for widget " + widgetId);

        SharedPreferences prefs = getEncryptedPreferences(context);
        prefs.edit()
                .remove(key_login + widgetId)
                .remove(key_password + widgetId)
                .remove(key_course + widgetId)
                .apply();
    }
}