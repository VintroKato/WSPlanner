package com.vintro.wsplanner;

import static android.content.Context.MODE_PRIVATE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.card.MaterialCardView;

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

class Utils {
    static final String outputFileName = "plan";
    static final String prefsName = "com.vintro.wsplanner.GetPlanWidget";
    static final String prefKey = "plan_widget_";
    static final String prefKeyLogin = prefKey  + "_login";
    static final String prefKeyPassword = prefKey  + "_password";
    static final String prefKeyCourse = prefKey  + "_course";

    static void animateAlpha(View v, float alpha) {
        v.animate().alpha(alpha).setDuration(300).start();
    }

    static void savePrefs(Context context, int widgetId, String login, String password, int course) {
        Logger.d("Utils.savePrefs", "Saving prefs for widget " + widgetId + ", course: " + course);
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        prefs.putString(prefKeyLogin + widgetId, login);
        prefs.putString(prefKeyPassword + widgetId, password);
        prefs.putInt(prefKeyCourse + widgetId, course);
        prefs.apply();
    }

    static String getLoginPref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        String login = prefs.getString(prefKeyLogin + widgetId, null);
        Logger.d("Utils.getLoginPref", "Getting login for widget " + widgetId);
        return login;
    }

    static String getPasswordPref(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        String password = prefs.getString(prefKeyPassword + widgetId, null);
        Logger.d("Utils.getPasswordPref", "Getting password for widget " + widgetId + ", result is null: " + (password == null));
        return password;
    }

    static int getCoursePref(Context context, Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        int course = prefs.getInt(prefKeyCourse + widgetId, 3);
        Logger.d("Utils.getCoursePref", "Getting course for widget " + widgetId + ", course: " + course);
        return course;
    }

    static void deletePrefs(Context context, int widgetId) {
        Logger.d("Utils.deletePrefs", "Deleting prefs for widget " + widgetId);
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        prefs.remove(prefKeyLogin + widgetId);
        prefs.remove(prefKeyPassword + widgetId);
        prefs.remove(prefKeyCourse + widgetId);
        prefs.apply();
    }

    static int getThemeColor(Context context, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }
}
