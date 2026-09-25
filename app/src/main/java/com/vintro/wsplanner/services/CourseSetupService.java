package com.vintro.wsplanner.services;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.parser.ParserUtils;
import com.vintro.wsplanner.utils.Logger;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// service for course onboarding and setup logic
public class CourseSetupService {
    private static CourseSetupService instance;

    private List<ParsedCourse> cachedCourses;
    private Integer cachedEnglishGroup;
    private String cachedStudentFullName;
    private String cachedSeminarTeacher;

    private CourseSetupService() {}

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

    // load course setup data asynchronously
    public void loadCourseSetupData(@NonNull Context context, @NonNull OnSetupDataLoadedCallback callback) {
        if (cachedCourses != null && !cachedCourses.isEmpty()) {
            Logger.d("CourseSetupService.loadCourseSetupData", "Reusing cached course setup data (" + cachedCourses.size() + " courses)");
            callback.onSuccess(cachedCourses, cachedEnglishGroup, cachedStudentFullName, cachedSeminarTeacher);
            return;
        }

        Logger.d("CourseSetupService.loadCourseSetupData", "Fetching course setup data from PUW");
        new Thread(() -> {
            try {
                OkHttpClient client = PUW.globalLogin(context);
                if (client == null) {
                    throw new IllegalStateException("Failed to login to PUW");
                }

                List<ParsedCourse> courses = StudyPlanScraper.getAvailableCourses(client);
                Integer englishGroup = StudyPlanScraper.getEnglishGroup(client);
                String studentFullName = PUW.getStudentFullName(client);
                String seminarTeacher = StudyPlanScraper.getSeminarTeacher(client);

                cachedCourses = courses != null ? courses : new ArrayList<>();
                cachedEnglishGroup = englishGroup;
                cachedStudentFullName = studentFullName;
                cachedSeminarTeacher = seminarTeacher;

                Logger.i("CourseSetupService.loadCourseSetupData", "Course setup data loaded successfully: " + cachedCourses.size() + " courses, EnglishGroup=" + englishGroup + ", StudentName='" + studentFullName + "', SeminarTeacher='" + seminarTeacher + "'");
                callback.onSuccess(cachedCourses, cachedEnglishGroup, cachedStudentFullName, cachedSeminarTeacher);
            } catch (Exception e) {
                Logger.e("CourseSetupService.loadCourseSetupData", "Error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
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
        Logger.d("CourseSetupService.determinePrimaryCourse", "Fallback primary course: " + fallback.fieldOfStudy);
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

    // load specializations for selected configuration
    public void loadSpecialties(@NonNull Context context, @NonNull OnSpecialtiesLoadedCallback callback) {
        new Thread(() -> {
            try {
                String major = PreferencesManager.getGlobalMajorPref(context);
                DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(context);
                StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(context);
                int year = PreferencesManager.getGlobalYearPref(context);

                Logger.d("CourseSetupService.loadSpecialties", "Loading specializations for " + major + ", " + degreeLevel + ", " + studyMode + ", year " + year);

                if (major == null || degreeLevel == null || studyMode == null) {
                    throw new IllegalStateException("Missing course configuration");
                }

                OkHttpClient client = PUW.globalLogin(context);
                if (client == null) {
                    throw new IllegalStateException("Login failed while fetching specialties");
                }

                List<String> specializations = new ArrayList<>();
                String fileUrl = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
                Logger.d("CourseSetupService.loadSpecialties", "Fetched schedule file url for specialties: " + fileUrl);

                if (fileUrl != null) {
                    Request request = new Request.Builder().url(fileUrl).build();
                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful() && response.body() != null) {
                            InputStream is = response.body().byteStream();
                            specializations = ParserUtils.extractSpecializations(is);
                        }
                    }
                }

                Logger.i("CourseSetupService.loadSpecialties", "Loaded " + specializations.size() + " specialization(s)");
                callback.onSuccess(specializations != null ? specializations : Collections.emptyList());
            } catch (Exception e) {
                Logger.e("CourseSetupService.loadSpecialties", "Error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // clear in-memory cache
    public void clearCache() {
        Logger.d("CourseSetupService.clearCache", "Clearing CourseSetupService memory cache");
        cachedCourses = null;
        cachedEnglishGroup = null;
        cachedStudentFullName = null;
    }
}
