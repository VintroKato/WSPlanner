package com.vintro.wsplanner.services;

import android.content.Context;

import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.utils.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;


public class AppUpgradeManager {
    private static final String TAG = "AppUpgradeManager";

    public static void onAppStart(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        int lastVersionCode = PreferencesManager.getLastRunVersionCode(appContext);
        int currentVersionCode = BuildConfig.VERSION_CODE;

        // Clean up any downloaded update APKs in background to free storage
        new Thread(() -> UpdateManager.cleanupDownloadedApks(appContext)).start();

        if (currentVersionCode > lastVersionCode) {
            Logger.i(TAG, "Application version change detected: " + lastVersionCode + " -> " + currentVersionCode);

            // Migrations for existing installs upgrading to v1.0.2
            if (lastVersionCode <= 1000002) {
                PreferencesManager.migrateLegacyStudentData(appContext);
                sanitizeLegacyLogsOnce(appContext);
            }

            PreferencesManager.setLastRunVersionCode(appContext, currentVersionCode);
            Logger.d(TAG, "Version migration check finished. Updated recorded version code to " + currentVersionCode);
        }
    }

    private static void sanitizeLegacyLogsOnce(Context context) {
        if (PreferencesManager.isLogsSanitized(context)) {
            return;
        }
        new Thread(() -> {
            try {
                Logger.flush();
                File logFile = Logger.getLogFile(context);
                if (logFile != null && logFile.exists() && logFile.length() > 0) {
                    File parentDir = logFile.getParentFile();
                    File tempFile = new File(parentDir, "app_log_sanitized.txt");
                    try (BufferedReader reader = new BufferedReader(new FileReader(logFile));
                         FileWriter writer = new FileWriter(tempFile, false)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.write(Logger.sanitizeLogMessage(line));
                            writer.write("\n");
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        if (logFile.delete()) {
                            tempFile.renameTo(logFile);
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
        }).start();
    }
}
