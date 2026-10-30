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

    public static String maskSurname(String surname) {
        if (surname == null) return "";
        String s = surname.trim();
        if (s.isEmpty()) return "";
        if (s.length() == 1) return s + "*";
        if (s.length() == 2) return s.charAt(0) + "*" + s.charAt(1);
        StringBuilder sb = new StringBuilder();
        sb.append(s.charAt(0));
        for (int i = 0; i < s.length() - 2; i++) {
            sb.append('*');
        }
        sb.append(s.charAt(s.length() - 1));
        return sb.toString();
    }

    // format exception to string ensuring class name and cause details are never null
    public static String formatException(Throwable t) {
        if (t == null) return "Unknown error (null exception)";
        String className = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg != null && !msg.trim().isEmpty() && !"null".equalsIgnoreCase(msg.trim())) {
            return className + ": " + msg.trim();
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && !causeMsg.trim().isEmpty() && !"null".equalsIgnoreCase(causeMsg.trim())) {
                return className + " (cause: " + cause.getClass().getSimpleName() + ": " + causeMsg.trim() + ")";
            }
            return className + " (cause: " + cause.getClass().getSimpleName() + ")";
        }
        String toStr = t.toString();
        if (toStr != null && !toStr.trim().isEmpty() && !"null".equalsIgnoreCase(toStr.trim())) {
            return toStr;
        }
        return className;
    }

    // convenience alias to get safe error description without null output
    public static String getErrorMessage(Throwable t) {
        return formatException(t);
    }

    // sanitize log message to prevent null error output and mask sensitive data
    public static String sanitizeLogMessage(String message) {
        if (message == null) return "[null]";
        String cleaned = message;
        if (cleaned.contains("Error: null")) {
            cleaned = cleaned.replace("Error: null", "Error: [Unknown/No message]");
        }
        if (cleaned.contains("error: null")) {
            cleaned = cleaned.replace("error: null", "error: [Unknown/No message]");
        }
        if (cleaned.contains(": null")) {
            cleaned = cleaned.replace(": null", ": [No message]");
        }
        return maskSensitiveData(cleaned);
    }

    // mask password credentials, emails, and student surnames in logs
    public static String maskSensitiveData(String message) {
        if (message == null || message.isEmpty()) return "";
        
        // mask passwords
        String masked = PASSWORD_PATTERN.matcher(message).replaceAll("$1=***");

        // mask emails: keep first up to 3 chars, mask the rest up to the domain ending
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
        masked = sb.toString();

        // mask student surnames: e.g. Kowalski -> K******i in standard log patterns
        Pattern surnamePattern = Pattern.compile("(?i)(surname\\s*=\\s*['\"]?|StudentSurname:\\s*['\"]|for student\\s*['\"]|surname:\\s*['\"]|student\\s*['\"])([a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ]{2,})(['\"]?)");
        Matcher surnameMatcher = surnamePattern.matcher(masked);
        StringBuffer surnameSb = new StringBuffer();
        while (surnameMatcher.find()) {
            String prefix = surnameMatcher.group(1);
            String name = surnameMatcher.group(2);
            String suffix = surnameMatcher.group(3);
            surnameMatcher.appendReplacement(surnameSb, Matcher.quoteReplacement(prefix + maskSurname(name) + suffix));
        }
        surnameMatcher.appendTail(surnameSb);
        return surnameSb.toString();
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
            String err = formatException(e);
            try {
                Log.e("Logger.trim", "Error trimming log file: " + err);
            } catch (Throwable ignored) {
                System.err.println("Logger.trim: Error trimming log file: " + err);
            }
        }
    }

    // append formatted log record to local file
    private static synchronized void writeToFile(String level, String tag, String message) {
        if (logFile == null) return;

        trimLogFileIfNeeded();

        String safeMsg = sanitizeLogMessage(message);
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

    // write debug log
    public static void d(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.d(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[DEBUG|" + tag + "]: " + safe);
        }
        writeToFile("DEBUG", tag, safe);
    }

    // write informational log
    public static void i(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.i(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[INFO|" + tag + "]: " + safe);
        }
        writeToFile("INFO", tag, safe);
    }

    // write warning log
    public static void w(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.w(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[WARN|" + tag + "]: " + safe);
        }
        writeToFile("WARN", tag, safe);
    }

    // write warning log with exception details
    public static void w(String tag, String msg, Throwable t) {
        String detail = (t != null) ? (msg + " | Exception: " + formatException(t)) : msg;
        w(tag, detail);
    }

    // write error log
    public static void e(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.e(tag, safe);
        } catch (Throwable ignored) {
            System.err.println("[ERROR|" + tag + "]: " + safe);
        }
        writeToFile("ERROR", tag, safe);
    }

    // write error log with exception details
    public static void e(String tag, String msg, Throwable t) {
        String detail = (t != null) ? (msg + " | Exception: " + formatException(t)) : msg;
        e(tag, detail);
    }

    // write verbose log
    public static void v(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.v(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[VERBOSE|" + tag + "]: " + safe);
        }
        writeToFile("VERBOSE", tag, safe);
    }

    // write assert failure log
    public static void wtf(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.wtf(tag, safe);
        } catch (Throwable ignored) {
            System.err.println("[WTF|" + tag + "]: " + safe);
        }
        writeToFile("WTF", tag, safe);
    }
}
