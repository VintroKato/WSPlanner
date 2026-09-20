package com.vintro.wsplanner.enums;

public enum StudyMode {
    FULL_TIME("st"),
    PART_TIME("nst"),
    PART_TIME_PUW("nst puw");

    private final String value;

    StudyMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static StudyMode fromString(String text) {
        for (StudyMode mode : StudyMode.values()) {
            if (mode.value.equalsIgnoreCase(text)) {
                return mode;
            }
        }
        return null;
    }
}
