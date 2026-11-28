package com.vintro.wsplanner.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.parser.ParserUtils;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// service for course onboarding and setup logic
public class CourseSetupService {
    private static CourseSetupService instance;

    private volatile List<ParsedCourse> cachedCourses;
    private volatile Integer cachedEnglishGroup;
    private volatile String cachedStudentFullName;
    private volatile String cachedSeminarTeacher;

    private CourseSetupService() {
    }

    public static synchronized CourseSetupService getInstance() {
        if (instance == null) {
            instance = new CourseSetupService();
        }
        return instance;
    }

    public Integer getCachedEnglishGroup() {
        return cachedEnglishGroup;
    }

    public String getCachedStudentFullName() {
        return cachedStudentFullName;
    }

    public String getCachedSeminarTeacher() {
        return cachedSeminarTeacher;
    }

    public interface OnSetupDataLoadedCallback {
        void onSuccess(@NonNull List<ParsedCourse> courses, @Nullable Integer englishGroup, @Nullable String studentFullName, @Nullable String seminarTeacher);

        void onError(@NonNull Exception e);
    }

    public interface OnSpecialtiesLoadedCallback {
        void onSuccess(@NonNull List<String> specialties);

        void onError(@NonNull Exception e);
    }

    public interface OnAdditionalDataLoadedCallback {
        void onSuccess(@Nullable Integer englishGroup, @Nullable String studentFullName, @Nullable String seminarTeacher);

        void onError(@NonNull Exception e);
    }

    // load course setup data asynchronously using application context
    public void loadCourseSetupData(@NonNull Context context, @NonNull OnSetupDataLoadedCallback callback) {
        List<ParsedCourse> currentCached = cachedCourses;
        if (currentCached != null && !currentCached.isEmpty()) {
            Logger.d("CourseSetupService.loadCourseSetupData", "Reusing cached course setup data (" + currentCached.size() + " courses)");
            callback.onSuccess(currentCached, cachedEnglishGroup, cachedStudentFullName, cachedSeminarTeacher);
            return;
        }

        final Context appContext = context.getApplicationContext();
        Logger.d("CourseSetupService.loadCourseSetupData", "Fetching course setup data from PUW");
        AppExecutors.runIo(() -> {
            try {
                OkHttpClient client = PUW.globalLogin(appContext);
                if (client == null) {
                    throw new IllegalStateException("Failed to login to PUW");
                }

                List<ParsedCourse> courses = StudyPlanScraper.getAvailableCourses(client);
                Integer englishGroup = StudyPlanScraper.getEnglishGroup(client);
                String studentFullName = PUW.getStudentFullName(client);
                String seminarTeacher = StudyPlanScraper.getSeminarTeacher(client);

                List<ParsedCourse> safeCourses = courses != null ? courses : new ArrayList<>();
                cachedCourses = safeCourses;
                cachedEnglishGroup = englishGroup;
                cachedStudentFullName = studentFullName;
                cachedSeminarTeacher = seminarTeacher;

                if (englishGroup != null) {
                    PreferencesManager.setDetectedEnglishGroup(appContext, englishGroup);
                }
                if (studentFullName != null) {
                    PreferencesManager.setDetectedStudentName(appContext, studentFullName);
                    String[] parts = studentFullName.trim().split("\\s+");
                    if (parts.length > 0) {
                        PreferencesManager.setDetectedStudentSurname(appContext, parts[parts.length - 1]);
                    }
                }
                if (seminarTeacher != null) {
                    PreferencesManager.setDetectedSeminarTeacher(appContext, seminarTeacher);
                }

                Logger.i("CourseSetupService.loadCourseSetupData", "Course setup data loaded successfully: " + safeCourses.size() + " courses, EnglishGroup=" + englishGroup + ", StudentName='" + Logger.maskName(studentFullName) + "', SeminarTeacher='" + seminarTeacher + "'");
                callback.onSuccess(safeCourses, englishGroup, studentFullName, seminarTeacher);
            } catch (Exception e) {
                Logger.e("CourseSetupService.loadCourseSetupData", "Error loading course setup data: " + Logger.formatException(e));
                callback.onError(e);
            }
        });
    }

    // load additional data (english group, seminar, student name) if missing after process restart
    public void loadAdditionalSetupData(@NonNull Context context, @NonNull OnAdditionalDataLoadedCallback callback) {
        if (cachedEnglishGroup != null || cachedSeminarTeacher != null || cachedStudentFullName != null) {
            Logger.d("CourseSetupService.loadAdditionalSetupData", "Reusing memory-cached additional setup data");
            callback.onSuccess(cachedEnglishGroup, cachedStudentFullName, cachedSeminarTeacher);
            return;
        }

        final Context appContext = context.getApplicationContext();
        Integer detectedEnglish = PreferencesManager.getDetectedEnglishGroup(appContext);
        String detectedSeminar = PreferencesManager.getDetectedSeminarTeacher(appContext);
        String detectedFullName = PreferencesManager.getDetectedStudentName(appContext);

        if (detectedEnglish != null || detectedSeminar != null || detectedFullName != null) {
            Logger.d("CourseSetupService.loadAdditionalSetupData", "Reusing persisted detected additional setup data");
            cachedEnglishGroup = detectedEnglish;
            cachedSeminarTeacher = detectedSeminar;
            cachedStudentFullName = detectedFullName;
            callback.onSuccess(cachedEnglishGroup, cachedStudentFullName, cachedSeminarTeacher);
            return;
        }

        Logger.d("CourseSetupService.loadAdditionalSetupData", "Fetching missing additional data from PUW");
        AppExecutors.runIo(() -> {
            try {
                OkHttpClient client = PUW.globalLogin(appContext);
                if (client == null) {
                    throw new IllegalStateException("Failed to login to PUW");
                }
                Integer englishGroup = StudyPlanScraper.getEnglishGroup(client);
                String studentFullName = PUW.getStudentFullName(client);
                String seminarTeacher = StudyPlanScraper.getSeminarTeacher(client);

                cachedEnglishGroup = englishGroup;
                cachedStudentFullName = studentFullName;
                cachedSeminarTeacher = seminarTeacher;

                if (englishGroup != null) {
                    PreferencesManager.setDetectedEnglishGroup(appContext, englishGroup);
                }
                if (studentFullName != null) {
                    PreferencesManager.setDetectedStudentName(appContext, studentFullName);
                    String[] parts = studentFullName.trim().split("\\s+");
                    if (parts.length > 0) {
                        PreferencesManager.setDetectedStudentSurname(appContext, parts[parts.length - 1]);
                    }
                }
                if (seminarTeacher != null) {
                    PreferencesManager.setDetectedSeminarTeacher(appContext, seminarTeacher);
                }

                Logger.i("CourseSetupService.loadAdditionalSetupData", "Additional setup data fetched: English=" + englishGroup + ", FullName='" + Logger.maskName(studentFullName) + "', Seminar='" + seminarTeacher + "'");
                callback.onSuccess(englishGroup, studentFullName, seminarTeacher);
            } catch (Exception e) {
                Logger.e("CourseSetupService.loadAdditionalSetupData", "Error loading additional setup data: " + Logger.formatException(e));
                callback.onError(e);
            }
        });
    }

    // get default or first matching course
    @Nullable
    public ParsedCourse determinePrimaryCourse(@Nullable List<ParsedCourse> courses) {
        List<ParsedCourse> source = (courses != null) ? courses : cachedCourses;
        if (source == null || source.isEmpty()) {
            Logger.w("CourseSetupService.determinePrimaryCourse", "No courses available to determine primary");
            return null;
        }
        for (ParsedCourse c : source) {
            if (c.fieldOfStudy != null && c.fieldOfStudy.toLowerCase().contains("informatyka")) {
                Logger.d("CourseSetupService.determinePrimaryCourse", "Matched primary course: " + c.fieldOfStudy);
                return c;
            }
        }
        ParsedCourse fallback = source.get(0);
        Logger.d("CourseSetupService.determinePrimaryCourse", "Fallback primary course: " + fallback.fieldOfStudy + " (level=" + fallback.degreeLevel + ")");
        return fallback;
    }

    // get degree levels for field of study
    @NonNull
    public List<DegreeLevel> getAvailableDegreeLevels(@Nullable List<ParsedCourse> courses, @Nullable String fieldOfStudy) {
        List<ParsedCourse> source = (courses != null) ? courses : cachedCourses;
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        Set<DegreeLevel> levels = new LinkedHashSet<>();
        for (ParsedCourse course : source) {
            if (fieldOfStudy == null || (course.fieldOfStudy != null && course.fieldOfStudy.equals(fieldOfStudy))) {
                if (course.degreeLevel != null) {
                    levels.add(course.degreeLevel);
                }
            }
        }
        return new ArrayList<>(levels);
    }

    // find course by field of study and degree level
    @Nullable
    public ParsedCourse findCourse(@Nullable List<ParsedCourse> courses, @Nullable String fieldOfStudy, @Nullable DegreeLevel level) {
        List<ParsedCourse> source = (courses != null) ? courses : cachedCourses;
        if (source == null) return null;

        for (ParsedCourse c : source) {
            boolean matchField = (fieldOfStudy == null || (c.fieldOfStudy != null && c.fieldOfStudy.equals(fieldOfStudy)));
            boolean matchLevel = (level == null || c.degreeLevel == level);
            if (matchField && matchLevel) {
                return c;
            }
        }
        return null;
    }

    // get study modes for course
    @NonNull
    public List<StudyMode> getAvailableStudyModes(@Nullable ParsedCourse course) {
        if (course == null || course.availableModesAndYears == null) {
            return Collections.emptyList();
        }

        Set<StudyMode> validModes = new LinkedHashSet<>();
        for (String modeStr : course.availableModesAndYears.keySet()) {
            StudyMode mode = StudyMode.fromString(modeStr);
            if (mode != null) {
                validModes.add(mode);
            }
        }
        return new ArrayList<>(validModes);
    }

    // get study years for course and mode
    @NonNull
    public List<Integer> getAvailableYears(@Nullable ParsedCourse course, @Nullable StudyMode mode) {
        if (course == null || course.availableModesAndYears == null || mode == null) {
            return Collections.emptyList();
        }

        List<Integer> years = course.availableModesAndYears.get(mode.getValue());
        if (years == null) {
            return Collections.emptyList();
        }
        List<Integer> sorted = new ArrayList<>(years);
        Collections.sort(sorted);
        return sorted;
    }

    // load specializations and seed the verified xlsx into ScheduleRepository to avoid downloading twice
    public void loadSpecialties(@NonNull Context context, @NonNull OnSpecialtiesLoadedCallback callback) {
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> {
            File tempFile = null;
            try {
                String major = PreferencesManager.getGlobalMajorPref(appContext);
                DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(appContext);
                StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(appContext);
                int year = PreferencesManager.getGlobalYearPref(appContext);

                Logger.d("CourseSetupService.loadSpecialties", "Loading specializations for " + major + ", " + degreeLevel + ", " + studyMode + ", year " + year);

                if (degreeLevel == DegreeLevel.ZERO_YEAR || (major != null && major.toLowerCase().contains("rok zerowy"))) {
                    Logger.i("CourseSetupService.loadSpecialties", "Rok Zerowy has no specializations; returning empty list directly");
                    callback.onSuccess(Collections.emptyList());
                    return;
                }

                if (major == null || degreeLevel == null || studyMode == null) {
                    throw new IllegalStateException("Missing course configuration");
                }

                OkHttpClient client = PUW.globalLogin(appContext);
                if (client == null) {
                    throw new IllegalStateException("Login failed while fetching specialties");
                }

                List<String> specializations = new ArrayList<>();
                String fileUrl = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
                Logger.d("CourseSetupService.loadSpecialties", "Fetched schedule file url for specialties: " + fileUrl);

                if (fileUrl != null) {
                    tempFile = new File(appContext.getCacheDir(), "specialties_probe_" + System.currentTimeMillis() + ".xlsx");
                    Request request = new Request.Builder().url(fileUrl).build();
                    try (Response response = client.newCall(request).execute()) {
                        String finalUrl = response.request().url().toString().toLowerCase(Locale.ROOT);
                        if (response.isSuccessful() && response.body() != null && !finalUrl.contains("/login/")) {
                            try (InputStream is = response.body().byteStream();
                                 FileOutputStream out = new FileOutputStream(tempFile)) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = is.read(buffer)) != -1) {
                                    out.write(buffer, 0, read);
                                }
                            }
                            if (ScheduleRepository.isValidZipFile(tempFile)) {
                                try (InputStream fileIn = new FileInputStream(tempFile)) {
                                    specializations = ParserUtils.extractSpecializations(fileIn);
                                }
                                ScheduleRepository.getInstance(appContext).seedScheduleFileFromDownload(tempFile);
                            } else {
                                Logger.w("CourseSetupService.loadSpecialties", "Downloaded specialty file failed ZIP header verification");
                            }
                        }
                    }
                }

                Logger.i("CourseSetupService.loadSpecialties", "Loaded " + specializations.size() + " specialization(s)");
                callback.onSuccess(specializations != null ? specializations : Collections.emptyList());
            } catch (Exception e) {
                Logger.e("CourseSetupService.loadSpecialties", "Error loading specialties: " + Logger.formatException(e));
                callback.onError(e);
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        });
    }

    // clear in-memory cache
    public void clearCache() {
        Logger.d("CourseSetupService.clearCache", "Clearing CourseSetupService memory cache");
        cachedCourses = null;
        cachedEnglishGroup = null;
        cachedStudentFullName = null;
        cachedSeminarTeacher = null;
    }
}
