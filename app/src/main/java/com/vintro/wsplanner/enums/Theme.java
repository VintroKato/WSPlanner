package com.vintro.wsplanner.enums;

import androidx.appcompat.app.AppCompatDelegate;

public enum Theme {
    AUTO(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO);

    public final int value;

    private Theme(int value) {
        this.value = value;
    }

    public static Theme getEnum(int value) {
        for (Theme theme : values()) {
            if (theme.value == value) {
                return theme;
            }
        }
        return AUTO;
    }
}
