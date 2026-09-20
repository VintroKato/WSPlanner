package com.vintro.wsplanner.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.utils.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;

import dev.spght.encryptedprefs.*;

public class PreferencesManager {
    private static final String prefs_name = "wsplanner_prefs";
    private static final String key_login = "data_login_";
    private static final String key_password = "data_password_";
    private static final String key_major = "data_major_";
    private static final String key_degree_level = "data_degree_level_";
    private static final String key_study_mode = "data_study_mode_";
    private static final String key_year = "data_year_";
    private static final String key_specialty = "data_specialty_";
    private static final String key_english_group = "data_english_group_";
    private static final String key_seminar_teacher = "data_seminar_teacher_";
    private static final String key_student_name = "data_student_name_";
    private static final String key_student_surname = "data_student_surname_";
    private static final String key_theme = "settings_theme";
    private static final String key_language = "settings_language";
    private static final String key_onboarding_stage = "onboarding_stage";
    private static final String key_specialty_configured = "specialty_configured_";

    public static final String ONBOARDING_STAGE_LOGIN = "LOGIN";
    public static final String ONBOARDING_STAGE_COURSE = "COURSE";
    public static final String ONBOARDING_STAGE_SPECIALTY = "SPECIALTY";
    public static final String ONBOARDING_STAGE_ADDITIONAL = "ADDITIONAL";
    public static final String ONBOARDING_STAGE_COMPLETED = "COMPLETED";

    public static final int GLOBAL_ID = -1;

    private static SharedPreferences getEncryptedPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    prefs_name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Logger.e("PreferencesManager", "Failed to create encrypted preferences, falling back to regular:" + e);
            return context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE);
        }
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE);
    }

    public static void saveGlobalPrefs(Context context, String login, String password, String major, DegreeLevel degreeLevel, StudyMode studyMode, int year, String specialty, String englishGroup) {
        savePrefs(context, GLOBAL_ID, login, password, major, degreeLevel, studyMode, year, specialty, englishGroup);
    }

    public static String getGlobalLoginPref(Context context) {
        return getLoginPref(context, GLOBAL_ID);
    }

    public static String getGlobalPasswordPref(Context context) {
        return getPasswordPref(context, GLOBAL_ID);
    }

    public static String getGlobalMajorPref(Context context) {
        return getMajorPref(context, GLOBAL_ID);
    }

    public static DegreeLevel getGlobalDegreeLevelPref(Context context) {
        return getDegreeLevelPref(context, GLOBAL_ID);
    }

    public static StudyMode getGlobalStudyModePref(Context context) {
        return getStudyModePref(context, GLOBAL_ID);
    }

    public static int getGlobalYearPref(Context context) {
        return getYearPref(context, GLOBAL_ID);
    }

    public static String getGlobalSpecialtyPref(Context context) {
        return getSpecialtyPref(context, GLOBAL_ID);
    }

    public static void setGlobalSpecialtyPref(Context context, String specialty) {
        getEncryptedPreferences(context).edit()
                .putString(key_specialty + GLOBAL_ID, specialty)
                .apply();
    }

    public static String getGlobalEnglishGroupPref(Context context) {
        return getEnglishGroupPref(context, GLOBAL_ID);
    }

    public static void setGlobalEnglishGroupPref(Context context, String englishGroup) {
        getEncryptedPreferences(context).edit()
                .putString(key_english_group + GLOBAL_ID, englishGroup)
                .apply();
    }

    public static String getGlobalSeminarTeacherPref(Context context) {
        return getSeminarTeacherPref(context, GLOBAL_ID);
    }

    public static void setGlobalSeminarTeacherPref(Context context, String teacher) {
        setSeminarTeacherPref(context, GLOBAL_ID, teacher);
    }

    public static String getGlobalStudentNamePref(Context context) {
        return getStudentNamePref(context, GLOBAL_ID);
    }

    public static void setGlobalStudentNamePref(Context context, String studentName) {
        setStudentNamePref(context, GLOBAL_ID, studentName);
    }

    public static String getGlobalStudentSurnamePref(Context context) {
        return getStudentSurnamePref(context, GLOBAL_ID);
    }

    public static void setGlobalStudentSurnamePref(Context context, String studentSurname) {
        setStudentSurnamePref(context, GLOBAL_ID, studentSurname);
    }

    public static void deleteGlobalPrefs(Context context) {
        deletePrefs(context, GLOBAL_ID);
    }

    public static void savePrefs(Context context, int widgetId, String login, String password, String major, DegreeLevel degreeLevel, StudyMode studyMode, int year, String specialty, String englishGroup) {
        Logger.d("PreferencesManager.savePrefs", "Saving prefs for widget " + widgetId + ", year: " + year);

        SharedPreferences prefs = getEncryptedPreferences(context);
        prefs.edit()
                .putString(key_login + widgetId, login)
                .putString(key_password + widgetId, password)
                .putString(key_major + widgetId, major)
                .putString(key_degree_level + widgetId, degreeLevel != null ? degreeLevel.name() : null)
                .putString(key_study_mode + widgetId, studyMode != null ? studyMode.name() : null)
                .putInt(key_year + widgetId, year)
                .putString(key_specialty + widgetId, specialty)
                .putString(key_english_group + widgetId, englishGroup)
                .apply();
    }

    public static String getLoginPref(Context context, int widgetId) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        String login = prefs.getString(key_login + widgetId, null);
        Logger.d("PreferencesManager.getLoginPref", "Getting login for widget " + widgetId);
        return login;
    }

    public static String getPasswordPref(Context context, int widgetId) {
        SharedPreferences prefs = getEncryptedPreferences(context);
        String password = prefs.getString(key_password + widgetId, null);
        Logger.d("PreferencesManager.getPasswordPref", "Getting password for widget " + widgetId + ", result is null: " + (password == null));
        return password;
    }

    public static String getMajorPref(Context context, int widgetId) {
        return getEncryptedPreferences(context).getString(key_major + widgetId, null);
    }

    public static DegreeLevel getDegreeLevelPref(Context context, int widgetId) {
        String level = getEncryptedPreferences(context).getString(key_degree_level + widgetId, null);
        return level != null ? DegreeLevel.valueOf(level) : null;
    }

    public static StudyMode getStudyModePref(Context context, int widgetId) {
        String mode = getEncryptedPreferences(context).getString(key_study_mode + widgetId, null);
        return mode != null ? StudyMode.valueOf(mode) : null;
    }

    public static int getYearPref(Context context, int widgetId) {
        int year = getEncryptedPreferences(context).getInt(key_year + widgetId, 1);
        Logger.d("PreferencesManager.getYearPref", "Getting year for widget " + widgetId + ", year: " + year);
        return year;
    }

    public static String getSpecialtyPref(Context context, int widgetId) {
        return getEncryptedPreferences(context).getString(key_specialty + widgetId, null);
    }

    public static String getEnglishGroupPref(Context context, int widgetId) {
        return getEncryptedPreferences(context).getString(key_english_group + widgetId, null);
    }

    public static String getSeminarTeacherPref(Context context, int widgetId) {
        return getEncryptedPreferences(context).getString(key_seminar_teacher + widgetId, null);
    }

    public static void setSeminarTeacherPref(Context context, int widgetId, String teacher) {
        getEncryptedPreferences(context).edit()
                .putString(key_seminar_teacher + widgetId, teacher)
                .apply();
    }

    public static String getStudentNamePref(Context context, int widgetId) {
        return getEncryptedPreferences(context).getString(key_student_name + widgetId, null);
    }

    public static void setStudentNamePref(Context context, int widgetId, String studentName) {
        getEncryptedPreferences(context).edit()
                .putString(key_student_name + widgetId, studentName)
                .apply();
    }

    public static String getStudentSurnamePref(Context context, int widgetId) {
        return getEncryptedPreferences(context).getString(key_student_surname + widgetId, null);
    }

    public static void setStudentSurnamePref(Context context, int widgetId, String studentSurname) {
        getEncryptedPreferences(context).edit()
                .putString(key_student_surname + widgetId, studentSurname)
                .apply();
    }

    public static void deletePrefs(Context context, int widgetId) {
        Logger.d("PreferencesManager.deletePrefs", "Deleting prefs for widget " + widgetId);

        SharedPreferences prefs = getEncryptedPreferences(context);
        prefs.edit()
                .remove(key_login + widgetId)
                .remove(key_password + widgetId)
                .remove(key_major + widgetId)
                .remove(key_degree_level + widgetId)
                .remove(key_study_mode + widgetId)
                .remove(key_year + widgetId)
                .remove(key_specialty + widgetId)
                .remove(key_english_group + widgetId)
                .remove(key_seminar_teacher + widgetId)
                .remove(key_student_name + widgetId)
                .remove(key_student_surname + widgetId)
                .remove(key_specialty_configured + widgetId)
                .apply();
        if (widgetId == GLOBAL_ID) {
            setOnboardingStage(context, ONBOARDING_STAGE_LOGIN);
        }
    }

    public static String getOnboardingStage(Context context) {
        return getPreferences(context).getString(key_onboarding_stage, ONBOARDING_STAGE_LOGIN);
    }

    public static void setOnboardingStage(Context context, String stage) {
        getPreferences(context).edit()
                .putString(key_onboarding_stage, stage)
                .apply();
    }

    public static boolean isSpecialtyConfigured(Context context, int widgetId) {
        return getPreferences(context).getBoolean(key_specialty_configured + widgetId, false);
    }

    public static void setSpecialtyConfigured(Context context, int widgetId, boolean configured) {
        getPreferences(context).edit()
                .putBoolean(key_specialty_configured + widgetId, configured)
                .apply();
    }

    public static boolean isGlobalSpecialtyConfigured(Context context) {
        return isSpecialtyConfigured(context, GLOBAL_ID);
    }

    public static void setGlobalSpecialtyConfigured(Context context, boolean configured) {
        setSpecialtyConfigured(context, GLOBAL_ID, configured);
    }

    public static boolean hasExplicitTheme(Context context) {
        return getPreferences(context).contains(key_theme);
    }

    public static boolean hasExplicitLanguage(Context context) {
        return getPreferences(context).contains(key_language);
    }

    public static AppTheme getThemePref(Context context) {
        SharedPreferences prefs = getPreferences(context);
        int pref = prefs.getInt(key_theme, AppTheme.AUTO.value);
        return AppTheme.getEnum(pref);
    }

    public static void setThemePref(Context context, AppTheme theme) {
        SharedPreferences prefs = getPreferences(context);
        prefs.edit()
                .putInt(key_theme, theme.value)
                .apply();
    }

    public static Language getLanguagePref(Context context) {
        SharedPreferences prefs = getPreferences(context);
        String saved = prefs.getString(key_language, null);
        if (saved != null) {
            return Language.getEnum(saved);
        }
        // fallback to system language
        String systemLang = Locale.getDefault().getLanguage();
        return Language.getEnum(systemLang);
    }

    public static void setLanguagePref(Context context, Language language) {
        SharedPreferences prefs = getPreferences(context);
        prefs.edit()
                .putString(key_language, language.code)
                .apply();
    }
}