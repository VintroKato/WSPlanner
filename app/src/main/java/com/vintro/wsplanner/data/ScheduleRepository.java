package com.vintro.wsplanner.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

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
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// handles schedule downloading, offline caching, conditional http checks, and parsing
public class ScheduleRepository {
    private static final String TAG = "ScheduleRepository";
    private static final String CACHE_FILE_PREFIX = "cached_schedule_";
    private static final String REPO_PREFS_NAME = "schedule_repo_prefs";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_CACHED_URL = "cached_schedule_url";
    private static final String KEY_CACHED_URL_CONFIG = "cached_schedule_url_config";
    private static final String KEY_CACHED_URL_TIME = "cached_schedule_url_time";
    private static final String KEY_CACHED_ETAG = "cached_schedule_etag";
    private static final String KEY_CACHED_LAST_MODIFIED = "cached_schedule_last_modified";

    private static final long COOLDOWN_SECONDS = 30;
    private static final long BG_COOLDOWN_SECONDS = 300;
    private static final long STALE_THRESHOLD_MINUTES = 30;
    private static final long URL_CACHE_TTL_MS = 24L * 60L * 60L * 1000L;
    public static final long AUTO_REFRESH_INTERVAL_HOURS = 6;

    private static ScheduleRepository instance;

    private final Context appContext;
    private final Object cacheLock = new Object();
    private final Object downloadLock = new Object();
    private final Object cooldownLock = new Object();
    private final AtomicBoolean isDownloadingInProgress = new AtomicBoolean(false);

    private List<Lesson> cachedAllLessons;
    private Map<LocalDate, List<Lesson>> cachedLessonsByDate;
    private LocalDateTime lastSyncTime;
    private String lastConfigSignature;
    private String lastFileConfigSignature;
    private boolean isLastLoadFromCacheFallback = false;
    private volatile long dataVersion = 0;
    private long lastManualSyncTimestampMillis = 0;
    private long lastBackgroundSyncTimestampMillis = 0;

    private enum DownloadOutcome {
        DOWNLOADED,
        NOT_MODIFIED
    }

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

    // monotonic counter incremented whenever cached schedule data changes
    public long getDataVersion() {
        return dataVersion;
    }

    public LocalDateTime getLastSyncTime() {
        synchronized (cacheLock) {
            if (lastSyncTime == null) {
                File cacheFile = getLocalScheduleFile();
                lastSyncTime = loadLastSyncTime(cacheFile);
            }
            return lastSyncTime;
        }
    }

    // check if cached schedule is older than auto refresh interval or was never synced
    public boolean isScheduleOutdated() {
        LocalDateTime syncTime = getLastSyncTime();
        if (syncTime == null) return true;
        try {
            return java.time.Duration.between(syncTime, LocalDateTime.now()).toHours() >= AUTO_REFRESH_INTERVAL_HOURS;
        } catch (Exception e) {
            return true;
        }
    }

    // remaining user-initiated manual refresh cooldown in seconds
    public long getCooldownRemainingSeconds() {
        synchronized (cooldownLock) {
            long elapsedSeconds = (System.currentTimeMillis() - lastManualSyncTimestampMillis) / 1000;
            if (elapsedSeconds < COOLDOWN_SECONDS) {
                return COOLDOWN_SECONDS - elapsedSeconds;
            }
            return 0;
        }
    }

    private boolean isBackgroundCooldownActive() {
        synchronized (cooldownLock) {
            long elapsedSeconds = (System.currentTimeMillis() - lastBackgroundSyncTimestampMillis) / 1000;
            return elapsedSeconds < BG_COOLDOWN_SECONDS;
        }
    }

    private void recordSyncAttempt(boolean isManualUserAction) {
        synchronized (cooldownLock) {
            long now = System.currentTimeMillis();
            if (isManualUserAction) {
                lastManualSyncTimestampMillis = now;
            } else {
                lastBackgroundSyncTimestampMillis = now;
            }
        }
    }

    // check if student profile configuration is completed
    public boolean isConfigurationComplete() {
        return buildFileConfigSignature() != null;
    }

    // one-time migration moving legacy xlsx schedule files from cacheDir to filesDir
    public static void migrateLegacyScheduleCache(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        File oldDir = new File(appCtx.getCacheDir(), "plans");
        if (!oldDir.exists() || !oldDir.isDirectory()) {
            return;
        }
        File[] legacyFiles = oldDir.listFiles((dir, name) -> name.startsWith(CACHE_FILE_PREFIX) && name.endsWith(".xlsx"));
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }

        ScheduleRepository repo = getInstance(appCtx);
        File targetFile = repo.getLocalScheduleFile();
        File newestValid = null;
        for (File candidate : legacyFiles) {
            if (candidate != null && candidate.isFile() && isValidZipFile(candidate)) {
                if (newestValid == null || candidate.lastModified() > newestValid.lastModified()) {
                    newestValid = candidate;
                }
            }
        }

        if (newestValid != null && (!targetFile.exists() || targetFile.length() == 0)) {
            try {
                Files.copy(newestValid.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                targetFile.setLastModified(newestValid.lastModified());
                Logger.i(TAG + ".migrateLegacyScheduleCache", "Migrated legacy schedule file to internal storage: " + targetFile.getName());
            } catch (Exception e) {
                Logger.w(TAG + ".migrateLegacyScheduleCache", "Failed copying legacy schedule file: " + Logger.formatException(e));
            }
        }

        for (File candidate : legacyFiles) {
            if (candidate != null && candidate.exists()) {
                candidate.delete();
            }
        }
    }

    // seed the local schedule file from an already downloaded xlsx (e.g. during onboarding)
    public boolean seedScheduleFileFromDownload(File downloadedFile) {
        if (downloadedFile == null || !isValidZipFile(downloadedFile)) {
            Logger.w(TAG + ".seedScheduleFileFromDownload", "Skipped seeding invalid or missing xlsx file");
            return false;
        }
        String fileConfig = buildFileConfigSignature();
        if (fileConfig == null) {
            return false;
        }
        File targetFile = getLocalScheduleFile();
        File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".seed_" + System.currentTimeMillis());
        try {
            Files.copy(downloadedFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            boolean replaced;
            synchronized (cacheLock) {
                replaced = replaceFileAtomically(tempFile, targetFile);
                if (replaced) {
                    LocalDateTime now = LocalDateTime.now();
                    lastSyncTime = now;
                    lastFileConfigSignature = fileConfig;
                    lastConfigSignature = null;
                    cachedAllLessons = null;
                    cachedLessonsByDate = null;
                    saveLastSyncTime(now);
                }
            }
            if (replaced) {
                cleanupOrphanScheduleFiles(targetFile);
                Logger.i(TAG + ".seedScheduleFileFromDownload", "Seeded schedule repository cache without duplicate download: " + targetFile.getName());
            }
            return replaced;
        } catch (Exception e) {
            Logger.w(TAG + ".seedScheduleFileFromDownload", "Failed seeding schedule file: " + Logger.formatException(e));
            return false;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // get schedule excel file, downloading if missing or stale
    public void getScheduleFileForOpen(FileReadyCallback callback) {
        AppExecutors.runIo(() -> {
            try {
                if (!isConfigurationComplete()) {
                    Logger.d(TAG + ".getScheduleFileForOpen", "Student configuration is not completed yet");
                    callback.onError(new NoScheduleCacheException("Student configuration is not completed yet"));
                    return;
                }
                File cacheFile = getLocalScheduleFile();
                boolean hasValidLocal = cacheFile.exists() && isValidZipFile(cacheFile);
                boolean shouldDownload = !hasValidLocal;

                if (hasValidLocal) {
                    long ageMinutes = (System.currentTimeMillis() - cacheFile.lastModified()) / (60 * 1000);
                    Logger.d(TAG + ".getScheduleFileForOpen", "Found cached schedule file: " + cacheFile.getName() + " (age: " + ageMinutes + " min)");
                    if (ageMinutes >= STALE_THRESHOLD_MINUTES) {
                        shouldDownload = true;
                    }
                }

                if (shouldDownload) {
                    if (!NetworkUtils.isNetworkAvailable(appContext)) {
                        Logger.w(TAG + ".getScheduleFileForOpen", "Device is offline, checking local cache");
                        if (!hasValidLocal) {
                            throw new NoScheduleCacheException("Device is offline and no cached schedule file exists");
                        }
                        Logger.i(TAG + ".getScheduleFileForOpen", "Offline fallback: using cached schedule file");
                        callback.onFileReady(cacheFile);
                        return;
                    }

                    synchronized (downloadLock) {
                        File tempFile = new File(cacheFile.getParentFile(), cacheFile.getName() + ".download_" + System.currentTimeMillis());
                        try {
                            DownloadOutcome outcome = downloadScheduleFile(tempFile, hasValidLocal);
                            LocalDateTime newSyncTime = LocalDateTime.now();
                            if (outcome == DownloadOutcome.NOT_MODIFIED) {
                                cacheFile.setLastModified(System.currentTimeMillis());
                                synchronized (cacheLock) {
                                    lastSyncTime = newSyncTime;
                                    saveLastSyncTime(newSyncTime);
                                }
                            } else if (tempFile.exists() && isValidZipFile(tempFile)) {
                                String currentConfig = buildConfigSignature();
                                String fileConfig = buildFileConfigSignature();
                                boolean replaced;
                                synchronized (cacheLock) {
                                    replaced = replaceFileAtomically(tempFile, cacheFile);
                                    if (replaced) {
                                        lastSyncTime = newSyncTime;
                                        lastConfigSignature = currentConfig;
                                        lastFileConfigSignature = fileConfig;
                                        saveLastSyncTime(newSyncTime);
                                    }
                                }
                                if (replaced) {
                                    cleanupOrphanScheduleFiles(cacheFile);
                                    parseFile(cacheFile);
                                }
                            }
                        } catch (Exception e) {
                            Logger.w(TAG + ".getScheduleFileForOpen", "Download failed, checking cached file: " + Logger.formatException(e));
                            if (!cacheFile.exists() || !isValidZipFile(cacheFile)) {
                                throw e;
                            }
                        } finally {
                            if (tempFile.exists()) {
                                tempFile.delete();
                            }
                        }
                    }
                } else {
                    Logger.d(TAG + ".getScheduleFileForOpen", "Reusing fresh cached schedule file");
                }

                callback.onFileReady(cacheFile);
            } catch (Exception e) {
                Logger.e(TAG + ".getScheduleFileForOpen", "Error getting schedule file: " + Logger.formatException(e));
                callback.onError(e);
            }
        });
    }

    // get schedule for a day asynchronously
    public void getScheduleForDate(LocalDate date, boolean forceRefresh, ScheduleCallback<DaySchedule> callback) {
        Logger.d(TAG + ".getScheduleForDate", "Requesting schedule for date: " + date + " (forceRefresh=" + forceRefresh + ")");
        AppExecutors.runIo(() -> {
            try {
                if (!isConfigurationComplete()) {
                    Logger.d(TAG + ".getScheduleForDate", "Student configuration is not completed yet, returning empty schedule");
                    callback.onSuccess(createDayScheduleForDate(date));
                    return;
                }
                ensureScheduleLoaded(forceRefresh, forceRefresh);
                DaySchedule daySchedule = createDayScheduleForDate(date);
                Logger.d(TAG + ".getScheduleForDate", "Returning " + daySchedule.getLessons().size() + " lesson(s) for " + date);
                callback.onSuccess(daySchedule);
            } catch (Exception e) {
                Logger.e(TAG + ".getScheduleForDate", "Error getting schedule for date: " + Logger.formatException(e));
                callback.onError(e);
            }
        });
    }

    // get schedule for a day synchronously strictly from local memory or disk without spawning network threads
    public DaySchedule getScheduleForDateSync(LocalDate date) {
        Logger.d(TAG + ".getScheduleForDateSync", "Requesting sync schedule for date: " + date);
        if (!isConfigurationComplete()) {
            Logger.d(TAG + ".getScheduleForDateSync", "Student configuration is not completed yet, returning empty schedule");
            return createDayScheduleForDate(date);
        }

        ensureLocalCacheLoadedOnly();
        return createDayScheduleForDate(date);
    }

    // check if in-memory lesson cache is loaded without re-reading preferences on every swipe
    public boolean isMemoryCacheLoaded() {
        synchronized (cacheLock) {
            return cachedAllLessons != null && lastConfigSignature != null;
        }
    }

    // get day schedule directly from in-memory cache without disk or network access
    public DaySchedule getScheduleForDateFromMemory(LocalDate date) {
        synchronized (cacheLock) {
            return createDayScheduleForDateFromMemory(date);
        }
    }

    // get cached lesson count for a specific date without disk or network access
    public int getLessonCountForDateFromMemory(LocalDate date) {
        if (date == null) return 0;
        synchronized (cacheLock) {
            if (cachedLessonsByDate == null || cachedLessonsByDate.isEmpty()) {
                return 0;
            }
            List<Lesson> lessons = cachedLessonsByDate.get(date);
            return lessons != null ? lessons.size() : 0;
        }
    }

    // check if cached schedule contains any lessons for a specific date
    public boolean hasLessonsOnDateFromMemory(LocalDate date) {
        return getLessonCountForDateFromMemory(date) > 0;
    }

    // find the earliest cached date strictly after afterDate that has at least one lesson
    @Nullable
    public LocalDate findNextClassDayAfterFromMemory(LocalDate afterDate) {
        if (afterDate == null) return null;
        synchronized (cacheLock) {
            if (cachedLessonsByDate == null || cachedLessonsByDate.isEmpty()) {
                return null;
            }
            LocalDate earliest = null;
            for (Map.Entry<LocalDate, List<Lesson>> entry : cachedLessonsByDate.entrySet()) {
                LocalDate date = entry.getKey();
                List<Lesson> lessons = entry.getValue();
                if (date != null && lessons != null && !lessons.isEmpty() && date.isAfter(afterDate)) {
                    if (earliest == null || date.isBefore(earliest)) {
                        earliest = date;
                    }
                }
            }
            return earliest;
        }
    }

    // find the earliest upcoming lesson strictly after the given timestamp across cached dates
    @Nullable
    public Lesson findNextLessonAfter(LocalDateTime afterDateTime) {
        if (afterDateTime == null || !isConfigurationComplete()) {
            return null;
        }
        ensureLocalCacheLoadedOnly();
        synchronized (cacheLock) {
            if (cachedAllLessons == null || cachedAllLessons.isEmpty()) {
                return null;
            }
            Lesson earliest = null;
            LocalDateTime earliestStart = null;
            for (Lesson lesson : cachedAllLessons) {
                if (lesson == null || lesson.getDate() == null || lesson.getStartTime() == null) {
                    continue;
                }
                LocalDateTime start = LocalDateTime.of(lesson.getDate(), lesson.getStartTime());
                if (start.isAfter(afterDateTime)) {
                    if (earliestStart == null || start.isBefore(earliestStart)) {
                        earliestStart = start;
                        earliest = lesson;
                    }
                }
            }
            return earliest;
        }
    }

    // load schedule from disk cache if in-memory cache is missing or invalidated, without network operations
    public void ensureLocalCacheLoadedOnly() {
        String currentConfig = buildConfigSignature();
        String currentFileConfig = buildFileConfigSignature();
        if (currentConfig == null || currentFileConfig == null) return;

        synchronized (cacheLock) {
            boolean configChanged = !currentConfig.equals(lastConfigSignature)
                    || !currentFileConfig.equals(lastFileConfigSignature);
            if (!configChanged && cachedAllLessons != null) {
                return;
            }
            File cacheFile = getLocalScheduleFile();
            if (cacheFile.exists() && isValidZipFile(cacheFile)) {
                parseFile(cacheFile);
                lastConfigSignature = currentConfig;
                lastFileConfigSignature = currentFileConfig;
                if (lastSyncTime == null) {
                    lastSyncTime = loadLastSyncTime(cacheFile);
                }
            }
        }
    }

    // trigger non-blocking background synchronization without affecting user manual cooldown
    public void triggerBackgroundSync() {
        if (!isConfigurationComplete() || !NetworkUtils.isNetworkAvailable(appContext) || isBackgroundCooldownActive()) {
            return;
        }
        if (!isDownloadingInProgress.compareAndSet(false, true)) {
            Logger.d(TAG + ".triggerBackgroundSync", "Background download already in progress, skipping duplicate");
            return;
        }

        Logger.i(TAG + ".triggerBackgroundSync", "Starting asynchronous background schedule sync");
        AppExecutors.runIo(() -> {
            try {
                ensureScheduleLoaded(true, false);
            } catch (Exception e) {
                Logger.w(TAG + ".triggerBackgroundSync", "Background sync completed with note: " + Logger.formatException(e));
            } finally {
                isDownloadingInProgress.set(false);
            }
        });
    }

    // get subject details asynchronously
    public void getSubjectDetails(String subjectName, String rawLessonType, boolean forceRefresh, ScheduleCallback<SubjectDetails> callback) {
        Logger.d(TAG + ".getSubjectDetails", "Requesting details for subject: '" + subjectName + "', type: '" + rawLessonType + "' (forceRefresh=" + forceRefresh + ")");
        AppExecutors.runIo(() -> {
            try {
                if (!isConfigurationComplete()) {
                    Logger.d(TAG + ".getSubjectDetails", "Student configuration is not completed yet");
                    callback.onError(new NoScheduleCacheException("Student configuration is not completed yet"));
                    return;
                }
                ensureScheduleLoaded(forceRefresh, forceRefresh);
                SubjectDetails details = createSubjectDetails(subjectName, rawLessonType);
                Logger.d(TAG + ".getSubjectDetails", "Resolved details with " + details.getTotalCount() + " lessons, teacher: '" + details.getTeacherName() + "'");
                callback.onSuccess(details);
            } catch (Exception e) {
                Logger.e(TAG + ".getSubjectDetails", "Error: " + Logger.formatException(e), e);
                callback.onError(e);
            }
        });
    }

    // get subject details synchronously (strictly non-blocking)
    public SubjectDetails getSubjectDetailsSync(String subjectName, String rawLessonType) {
        Logger.d(TAG + ".getSubjectDetailsSync", "Requesting sync details for subject: '" + subjectName + "'");
        if (!isConfigurationComplete()) {
            return createSubjectDetails(subjectName, rawLessonType);
        }
        try {
            ensureLocalCacheLoadedOnly();
        } catch (Exception e) {
            Logger.w(TAG + ".getSubjectDetailsSync", "Could not load details: " + Logger.formatException(e));
        }
        return createSubjectDetails(subjectName, rawLessonType);
    }

    // get all unique courses in the semester asynchronously
    public void getAllCourses(boolean forceRefresh, ScheduleCallback<List<SubjectDetails>> callback) {
        Logger.d(TAG + ".getAllCourses", "Requesting all courses list (forceRefresh=" + forceRefresh + ")");
        AppExecutors.runIo(() -> {
            try {
                if (!isConfigurationComplete()) {
                    Logger.d(TAG + ".getAllCourses", "Student configuration is not completed yet");
                    callback.onError(new NoScheduleCacheException("Student configuration is not completed yet"));
                    return;
                }
                ensureScheduleLoaded(forceRefresh, forceRefresh);
                List<SubjectDetails> courses = buildAllCourses();
                Logger.d(TAG + ".getAllCourses", "Returning " + courses.size() + " unique course(s)");
                callback.onSuccess(courses);
            } catch (Exception e) {
                Logger.e(TAG + ".getAllCourses", "Error getting all courses: " + Logger.formatException(e));
                callback.onError(e);
            }
        });
    }

    public List<SubjectDetails> buildAllCourses() {
        List<Lesson> snapshot;
        synchronized (cacheLock) {
            if (cachedAllLessons == null || cachedAllLessons.isEmpty()) {
                return new ArrayList<>();
            }
            snapshot = new ArrayList<>(cachedAllLessons);
        }

        Map<String, List<Lesson>> grouped = new LinkedHashMap<>();
        for (Lesson l : snapshot) {
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
            SubjectDetails details = createSubjectDetailsFromList(snapshot, first.getSubjectName(), first.getLessonType());
            result.add(details);
        }
        result.sort((a, b) -> a.getSubjectName().compareToIgnoreCase(b.getSubjectName()));
        return result;
    }

    private void rebuildLessonsByDateIndex(List<Lesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            cachedLessonsByDate = Collections.emptyMap();
            return;
        }
        Map<LocalDate, List<Lesson>> grouped = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson == null || lesson.getDate() == null) continue;
            grouped.computeIfAbsent(lesson.getDate(), d -> new ArrayList<>()).add(lesson);
        }
        for (Map.Entry<LocalDate, List<Lesson>> entry : grouped.entrySet()) {
            List<Lesson> dayList = entry.getValue();
            if (dayList.size() > 1) {
                dayList.sort(DaySchedule.START_TIME_COMPARATOR);
            }
            entry.setValue(Collections.unmodifiableList(dayList));
        }
        cachedLessonsByDate = Collections.unmodifiableMap(grouped);
    }

    private DaySchedule buildDayScheduleFromIndex(LocalDate date, LocalDateTime syncTime) {
        if (date == null || cachedLessonsByDate == null || cachedLessonsByDate.isEmpty()) {
            DaySchedule ds = new DaySchedule(date, Collections.emptyList(), syncTime, true);
            ds.setFromCacheFallback(isLastLoadFromCacheFallback);
            return ds;
        }
        List<Lesson> dayLessons = cachedLessonsByDate.get(date);
        DaySchedule ds = new DaySchedule(
                date,
                dayLessons != null ? dayLessons : Collections.emptyList(),
                syncTime,
                true
        );
        ds.setFromCacheFallback(isLastLoadFromCacheFallback);
        return ds;
    }

    private DaySchedule createDayScheduleForDateFromMemory(LocalDate date) {
        return buildDayScheduleFromIndex(date, lastSyncTime);
    }

    private DaySchedule createDayScheduleForDate(LocalDate date) {
        LocalDateTime syncTime = getLastSyncTime();
        synchronized (cacheLock) {
            return buildDayScheduleFromIndex(date, syncTime);
        }
    }

    private SubjectDetails createSubjectDetails(String subjectName, String rawLessonType) {
        List<Lesson> snapshot;
        synchronized (cacheLock) {
            snapshot = cachedAllLessons != null ? new ArrayList<>(cachedAllLessons) : Collections.emptyList();
        }
        return createSubjectDetailsFromList(snapshot, subjectName, rawLessonType);
    }

    private SubjectDetails createSubjectDetailsFromList(List<Lesson> lessons, String subjectName, String rawLessonType) {
        if (lessons == null || lessons.isEmpty() || subjectName == null) {
            LessonType fallbackType = (rawLessonType != null && !rawLessonType.trim().isEmpty())
                    ? LessonType.fromString(rawLessonType)
                    : LessonType.fromSubjectName(subjectName);
            return new SubjectDetails(subjectName, rawLessonType, fallbackType, "", Collections.emptyList());
        }

        String cleanSearchName = subjectName.trim().toLowerCase(Locale.ROOT);
        String cleanRawType = rawLessonType != null ? rawLessonType.trim().toLowerCase(Locale.ROOT) : "";
        LessonType targetResolved = !cleanRawType.isEmpty()
                ? LessonType.fromString(cleanRawType)
                : LessonType.fromSubjectName(cleanSearchName);

        List<Lesson> matched = new ArrayList<>();
        String teacher = "";

        for (Lesson l : lessons) {
            String itemSub = l.getSubjectName() != null ? l.getSubjectName().trim().toLowerCase(Locale.ROOT) : "";
            boolean nameMatch = itemSub.equals(cleanSearchName);

            boolean typeMatch = true;
            if (!cleanRawType.isEmpty()) {
                String itemRawType = l.getLessonType() != null ? l.getLessonType().trim().toLowerCase(Locale.ROOT) : "";
                typeMatch = itemRawType.equals(cleanRawType)
                        || (l.getResolvedLessonType() == targetResolved)
                        || (l.getLessonType() == null && targetResolved == LessonType.SEMINARIUM
                        && (itemSub.contains("seminarium") || itemSub.contains("dyplom")));
            } else if (targetResolved != LessonType.INNE) {
                typeMatch = (l.getResolvedLessonType() == targetResolved)
                        || (targetResolved == LessonType.SEMINARIUM && (itemSub.contains("seminarium") || itemSub.contains("dyplom")))
                        || (l.getResolvedLessonType() == LessonType.INNE);
            }

            if (nameMatch && typeMatch) {
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

        // fallback: match by exact subject name only
        if (matched.isEmpty()) {
            for (Lesson l : lessons) {
                String itemSub = l.getSubjectName() != null ? l.getSubjectName().trim().toLowerCase(Locale.ROOT) : "";
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

    public void ensureScheduleLoaded(boolean forceRefresh) throws NoScheduleCacheException {
        ensureScheduleLoaded(forceRefresh, forceRefresh);
    }

    // download and parse schedule if cache is missing, file config changed, or stale
    public void ensureScheduleLoaded(boolean forceRefresh, boolean isManualUserAction) throws NoScheduleCacheException {
        String currentConfig = buildConfigSignature();
        String currentFileConfig = buildFileConfigSignature();
        if (currentConfig == null || currentFileConfig == null) {
            Logger.d(TAG + ".ensureScheduleLoaded", "Cannot load schedule: Student configuration is not completed yet");
            throw new NoScheduleCacheException("Student configuration is not completed yet");
        }

        boolean configChanged;
        boolean fileConfigChanged;
        synchronized (cacheLock) {
            configChanged = !currentConfig.equals(lastConfigSignature);
            fileConfigChanged = lastFileConfigSignature != null && !currentFileConfig.equals(lastFileConfigSignature);
        }

        boolean isOutdated = isScheduleOutdated();
        File cacheFile = getLocalScheduleFile();
        boolean cacheExists = cacheFile.exists() && isValidZipFile(cacheFile);

        boolean cooldownBlocksAuto = isManualUserAction
                ? getCooldownRemainingSeconds() > 0
                : isBackgroundCooldownActive();

        boolean shouldDownload = forceRefresh
                || !cacheExists
                || fileConfigChanged
                || (isOutdated && NetworkUtils.isNetworkAvailable(appContext) && !cooldownBlocksAuto);

        synchronized (cacheLock) {
            if (!forceRefresh && !configChanged && !fileConfigChanged && cachedAllLessons != null && !shouldDownload) {
                Logger.d(TAG + ".ensureScheduleLoaded", "Memory cache is valid (" + cachedAllLessons.size() + " lessons, config unchanged), skipping reload");
                return;
            }
        }

        Logger.d(TAG + ".ensureScheduleLoaded", "Loading schedule (forceRefresh=" + forceRefresh
                + ", isManual=" + isManualUserAction + ", configChanged=" + configChanged
                + ", fileConfigChanged=" + fileConfigChanged + ", isOutdated=" + isOutdated + ")");

        boolean downloadAttempted = false;
        boolean downloadSucceeded = false;
        boolean fileContentUpdated = false;
        Exception downloadError = null;

        if (shouldDownload) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Logger.w(TAG + ".ensureScheduleLoaded", "Download requested on Main Thread; deferring to background");
                triggerBackgroundSync();
                ensureLocalCacheLoadedOnly();
                return;
            }

            downloadAttempted = true;
            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                Logger.w(TAG + ".ensureScheduleLoaded", "Device is offline, skipping schedule download");
                downloadError = new IllegalStateException("Device is offline");
            } else {
                synchronized (downloadLock) {
                    File tempFile = new File(cacheFile.getParentFile(), cacheFile.getName() + ".download_" + System.currentTimeMillis());
                    try {
                        boolean allowConditional = cacheExists && !fileConfigChanged;
                        DownloadOutcome outcome = downloadScheduleFile(tempFile, allowConditional);
                        LocalDateTime newSyncTime = LocalDateTime.now();

                        if (outcome == DownloadOutcome.NOT_MODIFIED) {
                            downloadSucceeded = true;
                            recordSyncAttempt(isManualUserAction);
                            cacheFile.setLastModified(System.currentTimeMillis());
                            synchronized (cacheLock) {
                                lastSyncTime = newSyncTime;
                                saveLastSyncTime(newSyncTime);
                            }
                            Logger.i(TAG + ".ensureScheduleLoaded", "Schedule unchanged on server (304 Not Modified)");
                        } else if (tempFile.exists() && isValidZipFile(tempFile)) {
                            boolean replaced;
                            synchronized (cacheLock) {
                                replaced = replaceFileAtomically(tempFile, cacheFile);
                                if (replaced) {
                                    lastSyncTime = newSyncTime;
                                    saveLastSyncTime(newSyncTime);
                                }
                            }
                            if (replaced) {
                                downloadSucceeded = true;
                                fileContentUpdated = true;
                                recordSyncAttempt(isManualUserAction);
                                cleanupOrphanScheduleFiles(cacheFile);
                            }
                        } else {
                            throw new IllegalStateException("Downloaded file is not a valid XLSX archive");
                        }
                    } catch (Exception e) {
                        downloadError = e;
                        recordSyncAttempt(isManualUserAction);
                        Logger.w(TAG + ".ensureScheduleLoaded", "Download failed: " + Logger.formatException(e), e);
                    } finally {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                    }
                }
            }
        }

        if (cacheFile.exists() && isValidZipFile(cacheFile)) {
            boolean needsParse;
            synchronized (cacheLock) {
                needsParse = fileContentUpdated || configChanged || fileConfigChanged || cachedAllLessons == null;
            }
            if (needsParse) {
                parseFile(cacheFile);
            }
            synchronized (cacheLock) {
                lastConfigSignature = currentConfig;
                lastFileConfigSignature = currentFileConfig;
                if (lastSyncTime == null) {
                    lastSyncTime = loadLastSyncTime(cacheFile);
                }
                if (downloadAttempted && !downloadSucceeded) {
                    isLastLoadFromCacheFallback = true;
                    Logger.i(TAG + ".ensureScheduleLoaded", "Offline fallback: loaded schedule from local cache after download failure");
                } else {
                    isLastLoadFromCacheFallback = false;
                }
            }

            if (downloadSucceeded) {
                try {
                    WidgetUpdateManager.updateAllWidgets(appContext);
                    WidgetUpdateManager.scheduleNextAlarm(appContext);
                    Logger.d(TAG + ".ensureScheduleLoaded", "Updated widgets after successful schedule sync");
                } catch (Exception e) {
                    Logger.w(TAG + ".ensureScheduleLoaded", "Error updating widgets: " + Logger.formatException(e));
                }
            }
        } else {
            Logger.e(TAG + ".ensureScheduleLoaded", "Failed to obtain schedule: offline and no local cache file exists");
            if (downloadError != null) {
                throw new NoScheduleCacheException("No cached schedule and download failed: " + Logger.formatException(downloadError), downloadError);
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
        int semester = (year * 2) - 1;

        Logger.d(TAG + ".parseFile", "Parsing local schedule file: " + file.getName() + " (size: " + file.length() + " bytes) for student '" + Logger.maskSurname(surname) + "', major '" + major + "', semester " + semester);

        try (InputStream in = new FileInputStream(file)) {
            Schedule schedule = parser.parse(in, major, semester, surname, specialty, langGroup, seminarTeacher);
            List<Lesson> parsedSnapshot;
            synchronized (cacheLock) {
                if (schedule != null && schedule.getLessons() != null) {
                    cachedAllLessons = new ArrayList<>(schedule.getLessons());
                    rebuildLessonsByDateIndex(cachedAllLessons);
                    Logger.i(TAG + ".parseFile", "Successfully cached " + cachedAllLessons.size() + " lessons from file: " + file.getName());
                } else {
                    cachedAllLessons = new ArrayList<>();
                    cachedLessonsByDate = Collections.emptyMap();
                    Logger.w(TAG + ".parseFile", "Parser returned null or empty lessons list");
                }
                dataVersion++;
                parsedSnapshot = cachedAllLessons;
            }
            if (!PreferencesManager.hasVisibleWeekdaysPref(appContext)) {
                PreferencesManager.initRecommendedVisibleDays(appContext, parsedSnapshot, mode);
            }
        } catch (Exception e) {
            Logger.e(TAG + ".parseFile", "Error parsing schedule file: " + Logger.formatException(e), e);
            synchronized (cacheLock) {
                cachedAllLessons = new ArrayList<>();
                cachedLessonsByDate = Collections.emptyMap();
                dataVersion++;
            }
        }
    }

    private DownloadOutcome downloadScheduleFile(File destFile, boolean allowConditionalGet) {
        Logger.d(TAG + ".downloadScheduleFile", "Initiating schedule file download to: " + destFile.getName());
        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            Logger.w(TAG + ".downloadScheduleFile", "Download aborted: device is offline");
            throw new IllegalStateException("Device is offline");
        }

        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);
        String fileConfigKey = buildFileConfigSignature();

        OkHttpClient client = PUW.globalLogin(appContext);
        if (client == null) {
            Logger.e(TAG + ".downloadScheduleFile", "Failed to login to PUW for downloading schedule");
            throw new IllegalStateException("Failed to login to PUW for downloading schedule");
        }

        String cachedUrl = getCachedScheduleUrl(fileConfigKey);
        if (cachedUrl != null) {
            try {
                Logger.d(TAG + ".downloadScheduleFile", "Attempting direct download using cached schedule URL");
                return executeScheduleDownloadAttempt(client, cachedUrl, destFile, allowConditionalGet, fileConfigKey);
            } catch (Exception cachedAttemptError) {
                Logger.w(TAG + ".downloadScheduleFile", "Cached URL download failed, falling back to fresh scrape: " + Logger.formatException(cachedAttemptError));
                clearCachedScheduleUrl();
                if (destFile.exists()) {
                    destFile.delete();
                }
            }
        }

        // fresh scrape path with one automatic session recovery retry if moodle session expired
        for (int attempt = 0; attempt < 2; attempt++) {
            if (attempt > 0) {
                Logger.w(TAG + ".downloadScheduleFile", "Retrying schedule download after refreshing PUW session");
                PUW.clearSession();
                client = PUW.globalLogin(appContext);
                if (client == null) {
                    throw new IllegalStateException("Failed to re-authenticate with PUW");
                }
            }
            String fileUrl = StudyPlanScraper.getScheduleFileUrl(client, major, degreeLevel, studyMode, year);
            if (fileUrl == null) {
                if (attempt == 0) {
                    continue;
                }
                Logger.e(TAG + ".downloadScheduleFile", "Could not resolve schedule download URL for " + major + ", " + degreeLevel + ", " + studyMode + ", year " + year);
                throw new IllegalStateException("Could not resolve schedule download URL");
            }
            try {
                DownloadOutcome outcome = executeScheduleDownloadAttempt(client, fileUrl, destFile, allowConditionalGet, fileConfigKey);
                saveCachedScheduleUrl(fileConfigKey, fileUrl);
                return outcome;
            } catch (SessionExpiredException expired) {
                if (destFile.exists()) {
                    destFile.delete();
                }
                if (attempt == 1) {
                    throw new IllegalStateException("Moodle session expired during schedule download", expired);
                }
            }
        }

        throw new IllegalStateException("Failed to download valid schedule file");
    }

    private static final class SessionExpiredException extends IOException {
        SessionExpiredException(String message) {
            super(message);
        }
    }

    private DownloadOutcome executeScheduleDownloadAttempt(
            OkHttpClient client,
            String fileUrl,
            File destFile,
            boolean allowConditionalGet,
            String fileConfigKey
    ) throws SessionExpiredException {
        Request.Builder requestBuilder = new Request.Builder().url(fileUrl);
        if (allowConditionalGet) {
            SharedPreferences prefs = getRepoPrefs();
            String savedUrl = prefs.getString(KEY_CACHED_URL, null);
            String savedConfig = prefs.getString(KEY_CACHED_URL_CONFIG, null);
            if (fileUrl.equals(savedUrl) && fileConfigKey != null && fileConfigKey.equals(savedConfig)) {
                String etag = prefs.getString(KEY_CACHED_ETAG, null);
                String lastModified = prefs.getString(KEY_CACHED_LAST_MODIFIED, null);
                if (etag != null && !etag.isEmpty()) {
                    requestBuilder.header("If-None-Match", etag);
                }
                if (lastModified != null && !lastModified.isEmpty()) {
                    requestBuilder.header("If-Modified-Since", lastModified);
                }
            }
        }

        Logger.d(TAG + ".downloadScheduleFile", "Downloading schedule from URL: " + fileUrl);
        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            String finalUrl = response.request().url().toString().toLowerCase(Locale.ROOT);
            if (finalUrl.contains("/login/")) {
                Logger.w(TAG + ".downloadScheduleFile", "Schedule request redirected to Moodle login page");
                throw new SessionExpiredException("Redirected to Moodle login");
            }

            if (response.code() == 304) {
                Logger.d(TAG + ".downloadScheduleFile", "Server returned 304 Not Modified for schedule file");
                return DownloadOutcome.NOT_MODIFIED;
            }

            if (response.code() == 401 || response.code() == 403) {
                throw new SessionExpiredException("HTTP " + response.code() + " while downloading schedule");
            }

            if (!response.isSuccessful()) {
                Logger.e(TAG + ".downloadScheduleFile", "Download HTTP error: " + response.code());
                throw new IllegalStateException("Download HTTP error: " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                Logger.e(TAG + ".downloadScheduleFile", "Empty response body from schedule URL");
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

            if (!isValidZipFile(destFile)) {
                Logger.w(TAG + ".downloadScheduleFile", "Downloaded payload failed XLSX ZIP header verification (likely HTML login/error page)");
                destFile.delete();
                throw new SessionExpiredException("Downloaded payload is not a valid XLSX ZIP archive");
            }

            saveHttpValidators(response.header("ETag"), response.header("Last-Modified"));
            Logger.i(TAG + ".downloadScheduleFile", "Schedule downloaded and verified (" + destFile.length() + " bytes)");
            return DownloadOutcome.DOWNLOADED;
        } catch (SessionExpiredException e) {
            throw e;
        } catch (Exception e) {
            Logger.e(TAG + ".downloadScheduleFile", "Error downloading schedule: " + Logger.formatException(e), e);
            throw new RuntimeException(e);
        }
    }

    // verify file starts with standard zip local file header magic bytes (pk\x03\x04)
    public static boolean isValidZipFile(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 4) {
            return false;
        }
        byte[] header = new byte[4];
        try (InputStream in = new FileInputStream(file)) {
            int read = in.read(header);
            return read == 4
                    && header[0] == 0x50
                    && header[1] == 0x4B
                    && header[2] == 0x03
                    && header[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean replaceFileAtomically(File tempFile, File targetFile) {
        if (tempFile == null || targetFile == null || !tempFile.exists()) {
            return false;
        }
        if (tempFile.renameTo(targetFile)) {
            return true;
        }
        try {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            Logger.e(TAG + ".replaceFileAtomically", "Atomic file replace failed: " + Logger.formatException(e));
            return false;
        }
    }

    private void cleanupOrphanScheduleFiles(@Nullable File keepFile) {
        File plansDir = getPlansDirectory();
        File[] files = plansDir.listFiles((dir, name) -> name.startsWith(CACHE_FILE_PREFIX) && name.endsWith(".xlsx"));
        if (files != null) {
            for (File f : files) {
                if (keepFile == null || !f.getName().equals(keepFile.getName())) {
                    if (f.delete()) {
                        Logger.d(TAG + ".cleanupOrphanScheduleFiles", "Removed obsolete schedule file: " + f.getName());
                    }
                }
            }
        }
        File legacyDir = new File(appContext.getCacheDir(), "plans");
        if (legacyDir.exists() && legacyDir.isDirectory()) {
            File[] legacyFiles = legacyDir.listFiles();
            if (legacyFiles != null) {
                for (File f : legacyFiles) {
                    f.delete();
                }
            }
        }
    }

    private File getPlansDirectory() {
        File dir = new File(appContext.getFilesDir(), "plans");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getLocalScheduleFile() {
        File dir = getPlansDirectory();
        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degree = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode mode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);

        String degreeTag = degree != null ? degree.name().toLowerCase(Locale.ROOT) : "unknown";
        String modeTag = mode != null ? mode.name().toLowerCase(Locale.ROOT) : "unknown";
        String majorTag = major != null ? Integer.toHexString(major.trim().toLowerCase(Locale.ROOT).hashCode()) : "0";
        return new File(dir, CACHE_FILE_PREFIX + degreeTag + "_" + modeTag + "_y" + year + "_" + majorTag + ".xlsx");
    }

    private String buildFileConfigSignature() {
        String major = PreferencesManager.getGlobalMajorPref(appContext);
        DegreeLevel degree = PreferencesManager.getGlobalDegreeLevelPref(appContext);
        StudyMode mode = PreferencesManager.getGlobalStudyModePref(appContext);
        int year = PreferencesManager.getGlobalYearPref(appContext);
        if (major == null || degree == null || mode == null) {
            return null;
        }
        return major + "|" + degree.name() + "|" + mode.name() + "|" + year;
    }

    public String buildConfigSignature() {
        String filePart = buildFileConfigSignature();
        if (filePart == null) {
            return null;
        }
        String spec = PreferencesManager.getGlobalSpecialtyPref(appContext);
        String lang = PreferencesManager.getGlobalEnglishGroupPref(appContext);
        String seminar = PreferencesManager.getGlobalSeminarTeacherPref(appContext);
        String surname = PreferencesManager.getGlobalStudentSurnamePref(appContext);
        return filePart + "|" + spec + "|" + lang + "|" + seminar + "|" + surname;
    }

    private SharedPreferences getRepoPrefs() {
        return appContext.getSharedPreferences(REPO_PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    private String getCachedScheduleUrl(@Nullable String fileConfigKey) {
        if (fileConfigKey == null) return null;
        SharedPreferences prefs = getRepoPrefs();
        String savedConfig = prefs.getString(KEY_CACHED_URL_CONFIG, null);
        String savedUrl = prefs.getString(KEY_CACHED_URL, null);
        long savedTime = prefs.getLong(KEY_CACHED_URL_TIME, 0L);
        if (savedUrl != null && fileConfigKey.equals(savedConfig)
                && (System.currentTimeMillis() - savedTime) < URL_CACHE_TTL_MS) {
            return savedUrl;
        }
        return null;
    }

    private void saveCachedScheduleUrl(String fileConfigKey, String fileUrl) {
        if (fileConfigKey == null || fileUrl == null) return;
        getRepoPrefs().edit()
                .putString(KEY_CACHED_URL_CONFIG, fileConfigKey)
                .putString(KEY_CACHED_URL, fileUrl)
                .putLong(KEY_CACHED_URL_TIME, System.currentTimeMillis())
                .apply();
    }

    private void saveHttpValidators(@Nullable String etag, @Nullable String lastModified) {
        SharedPreferences.Editor editor = getRepoPrefs().edit();
        if (etag != null && !etag.trim().isEmpty()) {
            editor.putString(KEY_CACHED_ETAG, etag.trim());
        } else {
            editor.remove(KEY_CACHED_ETAG);
        }
        if (lastModified != null && !lastModified.trim().isEmpty()) {
            editor.putString(KEY_CACHED_LAST_MODIFIED, lastModified.trim());
        } else {
            editor.remove(KEY_CACHED_LAST_MODIFIED);
        }
        editor.apply();
    }

    private void clearCachedScheduleUrl() {
        getRepoPrefs().edit()
                .remove(KEY_CACHED_URL)
                .remove(KEY_CACHED_URL_CONFIG)
                .remove(KEY_CACHED_URL_TIME)
                .remove(KEY_CACHED_ETAG)
                .remove(KEY_CACHED_LAST_MODIFIED)
                .apply();
    }

    private void saveLastSyncTime(LocalDateTime time) {
        if (time != null) {
            getRepoPrefs().edit()
                    .putString(KEY_LAST_SYNC_TIME, time.toString())
                    .apply();
        }
    }

    private LocalDateTime loadLastSyncTime(File cacheFile) {
        String saved = getRepoPrefs().getString(KEY_LAST_SYNC_TIME, null);
        if (saved != null) {
            try {
                return LocalDateTime.parse(saved);
            } catch (Exception ignored) {
            }
        }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0) {
            try {
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(cacheFile.lastModified()),
                        ZoneId.systemDefault()
                );
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // clear only in-memory parsed lessons so the local Excel file is re-parsed with updated filters
    public void invalidateMemoryCache() {
        Logger.d(TAG + ".invalidateMemoryCache", "Invalidating in-memory schedule cache for filter update");
        synchronized (cacheLock) {
            cachedAllLessons = null;
            cachedLessonsByDate = null;
            lastConfigSignature = null;
            dataVersion++;
        }
    }

    // clear in-memory cache, stored xlsx files, url cache, and http validators
    public void invalidateCache() {
        Logger.d(TAG + ".invalidateCache", "Invalidating memory and disk schedule cache");
        synchronized (cacheLock) {
            cachedAllLessons = null;
            cachedLessonsByDate = null;
            lastConfigSignature = null;
            lastFileConfigSignature = null;
            lastSyncTime = null;
            isLastLoadFromCacheFallback = false;
            dataVersion++;
        }
        synchronized (cooldownLock) {
            lastManualSyncTimestampMillis = 0;
            lastBackgroundSyncTimestampMillis = 0;
        }
        getRepoPrefs().edit().clear().apply();
        cleanupOrphanScheduleFiles(null);
    }
}
