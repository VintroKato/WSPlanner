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

public class UIHelper {
    public static boolean isNightMode(Context context) {
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
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

    public static Context cloneContext(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        Context newContext = context.createConfigurationContext(config);
        newContext.setTheme(R.style.Base_Theme_WSPlanner);
        return newContext;
    }

    public static void setSelectedTheme(Context context) {
        AppTheme theme = PreferencesManager.getThemePref(context);
        Logger.d("UIHelper.setSelectedTheme", "Changing theme to " + theme);
        AppCompatDelegate.setDefaultNightMode(theme.value);
    }

    public static void setSelectedLanguage(Activity activity) {
        Language language = PreferencesManager.getLanguagePref(activity);
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(language.code);
        Logger.d("UIHelper.setSelectedLanguage", "Changing language to " + language + " with locales " + locales.toLanguageTags());
        AppCompatDelegate.setApplicationLocales(locales);

        Locale locale = new Locale(language.code);
        Locale.setDefault(locale);

        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.setLocale(locale);

        activity.getResources().updateConfiguration(config,
                activity.getResources().getDisplayMetrics());
    }

//    public static void setSelectedLanguage2(Activity activity) {
//        String languageCode = PreferencesManager.getLanguagePref(activity).code;
//
//        Locale locale = new Locale(languageCode);
//        Locale.setDefault(locale);
//
//        Configuration config = new Configuration();
//        config.setLocale(locale);
//
//        activity.getResources().updateConfiguration(config,
//                activity.getResources().getDisplayMetrics());
//    }
}
