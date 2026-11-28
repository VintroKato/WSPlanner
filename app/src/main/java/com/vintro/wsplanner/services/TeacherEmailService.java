package com.vintro.wsplanner.services;

import android.content.Context;
import android.util.Patterns;

import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// service to fetch, parse, and cache teacher emails with ttl and synchronized disk access
public class TeacherEmailService {
    private static final String TAG = "TeacherEmailService";
    private static final String DIRECTORY_URL = "https://wspa.pl/student/dziekanat/strony-wykladowcow/";
    private static final String CACHE_FILE_NAME = "teachers_cache.json";
    private static final String DIRECTORY_CACHE_NAME = "teachers_directory.json";
    private static final long DIRECTORY_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long POSITIVE_CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long NEGATIVE_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static TeacherEmailService instance;

    private final Context appContext;
    private final OkHttpClient httpClient;
    private final Object diskCacheLock = new Object();

    private final Map<String, TeacherEmailResult> memoryCache = new ConcurrentHashMap<>();
    private volatile boolean diskCacheLoaded = false;
    private List<TeacherDirectoryEntry> cachedDirectory = null;
    private long directoryLastLoadedTimestamp = 0;

    public static class TeacherEmailResult {
        public final String email;
        public final boolean isFoundOnWebsite;
        public final boolean hasEmail;
        public final String profileUrl;
        public final String matchedName;
        public final boolean isNetworkError;

        public TeacherEmailResult(String email, boolean isFoundOnWebsite, String profileUrl, String matchedName) {
            this(email, isFoundOnWebsite, profileUrl, matchedName, false);
        }

        public TeacherEmailResult(String email, boolean isFoundOnWebsite, String profileUrl, String matchedName, boolean isNetworkError) {
            this.email = email;
            this.isFoundOnWebsite = isFoundOnWebsite;
            this.hasEmail = (email != null && !email.trim().isEmpty());
            this.profileUrl = profileUrl;
            this.matchedName = matchedName;
            this.isNetworkError = isNetworkError;
        }

        public static TeacherEmailResult networkError() {
            return new TeacherEmailResult(null, false, null, null, true);
        }

        public static TeacherEmailResult notFoundOnWebsite() {
            return new TeacherEmailResult(null, false, null, null, false);
        }

        public static TeacherEmailResult noEmail(String profileUrl, String matchedName) {
            return new TeacherEmailResult(null, true, profileUrl, matchedName, false);
        }

        public static TeacherEmailResult found(String email, String profileUrl, String matchedName) {
            return new TeacherEmailResult(email, true, profileUrl, matchedName, false);
        }
    }

    public interface TeacherEmailCallback {
        void onResult(TeacherEmailResult result);
    }

    private static class TeacherDirectoryEntry {
        final String name;
        final String profileUrl;
        final String cleanedName;
        final List<String> tokens;

        TeacherDirectoryEntry(String name, String profileUrl) {
            this.name = name != null ? name.trim() : "";
            this.profileUrl = profileUrl != null ? profileUrl.trim() : "";
            this.cleanedName = normalizeForSearch(this.name);
            this.tokens = extractTokens(this.cleanedName);
        }
    }

    private static final Set<String> GENERIC_EMAILS = new HashSet<>(Arrays.asList(
            "rektorat@wspa.pl",
            "kancelaria@wspa.pl",
            "dziekanat@wspa.pl",
            "rekrutacja@wspa.pl",
            "info@wspa.pl",
            "iod@wspa.pl",
            "kariera@wspa.pl",
            "studiapodyplomowe@wspa.pl",
            "studia@wspa.pl",
            "wspa@wspa.pl",
            "marketing@wspa.pl",
            "pr@wspa.pl",
            "admin@wspa.pl",
            "administrator@wspa.pl",
            "helpdesk@wspa.pl",
            "biblioteka@wspa.pl",
            "kwestura@wspa.pl",
            "abk@wspa.pl",
            "erasmus@wspa.pl"
    ));

    private static final Set<String> ACADEMIC_TITLES = new HashSet<>(Arrays.asList(
            "prof", "profesor", "nadzw", "hab", "habilitowany", "dr", "doktor",
            "inż", "inz", "inżynier", "mgr", "magister", "ks", "ksiądz",
            "doc", "docent", "lic", "licencjat", "arch", "architekt"
    ));

    private TeacherEmailService(Context context) {
        this.appContext = context.getApplicationContext();
        this.httpClient = NetworkUtils.getSharedHttpClient().newBuilder().build();
        // warm disk cache off the main thread so construction never blocks ui
        AppExecutors.runIo(this::ensureDiskCachesLoaded);
    }

    public static synchronized TeacherEmailService getInstance(Context context) {
        if (instance == null) {
            instance = new TeacherEmailService(context);
        }
        return instance;
    }

    // clear memory and disk teacher caches
    public void clearCache() {
        synchronized (diskCacheLock) {
            memoryCache.clear();
            cachedDirectory = null;
            directoryLastLoadedTimestamp = 0;
            diskCacheLoaded = false;
            deleteQuietly(getCacheFile(CACHE_FILE_NAME));
            deleteQuietly(getCacheFile(DIRECTORY_CACHE_NAME));
        }
        Logger.i(TAG + ".clearCache", "Cleared teacher email and directory caches");
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    // check if teacher email is already cached in memory
    public TeacherEmailResult getCachedEmail(String teacherQuery) {
        if (teacherQuery == null || teacherQuery.trim().isEmpty() || teacherQuery.equals("—") || teacherQuery.equalsIgnoreCase("Unknown Teacher")) {
            return TeacherEmailResult.notFoundOnWebsite();
        }
        String normalizedKey = normalizeForSearch(teacherQuery);
        if (normalizedKey.isEmpty()) {
            return TeacherEmailResult.notFoundOnWebsite();
        }
        TeacherEmailResult cached = memoryCache.get(normalizedKey);
        if (cached != null) {
            Logger.d(TAG + ".getCachedEmail", "Memory cache HIT for teacher [" + teacherQuery + "] -> " + (cached.hasEmail ? Logger.maskSensitiveData(cached.email) : "no email"));
            return cached;
        }
        return null;
    }

    // fetch teacher email asynchronously
    public void getTeacherEmail(String teacherQuery, TeacherEmailCallback callback) {
        if (teacherQuery == null || teacherQuery.trim().isEmpty() || teacherQuery.equals("—") || teacherQuery.equalsIgnoreCase("Unknown Teacher")) {
            deliverResult(callback, TeacherEmailResult.notFoundOnWebsite());
            return;
        }

        String normalizedKey = normalizeForSearch(teacherQuery);
        if (normalizedKey.isEmpty()) {
            deliverResult(callback, TeacherEmailResult.notFoundOnWebsite());
            return;
        }

        TeacherEmailResult cachedResult = memoryCache.get(normalizedKey);
        if (cachedResult != null) {
            Logger.d(TAG + ".getTeacherEmail", "Memory cache HIT for [" + teacherQuery + "] -> " + Logger.maskSensitiveData(cachedResult.email));
            deliverResult(callback, cachedResult);
            return;
        }

        AppExecutors.runIo(() -> {
            try {
                ensureDiskCachesLoaded();

                TeacherEmailResult diskHit = memoryCache.get(normalizedKey);
                if (diskHit != null) {
                    Logger.d(TAG + ".getTeacherEmail", "Disk cache HIT for [" + teacherQuery + "]");
                    deliverResult(callback, diskHit);
                    return;
                }

                if (!NetworkUtils.isNetworkAvailable(appContext)) {
                    Logger.w(TAG + ".getTeacherEmail", "No network available for fetching teacher email [" + teacherQuery + "]");
                    deliverResult(callback, TeacherEmailResult.networkError());
                    return;
                }

                Logger.d(TAG + ".getTeacherEmail", "Cache MISS for [" + teacherQuery + "], starting background search");

                List<TeacherDirectoryEntry> directory = getOrFetchDirectory();
                if (directory == null || directory.isEmpty()) {
                    Logger.e(TAG + ".getTeacherEmail", "Teacher directory is empty or failed to load");
                    boolean isNetworkIssue = !NetworkUtils.isNetworkAvailable(appContext);
                    TeacherEmailResult fallback = isNetworkIssue ? TeacherEmailResult.networkError() : TeacherEmailResult.notFoundOnWebsite();
                    if (!isNetworkIssue) {
                        saveResultToCache(normalizedKey, fallback);
                    }
                    deliverResult(callback, fallback);
                    return;
                }

                TeacherDirectoryEntry matchedEntry = findBestDirectoryMatch(teacherQuery, directory);
                if (matchedEntry == null || matchedEntry.profileUrl.isEmpty()) {
                    Logger.d(TAG + ".getTeacherEmail", "Teacher not found in directory: " + teacherQuery);
                    TeacherEmailResult notFound = TeacherEmailResult.notFoundOnWebsite();
                    saveResultToCache(normalizedKey, notFound);
                    deliverResult(callback, notFound);
                    return;
                }

                Logger.d(TAG + ".getTeacherEmail", "Matched teacher [" + teacherQuery + "] -> [" + matchedEntry.name + "] (" + matchedEntry.profileUrl + ")");

                String email = scrapeTeacherEmail(matchedEntry.profileUrl);
                TeacherEmailResult finalResult;
                if (email != null && !email.isEmpty()) {
                    finalResult = TeacherEmailResult.found(email, matchedEntry.profileUrl, matchedEntry.name);
                    Logger.i(TAG + ".getTeacherEmail", "Found email for [" + teacherQuery + "]: " + Logger.maskSensitiveData(email));
                } else {
                    finalResult = TeacherEmailResult.noEmail(matchedEntry.profileUrl, matchedEntry.name);
                    Logger.d(TAG + ".getTeacherEmail", "No email found on profile page for [" + teacherQuery + "]");
                }

                saveResultToCache(normalizedKey, finalResult);
                deliverResult(callback, finalResult);

            } catch (Exception e) {
                Logger.e(TAG + ".getTeacherEmail", "Error fetching teacher email for: " + teacherQuery + ", " + Logger.formatException(e));
                boolean isNetworkIssue = !NetworkUtils.isNetworkAvailable(appContext)
                        || (e instanceof java.io.IOException)
                        || (e instanceof java.net.UnknownHostException)
                        || (e instanceof java.net.SocketTimeoutException);
                TeacherEmailResult errorResult = isNetworkIssue ? TeacherEmailResult.networkError() : TeacherEmailResult.notFoundOnWebsite();
                if (!isNetworkIssue) {
                    saveResultToCache(normalizedKey, errorResult);
                }
                deliverResult(callback, errorResult);
            }
        });
    }

    private void ensureDiskCachesLoaded() {
        if (diskCacheLoaded) return;
        synchronized (diskCacheLock) {
            if (diskCacheLoaded) return;
            loadDiskCacheLocked();
            loadDirectoryCacheFromDiskLocked();
            diskCacheLoaded = true;
        }
    }

    private void deliverResult(TeacherEmailCallback callback, TeacherEmailResult result) {
        if (callback == null) return;
        AppExecutors.runOnMain(() -> callback.onResult(result));
    }

    // scrape teacher profile page for email
    private String scrapeTeacherEmail(String profileUrl) {
        try {
            Request request = new Request.Builder()
                    .url(profileUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Logger.e(TAG + ".scrapeTeacherEmail", "Profile request failed: HTTP " + response.code());
                    return null;
                }
                ResponseBody body = response.body();
                if (body == null) return null;

                String html = body.string();
                Document doc = Jsoup.parse(html, profileUrl);

                doc.select("footer, header, .kingster-footer-wrapper, .kingster-header-wrap, #footer, nav, .widget, .kingster-top-bar, .ibif-top-bar, #block-21, #block-22").remove();

                Element searchRoot = doc.selectFirst(".kingster-page-wrapper, .gdlr-core-personnel-single, .gdlr-core-page-builder-body, .gdlr-core-pbf-wrapper");
                if (searchRoot == null) {
                    searchRoot = doc.body();
                }
                if (searchRoot == null) return null;

                Elements elementsWithMail = searchRoot.select("p, li, div.gdlr-core-personnel-info-list, div, span");
                Pattern pMail = Pattern.compile("(?i)e-?mail:\\s*([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
                for (Element el : elementsWithMail) {
                    String text = el.text();
                    String lower = text.toLowerCase(Locale.ROOT);
                    if (lower.contains("e-mail:") || lower.contains("email:") || lower.contains("e-mail :") || lower.contains("email :")) {
                        Elements mailtoLinks = el.select("a[href^=mailto:]");
                        for (Element a : mailtoLinks) {
                            String email = cleanMailto(a.attr("href"));
                            if (isValidEmail(email) && !isGenericUniversityEmail(email)) {
                                return email;
                            }
                        }

                        Matcher m = pMail.matcher(text);
                        if (m.find()) {
                            String email = m.group(1).trim().toLowerCase(Locale.ROOT);
                            if (isValidEmail(email) && !isGenericUniversityEmail(email)) {
                                return email;
                            }
                        }
                    }
                }

                Elements personnelMailtos = searchRoot.select(".gdlr-core-personnel-single a[href^=mailto:], .gdlr-core-personnel-list-content-wrap a[href^=mailto:], .gdlr-core-personnel-info-list a[href^=mailto:]");
                for (Element a : personnelMailtos) {
                    String email = cleanMailto(a.attr("href"));
                    if (isValidEmail(email) && !isGenericUniversityEmail(email)) {
                        return email;
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(TAG + ".scrapeTeacherEmail", "Error scraping profile " + profileUrl + ": " + Logger.formatException(e));
        }
        return null;
    }

    private static String cleanMailto(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceFirst("(?i)mailto:", "").trim();
        int qIdx = cleaned.indexOf('?');
        if (qIdx != -1) cleaned = cleaned.substring(0, qIdx);
        return cleaned.toLowerCase(Locale.ROOT);
    }

    // load directory from memory, disk cache, or web
    private List<TeacherDirectoryEntry> getOrFetchDirectory() {
        synchronized (diskCacheLock) {
            if (cachedDirectory != null && !cachedDirectory.isEmpty()
                    && System.currentTimeMillis() - directoryLastLoadedTimestamp < DIRECTORY_CACHE_MAX_AGE_MS) {
                return cachedDirectory;
            }
            if (loadDirectoryCacheFromDiskLocked()) {
                return cachedDirectory;
            }
        }

        try {
            Logger.d(TAG + ".getOrFetchDirectory", "Downloading teacher directory from " + DIRECTORY_URL);
            Request request = new Request.Builder()
                    .url(DIRECTORY_URL)
                    .header("User-Agent", USER_AGENT)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Logger.e(TAG + ".getOrFetchDirectory", "Failed to download directory: HTTP " + response.code());
                    synchronized (diskCacheLock) {
                        return cachedDirectory != null ? cachedDirectory : Collections.emptyList();
                    }
                }

                ResponseBody body = response.body();
                if (body == null) return Collections.emptyList();

                String html = body.string();
                Document doc = Jsoup.parse(html, DIRECTORY_URL);
                Elements links = doc.select(".gdlr-core-personnel-list-content-wrap a, .gdlr-core-personnel-item a[href*=/personnel/], a[href*=/personnel/]");

                List<TeacherDirectoryEntry> entries = new ArrayList<>();
                Set<String> seenUrls = new HashSet<>();

                for (Element link : links) {
                    String url = link.attr("abs:href");
                    if (url.isEmpty()) url = link.attr("href");
                    if (!url.contains("personnel")) continue;

                    String name = link.text().trim();
                    if (name.isEmpty() || seenUrls.contains(url)) continue;

                    seenUrls.add(url);
                    entries.add(new TeacherDirectoryEntry(name, url));
                }

                Logger.d(TAG + ".getOrFetchDirectory", "Parsed " + entries.size() + " teachers from directory");
                if (!entries.isEmpty()) {
                    synchronized (diskCacheLock) {
                        cachedDirectory = entries;
                        directoryLastLoadedTimestamp = System.currentTimeMillis();
                        saveDirectoryCacheToDiskLocked(entries);
                    }
                }
                return entries;
            }
        } catch (Exception e) {
            Logger.e(TAG + ".getOrFetchDirectory", "Error downloading teacher directory: " + Logger.formatException(e));
            synchronized (diskCacheLock) {
                return cachedDirectory != null ? cachedDirectory : Collections.emptyList();
            }
        }
    }

    // match teacher name against directory entries
    private TeacherDirectoryEntry findBestDirectoryMatch(String query, List<TeacherDirectoryEntry> directory) {
        String cleanQuery = normalizeForSearch(query);
        List<String> queryTokens = extractTokens(cleanQuery);

        if (queryTokens.isEmpty()) return null;

        TeacherDirectoryEntry bestMatch = null;
        int bestScore = 0;

        for (TeacherDirectoryEntry entry : directory) {
            int score = calculateMatchScore(cleanQuery, queryTokens, entry.cleanedName, entry.tokens);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entry;
            }
        }

        return bestScore >= 60 ? bestMatch : null;
    }

    private int calculateMatchScore(String queryStr, List<String> qTokens, String targetStr, List<String> tTokens) {
        if (queryStr.equals(targetStr)) {
            return 100;
        }

        boolean allQueryInTarget = true;
        for (String qt : qTokens) {
            if (!tTokens.contains(qt)) {
                allQueryInTarget = false;
                break;
            }
        }
        if (allQueryInTarget) {
            return 90;
        }

        boolean allTargetInQuery = true;
        for (String tt : tTokens) {
            if (!qTokens.contains(tt)) {
                allTargetInQuery = false;
                break;
            }
        }
        if (allTargetInQuery) {
            return 90;
        }

        String qSurname = qTokens.get(qTokens.size() - 1);
        String tSurname = tTokens.get(tTokens.size() - 1);

        if (qSurname.equals(tSurname) && qSurname.length() >= 3) {
            if (qTokens.size() > 1 && tTokens.size() > 1) {
                String qFirst = qTokens.get(0);
                String tFirst = tTokens.get(0);
                if (qFirst.equals(tFirst)) return 85;
                if (qFirst.charAt(0) == tFirst.charAt(0)) return 75;
            }
            return 65;
        }

        return 0;
    }

    // remove academic titles and punctuation
    public static String normalizeForSearch(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.ROOT);
        lower = lower.replaceAll("[,.;:!?'\"()\\[\\]\\-–—/\\\\]", " ");

        String[] parts = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && !ACADEMIC_TITLES.contains(p)) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(p);
            }
        }
        return sb.toString();
    }

    public static boolean isGenericUniversityEmail(String email) {
        if (email == null) return false;
        String lower = email.trim().toLowerCase(Locale.ROOT);
        if (GENERIC_EMAILS.contains(lower)) return true;
        String prefix = lower.contains("@") ? lower.substring(0, lower.indexOf('@')) : lower;
        return prefix.equals("rektorat") || prefix.equals("dziekanat") || prefix.equals("kancelaria")
                || prefix.equals("rekrutacja") || prefix.equals("info") || prefix.equals("biuro")
                || prefix.equals("sekretariat") || prefix.equals("kontakt");
    }

    private static List<String> extractTokens(String cleaned) {
        if (cleaned.isEmpty()) return Collections.emptyList();
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) {
                tokens.add(p);
            }
        }
        return tokens;
    }

    private static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return Patterns.EMAIL_ADDRESS.matcher(email).matches() && email.contains(".");
    }

    private File getCacheFile(String filename) {
        File cacheFile = new File(appContext.getCacheDir(), filename);
        try {
            File filesDirFile = new File(appContext.getFilesDir(), filename);
            if (!cacheFile.exists() && filesDirFile.exists()) {
                filesDirFile.renameTo(cacheFile);
            }
        } catch (Exception ignored) {
        }
        return cacheFile;
    }

    private static String readUtf8File(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void writeUtf8FileAtomically(File targetFile, String content) throws Exception {
        File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tempFile, false), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
        if (!tempFile.renameTo(targetFile)) {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void saveResultToCache(String normalizedKey, TeacherEmailResult result) {
        if (result == null || result.isNetworkError) return;
        memoryCache.put(normalizedKey, result);

        synchronized (diskCacheLock) {
            try {
                File file = getCacheFile(CACHE_FILE_NAME);
                JSONObject rootJson = new JSONObject();
                if (file.exists()) {
                    try {
                        String raw = readUtf8File(file);
                        if (!raw.isEmpty()) rootJson = new JSONObject(raw);
                    } catch (Exception ignored) {
                    }
                }

                JSONObject entry = new JSONObject();
                if (result.email != null) {
                    entry.put("email", result.email);
                }
                entry.put("isFoundOnWebsite", result.isFoundOnWebsite);
                if (result.profileUrl != null) {
                    entry.put("profileUrl", result.profileUrl);
                }
                if (result.matchedName != null) {
                    entry.put("matchedName", result.matchedName);
                }
                entry.put("timestamp", System.currentTimeMillis());

                rootJson.put(normalizedKey, entry);
                writeUtf8FileAtomically(file, rootJson.toString());
                Logger.d(TAG + ".saveResultToCache", "Saved teacher email result to cache for [" + normalizedKey + "] (email=" + Logger.maskSensitiveData(result.email) + ")");
            } catch (Exception e) {
                Logger.e(TAG + ".saveResultToCache", "Failed to save teacher result to disk cache: " + Logger.formatException(e));
            }
        }
    }

    private void loadDiskCacheLocked() {
        try {
            File file = getCacheFile(CACHE_FILE_NAME);
            if (!file.exists()) return;

            String raw = readUtf8File(file);
            if (raw.isEmpty()) return;

            boolean cacheModified = false;
            long now = System.currentTimeMillis();
            JSONObject root = new JSONObject(raw);
            List<String> expiredKeys = new ArrayList<>();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = root.optJSONObject(key);
                if (obj != null) {
                    boolean found = obj.optBoolean("isFoundOnWebsite", false);
                    long ts = obj.optLong("timestamp", 0L);
                    long maxAge = found ? POSITIVE_CACHE_MAX_AGE_MS : NEGATIVE_CACHE_MAX_AGE_MS;
                    if (ts > 0L && (now - ts) > maxAge) {
                        expiredKeys.add(key);
                        cacheModified = true;
                        continue;
                    }

                    String email = obj.optString("email", null);
                    if ("null".equalsIgnoreCase(email) || (email != null && email.isEmpty())) email = null;
                    if (email != null && isGenericUniversityEmail(email)) {
                        email = null;
                        obj.put("email", JSONObject.NULL);
                        cacheModified = true;
                    }
                    String profileUrl = obj.optString("profileUrl", null);
                    if ("null".equalsIgnoreCase(profileUrl) || (profileUrl != null && profileUrl.isEmpty())) profileUrl = null;
                    String matchedName = obj.optString("matchedName", null);
                    if ("null".equalsIgnoreCase(matchedName) || (matchedName != null && matchedName.isEmpty())) matchedName = null;
                    memoryCache.put(key, new TeacherEmailResult(email, found, profileUrl, matchedName));
                }
            }
            for (String expiredKey : expiredKeys) {
                root.remove(expiredKey);
            }
            if (cacheModified) {
                writeUtf8FileAtomically(file, root.toString());
                Logger.d(TAG + ".loadDiskCacheLocked", "Cleaned expired or generic entries from teacher disk cache");
            }
            Logger.d(TAG + ".loadDiskCacheLocked", "Loaded " + memoryCache.size() + " teacher records from disk cache");
        } catch (Exception e) {
            Logger.e(TAG + ".loadDiskCacheLocked", "Error reading disk cache: " + Logger.formatException(e));
        }
    }

    private boolean loadDirectoryCacheFromDiskLocked() {
        try {
            File file = getCacheFile(DIRECTORY_CACHE_NAME);
            if (!file.exists()) return false;

            String raw = readUtf8File(file);
            if (raw.isEmpty()) return false;
            JSONObject root = new JSONObject(raw);
            long timestamp = root.optLong("timestamp", 0);
            if (System.currentTimeMillis() - timestamp > DIRECTORY_CACHE_MAX_AGE_MS) {
                return false;
            }

            JSONArray arr = root.optJSONArray("teachers");
            if (arr == null || arr.length() == 0) return false;

            List<TeacherDirectoryEntry> entries = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                entries.add(new TeacherDirectoryEntry(item.optString("name"), item.optString("url")));
            }

            cachedDirectory = entries;
            directoryLastLoadedTimestamp = timestamp;
            Logger.d(TAG + ".loadDirectoryCacheFromDiskLocked", "Loaded " + entries.size() + " teachers from directory disk cache");
            return true;
        } catch (Exception e) {
            Logger.e(TAG + ".loadDirectoryCacheFromDiskLocked", "Error loading directory cache: " + Logger.formatException(e));
            return false;
        }
    }

    private void saveDirectoryCacheToDiskLocked(List<TeacherDirectoryEntry> entries) {
        try {
            File file = getCacheFile(DIRECTORY_CACHE_NAME);
            JSONObject root = new JSONObject();
            root.put("timestamp", System.currentTimeMillis());

            JSONArray arr = new JSONArray();
            for (TeacherDirectoryEntry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("name", entry.name);
                item.put("url", entry.profileUrl);
                arr.put(item);
            }
            root.put("teachers", arr);

            writeUtf8FileAtomically(file, root.toString());
            Logger.d(TAG + ".saveDirectoryCacheToDiskLocked", "Directory cache written to disk (" + entries.size() + " items)");
        } catch (Exception e) {
            Logger.e(TAG + ".saveDirectoryCacheToDiskLocked", "Failed to save directory cache: " + Logger.formatException(e));
        }
    }
}
