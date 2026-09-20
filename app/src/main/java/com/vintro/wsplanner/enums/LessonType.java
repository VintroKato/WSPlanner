package com.vintro.wsplanner.enums;

public enum LessonType {
    WYKLAD("Wykład"),
    CWICZENIA("Ćwiczenia"),
    LABORATORIUM("Laboratorium"),
    WARSZTAT("Warsztat"),
    LEKTORAT("Lektorat"),
    PROJEKT("Projekt"),
    SEMINARIUM("Seminarium"),
    INNE("Inne");

    private final String displayName;

    LessonType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    // parse lesson type from raw text
    public static LessonType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return INNE;
        }
        String lower = raw.trim().toLowerCase();

        if (lower.contains("wykład") || lower.contains("wyklad") || lower.matches(".*\\bwyk\\b.*") || lower.matches(".*\\bw\\b.*")) {
            return WYKLAD;
        }
        if (lower.contains("ćwiczen") || lower.contains("cwiczen") || lower.matches(".*\\bćw\\b.*") || lower.matches(".*\\bcw\\b.*")) {
            return CWICZENIA;
        }
        if (lower.contains("laborator") || lower.matches(".*\\blab\\b.*")) {
            return LABORATORIUM;
        }
        if (lower.contains("warsztat") || lower.matches(".*\\bwar\\b.*")) {
            return WARSZTAT;
        }
        if (lower.contains("lektorat") || lower.matches(".*\\blek\\b.*")) {
            return LEKTORAT;
        }
        if (lower.contains("projekt") || lower.matches(".*\\bproj\\b.*")) {
            return PROJEKT;
        }
        if (lower.contains("seminarium") || lower.matches(".*\\bsem\\b.*")) {
            return SEMINARIUM;
        }

        return INNE;
    }
}
