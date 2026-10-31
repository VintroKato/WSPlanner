package com.vintro.wsplanner.ui.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.utils.Logger;

import java.util.Locale;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class UIHelper {
    public static boolean isNightTheme(Context context, AppTheme theme) {
        if (theme == AppTheme.DARK) {
            return true;
        } else if (theme == AppTheme.LIGHT) {
            return false;
        } else {
            int systemNight = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            if (systemNight == Configuration.UI_MODE_NIGHT_UNDEFINED && context != null) {
                systemNight = context.getApplicationContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            }
            return systemNight == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    public static boolean isNightMode(Context context) {
        if (context == null) return false;
        AppTheme theme = PreferencesManager.getThemePref(context);
        return isNightTheme(context, theme);
    }

    public static void setupSystemBars(Activity activity) {
        if (activity == null) return;
        setupSystemBars(activity, isNightMode(activity));
    }

    public static void setupSystemBars(Activity activity, boolean isNight) {
        if (activity == null || activity.getWindow() == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            boolean isLight = !isNight;
            controller.setAppearanceLightStatusBars(isLight);
            controller.setAppearanceLightNavigationBars(isLight);
        }
    }

    public static int getThemeColor(Context context, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }

    public static int getThemeColor(Resources.Theme theme, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }

    public static Context getThemedContext(Context context, boolean isNight) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        int nightMask = isNight ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = nightMask | (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
        Context themedContext = context.createConfigurationContext(config);
        themedContext.setTheme(R.style.Base_Theme_WSPlanner);
        return themedContext;
    }

    public static Context cloneContext(Context context) {
        return getThemedContext(context, isNightMode(context));
    }

    public static void setSelectedTheme(Context context) {
        AppTheme theme = PreferencesManager.getThemePref(context);
        Logger.d("UIHelper.setSelectedTheme", "Changing theme to " + theme);
        AppCompatDelegate.setDefaultNightMode(theme.value);
        if (context instanceof Activity) {
            setupSystemBars((Activity) context, isNightTheme(context, theme));
        }
    }

    public static void setSelectedLanguage(Context context) {
        if (context == null) return;
        Language language = PreferencesManager.getLanguagePref(context);
        Locale locale = new Locale(language.code);
        Locale.setDefault(locale);

        LocaleListCompat locales = LocaleListCompat.forLanguageTags(language.code);
        Logger.d("UIHelper.setSelectedLanguage", "Changing language to " + language + " with locales " + locales.toLanguageTags());
        AppCompatDelegate.setApplicationLocales(locales);

        applyLocaleToResources(context, locale);
        Context appContext = context.getApplicationContext();
        if (appContext != null && appContext != context) {
            applyLocaleToResources(appContext, locale);
        }
    }

    private static void applyLocaleToResources(Context context, Locale locale) {
        if (context == null) return;
        Resources res = context.getResources();
        if (res == null) return;
        Configuration config = new Configuration(res.getConfiguration());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            android.os.LocaleList localeList = new android.os.LocaleList(locale);
            android.os.LocaleList.setDefault(localeList);
            config.setLocales(localeList);
        } else {
            config.locale = locale;
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
    }
}
