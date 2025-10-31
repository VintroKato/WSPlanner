package com.vintro.wsplanner.data.preferences;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.utils.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;

import dev.spght.encryptedprefs.*;

public class PreferencesManager {
    private static final String prefs_name = "wsplanner_prefs";
    private static final String key_login = "data_login_";
    private static final String key_password = "data_password_";
    private static final String key_course = "data_course_";
    private static final String key_theme = "settings_theme";
    private static final String key_language = "settings_language";

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

    public static AppTheme getThemePref(Context context) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        int pref = prefs.getInt(key_theme, AppTheme.AUTO.value);
        return AppTheme.getEnum(pref);
    }

    public static void setThemePref(Context context, AppTheme theme) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        prefs.edit()
                .putInt(key_theme, theme.value)
                .apply();
    }

    public static Language getLanguagePref(Context context) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        String pref = prefs.getString(key_language, Locale.getDefault().getLanguage());
        return Language.getEnum(pref);
    }

    public static void setLanguagePref(Context context, Language language) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        prefs.edit()
                .putString(key_language, language.code)
                .apply();
    }
}