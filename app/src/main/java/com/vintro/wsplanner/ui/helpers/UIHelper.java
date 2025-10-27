package com.vintro.wsplanner.ui.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.Theme;
import com.vintro.wsplanner.utils.Logger;

import java.util.Locale;

public class UIHelper {
    public static int getThemeColor(Context context, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }

    public static void setSelectedTheme(Context context) {
        Theme theme = PreferencesManager.getThemePref(context);
        Logger.d("UIHelper.setSelectedTheme", "Changing theme to " + theme);
        AppCompatDelegate.setDefaultNightMode(theme.value);
    }

    public static void setSelectedLanguage(Context context) {
        Language language = PreferencesManager.getLanguagePref(context);
        LocaleListCompat locales = LocaleListCompat.forLanguageTags(language.code);
        Logger.d("UIHelper.setSelectedLanguage", "Changing language to " + language + " with locales " + locales.toLanguageTags());
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public static void setSelectedLanguage2(Activity activity) {
        String languageCode = PreferencesManager.getLanguagePref(activity).code;

        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        config.setLocale(locale);

        activity.getResources().updateConfiguration(config,
                activity.getResources().getDisplayMetrics());
    }
}
