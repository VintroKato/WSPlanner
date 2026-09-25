package com.vintro.wsplanner.enums;

import androidx.appcompat.app.AppCompatDelegate;

// application visual theme modes (system auto, dark, light)
public enum AppTheme {
    AUTO(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO);

    public final int value;

    private AppTheme(int value) {
        this.value = value;
    }

    // resolve theme enum from appcompat delegate night mode integer
    public static AppTheme getEnum(int value) {
        for (AppTheme theme : values()) {
            if (theme.value == value) {
                return theme;
            }
        }
        return AUTO;
    }
}
