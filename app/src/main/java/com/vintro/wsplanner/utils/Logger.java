package com.vintro.wsplanner.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vintro.wsplanner.R;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Logger {
    private static volatile File logFile;
    private static final Object FILE_LOCK = new Object();
    private static final SimpleDateFormat LOG_DATE_FORMAT =
            new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS", Locale.getDefault());
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wsplanner-logger");
        thread.setDaemon(true);
        return thread;
    });

    public static void init(Context context) {
        if (logFile != null || context == null) return;

        synchronized (FILE_LOCK) {
            if (logFile != null) return;
            try {
                File baseDir = context.getExternalFilesDir(null);
                if (baseDir == null) {
                    baseDir = context.getFilesDir();
                }
                File dir = new File(baseDir, "logs");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File targetFile = new File(dir, "app_log.txt");

                if (!targetFile.exists()) {
                    targetFile.createNewFile();
                }
                logFile = targetFile;
            } catch (IOException e) {
                try {
                    Log.e("Logger.init", "Error getting file: " + e.toString());
                } catch (Throwable ignored) {
                    System.err.println("Logger.init: Error getting file: " + e.toString());
                }
            }
        }
    }

    // wait briefly for queued log entries to finish writing to disk
    public static void flush() {
        if (logFile == null) return;
        try {
            Future<?> future = LOG_EXECUTOR.submit(() -> {});
            future.get(2, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
        }
    }

    public static File getLogFile(Context context) {
        if (logFile == null && context != null) {
            init(context);
        }
        return logFile;
    }

    public static void shareLogFile(Context context) {
        if (context == null) {
            w("Logger.shareLogFile", "Context is null, cannot proceed with sharing log file");
            return;
        }
        init(context);
        flush();

        if (logFile == null) {
            w("Logger.shareLogFile", "Log file handle is null");
            Toast.makeText(context, R.string.logs_empty_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!logFile.exists()) {
            w("Logger.shareLogFile", "Log file does not exist at: " + logFile.getAbsolutePath());
            Toast.makeText(context, R.string.logs_empty_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        long fileLen = logFile.length();
        if (fileLen == 0) {
            w("Logger.shareLogFile", "Log file is empty (0 bytes) at: " + logFile.getAbsolutePath());
            Toast.makeText(context, R.string.logs_empty_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        i("Logger.shareLogFile", "Initiating log sharing: path=" + logFile.getAbsolutePath() + ", size=" + fileLen + " bytes, lastModified=" + logFile.lastModified());

        try {
            String authority = context.getPackageName() + ".provider";
            Uri uri = FileProvider.getUriForFile(
                    context,
                    authority,
                    logFile
            );
            i("Logger.shareLogFile", "Generated FileProvider content URI: " + uri + " (authority=" + authority + ")");

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_logs_subject));
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.share_logs_chooser_title)
            );
            if (!(context instanceof Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(chooser);
            i("Logger.shareLogFile", "Opened Android system share chooser for log file successfully (" + fileLen + " bytes)");
        } catch (Exception e) {
            e("Logger.shareLogFile", "Failed to launch log share chooser: " + formatException(e), e);
            Toast.makeText(context, R.string.share_logs_error_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([a-zA-Z0-9_.+-]{1,3})([a-zA-Z0-9_.+-]*)@([a-zA-Z0-9-.]+)(\\.[a-zA-Z]{2,})");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^&\\s,]+");
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}*]+");

    // Patterns to detect student names / full names / surnames in log messages
    private static final Pattern QUOTED_NAME_PATTERN = Pattern.compile(
            "(?i)((?:StudentName|FullName|studentFullName|student_name|student_surname|studentSurname|surname|Full\\s*name|Student\\s*name|Student\\s*surname|for\\s+student|student)\\s*[:=]\\s*|for\\s+student\\s+)(['\"])([^'\"\\r\\n]+)\\2"
    );

    private static final Pattern SCRAPED_NAME_PATTERN = Pattern.compile(
            "(?i)(Name extracted from (?:dashboard|files(?:\\.php)?):\\s*)(['\"]?)([^'\"\\r\\n]+)\\2"
    );

    private static final Pattern SETTING_NAME_PATTERN = Pattern.compile(
            "(?i)(Setting (?:global )?student (?:name|surname):\\s*)(['\"]?)([^'\"\\r\\n]+)\\2"
    );

    private static final Pattern UNQUOTED_NAME_KEY_PATTERN = Pattern.compile(
            "(?i)((?:StudentName|FullName|studentFullName|student_name|student_surname|studentSurname|surname|Full\\s*name|Student\\s*name|Student\\s*surname)\\s*[:=]\\s*)(?!['\"])([^,\\r\\n;\\)]+)"
    );

    private static final Pattern UNQUOTED_FOR_STUDENT_PATTERN = Pattern.compile(
            "(?i)(for\\s+student\\s+)(?!['\"])([^,\\r\\n;\\)]+)"
    );

    // mask a single word keeping first and last characters
    // return already masked words as-is to avoid double masking
    public static String maskWord(String word) {
        if (word == null) return "";
        String s = word.trim();
        if (s.isEmpty()) return "";
        if (s.contains("*") || s.equalsIgnoreCase("null") || s.equalsIgnoreCase("none")) {
            return s;
        }
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

    // mask first name, surname, or full name by keeping first and last letters of each word
    // handles single names ("Kowalski" -> "K******i"), full names ("Jan Kowalski" -> "J*n K******i"), and hyphenated names
    public static String maskName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.equalsIgnoreCase("null") || trimmed.equalsIgnoreCase("none")) {
            return trimmed;
        }
        try {
            Matcher matcher = WORD_PATTERN.matcher(name);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String word = matcher.group();
                matcher.appendReplacement(sb, Matcher.quoteReplacement(maskWord(word)));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Throwable ignored) {
            return name;
        }
    }

    public static String maskSurname(String surname) {
        return maskName(surname);
    }

    public static String maskFullName(String fullName) {
        return maskName(fullName);
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
        try {
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
        } catch (Throwable ignored) {
            return message;
        }
    }

    // mask password credentials, emails, and student names/surnames in logs
    public static String maskSensitiveData(String message) {
        if (message == null || message.isEmpty()) return "";

        try {
            // mask passwords
            String masked = PASSWORD_PATTERN.matcher(message).replaceAll("$1=***");

            // mask emails: keep first up to 3 chars, mask the rest up to the domain ending
            Matcher emailMatcher = EMAIL_PATTERN.matcher(masked);
            StringBuffer emailSb = new StringBuffer();
            while (emailMatcher.find()) {
                String prefix = emailMatcher.group(1);
                String middle = emailMatcher.group(2);
                String domain = emailMatcher.group(3);
                String tld = emailMatcher.group(4);
                int maskLen = Math.max(middle != null ? middle.length() : 3, 3);
                StringBuilder starsSb = new StringBuilder();
                for (int i = 0; i < maskLen; i++) {
                    starsSb.append('*');
                }
                String maskedEmail = prefix + starsSb.toString() + "@" + (domain.length() > 2 ? domain.substring(0, 2) + "***" : "***") + tld;
                emailMatcher.appendReplacement(emailSb, Matcher.quoteReplacement(maskedEmail));
            }
            emailMatcher.appendTail(emailSb);
            masked = emailSb.toString();

            // mask quoted student names like StudentName='Jan Kowalski' or for student 'Kowalski'
            Matcher quotedMatcher = QUOTED_NAME_PATTERN.matcher(masked);
            StringBuffer quotedSb = new StringBuffer();
            while (quotedMatcher.find()) {
                String prefix = quotedMatcher.group(1);
                String quote = quotedMatcher.group(2);
                String rawName = quotedMatcher.group(3);
                quotedMatcher.appendReplacement(quotedSb, Matcher.quoteReplacement(prefix + quote + maskName(rawName) + quote));
            }
            quotedMatcher.appendTail(quotedSb);
            masked = quotedSb.toString();

            // mask scraped student names like "Name extracted from dashboard: Jan Kowalski"
            Matcher scrapedMatcher = SCRAPED_NAME_PATTERN.matcher(masked);
            StringBuffer scrapedSb = new StringBuffer();
            while (scrapedMatcher.find()) {
                String prefix = scrapedMatcher.group(1);
                String quote = scrapedMatcher.group(2);
                String rawName = scrapedMatcher.group(3);
                String q = (quote != null) ? quote : "";
                scrapedMatcher.appendReplacement(scrapedSb, Matcher.quoteReplacement(prefix + q + maskName(rawName) + q));
            }
            scrapedMatcher.appendTail(scrapedSb);
            masked = scrapedSb.toString();

            // mask settings student names like "Setting global student name: Jan Kowalski"
            Matcher settingMatcher = SETTING_NAME_PATTERN.matcher(masked);
            StringBuffer settingSb = new StringBuffer();
            while (settingMatcher.find()) {
                String prefix = settingMatcher.group(1);
                String quote = settingMatcher.group(2);
                String rawName = settingMatcher.group(3);
                String q = (quote != null) ? quote : "";
                settingMatcher.appendReplacement(settingSb, Matcher.quoteReplacement(prefix + q + maskName(rawName) + q));
            }
            settingMatcher.appendTail(settingSb);
            masked = settingSb.toString();

            // mask unquoted student key-values like StudentName=Jan Kowalski
            Matcher unquotedMatcher = UNQUOTED_NAME_KEY_PATTERN.matcher(masked);
            StringBuffer unquotedSb = new StringBuffer();
            while (unquotedMatcher.find()) {
                String prefix = unquotedMatcher.group(1);
                String rawName = unquotedMatcher.group(2);
                unquotedMatcher.appendReplacement(unquotedSb, Matcher.quoteReplacement(prefix + maskName(rawName)));
            }
            unquotedMatcher.appendTail(unquotedSb);
            masked = unquotedSb.toString();

            // mask unquoted "for student": e.g. for student Kowalski
            Matcher forStudentMatcher = UNQUOTED_FOR_STUDENT_PATTERN.matcher(masked);
            StringBuffer forStudentSb = new StringBuffer();
            while (forStudentMatcher.find()) {
                String prefix = forStudentMatcher.group(1);
                String rawName = forStudentMatcher.group(2);
                forStudentMatcher.appendReplacement(forStudentSb, Matcher.quoteReplacement(prefix + maskName(rawName)));
            }
            forStudentMatcher.appendTail(forStudentSb);

            return forStudentSb.toString();
        } catch (Throwable ignored) {
            return message;
        }
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

    // queue file write on background logger thread so callers never block on disk io
    private static void enqueueFileWrite(String level, String tag, String safeMsg) {
        if (logFile == null) return;
        long timestampMillis = System.currentTimeMillis();
        try {
            LOG_EXECUTOR.execute(() -> writeToFileSync(timestampMillis, level, tag, safeMsg));
        } catch (Throwable ignored) {
        }
    }

    // append formatted log record to local file synchronously on logger thread
    private static void writeToFileSync(long timestampMillis, String level, String tag, String safeMsg) {
        File targetFile = logFile;
        if (targetFile == null) return;

        synchronized (FILE_LOCK) {
            trimLogFileIfNeeded();

            String time = LOG_DATE_FORMAT.format(new Date(timestampMillis));
            String fullMessage = String.format("%s | [%s|%s]: %s\n", time, level, tag, safeMsg);

            try (FileWriter writer = new FileWriter(targetFile, true)) {
                writer.write(fullMessage);
            } catch (IOException e) {
                try {
                    Log.e("Logger.writeToFile", "Error writing to file: " + e.toString());
                } catch (Throwable ignored) {
                    System.err.println("Logger.writeToFile: Error writing to file: " + e.toString());
                }
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
        enqueueFileWrite("DEBUG", tag, safe);
    }

    // write informational log
    public static void i(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.i(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[INFO|" + tag + "]: " + safe);
        }
        enqueueFileWrite("INFO", tag, safe);
    }

    // write warning log
    public static void w(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.w(tag, safe);
        } catch (Throwable ignored) {
            System.out.println("[WARN|" + tag + "]: " + safe);
        }
        enqueueFileWrite("WARN", tag, safe);
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
        enqueueFileWrite("ERROR", tag, safe);
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
        enqueueFileWrite("VERBOSE", tag, safe);
    }

    // write assert failure log
    public static void wtf(String tag, String msg) {
        String safe = sanitizeLogMessage(msg);
        try {
            Log.wtf(tag, safe);
        } catch (Throwable ignored) {
            System.err.println("[WTF|" + tag + "]: " + safe);
        }
        enqueueFileWrite("WTF", tag, safe);
    }
}
