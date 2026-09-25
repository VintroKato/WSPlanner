package com.vintro.wsplanner.enums;

// supported application display languages with iso codes
public enum Language {
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    ENGLISH("en"),
    POLISH("pl");

    public final String code;

    private Language(String code) {
        this.code = code;
    }

    // resolve language enum by iso language code
    public static Language getEnum(String code) {
        for (Language lang : Language.values()) {
            if (lang.code.equals(code)) {
                return lang;
            }
        }
        return ENGLISH;
    }
}
