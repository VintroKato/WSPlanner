package com.vintro.wsplanner.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Schedule;
import com.vintro.wsplanner.models.SubjectDetails;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.parser.ParserFactory;
import com.vintro.wsplanner.parser.ScheduleParser;
import com.vintro.wsplanner.utils.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// handles schedule downloading, caching, and parsing
public class ScheduleRepository {
    private static final String TAG = "ScheduleRepository";
    private static final String CACHE_FILE_PREFIX = "cached_schedule_";

    private static ScheduleRepository instance;

    private final Context appContext;
    private List<Lesson> cachedAllLessons;
    private LocalDateTime lastSyncTime;
    private String lastConfigSignature;

    private ScheduleRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized ScheduleRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ScheduleRepository(context);
        }
        return instance;
    }

    public interface ScheduleCallback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    public interface FileReadyCallback {
        void onFileReady(File file);
        void onError(Exception e);
    }

    private static final long COOLDOWN_SECONDS = 30;
    private static final long STALE_THRESHOLD_MINUTES = 5;
    private long lastManualSyncTimestampMillis = 0;

    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    // remaining refresh cooldown in seconds
    public synchronized long getCooldownRemainingSeconds() {
        long elapsedSeconds = (System.currentTimeMillis() - lastManualSyncTimestampMillis) / 1000;
        if (elapsedSeconds < COOLDOWN_SECONDS) {
            return COOLDOWN_SECONDS - elapsedSeconds;
        }
        return 0;
    }

    // get schedule excel file, downloading if older than 5 minutes
    public void getScheduleFileForOpen(FileReadyCallback callback) {
        new Thread(() -> {
            try {
                File cacheFile = getLocalScheduleFile();
                boolean shouldDownload = true;

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    long ageMinutes = (System.currentTimeMillis() - cacheFile.lastModified()) / (60 * 1000);
                    if (ageMinutes < STALE_THRESHOLD_MINUTES) {
                        shouldDownload = false;
                    }
                }

                if (shouldDownload) {
                    downloadScheduleFile(cacheFile);
                    parseFile(cacheFile);
                    lastConfigSignature = buildConfigSignature();
                    lastSyncTime = LocalDateTime.now();
                }

                callback.onFileReady(cacheFile);
            } catch (Exception e) {
                Logger.e(TAG, "getScheduleFileForOpen error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // get schedule for a day asynchronously
    public void getScheduleForDate(LocalDate date, boolean forceRefresh, ScheduleCallback<DaySchedule> callback) {
        new Thread(() -> {
            try {
                ensureScheduleLoaded(forceRefresh);
                DaySchedule daySchedule = createDayScheduleForDate(date);
                callback.onSuccess(daySchedule);
            } catch (Exception e) {
                Logger.e(TAG, "getScheduleForDate error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // get schedule for a day synchronously
    public DaySchedule getScheduleForDateSync(LocalDate date) {
        ensureScheduleLoaded(false);
        return createDayScheduleForDate(date);
    }

    // get subject details asynchronously
    public void getSubjectDetails(String subjectName, String rawLessonType, boolean forceRefresh, ScheduleCallback<SubjectDetails> callback) {
        new Thread(() -> {
            try {
                ensureScheduleLoaded(forceRefresh);
                SubjectDetails details = createSubjectDetails(subjectName, rawLessonType);
                callback.onSuccess(details);
            } catch (Exception e) {
                Logger.e(TAG, "getSubjectDetails error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // get subject details synchronously
    public SubjectDetails getSubjectDetailsSync(String subjectName, String rawLessonType) {
        ensureScheduleLoaded(false);
        return createSubjectDetails(subjectName, rawLessonType);
    }

    // get all unique courses in the semester asynchronously
    public void getAllCourses(boolean forceRefresh, ScheduleCallback<List<SubjectDetails>> callback) {
        new Thread(() -> {
            try {
                ensureScheduleLoaded(forceRefresh);
                List<SubjectDetails> courses = buildAllCourses();
                callback.onSuccess(courses);
            } catch (Exception e) {
                Logger.e(TAG, "getAllCourses error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    public List<SubjectDetails> buildAllCourses() {
        if (cachedAllLessons == null || cachedAllLessons.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<Lesson>> grouped = new LinkedHashMap<>();
        for (Lesson l : cachedAllLessons) {
            if (l.getSubjectName() == null || l.getSubjectName().trim().isEmpty()) continue;
            String key = l.getSubjectNoteKey();
            if (!grouped.containsKey(key)) {
                grouped.put(key, new ArrayList<>());
            }
            grouped.get(key).add(l);
        }
        List<SubjectDetails> result = new ArrayList<>();
        for (List<Lesson> lessons : grouped.values()) {
            if (lessons.isEmpty()) continue;
            Lesson first = lessons.get(0);
            SubjectDetails details = createSubjectDetails(first.getSubjectName(), first.getLessonType());
            result.add(details);
        }
        result.sort((a, b) -> a.getSubjectName().compareToIgnoreCase(b.getSubjectName()));
        return result;
    }

    private DaySchedule createDayScheduleForDate(LocalDate date) {
        if (cachedAllLessons == null || cachedAllLessons.isEmpty()) {
            return new DaySchedule(date, Collections.emptyList(), lastSyncTime);
        }

        List<Lesson> dayLessons = new ArrayList<>();
        for (Lesson l : cachedAllLessons) {
            if (l.getDate() != null && l.getDate().isEqual(date)) {
                dayLessons.add(l);
            }
        }
        return new DaySchedule(date, dayLessons, lastSyncTime);
    }

    private SubjectDetails createSubjectDetails(String subjectName, String rawLessonType) {
        if (cachedAllLessons == null || cachedAllLessons.isEmpty() || subjectName == null) {
            return new SubjectDetails(subjectName, rawLessonType, LessonType.fromString(rawLessonType), "", Collections.emptyList());
        }

        String cleanSearchName = subjectName.trim().toLowerCase();
        String cleanRawType = rawLessonType != null ? rawLessonType.trim().toLowerCase() : "";
        LessonType targetResolved = LessonType.fromString(!cleanRawType.isEmpty() ? cleanRawType : cleanSearchName);

        List<Lesson> matched = new ArrayList<>();
        String teacher = "";

        for (Lesson l : cachedAllLessons) {
            String itemSub = l.getSubjectName() != null ? l.getSubjectName().trim().toLowerCase() : "";
            boolean nameMatch = itemSub.equals(cleanSearchName) || itemSub.contains(cleanSearchName) || cleanSearchName.contains(itemSub);

            boolean typeMatch = true;
            if (!cleanRawType.isEmpty()) {
                String itemRawType = l.getLessonType() != null ? l.getLessonType().trim().toLowerCase() : "";
                typeMatch = itemRawType.equals(cleanRawType) 
                        || (l.getResolvedLessonType() == targetResolved)
                        || (l.getLessonType() == null && targetResolved == LessonType.SEMINARIUM && (l.getSubjectName().toLowerCase().contains("seminarium") || l.getSubjectName().toLowerCase().contains("dyplom")));
            } else if (targetResolved != LessonType.INNE) {
                typeMatch = (l.getResolvedLessonType() == targetResolved)
                        || (targetResolved == LessonType.SEMINARIUM && (l.getSubjectName().toLowerCase().contains("seminarium") || l.getSubjectName().toLowerCase().contains("dyplom")))
                        || (l.getResolvedLessonType() == LessonType.INNE);
            }

            if (nameMatch && typeMatch) {
                // Avoid duplicates if identical date and start time
                boolean duplicate = false;
                for (Lesson existing : matched) {
                    if (existing.getDate() != null && existing.getDate().isEqual(l.getDate())
                            && existing.getStartTime() != null && existing.getStartTime().equals(l.getStartTime())) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) continue;

                matched.add(l);
                if (teacher.isEmpty() && !l.getTeacherName().isEmpty()) {
                    teacher = l.getTeacherName();
                }
            }
        }

        // fallback: match by subject name only
        if (matched.isEmpty()) {
            for (Lesson l : cachedAllLessons) {
                String itemSub = l.getSubjectName() != null ? l.getSubjectName().trim().toLowerCase() : "";
                if (itemSub.equals(cleanSearchName) || itemSub.contains(cleanSearchName) || cleanSearchName.contains(itemSub)) {
                    boolean duplicate = false;
                    for (Lesson existing : matched) {
                        if (existing.getDate() != null && existing.getDate().isEqual(l.getDate())
                                && existing.getStartTime() != null && existing.getStartTime().equals(l.getStartTime())) {
                            duplicate = true;
                            break;
                        }
                    }
                    if (!duplicate) {
                        matched.add(l);
                        if (teacher.isEmpty() && !l.getTeacherName().isEmpty()) {
                            teacher = l.getTeacherName();
                        }
                    }
                }
            }
        }

        // sort by date and time
        Collections.sort(matched, (a, b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return -1;
            if (b.getDate() == null) return 1;
            int dateCmp = a.getDate().compareTo(b.getDate());
            if (dateCmp != 0) return dateCmp;
            if (a.getStartTime() == null && b.getStartTime() == null) return 0;
            if (a.getStartTime() == null) return -1;
            if (b.getStartTime() == null) return 1;
            return a.getStartTime().compareTo(b.getStartTime());
        });

        if (teacher.isEmpty() && !matched.isEmpty()) {
            teacher = matched.get(0).getTeacherName();
        }

        return new SubjectDetails(subjectName, rawLessonType, targetResolved, teacher, matched);
    }

    // download and parse schedule if cache is missing or stale
    public synchronized void ensureScheduleLoaded(boolean forceRefresh) {
        String currentConfig = buildConfigSignature();
        if (currentConfig == null) {
            throw new IllegalStateException("Student configuration is not completed yet");
        }

        boolean configChanged = !currentConfig.equals(lastConfigSignature);

        if (!forceRefresh && !configChanged && cachedAllLessons != null) {
            return; // memory cache is valid
        }

        File cacheFile = getLocalScheduleFile();
        if (forceRefresh || !cacheFile.exists() || cacheFile.length() == 0) {
            downloadScheduleFile(cacheFile);
            lastManualSyncTimestampMillis = System.currentTimeMillis();
        }

        if (cacheFile.exists() && cacheFile.length() > 0) {
            parseFile(cacheFile);
            lastConfigSignature = currentConfig;
            lastSyncTime = LocalDateTime.now();
        } else {
            throw new IllegalStateException("Failed to obtain schedule Excel file");
        }
    }

    private void parseFile(File file) {
        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degree = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode mode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);
        String specialty = PreferencesManager.getGlobalSpecialtyPref(appContext);
        String langGroup = PreferencesManager.getGlobalEnglishGroupPref(appContext);
        String seminarTeacher = PreferencesManager.getGlobalSeminarTeacherPref(appContext);
        String surname = PreferencesManager.getGlobalStudentSurnamePref(appContext);
        if (surname == null || surname.isEmpty()) {
            String fullName = PreferencesManager.getGlobalStudentNamePref(appContext);
            if (fullName != null && !fullName.isEmpty()) {
                surname = fullName;
            }
        }
        if (surname == null || surname.isEmpty()) {
            surname = PreferencesManager.getGlobalLoginPref(appContext);
        }

        ScheduleParser parser = ParserFactory.getParser(degree, mode);
        int semester = (year * 2) - 1; // default to odd semester

        try (InputStream in = new FileInputStream(file)) {
            Schedule schedule = parser.parse(in, major, semester, surname, specialty, langGroup, seminarTeacher);
            if (schedule != null && schedule.getLessons() != null) {
                cachedAllLessons = new ArrayList<>(schedule.getLessons());
                Logger.d(TAG, "Parsed " + cachedAllLessons.size() + " lessons successfully");
            } else {
                cachedAllLessons = new ArrayList<>();
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing schedule file: " + e.getMessage());
            cachedAllLessons = new ArrayList<>();
        }
    }

    private void downloadScheduleFile(File destFile) {
        OkHttpClient client = PUW.globalLogin(appContext);
        if (client == null) {
            throw new IllegalStateException("Failed to login to PUW for downloading schedule");
        }

        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);

        String fileUrl = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
        if (fileUrl == null) {
            throw new IllegalStateException("Could not resolve schedule download URL");
        }

        Request request = new Request.Builder().url(fileUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("Download HTTP error: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("Empty response body from schedule URL");
            }

            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            Logger.d(TAG, "Schedule downloaded successfully to " + destFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.e(TAG, "Error downloading schedule: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private File getLocalScheduleFile() {
        int year = PreferencesManager.getGlobalYearPref(appContext);
        return new File(appContext.getCacheDir(), CACHE_FILE_PREFIX + year + ".xlsx");
    }

    private String buildConfigSignature() {
        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degree = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode mode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);
        String spec = PreferencesManager.getGlobalSpecialtyPref(appContext);
        String lang = PreferencesManager.getGlobalEnglishGroupPref(appContext);

        if (major == null || degree == null || mode == null) {
            return null;
        }
        return major + "|" + degree.name() + "|" + mode.name() + "|" + year + "|" + spec + "|" + lang;
    }

    // clear in-memory and disk cache
    public synchronized void invalidateCache() {
        cachedAllLessons = null;
        lastConfigSignature = null;
        lastSyncTime = null;
        File cacheFile = getLocalScheduleFile();
        if (cacheFile.exists()) {
            cacheFile.delete();
        }
    }
}
