package com.vintro.wsplanner.services;

import android.content.Context;

import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

// handles one-time application version migrations on cold start
public class AppUpgradeManager {
    private static final String TAG = "AppUpgradeManager";
    private static final int VERSION_CODE_LEGACY_SANITIZE = 1000002;
    private static final int VERSION_CODE_STORAGE_MIGRATION = 1004002;

    public static void onAppStart(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        int lastVersionCode = PreferencesManager.getLastRunVersionCode(appContext);
        int currentVersionCode = BuildConfig.VERSION_CODE;

        if (currentVersionCode <= lastVersionCode) {
            return;
        }

        Logger.i(TAG, "Cold-start version upgrade detected (" + lastVersionCode + " -> " + currentVersionCode + ")");

        // clean up downloaded apks and reset cached changelog after any app upgrade
        AppExecutors.runIo(() -> UpdateManager.cleanupDownloadedApks(appContext));
        UpdateManager.clearChangelogCacheOnUpgrade(appContext);

        // migrations for legacy installs upgrading from v1.0.2 or earlier
        if (lastVersionCode <= VERSION_CODE_LEGACY_SANITIZE) {
            PreferencesManager.migrateLegacyStudentData(appContext);
            sanitizeLegacyLogsOnce(appContext);
        }

        // migrations for installs upgrading from versions prior to v1.4.2
        if (lastVersionCode < VERSION_CODE_STORAGE_MIGRATION) {
            // move non-sensitive course config out of keystore into plain preferences
            PreferencesManager.migrateCourseConfigToPlainPrefs(appContext);
            // move cached offline schedule xlsx from cacheDir to filesDir so system cleaners keep it
            ScheduleRepository.migrateLegacyScheduleCache(appContext);
            // migrate legacy external log file into internal filesDir
            Logger.migrateLegacyLogsIfNeeded(appContext);
            Logger.i(TAG, "Storage migration for v1.4.2 completed");
        }

        PreferencesManager.setLastRunVersionCode(appContext, currentVersionCode);
        Logger.d(TAG, "Updated recorded version code to " + currentVersionCode);
    }

    private static void sanitizeLegacyLogsOnce(Context context) {
        if (PreferencesManager.isLogsSanitized(context)) {
            return;
        }
        AppExecutors.runIo(() -> {
            try {
                Logger.flush();
                File logFile = Logger.getLogFile(context);
                if (logFile != null && logFile.exists() && logFile.length() > 0) {
                    File parentDir = logFile.getParentFile();
                    File tempFile = new File(parentDir, "app_log_sanitized.txt");
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8));
                         BufferedWriter writer = new BufferedWriter(
                                 new OutputStreamWriter(new FileOutputStream(tempFile, false), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.write(Logger.sanitizeLogMessage(line));
                            writer.newLine();
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (!tempFile.renameTo(logFile)) {
                            if (logFile.delete()) {
                                tempFile.renameTo(logFile);
                            }
                        }
                    } else {
                        tempFile.delete();
                    }
                }
                PreferencesManager.setLogsSanitized(context, true);
                Logger.d(TAG, "Legacy logs sanitization completed successfully");
            } catch (Throwable t) {
                Logger.w(TAG, "Non-fatal error during one-time log sanitization: " + Logger.formatException(t));
            }
        });
    }
}
