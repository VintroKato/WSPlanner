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
import com.vintro.wsplanner.utils.NetworkUtils;

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
    private boolean isLastLoadFromCacheFallback = false;

    public static class NoScheduleCacheException extends Exception {
        public NoScheduleCacheException(String message) {
            super(message);
        }
        public NoScheduleCacheException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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
        if (lastSyncTime == null) {
            File cacheFile = getLocalScheduleFile();
            lastSyncTime = loadLastSyncTime(cacheFile);
        }
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
                    Logger.d("ScheduleRepository.getScheduleFileForOpen", "Found cached schedule file: " + cacheFile.getAbsolutePath() + " (age: " + ageMinutes + " min)");
                    if (ageMinutes < STALE_THRESHOLD_MINUTES) {
                        shouldDownload = false;
                    }
                }

                if (shouldDownload) {
                    if (!NetworkUtils.isNetworkAvailable(appContext)) {
                        Logger.w("ScheduleRepository.getScheduleFileForOpen", "Device is offline, skipping download and checking local cache");
                        if (!cacheFile.exists() || cacheFile.length() == 0) {
                            Logger.e("ScheduleRepository.getScheduleFileForOpen", "Device is offline and no cached schedule file exists");
                            throw new NoScheduleCacheException("Device is offline and no cached schedule file exists");
                        }
                        Logger.i("ScheduleRepository.getScheduleFileForOpen", "Offline fallback: using cached schedule file");
                        callback.onFileReady(cacheFile);
                        return;
                    }
                    Logger.d("ScheduleRepository.getScheduleFileForOpen", "Cache missing or stale, attempting fresh download");
                    File tempFile = new File(cacheFile.getParentFile(), cacheFile.getName() + ".download_" + System.currentTimeMillis());
                    try {
                        downloadScheduleFile(tempFile);
                        if (tempFile.exists() && tempFile.length() > 0) {
                            if (cacheFile.exists()) {
                                cacheFile.delete();
                            }
                            if (tempFile.renameTo(cacheFile)) {
                                parseFile(cacheFile);
                                lastConfigSignature = buildConfigSignature();
                                lastSyncTime = LocalDateTime.now();
                                saveLastSyncTime(lastSyncTime);
                            }
                        }
                    } catch (Exception e) {
                        Logger.w("ScheduleRepository.getScheduleFileForOpen", "Download failed, checking cached file: " + e.getMessage());
                        if (!cacheFile.exists() || cacheFile.length() == 0) {
                            throw e;
                        }
                    } finally {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                    }
                } else {
                    Logger.d("ScheduleRepository.getScheduleFileForOpen", "Reusing cached schedule file");
                }

                callback.onFileReady(cacheFile);
            } catch (Exception e) {
                Logger.e("ScheduleRepository.getScheduleFileForOpen", "Error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // get schedule for a day asynchronously
    public void getScheduleForDate(LocalDate date, boolean forceRefresh, ScheduleCallback<DaySchedule> callback) {
        Logger.d("ScheduleRepository.getScheduleForDate", "Requesting schedule for date: " + date + " (forceRefresh=" + forceRefresh + ")");
        new Thread(() -> {
            try {
                ensureScheduleLoaded(forceRefresh);
                DaySchedule daySchedule = createDayScheduleForDate(date);
                Logger.d("ScheduleRepository.getScheduleForDate", "Returning " + daySchedule.getLessons().size() + " lesson(s) for " + date);
                callback.onSuccess(daySchedule);
            } catch (Exception e) {
                Logger.e("ScheduleRepository.getScheduleForDate", "Error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // get schedule for a day synchronously
    public DaySchedule getScheduleForDateSync(LocalDate date) {
        Logger.d("ScheduleRepository.getScheduleForDateSync", "Requesting sync schedule for date: " + date);
        try {
            ensureScheduleLoaded(false);
        } catch (Exception e) {
            Logger.w("ScheduleRepository.getScheduleForDateSync", "Could not load schedule: " + e.getMessage());
        }
        return createDayScheduleForDate(date);
    }

    // get subject details asynchronously
    public void getSubjectDetails(String subjectName, String rawLessonType, boolean forceRefresh, ScheduleCallback<SubjectDetails> callback) {
        Logger.d("ScheduleRepository.getSubjectDetails", "Requesting details for subject: '" + subjectName + "', type: '" + rawLessonType + "' (forceRefresh=" + forceRefresh + ")");
        new Thread(() -> {
            try {
                ensureScheduleLoaded(forceRefresh);
                SubjectDetails details = createSubjectDetails(subjectName, rawLessonType);
                Logger.d("ScheduleRepository.getSubjectDetails", "Resolved details with " + details.getTotalCount() + " lessons, teacher: '" + details.getTeacherName() + "'");
                callback.onSuccess(details);
            } catch (Exception e) {
                Logger.e("ScheduleRepository.getSubjectDetails", "Error: " + e.getMessage());
                callback.onError(e);
            }
        }).start();
    }

    // get subject details synchronously
    public SubjectDetails getSubjectDetailsSync(String subjectName, String rawLessonType) {
        Logger.d("ScheduleRepository.getSubjectDetailsSync", "Requesting sync details for subject: '" + subjectName + "'");
        try {
            ensureScheduleLoaded(false);
        } catch (Exception e) {
            Logger.w("ScheduleRepository.getSubjectDetailsSync", "Could not load details: " + e.getMessage());
        }
        return createSubjectDetails(subjectName, rawLessonType);
    }

    // get all unique courses in the semester asynchronously
    public void getAllCourses(boolean forceRefresh, ScheduleCallback<List<SubjectDetails>> callback) {
        Logger.d("ScheduleRepository.getAllCourses", "Requesting all courses list (forceRefresh=" + forceRefresh + ")");
        new Thread(() -> {
            try {
                ensureScheduleLoaded(forceRefresh);
                List<SubjectDetails> courses = buildAllCourses();
                Logger.d("ScheduleRepository.getAllCourses", "Returning " + courses.size() + " unique course(s)");
                callback.onSuccess(courses);
            } catch (Exception e) {
                Logger.e("ScheduleRepository.getAllCourses", "Error: " + e.getMessage());
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
        LocalDateTime syncTime = getLastSyncTime();
        if (cachedAllLessons == null || cachedAllLessons.isEmpty()) {
            DaySchedule ds = new DaySchedule(date, Collections.emptyList(), syncTime);
            ds.setFromCacheFallback(isLastLoadFromCacheFallback);
            return ds;
        }

        List<Lesson> dayLessons = new ArrayList<>();
        for (Lesson l : cachedAllLessons) {
            if (l.getDate() != null && l.getDate().isEqual(date)) {
                dayLessons.add(l);
            }
        }
        DaySchedule ds = new DaySchedule(date, dayLessons, syncTime);
        ds.setFromCacheFallback(isLastLoadFromCacheFallback);
        return ds;
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
            boolean nameMatch = itemSub.equals(cleanSearchName);

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
                if (teacher.isEmpty() && !l.getTeacherName().isEmpty() && !l.getTeacherName().equalsIgnoreCase("Unknown Teacher")) {
                    teacher = l.getTeacherName();
                }
            }
        }

        // fallback: match by exact subject name only (ignoring lesson type)
        if (matched.isEmpty()) {
            for (Lesson l : cachedAllLessons) {
                String itemSub = l.getSubjectName() != null ? l.getSubjectName().trim().toLowerCase() : "";
                if (itemSub.equals(cleanSearchName)) {
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
                        if (teacher.isEmpty() && !l.getTeacherName().isEmpty() && !l.getTeacherName().equalsIgnoreCase("Unknown Teacher")) {
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
    public synchronized void ensureScheduleLoaded(boolean forceRefresh) throws NoScheduleCacheException {
        String currentConfig = buildConfigSignature();
        if (currentConfig == null) {
            Logger.e("ScheduleRepository.ensureScheduleLoaded", "Cannot load schedule: Student configuration is not completed yet");
            throw new IllegalStateException("Student configuration is not completed yet");
        }

        boolean configChanged = !currentConfig.equals(lastConfigSignature);

        if (!forceRefresh && !configChanged && cachedAllLessons != null) {
            Logger.d("ScheduleRepository.ensureScheduleLoaded", "Memory cache is valid (" + cachedAllLessons.size() + " lessons, config unchanged), skipping reload");
            return;
        }

        Logger.d("ScheduleRepository.ensureScheduleLoaded", "Loading schedule (forceRefresh=" + forceRefresh + ", configChanged=" + configChanged + ", cachedLessons=" + (cachedAllLessons != null ? cachedAllLessons.size() : "null") + ")");

        File cacheFile = getLocalScheduleFile();
        boolean cacheExists = cacheFile.exists() && cacheFile.length() > 0;
        boolean downloadAttempted = false;
        boolean downloadSucceeded = false;
        Exception downloadError = null;

        if (forceRefresh || !cacheExists) {
            downloadAttempted = true;
            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                Logger.w("ScheduleRepository.ensureScheduleLoaded", "Device is offline, skipping schedule download");
                downloadSucceeded = false;
                downloadError = new IllegalStateException("Device is offline");
            } else {
                File tempFile = new File(cacheFile.getParentFile(), cacheFile.getName() + ".download_" + System.currentTimeMillis());
                try {
                    downloadScheduleFile(tempFile);
                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (cacheFile.exists()) {
                            cacheFile.delete();
                        }
                        if (tempFile.renameTo(cacheFile)) {
                            downloadSucceeded = true;
                            lastManualSyncTimestampMillis = System.currentTimeMillis();
                            lastSyncTime = LocalDateTime.now();
                            saveLastSyncTime(lastSyncTime);
                        }
                    }
                } catch (Exception e) {
                    downloadError = e;
                    Logger.w("ScheduleRepository.ensureScheduleLoaded", "Download failed: " + e.getMessage());
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }
        }

        if (cacheFile.exists() && cacheFile.length() > 0) {
            parseFile(cacheFile);
            lastConfigSignature = currentConfig;
            if (lastSyncTime == null) {
                lastSyncTime = loadLastSyncTime(cacheFile);
            }
            if (downloadAttempted && !downloadSucceeded) {
                isLastLoadFromCacheFallback = true;
                Logger.i("ScheduleRepository.ensureScheduleLoaded", "Offline fallback: successfully loaded schedule from local cache after download failure");
            } else {
                isLastLoadFromCacheFallback = false;
            }
        } else {
            Logger.e("ScheduleRepository.ensureScheduleLoaded", "Failed to obtain schedule: offline and no local cache file exists");
            if (downloadError != null) {
                throw new NoScheduleCacheException("No cached schedule and download failed: " + downloadError.getMessage(), downloadError);
            } else {
                throw new NoScheduleCacheException("Failed to obtain schedule Excel file");
            }
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

        Logger.d("ScheduleRepository.parseFile", "Parsing local schedule file: " + file.getAbsolutePath() + " (size: " + file.length() + " bytes) for student '" + surname + "', major '" + major + "', semester " + semester);

        try (InputStream in = new FileInputStream(file)) {
            Schedule schedule = parser.parse(in, major, semester, surname, specialty, langGroup, seminarTeacher);
            if (schedule != null && schedule.getLessons() != null) {
                cachedAllLessons = new ArrayList<>(schedule.getLessons());
                Logger.i("ScheduleRepository.parseFile", "Successfully cached " + cachedAllLessons.size() + " lessons from file: " + file.getName());
            } else {
                cachedAllLessons = new ArrayList<>();
                Logger.w("ScheduleRepository.parseFile", "Parser returned null or empty lessons list");
            }
        } catch (Exception e) {
            Logger.e("ScheduleRepository.parseFile", "Error parsing schedule file: " + e.getMessage());
            cachedAllLessons = new ArrayList<>();
        }
    }

    private void downloadScheduleFile(File destFile) {
        Logger.d("ScheduleRepository.downloadScheduleFile", "Initiating schedule file download to: " + destFile.getAbsolutePath());
        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            Logger.w("ScheduleRepository.downloadScheduleFile", "Download aborted: device is offline");
            throw new IllegalStateException("Device is offline");
        }
        OkHttpClient client = PUW.globalLogin(appContext);
        if (client == null) {
            Logger.e("ScheduleRepository.downloadScheduleFile", "Failed to login to PUW for downloading schedule");
            throw new IllegalStateException("Failed to login to PUW for downloading schedule");
        }

        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);

        String fileUrl = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
        if (fileUrl == null) {
            Logger.e("ScheduleRepository.downloadScheduleFile", "Could not resolve schedule download URL for " + major + ", " + degreeLevel + ", " + studyMode + ", year " + year);
            throw new IllegalStateException("Could not resolve schedule download URL");
        }

        Logger.d("ScheduleRepository.downloadScheduleFile", "Downloading schedule from URL: " + fileUrl);
        Request request = new Request.Builder().url(fileUrl).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Logger.e("ScheduleRepository.downloadScheduleFile", "Download HTTP error: " + response.code());
                throw new IllegalStateException("Download HTTP error: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                Logger.e("ScheduleRepository.downloadScheduleFile", "Empty response body from schedule URL");
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
            Logger.i("ScheduleRepository.downloadScheduleFile", "Schedule downloaded successfully to " + destFile.getAbsolutePath() + " (" + destFile.length() + " bytes)");
        } catch (Exception e) {
            Logger.e("ScheduleRepository.downloadScheduleFile", "Error downloading schedule: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private File getLocalScheduleFile() {
        File dir = new File(appContext.getCacheDir(), "plans");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        int year = PreferencesManager.getGlobalYearPref(appContext);
        return new File(dir, CACHE_FILE_PREFIX + year + ".xlsx");
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

    private void saveLastSyncTime(LocalDateTime time) {
        if (time != null) {
            appContext.getSharedPreferences("schedule_repo_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_sync_time", time.toString())
                    .apply();
        }
    }

    private LocalDateTime loadLastSyncTime(File cacheFile) {
        String saved = appContext.getSharedPreferences("schedule_repo_prefs", Context.MODE_PRIVATE)
                .getString("last_sync_time", null);
        if (saved != null) {
            try {
                return LocalDateTime.parse(saved);
            } catch (Exception ignored) {}
        }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            try {
                return LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(cacheFile.lastModified()),
                        java.time.ZoneId.systemDefault()
                );
            } catch (Exception ignored) {}
        }
        return null;
    }

    // clear in-memory and disk cache
    public synchronized void invalidateCache() {
        Logger.d("ScheduleRepository.invalidateCache", "Invalidating memory and disk schedule cache");
        cachedAllLessons = null;
        lastConfigSignature = null;
        lastSyncTime = null;
        isLastLoadFromCacheFallback = false;
        appContext.getSharedPreferences("schedule_repo_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove("last_sync_time")
                .apply();
        File cacheFile = getLocalScheduleFile();
        if (cacheFile.exists()) {
            boolean deleted = cacheFile.delete();
            Logger.d("ScheduleRepository.invalidateCache", "Cache file deletion result: " + deleted);
        }
    }
}
