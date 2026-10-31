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
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import org.json.JSONObject;

import java.io.IOException;

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

    public static final long COOLDOWN_MS = 60_000L;
    public static final long AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private static long lastManualCheckTimestamp = 0L;
    private static final OkHttpClient httpClient = new OkHttpClient();
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

    public static void clearSavedUpdate(Context context) {
        getPrefs(context).edit().remove(KEY_LATEST_VERSION).remove(KEY_LATEST_URL).apply();
    }

    // Check if a newer version is known and pending install
    public static boolean hasPendingUpdate(Context context) {
        String savedVersion = getSavedLatestVersion(context);
        if (savedVersion == null) return false;
        return isNewerVersion(BuildConfig.VERSION_NAME, savedVersion);
    }

    // Automatic update check (e.g. called in MainActivity on launch)
    public static void checkAutoUpdate(Context context, UpdateCheckCallback callback) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Logger.d(TAG, "Device is offline, skipping auto update check");
            if (callback != null) {
                if (hasPendingUpdate(context)) {
                    callback.onUpdateAvailable(getSavedLatestVersion(context), getSavedReleaseUrl(context));
                } else {
                    callback.onUpToDate(BuildConfig.VERSION_NAME);
                }
            }
            return;
        }

        long lastCheck = getPrefs(context).getLong(KEY_LAST_AUTO_CHECK_TIME, 0L);
        long now = System.currentTimeMillis();

        if (now - lastCheck < AUTO_CHECK_INTERVAL_MS) {
            Logger.d(TAG, "Auto update check interval not elapsed yet. Last check: " + lastCheck);
            if (callback != null) {
                if (hasPendingUpdate(context)) {
                    callback.onUpdateAvailable(getSavedLatestVersion(context), getSavedReleaseUrl(context));
                } else {
                    callback.onUpToDate(BuildConfig.VERSION_NAME);
                }
            }
            return;
        }

        executeGitHubCheck(context, new UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String releaseUrl) {
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
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    // Manual update check (e.g. called from SettingsActivity)
    public static void checkManualUpdate(Context context, UpdateCheckCallback callback) {
        recordManualCheck();

        if (!NetworkUtils.isNetworkAvailable(context)) {
            if (callback != null) {
                callback.onError("No network");
            }
            return;
        }

        executeGitHubCheck(context, new UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String releaseUrl) {
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
                if (callback != null) {
                    callback.onError(errorMessage);
                }
            }
        });
    }

    private static void executeGitHubCheck(Context context, UpdateCheckCallback callback) {
        Request request = new Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE)
                .header("User-Agent", "WSPlanner-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Logger.e(TAG, "GitHub release check network failure: " + Logger.formatException(e));
                postToMain(() -> {
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        String err = "HTTP " + response.code();
                        Logger.w(TAG, "GitHub release check returned error: " + err);
                        postToMain(() -> {
                            if (callback != null) callback.onError(err);
                        });
                        return;
                    }

                    String jsonStr = body.string();
                    JSONObject json = new JSONObject(jsonStr);
                    String tagName = json.optString("tag_name", "");
                    String htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL);

                    Logger.i(TAG, "Latest GitHub release tag: " + tagName + ", url: " + htmlUrl);
                    String currentVersion = BuildConfig.VERSION_NAME;

                    boolean hasUpdate = isNewerVersion(currentVersion, tagName);

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
                    Logger.e(TAG, "Failed to parse GitHub release JSON: " + Logger.formatException(e));
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

    // Show custom popup-styled dialog
    public static void showUpdateDialog(Context context, String newVersion, String releaseUrl) {
        if (context == null) return;

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

        String targetUrl = releaseUrl != null && !releaseUrl.isEmpty() ? releaseUrl : GITHUB_RELEASES_URL;

        btnOpenGithub.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Logger.e(TAG, "Failed to open release URL: " + Logger.formatException(e));
            }
            dialog.dismiss();
        });

        btnLater.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public static void openGitHubRepo(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to open GitHub repo: " + Logger.formatException(e));
        }
    }
}
