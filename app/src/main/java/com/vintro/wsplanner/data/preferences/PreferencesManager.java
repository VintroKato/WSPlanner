package com.vintro.wsplanner.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.utils.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.ProviderException;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// preferences manager for app settings, user profiles, and secure credentials
public class PreferencesManager {
    private static final String prefs_name = "wsplanner_prefs";
    private static final String secure_prefs_name = "wsplanner_secure_prefs";
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
    private static final String key_detected_english = "detected_english_group";
    private static final String key_detected_seminar = "detected_seminar_teacher";
    private static final String key_detected_student_name = "detected_student_name";
    private static final String key_detected_student_surname = "detected_student_surname";
    private static final String key_theme = "settings_theme";
    private static final String key_language = "settings_language";
    private static final String key_onboarding_stage = "onboarding_stage";
    private static final String key_specialty_configured = "specialty_configured_";
    private static final String key_moodle_token = "moodle_mobile_token";
    private static final String key_moodle_private_token = "moodle_mobile_private_token";
    private static final String key_moodle_user_id = "moodle_mobile_user_id";

    public static final String ONBOARDING_STAGE_LOGIN = "LOGIN";
    public static final String ONBOARDING_STAGE_COURSE = "COURSE";
    public static final String ONBOARDING_STAGE_SPECIALTY = "SPECIALTY";
    public static final String ONBOARDING_STAGE_ADDITIONAL = "ADDITIONAL";
    public static final String ONBOARDING_STAGE_COMPLETED = "COMPLETED";

    public static final int GLOBAL_ID = -1;

    private static volatile SharedPreferences encryptedPrefsInstance;

    // get or initialize secure hardware-backed preferences
    private static synchronized SharedPreferences getEncryptedPreferences(Context context) {
        if (encryptedPrefsInstance != null) {
            return encryptedPrefsInstance;
        }
        try {
            encryptedPrefsInstance = SecurePreferences.create(context, secure_prefs_name);
            return encryptedPrefsInstance;
        } catch (GeneralSecurityException | IOException | ProviderException e) {
            Logger.e("PreferencesManager.getEncryptedPreferences", "Failed to initialize encrypted preferences: " + Logger.formatException(e));
            throw new IllegalStateException("Secure storage unavailable", e);
        }
    }

    // check keystore availability with an active encrypt and decrypt round trip
    public static boolean checkKeystoreHealth(Context context) {
        try {
            SharedPreferences prefs = getEncryptedPreferences(context);
            if (prefs instanceof SecurePreferences) {
                boolean healthy = ((SecurePreferences) prefs).verifyRoundTrip();
                if (!healthy) {
                    Logger.e("PreferencesManager.checkKeystoreHealth", "Keystore round-trip verification failed");
                }
                return healthy;
            }
            return true;
        } catch (Exception e) {
            Logger.e("PreferencesManager.checkKeystoreHealth", "Keystore health check failed: " + Logger.formatException(e));
            return false;
        }
    }

    // reset corrupted storage and remove the keystore master key alias so recovery can regenerate it
    public static void resetCorruptedStorage(Context context) {
        try {
            encryptedPrefsInstance = null;
            SecurePreferences.deleteMasterKey();
            context.deleteSharedPreferences(prefs_name);
            context.deleteSharedPreferences(secure_prefs_name);
            context.deleteSharedPreferences("schedule_repo_prefs");
            Logger.i("PreferencesManager.resetCorruptedStorage", "Corrupted storage and keystore alias reset successfully");
        } catch (Exception e) {
            Logger.e("PreferencesManager.resetCorruptedStorage", "Failed to reset corrupted storage: " + Logger.formatException(e));
        }
    }

    // standard unencrypted preferences for non-sensitive data
    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(prefs_name, Context.MODE_PRIVATE);
    }

    public static int getLastRunVersionCode(Context context) {
        return getPreferences(context).getInt("last_run_version_code", 0);
    }

    public static void setLastRunVersionCode(Context context, int versionCode) {
        getPreferences(context).edit().putInt("last_run_version_code", versionCode).apply();
    }

    public static boolean isLogsSanitized(Context context) {
        return getPreferences(context).getBoolean("logs_sanitized", false);
    }

    public static void setLogsSanitized(Context context, boolean sanitized) {
        getPreferences(context).edit().putBoolean("logs_sanitized", sanitized).apply();
    }

    // one-time migration hook executed by AppUpgradeManager during upgrades
    public static void migrateLegacyStudentData(Context context) {
        try {
            SharedPreferences plainPrefs = getPreferences(context);
            String legacyName = plainPrefs.getString(key_detected_student_name, null);
            String legacySurname = plainPrefs.getString(key_detected_student_surname, null);

            if (legacyName != null || legacySurname != null) {
                Logger.i("PreferencesManager.migrateLegacyStudentData", "Migrating legacy student name/surname to secure preferences");
                SharedPreferences securePrefs = getEncryptedPreferences(context);
                SharedPreferences.Editor secureEditor = securePrefs.edit();
                SharedPreferences.Editor plainEditor = plainPrefs.edit();

                if (legacyName != null && !legacyName.trim().isEmpty()) {
                    if (securePrefs.getString(key_detected_student_name, null) == null) {
                        secureEditor.putString(key_detected_student_name, legacyName.trim());
                    }
                    plainEditor.remove(key_detected_student_name);
                }

                if (legacySurname != null && !legacySurname.trim().isEmpty()) {
                    if (securePrefs.getString(key_detected_student_surname, null) == null) {
                        secureEditor.putString(key_detected_student_surname, legacySurname.trim());
                    }
                    plainEditor.remove(key_detected_student_surname);
                }

                secureEditor.apply();
                plainEditor.apply();
                Logger.d("PreferencesManager.migrateLegacyStudentData", "Legacy student profile data migration finished successfully");
            }
        } catch (Throwable t) {
            Logger.w("PreferencesManager.migrateLegacyStudentData", "Non-fatal error migrating legacy student data: " + Logger.formatException(t));
        }
    }

    // one-time migration moving non-sensitive course config from secure preferences to plain preferences
    public static void migrateCourseConfigToPlainPrefs(Context context) {
        try {
            SharedPreferences securePrefs = getEncryptedPreferences(context);
            SharedPreferences plainPrefs = getPreferences(context);

            String majorKey = key_major + GLOBAL_ID;
            String degreeKey = key_degree_level + GLOBAL_ID;
            String modeKey = key_study_mode + GLOBAL_ID;
            String yearKey = key_year + GLOBAL_ID;
            String specKey = key_specialty + GLOBAL_ID;
            String englishKey = key_english_group + GLOBAL_ID;
            String seminarKey = key_seminar_teacher + GLOBAL_ID;

            boolean hasAnyLegacyKey = securePrefs.contains(majorKey)
                    || securePrefs.contains(degreeKey)
                    || securePrefs.contains(modeKey)
                    || securePrefs.contains(yearKey)
                    || securePrefs.contains(specKey)
                    || securePrefs.contains(englishKey)
                    || securePrefs.contains(seminarKey);

            if (!hasAnyLegacyKey) {
                return;
            }

            Logger.i("PreferencesManager.migrateCourseConfigToPlainPrefs", "Migrating non-sensitive course config out of secure storage");
            SharedPreferences.Editor plainEditor = plainPrefs.edit();
            SharedPreferences.Editor secureEditor = securePrefs.edit();

            copyStringIfMissing(securePrefs, plainPrefs, plainEditor, secureEditor, majorKey);
            copyStringIfMissing(securePrefs, plainPrefs, plainEditor, secureEditor, degreeKey);
            copyStringIfMissing(securePrefs, plainPrefs, plainEditor, secureEditor, modeKey);
            copyStringIfMissing(securePrefs, plainPrefs, plainEditor, secureEditor, specKey);
            copyStringIfMissing(securePrefs, plainPrefs, plainEditor, secureEditor, englishKey);
            copyStringIfMissing(securePrefs, plainPrefs, plainEditor, secureEditor, seminarKey);

            if (securePrefs.contains(yearKey)) {
                int yearVal = securePrefs.getInt(yearKey, -1);
                if (yearVal > 0 && !plainPrefs.contains(yearKey)) {
                    plainEditor.putInt(yearKey, yearVal);
                }
                secureEditor.remove(yearKey);
            }

            plainEditor.apply();
            secureEditor.apply();
            Logger.d("PreferencesManager.migrateCourseConfigToPlainPrefs", "Non-sensitive course config migration completed");
        } catch (Throwable t) {
            Logger.w("PreferencesManager.migrateCourseConfigToPlainPrefs", "Non-fatal error migrating course config: " + Logger.formatException(t));
        }
    }

    private static void copyStringIfMissing(
            SharedPreferences securePrefs,
            SharedPreferences plainPrefs,
            SharedPreferences.Editor plainEditor,
            SharedPreferences.Editor secureEditor,
            String key
    ) {
        if (securePrefs.contains(key)) {
            String value = securePrefs.getString(key, null);
            if (value != null && !plainPrefs.contains(key)) {
                plainEditor.putString(key, value);
            }
            secureEditor.remove(key);
        }
    }

    // save global user profile preferences
    public static void saveGlobalPrefs(Context context, String login, String password, String major, DegreeLevel degreeLevel, StudyMode studyMode, int year, String specialty, String englishGroup) {
        Logger.d("PreferencesManager.saveGlobalPrefs", "Saving global preferences: login=" + Logger.maskSensitiveData(login) + ", major=" + major + ", degree=" + degreeLevel + ", mode=" + studyMode + ", year=" + year + ", specialty=" + specialty + ", englishGroup=" + englishGroup);

        SharedPreferences securePrefs = getEncryptedPreferences(context);
        securePrefs.edit()
                .putString(key_login + GLOBAL_ID, login)
                .putString(key_password + GLOBAL_ID, password)
                .apply();

        SharedPreferences plainPrefs = getPreferences(context);
        plainPrefs.edit()
                .putString(key_major + GLOBAL_ID, major)
                .putString(key_degree_level + GLOBAL_ID, degreeLevel != null ? degreeLevel.name() : null)
                .putString(key_study_mode + GLOBAL_ID, studyMode != null ? studyMode.name() : null)
                .putInt(key_year + GLOBAL_ID, year)
                .putString(key_specialty + GLOBAL_ID, specialty)
                .putString(key_english_group + GLOBAL_ID, englishGroup)
                .apply();
    }

    public static String getGlobalLoginPref(Context context) {
        try {
            SharedPreferences prefs = getEncryptedPreferences(context);
            String login = prefs.getString(key_login + GLOBAL_ID, null);
            Logger.d("PreferencesManager.getGlobalLoginPref", "Retrieved login: " + Logger.maskSensitiveData(login));
            return login;
        } catch (Exception e) {
            Logger.w("PreferencesManager.getGlobalLoginPref", "Unable to read login from secure storage: " + Logger.formatException(e));
            return null;
        }
    }

    public static String getGlobalPasswordPref(Context context) {
        try {
            SharedPreferences prefs = getEncryptedPreferences(context);
            return prefs.getString(key_password + GLOBAL_ID, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getGlobalPasswordPref", "Unable to read password from secure storage: " + Logger.formatException(e));
            return null;
        }
    }

    public static String getGlobalMajorPref(Context context) {
        return getPreferences(context).getString(key_major + GLOBAL_ID, null);
    }

    public static DegreeLevel getGlobalDegreeLevelPref(Context context) {
        String level = getPreferences(context).getString(key_degree_level + GLOBAL_ID, null);
        if (level == null || level.trim().isEmpty()) {
            return null;
        }
        try {
            return DegreeLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            Logger.w("PreferencesManager.getGlobalDegreeLevelPref", "Unknown degree level stored: " + level);
            return null;
        }
    }

    public static StudyMode getGlobalStudyModePref(Context context) {
        String mode = getPreferences(context).getString(key_study_mode + GLOBAL_ID, null);
        if (mode == null || mode.trim().isEmpty()) {
            return null;
        }
        try {
            return StudyMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            Logger.w("PreferencesManager.getGlobalStudyModePref", "Unknown study mode stored: " + mode);
            return null;
        }
    }

    public static int getGlobalYearPref(Context context) {
        int year = getPreferences(context).getInt(key_year + GLOBAL_ID, 1);
        Logger.d("PreferencesManager.getGlobalYearPref", "Getting global year: " + year);
        return year;
    }

    public static String getGlobalSpecialtyPref(Context context) {
        return getPreferences(context).getString(key_specialty + GLOBAL_ID, null);
    }

    public static void setGlobalSpecialtyPref(Context context, String specialty) {
        Logger.d("PreferencesManager.setGlobalSpecialtyPref", "Setting global specialty: " + specialty);
        getPreferences(context).edit()
                .putString(key_specialty + GLOBAL_ID, specialty)
                .apply();
    }

    public static String getGlobalEnglishGroupPref(Context context) {
        return getPreferences(context).getString(key_english_group + GLOBAL_ID, null);
    }

    public static void setGlobalEnglishGroupPref(Context context, String englishGroup) {
        Logger.d("PreferencesManager.setGlobalEnglishGroupPref", "Setting global English group: " + englishGroup);
        getPreferences(context).edit()
                .putString(key_english_group + GLOBAL_ID, englishGroup)
                .apply();
    }

    public static String getGlobalSeminarTeacherPref(Context context) {
        return getPreferences(context).getString(key_seminar_teacher + GLOBAL_ID, null);
    }

    public static void setGlobalSeminarTeacherPref(Context context, String teacher) {
        Logger.d("PreferencesManager.setGlobalSeminarTeacherPref", "Setting global seminar teacher: " + teacher);
        getPreferences(context).edit()
                .putString(key_seminar_teacher + GLOBAL_ID, teacher)
                .apply();
    }

    public static String getGlobalStudentNamePref(Context context) {
        try {
            return getEncryptedPreferences(context).getString(key_student_name + GLOBAL_ID, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getGlobalStudentNamePref", "Unable to read student name from secure storage: " + Logger.formatException(e));
            return null;
        }
    }

    public static void setGlobalStudentNamePref(Context context, String studentName) {
        Logger.d("PreferencesManager.setGlobalStudentNamePref", "Setting global student name: " + Logger.maskName(studentName));
        getEncryptedPreferences(context).edit()
                .putString(key_student_name + GLOBAL_ID, studentName)
                .apply();
    }

    public static String getGlobalStudentSurnamePref(Context context) {
        try {
            return getEncryptedPreferences(context).getString(key_student_surname + GLOBAL_ID, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getGlobalStudentSurnamePref", "Unable to read student surname from secure storage: " + Logger.formatException(e));
            return null;
        }
    }

    public static void setGlobalStudentSurnamePref(Context context, String studentSurname) {
        Logger.d("PreferencesManager.setGlobalStudentSurnamePref", "Setting global student surname: " + Logger.maskSurname(studentSurname));
        getEncryptedPreferences(context).edit()
                .putString(key_student_surname + GLOBAL_ID, studentSurname)
                .apply();
    }

    public static void deleteGlobalPrefs(Context context) {
        Logger.d("PreferencesManager.deleteGlobalPrefs", "Deleting global preferences");

        try {
            SharedPreferences securePrefs = getEncryptedPreferences(context);
            securePrefs.edit()
                    .remove(key_login + GLOBAL_ID)
                    .remove(key_password + GLOBAL_ID)
                    .remove(key_major + GLOBAL_ID)
                    .remove(key_degree_level + GLOBAL_ID)
                    .remove(key_study_mode + GLOBAL_ID)
                    .remove(key_year + GLOBAL_ID)
                    .remove(key_specialty + GLOBAL_ID)
                    .remove(key_english_group + GLOBAL_ID)
                    .remove(key_seminar_teacher + GLOBAL_ID)
                    .remove(key_student_name + GLOBAL_ID)
                    .remove(key_student_surname + GLOBAL_ID)
                    .remove(key_specialty_configured + GLOBAL_ID)
                    .remove(key_detected_student_name)
                    .remove(key_detected_student_surname)
                    .apply();
        } catch (Exception e) {
            Logger.w("PreferencesManager.deleteGlobalPrefs", "Error clearing secure prefs: " + Logger.formatException(e));
        }

        clearMoodleAuthTokens(context);
        getPreferences(context).edit()
                .remove(key_major + GLOBAL_ID)
                .remove(key_degree_level + GLOBAL_ID)
                .remove(key_study_mode + GLOBAL_ID)
                .remove(key_year + GLOBAL_ID)
                .remove(key_specialty + GLOBAL_ID)
                .remove(key_english_group + GLOBAL_ID)
                .remove(key_seminar_teacher + GLOBAL_ID)
                .remove(key_specialty_configured + GLOBAL_ID)
                .remove(key_detected_english)
                .remove(key_detected_seminar)
                .remove(key_detected_student_name)
                .remove(key_detected_student_surname)
                .apply();
        setOnboardingStage(context, ONBOARDING_STAGE_LOGIN);
    }

    // store moodle mobile webservice tokens in encrypted preferences only
    public static void saveMoodleTokens(Context context, String token, String privateToken) {
        Logger.d("PreferencesManager.saveMoodleTokens", "Saving encrypted Moodle mobile tokens (hasToken=" + (token != null && !token.isEmpty()) + ", hasPrivateToken=" + (privateToken != null && !privateToken.isEmpty()) + ")");
        getEncryptedPreferences(context).edit()
                .putString(key_moodle_token, token)
                .putString(key_moodle_private_token, privateToken)
                .apply();
    }

    public static String getMoodleToken(Context context) {
        try {
            return getEncryptedPreferences(context).getString(key_moodle_token, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getMoodleToken", "Unable to read Moodle token: " + Logger.formatException(e));
            return null;
        }
    }

    public static String getMoodlePrivateToken(Context context) {
        try {
            return getEncryptedPreferences(context).getString(key_moodle_private_token, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getMoodlePrivateToken", "Unable to read Moodle private token: " + Logger.formatException(e));
            return null;
        }
    }

    public static void setMoodleUserId(Context context, long userId) {
        Logger.d("PreferencesManager.setMoodleUserId", "Saving encrypted Moodle userId (valid=" + (userId > 0) + ")");
        getEncryptedPreferences(context).edit()
                .putLong(key_moodle_user_id, userId)
                .apply();
    }

    public static long getMoodleUserId(Context context) {
        try {
            return getEncryptedPreferences(context).getLong(key_moodle_user_id, -1L);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getMoodleUserId", "Unable to read Moodle userId: " + Logger.formatException(e));
            return -1L;
        }
    }

    public static void clearMoodleAuthTokens(Context context) {
        Logger.i("PreferencesManager.clearMoodleAuthTokens", "Clearing encrypted Moodle mobile tokens and userId");
        try {
            getEncryptedPreferences(context).edit()
                    .remove(key_moodle_token)
                    .remove(key_moodle_private_token)
                    .remove(key_moodle_user_id)
                    .apply();
        } catch (Exception e) {
            Logger.w("PreferencesManager.clearMoodleAuthTokens", "Unable to clear Moodle tokens: " + Logger.formatException(e));
        }
    }

    public static Integer getDetectedEnglishGroup(Context context) {
        String val = getPreferences(context).getString(key_detected_english, null);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public static void setDetectedEnglishGroup(Context context, Integer group) {
        if (group != null) {
            getPreferences(context).edit().putString(key_detected_english, String.valueOf(group)).apply();
        } else {
            getPreferences(context).edit().remove(key_detected_english).apply();
        }
    }

    public static String getDetectedSeminarTeacher(Context context) {
        return getPreferences(context).getString(key_detected_seminar, null);
    }

    public static void setDetectedSeminarTeacher(Context context, String teacher) {
        if (teacher != null && !teacher.trim().isEmpty()) {
            getPreferences(context).edit().putString(key_detected_seminar, teacher.trim()).apply();
        } else {
            getPreferences(context).edit().remove(key_detected_seminar).apply();
        }
    }

    public static String getDetectedStudentName(Context context) {
        try {
            return getEncryptedPreferences(context).getString(key_detected_student_name, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getDetectedStudentName", "Unable to read detected student name: " + Logger.formatException(e));
            return null;
        }
    }

    public static void setDetectedStudentName(Context context, String name) {
        if (name != null && !name.trim().isEmpty()) {
            getEncryptedPreferences(context).edit().putString(key_detected_student_name, name.trim()).apply();
        } else {
            getEncryptedPreferences(context).edit().remove(key_detected_student_name).apply();
        }
    }

    public static String getDetectedStudentSurname(Context context) {
        try {
            return getEncryptedPreferences(context).getString(key_detected_student_surname, null);
        } catch (Exception e) {
            Logger.w("PreferencesManager.getDetectedStudentSurname", "Unable to read detected student surname: " + Logger.formatException(e));
            return null;
        }
    }

    public static void setDetectedStudentSurname(Context context, String surname) {
        if (surname != null && !surname.trim().isEmpty()) {
            getEncryptedPreferences(context).edit().putString(key_detected_student_surname, surname.trim()).apply();
        } else {
            getEncryptedPreferences(context).edit().remove(key_detected_student_surname).apply();
        }
    }

    // onboarding stage tracking (login -> course -> specialty -> additional -> completed)
    public static String getOnboardingStage(Context context) {
        return getPreferences(context).getString(key_onboarding_stage, ONBOARDING_STAGE_LOGIN);
    }

    public static void setOnboardingStage(Context context, String stage) {
        Logger.d("PreferencesManager.setOnboardingStage", "Transitioning onboarding stage to: " + stage);
        getPreferences(context).edit()
                .putString(key_onboarding_stage, stage)
                .apply();
    }

    public static boolean isOnboardingCompleted(Context context) {
        return ONBOARDING_STAGE_COMPLETED.equals(getOnboardingStage(context));
    }

    // course specialty configuration flag
    public static boolean isGlobalSpecialtyConfigured(Context context) {
        return getPreferences(context).getBoolean(key_specialty_configured + GLOBAL_ID, false);
    }

    public static void setGlobalSpecialtyConfigured(Context context, boolean configured) {
        getPreferences(context).edit()
                .putBoolean(key_specialty_configured + GLOBAL_ID, configured)
                .apply();
    }

    // theme and language preferences
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
        Logger.d("PreferencesManager.setThemePref", "Setting theme preference: " + theme);
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
        String systemLang = Locale.getDefault().getLanguage();
        return Language.getEnum(systemLang);
    }

    public static void setLanguagePref(Context context, Language language) {
        Logger.d("PreferencesManager.setLanguagePref", "Setting language preference: " + language);
        SharedPreferences prefs = getPreferences(context);
        prefs.edit()
                .putString(key_language, language.code)
                .apply();
    }

    // widget preferences
    public static final String WIDGET_THEME_SYSTEM = "SYSTEM";
    public static final String WIDGET_THEME_DARK = "DARK";
    public static final String WIDGET_THEME_LIGHT = "LIGHT";

    public static final String WIDGET_CLICK_OPEN_DETAILS = "OPEN_DETAILS";
    public static final String WIDGET_CLICK_TOGGLE = "TOGGLE";
    public static final String WIDGET_CLICK_OPEN_APP = "OPEN_APP";
    public static final String WIDGET_CLICK_OPEN_EXCEL = "OPEN_EXCEL";
    public static final String WIDGET_CLICK_REFRESH = "REFRESH";

    public static final String WIDGET_BREAK_OPEN_APP = "OPEN_APP";
    public static final String WIDGET_BREAK_OPEN_EXCEL = "OPEN_EXCEL";
    public static final String DAILY_WIDGET_CLICK_OPEN_DETAILS = "OPEN_DETAILS";
    public static final String DAILY_WIDGET_CLICK_OPEN_APP = "OPEN_APP";

    private static final String KEY_WIDGET_THEME = "widget_theme_pref";
    private static final String KEY_WIDGET_CLICK_ACTION = "widget_click_action_pref";
    private static final String KEY_DAILY_WIDGET_LESSON_ACTION = "daily_widget_lesson_action_pref";
    private static final String KEY_WIDGET_BREAK_ACTION = "widget_break_action_pref";
    private static final String KEY_WIDGET_SHOW_EXCEL = "widget_show_excel_pref";
    private static final String KEY_WIDGET_SHOW_REFRESH = "widget_show_refresh_pref";
    private static final String KEY_WIDGET_SHOW_APP = "widget_show_app_pref";
    private static final String KEY_WIDGET_FORCED_NEXT = "widget_forced_next_";

    // get widget visual color theme
    public static String getWidgetTheme(Context context) {
        return getPreferences(context).getString(KEY_WIDGET_THEME, WIDGET_THEME_SYSTEM);
    }

    // save widget visual color theme
    public static void setWidgetTheme(Context context, String theme) {
        getPreferences(context).edit().putString(KEY_WIDGET_THEME, theme).apply();
    }

    // get click action behavior for widget lesson card
    public static String getWidgetClickAction(Context context) {
        return getPreferences(context).getString(KEY_WIDGET_CLICK_ACTION, WIDGET_CLICK_OPEN_APP);
    }

    // save click action behavior for widget lesson card
    public static void setWidgetClickAction(Context context, String action) {
        getPreferences(context).edit().putString(KEY_WIDGET_CLICK_ACTION, action).apply();
    }

    // get click action behavior for daily schedule widget lesson item
    public static String getDailyWidgetLessonAction(Context context) {
        return getPreferences(context).getString(KEY_DAILY_WIDGET_LESSON_ACTION, DAILY_WIDGET_CLICK_OPEN_DETAILS);
    }

    // save click action behavior for daily schedule widget lesson item
    public static void setDailyWidgetLessonAction(Context context, String action) {
        getPreferences(context).edit().putString(KEY_DAILY_WIDGET_LESSON_ACTION, action).apply();
    }

    // get click action when tapping widget during breaks or empty state
    public static String getWidgetBreakAction(Context context) {
        return getPreferences(context).getString(KEY_WIDGET_BREAK_ACTION, WIDGET_BREAK_OPEN_APP);
    }

    // save click action when tapping widget during breaks or empty state
    public static void setWidgetBreakAction(Context context, String action) {
        getPreferences(context).edit().putString(KEY_WIDGET_BREAK_ACTION, action).apply();
    }

    // check if excel button is visible on daily widget header
    public static boolean isWidgetShowExcel(Context context) {
        return getPreferences(context).getBoolean(KEY_WIDGET_SHOW_EXCEL, true);
    }

    // set visibility of excel button on daily widget header
    public static void setWidgetShowExcel(Context context, boolean show) {
        getPreferences(context).edit().putBoolean(KEY_WIDGET_SHOW_EXCEL, show).apply();
    }

    // check if refresh button is visible on daily widget header
    public static boolean isWidgetShowRefresh(Context context) {
        return getPreferences(context).getBoolean(KEY_WIDGET_SHOW_REFRESH, true);
    }

    // set visibility of refresh button on daily widget header
    public static void setWidgetShowRefresh(Context context, boolean show) {
        getPreferences(context).edit().putBoolean(KEY_WIDGET_SHOW_REFRESH, show).apply();
    }

    // check if open app button is visible on daily widget header
    public static boolean isWidgetShowApp(Context context) {
        return getPreferences(context).getBoolean(KEY_WIDGET_SHOW_APP, true);
    }

    // set visibility of open app button on daily widget header
    public static void setWidgetShowApp(Context context, boolean show) {
        getPreferences(context).edit().putBoolean(KEY_WIDGET_SHOW_APP, show).apply();
    }

    // check whether specific widget is toggled to show next lesson instead of current
    public static boolean isWidgetShowingForcedNext(Context context, int widgetId) {
        return getPreferences(context).getBoolean(KEY_WIDGET_FORCED_NEXT + widgetId, false);
    }

    // toggle whether specific widget is forced to show next lesson
    public static void setWidgetShowingForcedNext(Context context, int widgetId, boolean next) {
        getPreferences(context).edit().putBoolean(KEY_WIDGET_FORCED_NEXT + widgetId, next).apply();
    }

    // remove per-widget toggle state when a widget instance is deleted
    public static void clearWidgetForcedNext(Context context, int widgetId) {
        getPreferences(context).edit().remove(KEY_WIDGET_FORCED_NEXT + widgetId).apply();
    }

    private static final String KEY_PIN_WIDGET_REQUEST_TIMESTAMP = "pin_widget_request_timestamp";
    private static final long PIN_WIDGET_TIMEOUT_MS = 20_000L;

    // record timestamp when user requests widget pinning from inside settings
    public static void setPinWidgetRequested(Context context) {
        getPreferences(context).edit().putLong(KEY_PIN_WIDGET_REQUEST_TIMESTAMP, System.currentTimeMillis()).apply();
    }

    // check if a widget pin request was initiated within the recent timeout window
    public static boolean wasPinWidgetRequestedRecently(Context context) {
        long timestamp = getPreferences(context).getLong(KEY_PIN_WIDGET_REQUEST_TIMESTAMP, 0L);
        return (System.currentTimeMillis() - timestamp) <= PIN_WIDGET_TIMEOUT_MS;
    }

    // clear the widget pin request flag once handled
    public static void clearPinWidgetRequested(Context context) {
        getPreferences(context).edit().remove(KEY_PIN_WIDGET_REQUEST_TIMESTAMP).apply();
    }

    // visible weekdays preference for schedule day strip and pager
    private static final String KEY_VISIBLE_WEEKDAYS = "visible_weekdays";

    public static boolean hasVisibleWeekdaysPref(Context context) {
        return getPreferences(context).contains(KEY_VISIBLE_WEEKDAYS);
    }

    public static Set<DayOfWeek> getVisibleWeekdaysPref(Context context) {
        Set<String> saved = getPreferences(context).getStringSet(KEY_VISIBLE_WEEKDAYS, null);
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        if (saved != null) {
            for (String name : saved) {
                try {
                    result.add(DayOfWeek.valueOf(name));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (result.isEmpty()) {
            return getFallbackVisibleWeekdays(getGlobalStudyModePref(context));
        }
        return result;
    }

    public static void setVisibleWeekdaysPref(Context context, Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return;
        }
        Set<String> raw = new HashSet<>();
        for (DayOfWeek day : days) {
            if (day != null) {
                raw.add(day.name());
            }
        }
        if (raw.isEmpty()) {
            return;
        }
        getPreferences(context).edit().putStringSet(KEY_VISIBLE_WEEKDAYS, raw).apply();
        Logger.i("PreferencesManager.setVisibleWeekdaysPref", "Saved visible weekdays: " + raw);
    }

    public static void initRecommendedVisibleDays(Context context, List<Lesson> lessons, StudyMode studyMode) {
        if (hasVisibleWeekdaysPref(context)) {
            return;
        }
        EnumSet<DayOfWeek> detected = EnumSet.noneOf(DayOfWeek.class);
        if (lessons != null) {
            for (Lesson lesson : lessons) {
                if (lesson != null && lesson.getDate() != null) {
                    detected.add(lesson.getDate().getDayOfWeek());
                }
            }
        }
        Set<DayOfWeek> target = detected.isEmpty() ? getFallbackVisibleWeekdays(studyMode) : detected;
        setVisibleWeekdaysPref(context, target);
        Logger.i("PreferencesManager.initRecommendedVisibleDays", "Initialized recommended visible weekdays (fromLessons=" + !detected.isEmpty() + ", mode=" + studyMode + "): " + target);
    }

    public static void clearVisibleWeekdaysPref(Context context) {
        getPreferences(context).edit().remove(KEY_VISIBLE_WEEKDAYS).apply();
        Logger.d("PreferencesManager.clearVisibleWeekdaysPref", "Cleared visible weekdays preference");
    }

    private static Set<DayOfWeek> getFallbackVisibleWeekdays(StudyMode studyMode) {
        if (studyMode == StudyMode.PART_TIME || studyMode == StudyMode.PART_TIME_PUW) {
            return EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
        return EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
    }
}
