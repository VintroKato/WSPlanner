package com.vintro.wsplanner.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Logger {
    private static File logFile;
    public static void init(Context context) {
        if (logFile != null) return;

        try {
            File dir = new File(context.getExternalFilesDir(null), "logs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            logFile = new File(dir, "app_log.txt");

            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            try {
                Log.e("Logger.init", "Error getting file: " + e.toString());
            } catch (Throwable ignored) {
                System.err.println("Logger.init: Error getting file: " + e.toString());
            }
        }
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([a-zA-Z0-9_.+-]{1,3})([a-zA-Z0-9_.+-]*)@([a-zA-Z0-9-.]+)(\\.[a-zA-Z]{2,})");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^&\\s,]+");

    public static String maskSensitiveData(String message) {
        if (message == null || message.isEmpty()) return "";
        
        // Mask passwords
        String masked = PASSWORD_PATTERN.matcher(message).replaceAll("$1=***");

        // Mask emails: keep first up to 3 chars, mask the rest up to the domain ending
        Matcher emailMatcher = EMAIL_PATTERN.matcher(masked);
        StringBuffer sb = new StringBuffer();
        while (emailMatcher.find()) {
            String prefix = emailMatcher.group(1);
            String middle = emailMatcher.group(2);
            String domain = emailMatcher.group(3);
            String tld = emailMatcher.group(4);
            int maskLen = Math.max(middle != null ? middle.length() : 3, 3);
            String stars = "*".repeat(maskLen);
            String maskedEmail = prefix + stars + "@" + (domain.length() > 2 ? domain.substring(0, 2) + "***" : "***") + tld;
            emailMatcher.appendReplacement(sb, Matcher.quoteReplacement(maskedEmail));
        }
        emailMatcher.appendTail(sb);
        return sb.toString();
    }

    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024L; // 10 MB

    private static void trimLogFileIfNeeded() {
        if (logFile == null || !logFile.exists() || logFile.length() <= MAX_LOG_SIZE) return;

        try {
            File parentDir = logFile.getParentFile();
            File tempFile = new File(parentDir, "app_log_temp.txt");
            long keepBytes = MAX_LOG_SIZE / 5;
            long skipBytes = Math.max(0, logFile.length() - keepBytes);

            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile, "r");
                 FileWriter writer = new FileWriter(tempFile)) {
                raf.seek(skipBytes);
                // Skip the first partial line
                raf.readLine();

                writer.write("--- [LOG ROTATED: OLDEST ENTRIES TRIMMED TO MAINTAIN 10MB LIMIT] ---\n");
                String line;
                while ((line = raf.readLine()) != null) {
                    writer.write(line);
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
        } catch (Exception e) {
            try {
                Log.e("Logger.trim", "Error trimming log file: " + e.getMessage());
            } catch (Throwable ignored) {
                System.err.println("Logger.trim: Error trimming log file: " + e.getMessage());
            }
        }
    }

    private static synchronized void writeToFile(String level, String tag, String message) {
        if (logFile == null) return;

        trimLogFileIfNeeded();

        String safeMsg = maskSensitiveData(message);
        String time = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
        String fullMessage = String.format("%s | [%s|%s]: %s\n", time, level, tag, safeMsg);

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(fullMessage);
        } catch (IOException e) {
            try {
                Log.e("Logger.writeToFile", "Error writing to file: " + e.toString());
            } catch (Throwable ignored) {
                System.err.println("Logger.writeToFile: Error writing to file: " + e.toString());
            }
        }
    }

    public static void d(String tag, String msg) {
        String safe = maskSensitiveData(msg);
        try {
            Log.d(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[DEBUG|" + tag + "]: " + safe);
        }
        writeToFile("DEBUG", tag, safe);
    }

    public static void i(String tag, String msg) {
        String safe = maskSensitiveData(msg);
        try {
            Log.i(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[INFO|" + tag + "]: " + safe);
        }
        writeToFile("INFO", tag, safe);
    }

    public static void w(String tag, String msg) {
        String safe = maskSensitiveData(msg);
        try {
            Log.w(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[WARN|" + tag + "]: " + safe);
        }
        writeToFile("WARN", tag, safe);
    }

    public static void e(String tag, String msg) {
        String safe = maskSensitiveData(msg);
        try {
            Log.e(tag, safe);
        } catch (Throwable ignored) {
            System.err.println("[ERROR|" + tag + "]: " + safe);
        }
        writeToFile("ERROR", tag, safe);
    }

    public static void v(String tag, String msg) {
        String safe = maskSensitiveData(msg);
        try {
            Log.v(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[VERBOSE|" + tag + "]: " + safe);
        }
        writeToFile("VERBOSE", tag, safe);
    }

    public static void wtf(String tag, String msg) {
        String safe = maskSensitiveData(msg);
        try {
            Log.wtf(tag, safe);
        } catch (Throwable ignored) {
            System.err.println("[WTF|" + tag + "]: " + safe);
        }
        writeToFile("WTF", tag, safe);
    }
}
