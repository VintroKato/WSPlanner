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

public class Utils {
    public static final String baseUrl = "https://puw.wspa.pl/";
    public static final String loginUrl = baseUrl + "login/index.php";
    public static final String homeUrl = baseUrl + "my/";
    public static final String fileUrlStart = "https://puw.wspa.pl/pluginfile.php/249798/mod_folder/content/0/Informatyka%20-%20studia%20I%20stopnia%20-%20st%20";
    public static final String fileUrlEnd = "%20-%20semestr%20zimowy.xlsx?forcedownload=1";
    public static final String outputFileName = "plan";
    public static final String prefsName = "com.vintro.wsplanner.GetPlanWidget";
    public static final String prefKey = "plan_widget_";
    public static final String prefKeyLogin = prefKey  + "_login";
    public static final String prefKeyPassword = prefKey  + "_password";
    public static final String prefKeyCourse = prefKey  + "_course";

    public static void animateAlpha(View v, float alpha) {
        v.animate().alpha(alpha).setDuration(300).start();
    }

    public static void savePrefs(Context context, int widgetId, String login, String password, int course) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        prefs.putString(prefKeyLogin + widgetId, login);
        prefs.putString(prefKeyPassword + widgetId, password);
        prefs.putInt(prefKeyCourse + widgetId, course);
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

    public static int getCoursePref(Context context, Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        int course = prefs.getInt(prefKeyCourse + widgetId, 3);
        return course;
    }

    public static void deletePrefs(Context context, int widgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE).edit();
        prefs.remove(prefKeyLogin + widgetId);
        prefs.remove(prefKeyPassword + widgetId);
        prefs.remove(prefKeyCourse + widgetId);
        prefs.apply();
    }

    public static String getFileUrl(Context context, Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        SharedPreferences prefs = context.getSharedPreferences(prefsName, MODE_PRIVATE);
        int course = getCoursePref(context, intent);
        String url = Utils.fileUrlStart + "I".repeat(course) + Utils.fileUrlEnd;
        return url;
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

    public static void animateInputBackground(Activity activity, EditText input, GetPlanWidgetConfigureActivity.InputState state, GetPlanWidgetConfigureActivity.InputState oldState) {
        Resources res = activity.getResources();
        GradientDrawable drawable = (GradientDrawable) input.getBackground().mutate();

        int startBgColor = drawable.getColor().getDefaultColor();
        int startBorderColor = getInputBorderColor(res, oldState, startBgColor);

        int endBgColor = input.isFocused() ?
                res.getColor(R.color.card_checked_background_dark) :
                res.getColor(R.color.input_bg_dark);

        int endBorderColor = getEndBorderColor(res, state, input.isFocused());

        if (startBgColor == endBgColor && startBorderColor == endBorderColor) {
            return;
        }

        animateColors(drawable, startBgColor, endBgColor, startBorderColor, endBorderColor, res);
    }

    public static void animateCardSelection(Activity activity, MaterialCardView card, boolean checked) {
        Resources res = activity.getResources();

        int startBorderColor = card.isChecked() ?
                res.getColor(R.color.input_border_active_dark) :
                res.getColor(R.color.input_border_dark);
        int startBgColor = card.isChecked() ?
                res.getColor(R.color.card_checked_background_dark) :
                res.getColor(R.color.input_bg_dark);

        int endBorderColor = checked ?
                res.getColor(R.color.input_border_active_dark) :
                res.getColor(R.color.input_border_dark);
        int endBgColor = checked ?
                res.getColor(R.color.card_checked_background_dark) :
                res.getColor(R.color.input_bg_dark);

        if (startBorderColor == endBorderColor && startBgColor == endBgColor) {
            card.setChecked(checked);
            return;
        }

        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, checked);
    }

    private static int getInputBorderColor(Resources res, GetPlanWidgetConfigureActivity.InputState state, int bgColor) {
        switch (state) {
            case NORMAL:
                return bgColor == res.getColor(R.color.card_checked_background_dark) ?
                        res.getColor(R.color.input_border_active_dark) :
                        res.getColor(R.color.input_border_dark);
            case OK:
                return res.getColor(R.color.input_border_ok_dark);
            case ERROR:
                return res.getColor(R.color.input_border_error_dark);
            default:
                return res.getColor(R.color.input_border_dark);
        }
    }

    private static int getEndBorderColor(Resources res, GetPlanWidgetConfigureActivity.InputState state, boolean isFocused) {
        switch (state) {
            case NORMAL:
                return isFocused ?
                        res.getColor(R.color.input_border_active_dark) :
                        res.getColor(R.color.input_border_dark);
            case OK:
                return res.getColor(R.color.input_border_ok_dark);
            case ERROR:
                return res.getColor(R.color.input_border_error_dark);
            default:
                return res.getColor(R.color.input_border_dark);
        }
    }

    private static void animateColors(GradientDrawable drawable, int startBg, int endBg,
                                      int startBorder, int endBorder, Resources res) {
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                color -> drawable.setColor(color));

        int borderWidth = (int) (2 * res.getDisplayMetrics().density);
        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder,
                color -> drawable.setStroke(borderWidth, color));

        bgAnimator.start();
        borderAnimator.start();
    }

    private static void animateCardColors(MaterialCardView card, int startBorder, int endBorder,
                                          int startBg, int endBg, boolean checked) {
        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder,
                card::setStrokeColor);

        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                card::setCardBackgroundColor);

        borderAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                card.setChecked(checked);
            }
        });

        borderAnimator.start();
        bgAnimator.start();
    }

    private static ValueAnimator createColorAnimator(int startColor, int endColor, ColorUpdateListener listener) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        animator.addUpdateListener(anim -> listener.onColorUpdate((int) anim.getAnimatedValue()));
        animator.setDuration(300);
        return animator;
    }

    @FunctionalInterface
    private interface ColorUpdateListener {
        void onColorUpdate(int color);
    }
}
