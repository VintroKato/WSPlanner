package com.vintro.wsplanner.enums;

// study mode: full-time (st), part-time (nst), or part-time puw (nst puw)
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

    // parse study mode from raw string identifier
    public static StudyMode fromString(String text) {
        for (StudyMode mode : StudyMode.values()) {
            if (mode.value.equalsIgnoreCase(text)) {
                return mode;
            }
        }
        return null;
    }
}
