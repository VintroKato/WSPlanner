package com.vintro.wsplanner.enums;

public enum Language {
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    ENGLISH("en"),
    POLISH("pl");

    public final String code;

    private Language(String code) {
        this.code = code;
    }

    public static Language getEnum(String code) {
        for (Language lang : Language.values()) {
            if (lang.code.equals(code)) {
                return lang;
            }
        }
        return ENGLISH;
    }
}
