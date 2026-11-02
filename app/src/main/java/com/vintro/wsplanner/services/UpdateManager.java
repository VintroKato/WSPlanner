package com.vintro.wsplanner.services;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

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
    private static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/VintroKato/WSPlanner/releases/latest";

    private static final String PREFS_NAME = "wsplanner_update_prefs";
    private static final String KEY_LAST_AUTO_CHECK_TIME = "last_auto_check_timestamp";
    private static final String KEY_LATEST_VERSION = "latest_available_version";
    private static final String KEY_LATEST_URL = "latest_release_url";
    private static final String KEY_LATEST_APK_URL = "latest_apk_download_url";

    public static final long COOLDOWN_MS = 60_000L;
    public static final long AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static long lastManualCheckTimestamp = 0L;
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
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

    public static String getSavedLatestVersion(Context context) {
        return getPrefs(context).getString(KEY_LATEST_VERSION, null);
    }

    public static String getSavedReleaseUrl(Context context) {
        return getPrefs(context).getString(KEY_LATEST_URL, GITHUB_RELEASES_URL);
    }

    public static String getSavedApkUrl(Context context) {
        return getPrefs(context).getString(KEY_LATEST_APK_URL, null);
    }

    public static void clearSavedUpdate(Context context) {
        getPrefs(context).edit()
                .remove(KEY_LATEST_VERSION)
                .remove(KEY_LATEST_URL)
                .remove(KEY_LATEST_APK_URL)
                .apply();
    }

    // Check if a newer version is known and pending install
    public static boolean hasPendingUpdate(Context context) {
        String savedVersion = getSavedLatestVersion(context);
        if (savedVersion == null) return false;
        return isNewerVersion(BuildConfig.VERSION_NAME, savedVersion);
    }

    // Automatic update check (e.g. called in MainActivity on launch)
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

        if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
            long remainingHours = (AUTO_CHECK_INTERVAL_MS - (now - lastCheck)) / (1000 * 60 * 60);
            Logger.d(TAG, "Auto update check interval not elapsed yet. Last check: " + lastCheck + " (~" + remainingHours + "h remaining)");
            if (callback != null) {
                if (hasPendingUpdate(context)) {
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
                Logger.e(TAG, "Manual update check error: " + errorMessage);
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    private static void executeGitHubCheck(Context context, UpdateCheckCallback callback) {
        Logger.d(TAG, "Executing GitHub API request -> " + GITHUB_API_LATEST_RELEASE);
        Request request = new Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE)
                .header("User-Agent", "WSPlanner-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Logger.e(TAG, "GitHub release check HTTP failure: " + Logger.formatException(e), e);
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

                    String jsonStr = body.string();
                    JSONObject json = new JSONObject(jsonStr);
                    String tagName = json.optString("tag_name", "");
                    String htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL);
                    String currentVersion = BuildConfig.VERSION_NAME;

                    String apkUrl = "";
                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.optJSONObject(i);
                            if (asset != null) {
                                String assetName = asset.optString("name", "");
                                if (assetName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url", "");
                                    Logger.i(TAG, "Discovered APK asset in GitHub release: " + assetName + " -> " + apkUrl);
                                    break;
                                }
                            }
                        }
                    }

                    if ((apkUrl == null || apkUrl.isEmpty()) && !tagName.isEmpty()) {
                        String cleanVer = tagName.startsWith("v") || tagName.startsWith("V") ? tagName : "v" + tagName;
                        apkUrl = "https://github.com/VintroKato/WSPlanner/releases/download/" + tagName + "/WSPlanner-" + cleanVer + ".apk";
                        Logger.i(TAG, "No explicit asset parsed; using fallback download URL: " + apkUrl);
                    }

                    boolean hasUpdate = isNewerVersion(currentVersion, tagName);
                    Logger.i(TAG, "GitHub release check complete: responseCode=" + responseCode
                            + ", current=" + currentVersion
                            + ", remoteTag=" + tagName
                            + ", hasUpdate=" + hasUpdate
                            + ", releaseUrl=" + htmlUrl
                            + ", apkUrl=" + apkUrl);

                    if (hasUpdate) {
                        getPrefs(context).edit().putString(KEY_LATEST_APK_URL, apkUrl).apply();
                    } else {
                        getPrefs(context).edit().remove(KEY_LATEST_APK_URL).apply();
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
        void onSuccess();
        void onFailure(Exception e);
    }

    public static void downloadAndInstallApk(Context context, String version, String directApkUrl, DownloadInstallCallback callback) {
        Logger.i(TAG, "downloadAndInstallApk initiated for version: " + version + " | directApkUrl: " + directApkUrl);

        if (!NetworkUtils.isNetworkAvailable(context)) {
            Logger.w(TAG, "Cannot download update APK: device is offline");
            if (callback != null) {
                callback.onFailure(new IOException("No network connection"));
            }
            return;
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

        final String finalUrl = targetUrl;
        Logger.i(TAG, "Starting background download from: " + finalUrl + " -> file: " + fileName);

        if (callback != null) {
            callback.onDownloadStarted();
        }

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "WSPlanner-Android-App")
                        .build();

                Response response = httpClient.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new IOException("Download HTTP error: " + response.code() + " " + response.message());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Response body is null");
                }

                File cacheDir = context.getExternalCacheDir();
                if (cacheDir == null) {
                    cacheDir = context.getCacheDir();
                }
                File updateDir = new File(cacheDir, "updates");
                if (!updateDir.exists()) {
                    boolean created = updateDir.mkdirs();
                    Logger.d(TAG, "Created update cache directory: " + updateDir.getAbsolutePath() + " (success: " + created + ")");
                }

                File destFile = new File(updateDir, fileName);
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
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                    }
                    out.flush();
                    Logger.i(TAG, "Download finished. Total bytes written: " + downloadedBytes);
                }

                if (!destFile.exists() || destFile.length() == 0) {
                    throw new IOException("Downloaded file is empty or missing: " + destFile.getAbsolutePath());
                }

                Logger.i(TAG, "APK successfully downloaded (" + destFile.length() + " bytes). Generating FileProvider URI…");

                Uri apkUri = FileProvider.getUriForFile(
                        context.getApplicationContext(),
                        context.getPackageName() + ".provider",
                        destFile
                );
                Logger.i(TAG, "FileProvider content URI created: " + apkUri);

                Intent installIntent = new Intent(Intent.ACTION_VIEW);
                installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (!(context instanceof android.app.Activity)) {
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }

                Logger.i(TAG, "Dispatching system package installer intent for: " + fileName);
                context.startActivity(installIntent);

                postToMain(() -> {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                });

            } catch (Exception e) {
                Logger.e(TAG, "Failed to download and launch update APK: " + Logger.formatException(e), e);
                postToMain(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            }
        }).start();
    }

    // Show custom popup-styled dialog
    public static void showUpdateDialog(Context context, String newVersion, String releaseUrl) {
        showUpdateDialog(context, newVersion, releaseUrl, getSavedApkUrl(context));
    }

    public static void showUpdateDialog(Context context, String newVersion, String releaseUrl, String apkUrl) {
        if (context == null) {
            Logger.w(TAG, "showUpdateDialog called with null context");
            return;
        }

        String targetUrl = releaseUrl != null && !releaseUrl.isEmpty() ? releaseUrl : GITHUB_RELEASES_URL;
        Logger.i(TAG, "Displaying update available dialog: newVersion=" + newVersion + ", targetUrl=" + targetUrl + ", apkUrl=" + apkUrl);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_update_available, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.60f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(24);
            }
        }

        TextView currentBadge = view.findViewById(R.id.dialog_current_version_badge);
        TextView newBadge = view.findViewById(R.id.dialog_new_version_badge);
        ImageButton btnClose = view.findViewById(R.id.dialog_update_close);
        MaterialButton btnAutoUpdate = view.findViewById(R.id.dialog_btn_auto_update);
        ProgressBar progressAutoUpdate = view.findViewById(R.id.dialog_auto_update_progress);
        LinearLayout errorCard = view.findViewById(R.id.dialog_update_error_card);
        MaterialButton btnOpenGithub = view.findViewById(R.id.dialog_btn_open_github);
        MaterialButton btnLater = view.findViewById(R.id.dialog_btn_later);

        String currentVerStr = BuildConfig.VERSION_NAME;
        if (!currentVerStr.startsWith("v") && !currentVerStr.startsWith("V")) {
            currentVerStr = "v" + currentVerStr;
        }
        currentBadge.setText(context.getString(R.string.update_dialog_current_badge, currentVerStr));

        String newVerStr = newVersion != null ? newVersion : "";
        if (!newVerStr.startsWith("v") && !newVerStr.startsWith("V") && !newVerStr.isEmpty()) {
            newVerStr = "v" + newVerStr;
        }
        newBadge.setText(context.getString(R.string.update_dialog_new_badge, newVerStr));

        if (btnAutoUpdate != null) {
            btnAutoUpdate.setOnClickListener(v -> {
                Logger.i(TAG, "Update dialog: user clicked 'Update automatically' button for version: " + newVersion);
                if (errorCard != null) {
                    errorCard.setVisibility(View.GONE);
                }
                btnAutoUpdate.setEnabled(false);
                btnAutoUpdate.setText(R.string.update_dialog_downloading);
                if (progressAutoUpdate != null) {
                    progressAutoUpdate.setVisibility(View.VISIBLE);
                }
                if (btnOpenGithub != null) btnOpenGithub.setEnabled(false);
                if (btnLater != null) btnLater.setEnabled(false);
                if (btnClose != null) btnClose.setEnabled(false);
                dialog.setCancelable(false);

                String resolvedApkUrl = apkUrl != null && !apkUrl.isEmpty() ? apkUrl : getSavedApkUrl(context);

                downloadAndInstallApk(context, newVersion, resolvedApkUrl, new DownloadInstallCallback() {
                    @Override
                    public void onDownloadStarted() {
                        Logger.d(TAG, "Auto-update download stream started");
                    }

                    @Override
                    public void onSuccess() {
                        Logger.i(TAG, "Auto-update succeeded: installer dispatched. Dismissing update dialog");
                        dialog.dismiss();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Logger.w(TAG, "Auto-update failed: " + Logger.formatException(e));
                        if (progressAutoUpdate != null) {
                            progressAutoUpdate.setVisibility(View.GONE);
                        }
                        btnAutoUpdate.setText(R.string.update_dialog_button_auto);
                        btnAutoUpdate.setEnabled(true);
                        if (btnOpenGithub != null) btnOpenGithub.setEnabled(true);
                        if (btnLater != null) btnLater.setEnabled(true);
                        if (btnClose != null) btnClose.setEnabled(true);
                        dialog.setCancelable(true);
                        if (errorCard != null) {
                            errorCard.setVisibility(View.VISIBLE);
                        }
                    }
                });
            });
        }

        btnOpenGithub.setOnClickListener(v -> {
            Logger.i(TAG, "Update dialog: user clicked 'Go to GitHub' button -> targetUrl: " + targetUrl);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                if (!(context instanceof android.app.Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
                Logger.i(TAG, "Update dialog: successfully launched browser intent for release URL: " + targetUrl);
            } catch (Exception e) {
                Logger.e(TAG, "Update dialog: failed to open release URL: " + targetUrl + " | " + Logger.formatException(e), e);
            }
            dialog.dismiss();
        });

        btnLater.setOnClickListener(v -> {
            Logger.i(TAG, "Update dialog: user clicked 'Later' button (dialog dismissed)");
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> {
            Logger.i(TAG, "Update dialog: user clicked close ('X') button (dialog dismissed)");
            dialog.dismiss();
        });

        dialog.setOnCancelListener(d -> {
            Logger.i(TAG, "Update dialog: dialog was cancelled/dismissed via back button or touch outside");
        });

        dialog.show();
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
