package com.vintro.wsplanner.services;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class UpdateManager {
    private static final String TAG = "UpdateManager";

    public static final String GITHUB_REPO_URL = "https://github.com/VintroKato/WSPlanner";
    public static final String GITHUB_RELEASES_URL = "https://github.com/VintroKato/WSPlanner/releases";
    private static final String GITHUB_API_RELEASES = "https://api.github.com/repos/VintroKato/WSPlanner/releases?per_page=15";

    private static final String PREFS_NAME = "wsplanner_update_prefs";
    private static final String KEY_LAST_AUTO_CHECK_TIME = "last_auto_check_timestamp";
    private static final String KEY_LATEST_VERSION = "latest_available_version";
    private static final String KEY_LATEST_URL = "latest_release_url";
    private static final String KEY_LATEST_APK_URL = "latest_apk_download_url";
    private static final String KEY_LATEST_SHA256_URL = "latest_sha256_download_url";
    private static final String KEY_LATEST_CHANGELOG = "latest_release_changelog";

    public static final long COOLDOWN_MS = 120_000L;
    public static final long AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours
    public static final long PENDING_UPDATE_RECHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L; // 6 hours when update is pending

    private static long lastManualCheckTimestamp = 0L;
    private static final OkHttpClient httpClient = NetworkUtils.getSharedHttpClient().newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static Handler mainHandler;

    private static synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }

    private static void postToMain(Runnable runnable) {
        try {
            getMainHandler().post(runnable);
        } catch (Throwable t) {
            runnable.run();
        }
    }

    public interface UpdateCheckCallback {
        void onUpdateAvailable(String newVersion, String releaseUrl);
        void onUpToDate(String currentVersion);
        void onError(String errorMessage);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isCooldownActive() {
        return (System.currentTimeMillis() - lastManualCheckTimestamp) < COOLDOWN_MS;
    }

    public static int getRemainingCooldownSeconds() {
        long elapsed = System.currentTimeMillis() - lastManualCheckTimestamp;
        if (elapsed >= COOLDOWN_MS) return 0;
        return (int) Math.ceil((COOLDOWN_MS - elapsed) / 1000.0);
    }

    public static void recordManualCheck() {
        lastManualCheckTimestamp = System.currentTimeMillis();
    }

    public static long getLastCheckTimestamp(Context context) {
        long lastAuto = context != null ? getPrefs(context).getLong(KEY_LAST_AUTO_CHECK_TIME, 0L) : 0L;
        return Math.max(lastManualCheckTimestamp, lastAuto);
    }

    // check if either an auto or manual update check ran within the cooldown window
    public static boolean isRecentCheckWithinCooldown(Context context) {
        return (System.currentTimeMillis() - getLastCheckTimestamp(context)) < COOLDOWN_MS;
    }

    public static String getSavedLatestVersion(Context context) {
        return getPrefs(context).getString(KEY_LATEST_VERSION, null);
    }

    public static String getSavedReleaseUrl(Context context) {
        return getPrefs(context).getString(KEY_LATEST_URL, GITHUB_RELEASES_URL);
    }

    public static String getSavedApkUrl(Context context) {
        return getPrefs(context).getString(KEY_LATEST_APK_URL, null);
    }

    public static String getSavedSha256Url(Context context) {
        return getPrefs(context).getString(KEY_LATEST_SHA256_URL, null);
    }

    public static String getSavedChangelog(Context context) {
        return getPrefs(context).getString(KEY_LATEST_CHANGELOG, null);
    }

    public static void clearSavedUpdate(Context context) {
        getPrefs(context).edit()
                .remove(KEY_LATEST_VERSION)
                .remove(KEY_LATEST_URL)
                .remove(KEY_LATEST_APK_URL)
                .remove(KEY_LATEST_SHA256_URL)
                .remove(KEY_LATEST_CHANGELOG)
                .apply();
    }

    public static void cleanupDownloadedApks(Context context) {
        if (context == null) return;
        deleteApksInDir(context.getExternalCacheDir());
        deleteApksInDir(context.getCacheDir());
    }

    private static void deleteApksInDir(File baseCacheDir) {
        if (baseCacheDir == null) return;
        File updateDir = new File(baseCacheDir, "updates");
        if (!updateDir.exists() || !updateDir.isDirectory()) return;
        File[] files = updateDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f != null && f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                boolean deleted = f.delete();
                Logger.d(TAG, "Cleaned up cached update APK: " + f.getName() + " (deleted=" + deleted + ")");
            }
        }
    }

    // Check if a newer version is known and pending install
    public static boolean hasPendingUpdate(Context context) {
        String savedVersion = getSavedLatestVersion(context);
        if (savedVersion == null) return false;
        return isNewerVersion(BuildConfig.VERSION_NAME, savedVersion);
    }

    // clear cached changelog and auto-check timer after an app upgrade so new releases are parsed fresh
    public static void clearChangelogCacheOnUpgrade(Context context) {
        if (context == null) return;
        getPrefs(context).edit()
                .remove(KEY_LAST_AUTO_CHECK_TIME)
                .remove(KEY_LATEST_CHANGELOG)
                .remove("last_checked_app_version")
                .remove("changelog_parser_version")
                .apply();
        Logger.d(TAG, "Cleared cached changelog and auto-check timer after app upgrade");
    }

    // Automatic update check
    public static void checkAutoUpdate(Context context, UpdateCheckCallback callback) {
        Logger.d(TAG, "checkAutoUpdate triggered");
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Logger.d(TAG, "Device is offline, skipping auto update check");
            if (callback != null) {
                if (hasPendingUpdate(context)) {
                    String pendingVer = getSavedLatestVersion(context);
                    String pendingUrl = getSavedReleaseUrl(context);
                    Logger.i(TAG, "Offline auto-check: found previously cached pending update: " + pendingVer + " (" + pendingUrl + ")");
                    callback.onUpdateAvailable(pendingVer, pendingUrl);
                } else {
                    Logger.d(TAG, "Offline auto-check: no pending update cached, using current version: " + BuildConfig.VERSION_NAME);
                    callback.onUpToDate(BuildConfig.VERSION_NAME);
                }
            }
            return;
        }

        long lastCheck = getPrefs(context).getLong(KEY_LAST_AUTO_CHECK_TIME, 0L);
        long now = System.currentTimeMillis();
        boolean pendingUpdate = hasPendingUpdate(context);
        long effectiveInterval = pendingUpdate ? PENDING_UPDATE_RECHECK_INTERVAL_MS : AUTO_CHECK_INTERVAL_MS;

        if (now - lastCheck < effectiveInterval) {
            long remainingHours = (effectiveInterval - (now - lastCheck)) / (1000 * 60 * 60);
            Logger.d(TAG, "Auto update check interval not elapsed yet (pending=" + pendingUpdate + "). Last check: " + lastCheck + " (~" + remainingHours + "h remaining)");
            if (callback != null) {
                if (pendingUpdate) {
                    String pendingVer = getSavedLatestVersion(context);
                    String pendingUrl = getSavedReleaseUrl(context);
                    Logger.i(TAG, "Auto-check skipped by interval: found cached pending update: " + pendingVer);
                    callback.onUpdateAvailable(pendingVer, pendingUrl);
                } else {
                    Logger.d(TAG, "Auto-check skipped by interval: app is up to date (" + BuildConfig.VERSION_NAME + ")");
                    callback.onUpToDate(BuildConfig.VERSION_NAME);
                }
            }
            return;
        }

        Logger.i(TAG, "Starting automatic GitHub update check (last checked " + ((now - lastCheck) / (1000 * 60)) + " min ago)");
        executeGitHubCheck(context, new UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String releaseUrl) {
                Logger.i(TAG, "Auto update check: update available! newVersion=" + newVersion + ", releaseUrl=" + releaseUrl);
                getPrefs(context).edit()
                        .putLong(KEY_LAST_AUTO_CHECK_TIME, System.currentTimeMillis())
                        .putString(KEY_LATEST_VERSION, newVersion)
                        .putString(KEY_LATEST_URL, releaseUrl)
                        .apply();
                if (callback != null) {
                    callback.onUpdateAvailable(newVersion, releaseUrl);
                }
            }

            @Override
            public void onUpToDate(String currentVersion) {
                Logger.d(TAG, "Auto update check: app is up to date (" + currentVersion + ")");
                getPrefs(context).edit()
                        .putLong(KEY_LAST_AUTO_CHECK_TIME, System.currentTimeMillis())
                        .remove(KEY_LATEST_VERSION)
                        .remove(KEY_LATEST_URL)
                        .apply();
                if (callback != null) {
                    callback.onUpToDate(currentVersion);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Logger.w(TAG, "Auto update check failed: " + errorMessage);
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    // Manual update check (e.g. called from SettingsActivity)
    public static void checkManualUpdate(Context context, UpdateCheckCallback callback) {
        Logger.i(TAG, "Manual update check initiated by user (current version: " + BuildConfig.VERSION_NAME + ")");
        recordManualCheck();

        if (!NetworkUtils.isNetworkAvailable(context)) {
            Logger.w(TAG, "Manual update check aborted: device has no internet connection");
            if (callback != null) {
                callback.onError("No network");
            }
            return;
        }

        executeGitHubCheck(context, new UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String releaseUrl) {
                Logger.i(TAG, "Manual update check: update available! tag=" + newVersion + ", url=" + releaseUrl);
                getPrefs(context).edit()
                        .putLong(KEY_LAST_AUTO_CHECK_TIME, System.currentTimeMillis())
                        .putString(KEY_LATEST_VERSION, newVersion)
                        .putString(KEY_LATEST_URL, releaseUrl)
                        .apply();
                if (callback != null) {
                    callback.onUpdateAvailable(newVersion, releaseUrl);
                }
            }

            @Override
            public void onUpToDate(String currentVersion) {
                Logger.i(TAG, "Manual update check: app is up to date (" + currentVersion + ")");
                getPrefs(context).edit()
                        .putLong(KEY_LAST_AUTO_CHECK_TIME, System.currentTimeMillis())
                        .remove(KEY_LATEST_VERSION)
                        .remove(KEY_LATEST_URL)
                        .apply();
                if (callback != null) {
                    callback.onUpToDate(currentVersion);
                }
            }

            @Override
            public void onError(String errorMessage) {
                Logger.w(TAG, "Manual update check error: " + errorMessage);
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    // check whether a GitHub release entry is a stable published release
    public static boolean isStableRelease(boolean isDraft, boolean isPrerelease) {
        return !isDraft && !isPrerelease;
    }

    // pick the newest non-draft, non-prerelease entry from GitHub releases array
    public static JSONObject findLatestStableRelease(JSONArray releasesArray) {
        if (releasesArray == null) return null;
        for (int i = 0; i < releasesArray.length(); i++) {
            JSONObject candidate = releasesArray.optJSONObject(i);
            if (candidate == null) continue;
            if (!isStableRelease(candidate.optBoolean("draft", false), candidate.optBoolean("prerelease", false))) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static void executeGitHubCheck(Context context, UpdateCheckCallback callback) {
        final Context appContext = context.getApplicationContext();
        Logger.d(TAG, "Executing GitHub API request -> " + GITHUB_API_RELEASES);
        Request request = new Request.Builder()
                .url(GITHUB_API_RELEASES)
                .header("User-Agent", "WSPlanner-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Logger.w(TAG, "GitHub release check HTTP failure: " + Logger.formatException(e));
                postToMain(() -> {
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    int responseCode = response.code();
                    if (!response.isSuccessful() || body == null) {
                        String err = "HTTP " + responseCode + " (" + response.message() + ")";
                        Logger.w(TAG, "GitHub release API returned unsuccessful code: " + err);
                        postToMain(() -> {
                            if (callback != null) callback.onError(err);
                        });
                        return;
                    }

                    String jsonStr = body.string().trim();
                    JSONArray releasesArray;
                    if (jsonStr.startsWith("[")) {
                        releasesArray = new JSONArray(jsonStr);
                    } else if (jsonStr.startsWith("{")) {
                        releasesArray = new JSONArray();
                        releasesArray.put(new JSONObject(jsonStr));
                    } else {
                        throw new IOException("Unexpected API response format");
                    }

                    JSONObject latestRelease = findLatestStableRelease(releasesArray);
                    if (latestRelease == null) {
                        Logger.w(TAG, "No stable non-prerelease GitHub release found");
                        postToMain(() -> {
                            if (callback != null) callback.onUpToDate(BuildConfig.VERSION_NAME);
                        });
                        return;
                    }

                    String tagName = latestRelease.optString("tag_name", "");
                    String htmlUrl = latestRelease.optString("html_url", GITHUB_RELEASES_URL);
                    String currentVersion = BuildConfig.VERSION_NAME;

                    String apkUrl = "";
                    String sha256Url = "";
                    JSONArray assets = latestRelease.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.optJSONObject(i);
                            if (asset != null) {
                                String assetName = asset.optString("name", "");
                                String assetLower = assetName.toLowerCase(Locale.ROOT);
                                if (assetLower.endsWith(".apk") && apkUrl.isEmpty()) {
                                    apkUrl = asset.optString("browser_download_url", "");
                                    Logger.i(TAG, "Discovered APK asset in GitHub release: " + assetName + " -> " + apkUrl);
                                } else if (assetLower.equals("sha256sum.txt")) {
                                    sha256Url = asset.optString("browser_download_url", "");
                                    Logger.i(TAG, "Discovered SHA-256 asset in GitHub release: " + assetName + " -> " + sha256Url);
                                }
                            }
                        }
                    }

                    if ((apkUrl == null || apkUrl.isEmpty()) && !tagName.isEmpty()) {
                        String cleanVer = tagName.startsWith("v") || tagName.startsWith("V") ? tagName : "v" + tagName;
                        apkUrl = "https://github.com/VintroKato/WSPlanner/releases/download/" + tagName + "/WSPlanner-" + cleanVer + ".apk";
                        Logger.i(TAG, "No explicit asset parsed; using fallback download URL: " + apkUrl);
                    }

                    if ((sha256Url == null || sha256Url.isEmpty()) && !tagName.isEmpty()) {
                        sha256Url = "https://github.com/VintroKato/WSPlanner/releases/download/" + tagName + "/sha256sum.txt";
                        Logger.i(TAG, "No explicit sha256sum.txt asset parsed; using fallback URL: " + sha256Url);
                    }

                    boolean hasUpdate = isNewerVersion(currentVersion, tagName);

                    JSONArray multiVersionChangelog = new JSONArray();
                    if (hasUpdate) {
                        for (int i = 0; i < releasesArray.length(); i++) {
                            JSONObject rel = releasesArray.optJSONObject(i);
                            if (rel == null || rel.optBoolean("draft", false) || rel.optBoolean("prerelease", false)) {
                                continue;
                            }
                            String relTag = rel.optString("tag_name", "");
                            if (!isNewerVersion(currentVersion, relTag)) {
                                break;
                            }
                            String relName = rel.optString("name", "");
                            String relBody = rel.optString("body", "");
                            List<String> items = extractChangelogBulletList(relName, relBody);
                            String commitMsg = extractCommitMessage(relName, relBody);
                            LocalizedChangelog ruChangelog = extractLocalizedChangelog(relBody, "ru");
                            LocalizedChangelog ukChangelog = extractLocalizedChangelog(relBody, "uk");

                            JSONObject verObj = new JSONObject();
                            verObj.put("version", relTag);
                            verObj.put("title", commitMsg);
                            verObj.put("items", toJsonArray(items));
                            if (ruChangelog != null) {
                                if (!ruChangelog.title.isEmpty()) {
                                    verObj.put("title_ru", ruChangelog.title);
                                }
                                if (!ruChangelog.items.isEmpty()) {
                                    verObj.put("items_ru", toJsonArray(ruChangelog.items));
                                }
                            }
                            if (ukChangelog != null) {
                                if (!ukChangelog.title.isEmpty()) {
                                    verObj.put("title_uk", ukChangelog.title);
                                }
                                if (!ukChangelog.items.isEmpty()) {
                                    verObj.put("items_uk", toJsonArray(ukChangelog.items));
                                }
                            }
                            multiVersionChangelog.put(verObj);
                        }
                    }

                    Logger.i(TAG, "GitHub release check complete: responseCode=" + responseCode
                            + ", current=" + currentVersion
                            + ", remoteTag=" + tagName
                            + ", hasUpdate=" + hasUpdate
                            + ", releaseUrl=" + htmlUrl
                            + ", apkUrl=" + apkUrl
                            + ", missedVersionsCount=" + multiVersionChangelog.length());

                    if (hasUpdate) {
                        SharedPreferences.Editor editor = getPrefs(appContext).edit()
                                .putString(KEY_LATEST_APK_URL, apkUrl)
                                .putString(KEY_LATEST_SHA256_URL, sha256Url);
                        if (multiVersionChangelog.length() > 0) {
                            editor.putString(KEY_LATEST_CHANGELOG, multiVersionChangelog.toString());
                        } else {
                            editor.remove(KEY_LATEST_CHANGELOG);
                        }
                        editor.apply();
                    } else {
                        getPrefs(appContext).edit()
                                .remove(KEY_LATEST_APK_URL)
                                .remove(KEY_LATEST_SHA256_URL)
                                .remove(KEY_LATEST_CHANGELOG)
                                .apply();
                    }

                    postToMain(() -> {
                        if (callback != null) {
                            if (hasUpdate) {
                                callback.onUpdateAvailable(tagName, htmlUrl);
                            } else {
                                callback.onUpToDate(currentVersion);
                            }
                        }
                    });
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to parse GitHub release JSON: " + Logger.formatException(e), e);
                    postToMain(() -> {
                        if (callback != null) callback.onError(e.getMessage());
                    });
                }
            }
        });
    }

    // holds localized release title and bullet points parsed from hidden changelog comment
    public static final class LocalizedChangelog {
        public final String title;
        public final List<String> items;

        public LocalizedChangelog(String title, List<String> items) {
            this.title = title != null ? title : "";
            this.items = items != null ? items : new ArrayList<>();
        }
    }

    private static JSONArray toJsonArray(List<String> items) {
        JSONArray array = new JSONArray();
        if (items != null) {
            for (String item : items) {
                array.put(item);
            }
        }
        return array;
    }

    // extract localized title and bullet list from hidden html comment block in release body
    public static LocalizedChangelog extractLocalizedChangelog(String body, String langCode) {
        if (body == null || body.trim().isEmpty() || langCode == null || langCode.trim().isEmpty()) {
            return null;
        }
        String normalized = langCode.trim().toLowerCase(Locale.ROOT);
        String langPattern = ("uk".equals(normalized) || "ua".equals(normalized))
                ? "(?:uk|ua)"
                : Pattern.quote(normalized);

        Pattern commentPattern = Pattern.compile("(?i)<!--\\s*changelog:" + langPattern + "\\s*\\r?\\n([\\s\\S]*?)-->");
        Matcher matcher = commentPattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        String block = matcher.group(1);
        if (block == null || block.trim().isEmpty()) {
            return null;
        }

        String localizedTitle = "";
        List<String> items = new ArrayList<>();
        String[] lines = block.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.matches("^[-*•]\\s+.*")) {
                String item = line.replaceFirst("^[-*•]\\s+", "")
                        .replace("`", "")
                        .trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            } else if (localizedTitle.isEmpty() && items.isEmpty()) {
                localizedTitle = line.replaceFirst("^#{1,6}\\s*", "")
                        .replace("`", "")
                        .trim();
            }
        }

        if (localizedTitle.isEmpty() && items.isEmpty()) {
            return null;
        }
        return new LocalizedChangelog(localizedTitle, items);
    }

    // Extract only bulleted list items from the release changelog section, excluding headings and commit titles
    public static List<String> extractChangelogBulletList(String releaseName, String body) {
        String section = extractChangelogSection(body);
        if (section == null || section.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return parseBulletLines(section);
    }

    // Parse lines formatted as markdown or text bullet points ('-', '*', or '•' followed by whitespace)
    public static List<String> parseBulletLines(String text) {
        List<String> items = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return items;
        }

        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.matches("^[-*•]\\s+.*")) {
                String item = line.replaceFirst("^[-*•]\\s+", "")
                        .replace("`", "")
                        .trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private static String extractChangelogSection(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        String raw = body.trim();
        Pattern p = Pattern.compile("(?i)#{1,4}\\s*(?:what['’]s changed|changelog|changes)[:\\s]*([\\s\\S]*?)(?:---|#{1,4}\\s*build info|\\Z)");
        Matcher m = p.matcher(raw);
        if (m.find()) {
            return m.group(1);
        }

        // Check if there is user text placed before "### Build info" / "## Build info"
        Matcher buildInfoMatcher = Pattern.compile("(?i)(?m)^#{1,4}\\s*build info").matcher(raw);
        if (buildInfoMatcher.find() && buildInfoMatcher.start() > 0) {
            String prefix = raw.substring(0, buildInfoMatcher.start()).trim();
            // Ensure prefix is not technical boilerplate
            if (!prefix.isEmpty() && !prefix.startsWith(">") && !prefix.startsWith("#") && !prefix.startsWith("|")) {
                return prefix;
            }
        }

        return null;
    }

    public static String extractCommitMessage(String releaseName, String body) {
        if (releaseName != null && !isVersionTagOnly(releaseName)) {
            return releaseName.trim();
        }
        if (body == null || body.trim().isEmpty()) {
            return "";
        }

        // Pattern 1: Table format from release.yml:
        // | **Commit** | daf0753a1ca6c6a39cc0bc9f94ac7c9ab490f000 (Added automatic update option in update dialog) |
        Pattern tablePattern = Pattern.compile("(?im)^\\s*\\|\\s*\\*\\*Commit\\*\\*\\s*\\|\\s*[0-9a-fA-F]+\\s*\\(([^\\r\\n|]+)\\)\\s*(?:\\||$)");
        Matcher tableMatcher = tablePattern.matcher(body);
        if (tableMatcher.find()) {
            String msg = tableMatcher.group(1);
            if (msg != null && !msg.trim().isEmpty() && !isVersionTagOnly(msg)) {
                return msg.trim();
            }
        }

        // Pattern 2: List format from earlier releases (v1.0.1, v1.0.0, v0.6.3...):
        // * **Source Commit:** 7f7d9a559fcf6e1d89d427bf502ee1084613225d — Added adaptive font size and sticky bottom to small widget
        Pattern bulletPattern = Pattern.compile("(?i)\\*\\*\\s*Source Commit:\\*\\*\\s*[0-9a-fA-F]+\\s*(?:—|-|–)\\s*([^\\r\\n*]+)");
        Matcher bulletMatcher = bulletPattern.matcher(body);
        if (bulletMatcher.find()) {
            String msg = bulletMatcher.group(1);
            if (msg != null && !msg.trim().isEmpty() && !isVersionTagOnly(msg)) {
                return msg.trim();
            }
        }

        // Pattern 3: Any generic commit line
        Pattern genericPattern = Pattern.compile("(?i)commit[:\\s]+[0-9a-fA-F]+\\s*(?:\\(([^)]+)\\)|(?:—|-|–)\\s*([^\\r\\n*|]+))");
        Matcher genericMatcher = genericPattern.matcher(body);
        if (genericMatcher.find()) {
            String msg = genericMatcher.group(1) != null ? genericMatcher.group(1) : genericMatcher.group(2);
            if (msg != null && !msg.trim().isEmpty() && !isVersionTagOnly(msg)) {
                return msg.trim();
            }
        }

        return "";
    }

    public static String extractChangelog(String releaseName, String body) {
        String cleanTitle = !isVersionTagOnly(releaseName) ? releaseName.trim() : null;
        String changelogSection = extractChangelogSection(body);

        // If no user changelog section exists in the release body:
        if (changelogSection == null) {
            return cleanTitle;
        }

        String cleaned = changelogSection
                .replaceAll("(?m)^\\s*[-*•]\\s*", "• ")
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .replaceAll("`", "")
                .replaceAll("---", "")
                .trim();

        // Guard against any leftover technical boilerplate
        if (cleaned.isEmpty()
                || cleaned.toLowerCase(Locale.ROOT).startsWith("build info")
                || cleaned.startsWith(">")
                || cleaned.startsWith("|")) {
            return cleanTitle;
        }

        if (cleanTitle != null && !cleanTitle.equalsIgnoreCase(cleaned) && !cleaned.startsWith(cleanTitle)) {
            return cleanTitle + "\n\n" + cleaned;
        }

        return cleaned;
    }

    public static boolean isVersionTagOnly(String text) {
        if (text == null) return true;
        String trimmed = text.trim();
        return trimmed.isEmpty() || trimmed.matches("(?i)^v?\\d+(\\.\\d+)*([-_\\w]+)?$");
    }

    // Semantic Version comparison: returns true if remoteTag is strictly newer than currentVersion
    public static boolean isNewerVersion(String currentVersion, String remoteTag) {
        if (currentVersion == null || remoteTag == null) return false;
        String cleanCurrent = currentVersion.replace("v", "").replace("V", "").trim();
        String cleanRemote = remoteTag.replace("v", "").replace("V", "").trim();

        String[] curTokens = cleanCurrent.split("\\.");
        String[] remTokens = cleanRemote.split("\\.");

        int length = Math.max(curTokens.length, remTokens.length);
        for (int i = 0; i < length; i++) {
            int curNum = 0;
            int remNum = 0;

            if (i < curTokens.length) {
                String digits = curTokens[i].replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try { curNum = Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
                }
            }

            if (i < remTokens.length) {
                String digits = remTokens[i].replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try { remNum = Integer.parseInt(digits); } catch (NumberFormatException ignored) {}
                }
            }

            if (remNum > curNum) return true;
            if (remNum < curNum) return false;
        }

        return false;
    }

    public interface DownloadInstallCallback {
        void onDownloadStarted();
        default void onProgress(int percent, long downloadedBytes, long totalBytes) {}
        default void onVerifyingHash() {}
        default void onInstallPermissionRequired() {}
        void onSuccess();
        void onFailure(Exception e);
    }

    private static boolean openUnknownSourcesSettings(Context context) {
        if (context == null) return false;
        try {
            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName())
            );
            if (!(context instanceof Activity)) {
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(settingsIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            Logger.w(TAG, "ACTION_MANAGE_UNKNOWN_APP_SOURCES unavailable on this ROM: " + Logger.formatException(e));
            return false;
        } catch (Exception e) {
            Logger.w(TAG, "Failed opening unknown app sources settings: " + Logger.formatException(e));
            return false;
        }
    }

    public static void downloadAndInstallApk(Context context, String version, String directApkUrl, DownloadInstallCallback callback) {
        Logger.i(TAG, "downloadAndInstallApk initiated for version: " + version + " | directApkUrl: " + directApkUrl);
        if (context == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Context is null"));
            }
            return;
        }

        final Context appContext = context.getApplicationContext();

        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            Logger.w(TAG, "Cannot download update APK: device is offline");
            if (callback != null) {
                callback.onFailure(new IOException("No network connection"));
            }
            return;
        }

        // check install permission up front so the user does not waste mobile data before granting access
        if (!appContext.getPackageManager().canRequestPackageInstalls()) {
            Logger.w(TAG, "REQUEST_INSTALL_PACKAGES not yet granted; prompting user via system settings");
            if (openUnknownSourcesSettings(context)) {
                postToMain(() -> {
                    Toast.makeText(appContext, R.string.update_install_permission_toast, Toast.LENGTH_LONG).show();
                    if (callback != null) {
                        callback.onInstallPermissionRequired();
                    }
                });
                return;
            }
        }

        String cleanVer = version != null ? version.trim() : "";
        if (!cleanVer.startsWith("v") && !cleanVer.startsWith("V") && !cleanVer.isEmpty()) {
            cleanVer = "v" + cleanVer;
        }
        String fileName = "WSPlanner-" + cleanVer + ".apk";

        String targetUrl = directApkUrl;
        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            targetUrl = "https://github.com/VintroKato/WSPlanner/releases/download/" + cleanVer + "/" + fileName;
            Logger.d(TAG, "Using computed download URL: " + targetUrl);
        }

        String savedSha256Url = getSavedSha256Url(appContext);
        if (savedSha256Url == null || savedSha256Url.trim().isEmpty()) {
            int lastSlash = targetUrl.lastIndexOf('/');
            if (lastSlash > 0) {
                savedSha256Url = targetUrl.substring(0, lastSlash + 1) + "sha256sum.txt";
            } else {
                savedSha256Url = "https://github.com/VintroKato/WSPlanner/releases/download/" + cleanVer + "/sha256sum.txt";
            }
        }

        final String finalUrl = targetUrl;
        final String finalSha256Url = savedSha256Url;
        Logger.i(TAG, "Starting background download from: " + finalUrl + " -> file: " + fileName);

        if (callback != null) {
            callback.onDownloadStarted();
        }

        AppExecutors.runIo(() -> {
            File destFile = null;
            try {
                cleanupDownloadedApks(appContext);

                Request request = new Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "WSPlanner-Android-App")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("Download HTTP error: " + response.code() + " " + response.message());
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Response body is null");
                    }

                    // store apk in private internal cache so other apps cannot modify it before verification
                    File updateDir = new File(appContext.getCacheDir(), "updates");
                    if (!updateDir.exists()) {
                        boolean created = updateDir.mkdirs();
                        Logger.d(TAG, "Created update cache directory: " + updateDir.getAbsolutePath() + " (success: " + created + ")");
                    }

                    destFile = new File(updateDir, fileName);
                    if (destFile.exists()) {
                        destFile.delete();
                    }

                    long totalBytes = body.contentLength();
                    Logger.i(TAG, "Writing APK stream to " + destFile.getAbsolutePath() + " (expected: " + totalBytes + " bytes)");

                    try (InputStream in = body.byteStream();
                         FileOutputStream out = new FileOutputStream(destFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long downloadedBytes = 0;
                        int lastReportedPercent = -1;
                        long lastReportTimeMs = 0L;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            downloadedBytes += bytesRead;

                            long nowMs = System.currentTimeMillis();
                            if (totalBytes > 0) {
                                int percent = (int) Math.min(100L, (downloadedBytes * 100L) / totalBytes);
                                if (percent != lastReportedPercent && (nowMs - lastReportTimeMs >= 120L || percent == 100)) {
                                    lastReportedPercent = percent;
                                    lastReportTimeMs = nowMs;
                                    final int reportedPercent = percent;
                                    final long reportedBytes = downloadedBytes;
                                    postToMain(() -> {
                                        if (callback != null) {
                                            callback.onProgress(reportedPercent, reportedBytes, totalBytes);
                                        }
                                    });
                                }
                            } else if (nowMs - lastReportTimeMs >= 250L) {
                                lastReportTimeMs = nowMs;
                                final long reportedBytes = downloadedBytes;
                                postToMain(() -> {
                                    if (callback != null) {
                                        callback.onProgress(-1, reportedBytes, -1L);
                                    }
                                });
                            }
                        }
                        out.flush();
                        Logger.i(TAG, "Download finished. Total bytes written: " + downloadedBytes);
                    }
                }

                if (destFile == null || !destFile.exists() || destFile.length() == 0) {
                    throw new IOException("Downloaded file is empty or missing");
                }

                Logger.i(TAG, "APK downloaded (" + destFile.length() + " bytes). Starting SHA-256 verification");
                postToMain(() -> {
                    if (callback != null) {
                        callback.onVerifyingHash();
                    }
                });

                verifyApkSha256(destFile, fileName, finalSha256Url);
                Logger.i(TAG, "SHA-256 verification passed! Generating FileProvider URI");

                Uri apkUri = FileProvider.getUriForFile(
                        appContext,
                        appContext.getPackageName() + ".provider",
                        destFile
                );
                Logger.i(TAG, "FileProvider content URI created: " + apkUri);

                Intent installIntent = new Intent(Intent.ACTION_VIEW);
                installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

                final File verifiedFile = destFile;
                postToMain(() -> {
                    if (!appContext.getPackageManager().canRequestPackageInstalls()
                            && openUnknownSourcesSettings(appContext)) {
                        Toast.makeText(appContext, R.string.update_install_permission_toast, Toast.LENGTH_LONG).show();
                        if (callback != null) {
                            callback.onInstallPermissionRequired();
                        }
                        return;
                    }
                    try {
                        Logger.i(TAG, "Dispatching system package installer intent for: " + fileName);
                        appContext.startActivity(installIntent);
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } catch (Exception launchError) {
                        Logger.e(TAG, "Failed to launch system package installer for " + verifiedFile.getName() + ": " + Logger.formatException(launchError), launchError);
                        if (callback != null) {
                            callback.onFailure(launchError);
                        }
                    }
                });

            } catch (Exception e) {
                if (destFile != null && destFile.exists()) {
                    boolean deleted = destFile.delete();
                    Logger.w(TAG, "Deleted unverified/failed APK file after error (deleted=" + deleted + ")");
                }
                Logger.e(TAG, "Failed to download, verify, or launch update APK: " + Logger.formatException(e), e);
                postToMain(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            }
        });
    }

    private static void verifyApkSha256(File apkFile, String apkFileName, String sha256Url) throws Exception {
        Logger.i(TAG, "Fetching SHA-256 checksum file from: " + sha256Url);
        Request hashRequest = new Request.Builder()
                .url(sha256Url)
                .header("User-Agent", "WSPlanner-Android-App")
                .build();

        String sha256Content;
        try (Response response = httpClient.newCall(hashRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to fetch sha256sum.txt: HTTP " + response.code());
            }
            sha256Content = response.body().string();
        }

        String expectedHash = parseExpectedSha256(sha256Content, apkFileName);
        if (expectedHash == null || expectedHash.length() != 64) {
            throw new IOException("Invalid or missing SHA-256 hash in sha256sum.txt");
        }

        String actualHash = computeFileSha256(apkFile);
        Logger.i(TAG, "SHA-256 check: expected=" + expectedHash + ", actual=" + actualHash);

        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            throw new SecurityException("SHA-256 hash mismatch! Expected: " + expectedHash + ", Actual: " + actualHash);
        }
    }

    private static String parseExpectedSha256(String content, String targetFileName) {
        if (content == null) return null;
        String[] lines = content.split("\\r?\\n");
        String fallbackHash = null;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            if (parts.length >= 1) {
                String candidate = parts[0].trim();
                if (candidate.matches("^[0-9a-fA-F]{64}$")) {
                    if (fallbackHash == null) {
                        fallbackHash = candidate;
                    }
                    if (parts.length >= 2) {
                        String filePart = parts[parts.length - 1].replace("*", "").trim();
                        if (filePart.equalsIgnoreCase(targetFileName)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return fallbackHash;
    }

    private static String computeFileSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    // Show custom popup-styled dialog (delegated to UpdateDialog)
    public static void showUpdateDialog(Context context, String newVersion, String releaseUrl) {
        showUpdateDialog(context, newVersion, releaseUrl, getSavedApkUrl(context));
    }

    public static void showUpdateDialog(Context context, String newVersion, String releaseUrl, String apkUrl) {
        com.vintro.wsplanner.ui.dialogs.UpdateDialog.show(context, newVersion, releaseUrl, apkUrl);
    }

    public static void openGitHubRepo(Context context) {
        if (context == null) {
            Logger.w(TAG, "openGitHubRepo called with null context");
            return;
        }
        Logger.i(TAG, "Opening GitHub repository in browser: " + GITHUB_REPO_URL);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL));
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            Logger.i(TAG, "Successfully launched browser intent for GitHub repository: " + GITHUB_REPO_URL);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to open GitHub repo URL: " + GITHUB_REPO_URL + " | Error: " + Logger.formatException(e), e);
        }
    }
}
