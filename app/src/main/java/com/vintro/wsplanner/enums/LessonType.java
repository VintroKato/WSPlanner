package com.vintro.wsplanner.enums;

import android.content.Context;

import com.vintro.wsplanner.R;

// academic lesson types (lecture, exercises, lab, workshop, language, project, seminar)
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

    // return localized display name for ui badges, filter chips, and widgets
    public String getLocalizedDisplayName(Context context) {
        if (context == null) {
            return displayName;
        }
        switch (this) {
            case WYKLAD:
                return context.getString(R.string.lesson_type_wyklad);
            case CWICZENIA:
                return context.getString(R.string.lesson_type_cwiczenia);
            case LABORATORIUM:
                return context.getString(R.string.lesson_type_laboratorium);
            case WARSZTAT:
                return context.getString(R.string.lesson_type_warsztat);
            case LEKTORAT:
                return context.getString(R.string.lesson_type_lektorat);
            case PROJEKT:
                return context.getString(R.string.lesson_type_projekt);
            case SEMINARIUM:
                return context.getString(R.string.lesson_type_seminarium);
            case INNE:
            default:
                return context.getString(R.string.lesson_type_inne);
        }
    }

    // parse lesson type from a dedicated raw type token where short abbreviations like w are valid
    public static LessonType fromString(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return INNE;
        }
        String lower = raw.trim().toLowerCase();

        if (lower.contains("wykład") || lower.contains("wyklad")
                || lower.matches(".*\\bwyk\\b.*")
                || lower.equals("w")
                || lower.equals("w.")
                || lower.matches("^w\\.?\\s*\\(.*")) {
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

    // infer lesson type from a full subject title without matching the polish preposition w
    public static LessonType fromSubjectName(String subjectName) {
        if (subjectName == null || subjectName.trim().isEmpty()) {
            return INNE;
        }
        String lower = subjectName.trim().toLowerCase();

        if (lower.contains("wykład") || lower.contains("wyklad") || lower.matches(".*\\bwyk\\b.*")) {
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
