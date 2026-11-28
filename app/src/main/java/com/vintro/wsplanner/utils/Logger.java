package com.vintro.wsplanner.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vintro.wsplanner.R;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// asynchronous utf-8 file logger keeping all log levels enabled while masking sensitive data off the ui thread
public class Logger {
    private static final long MAX_LOG_SIZE = 10 * 1024 * 1024L;
    private static final int FLUSH_BATCH_SIZE = 10;

    private static volatile File logFile;
    private static BufferedWriter bufferedWriter;
    private static int writesSinceFlush = 0;
    private static final Object FILE_LOCK = new Object();
    private static final SimpleDateFormat LOG_DATE_FORMAT =
            new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS", Locale.getDefault());
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wsplanner-logger");
        thread.setDaemon(true);
        return thread;
    });

    // initialize internal log file without blocking on external storage lookup
    public static void init(Context context) {
        if (logFile != null || context == null) return;

        synchronized (FILE_LOCK) {
            if (logFile != null) return;
            try {
                File dir = new File(context.getApplicationContext().getFilesDir(), "logs");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File targetFile = new File(dir, "app_log.txt");
                if (!targetFile.exists()) {
                    targetFile.createNewFile();
                }
                logFile = targetFile;
            } catch (IOException e) {
                logFallbackError("Logger.init", "Error getting file: " + e.toString());
            }
        }
    }

    // one-time migration copying legacy external logs into internal filesDir
    public static void migrateLegacyLogsIfNeeded(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        LOG_EXECUTOR.execute(() -> {
            try {
                init(appContext);
                File extBase = appContext.getExternalFilesDir(null);
                if (extBase == null) return;
                File legacyLog = new File(new File(extBase, "logs"), "app_log.txt");
                if (!legacyLog.exists() || legacyLog.length() == 0) return;

                synchronized (FILE_LOCK) {
                    File target = logFile;
                    if (target != null && target.length() < 1024) {
                        closeWriterLocked();
                        Files.copy(legacyLog.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    legacyLog.delete();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    // wait briefly for queued log entries and buffered writes to flush to disk
    public static void flush() {
        if (logFile == null) return;
        try {
            Future<?> future = LOG_EXECUTOR.submit(() -> {
                synchronized (FILE_LOCK) {
                    flushWriterLocked();
                }
            });
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

    // flush logs off the main thread and launch the system share sheet on the main thread
    public static void shareLogFile(Context context) {
        if (context == null) {
            w("Logger.shareLogFile", "Context is null, cannot proceed with sharing log file");
            return;
        }
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> {
            init(appContext);
            flush();

            File target = logFile;
            if (target == null || !target.exists() || target.length() == 0) {
                w("Logger.shareLogFile", "Log file missing or empty");
                AppExecutors.runOnMain(() -> Toast.makeText(appContext, R.string.logs_empty_toast, Toast.LENGTH_SHORT).show());
                return;
            }

            long fileLen = target.length();
            i("Logger.shareLogFile", "Initiating log sharing: path=" + target.getAbsolutePath() + ", size=" + fileLen + " bytes");

            AppExecutors.runOnMain(() -> {
                try {
                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                    }
                    String authority = appContext.getPackageName() + ".provider";
                    Uri uri = FileProvider.getUriForFile(appContext, authority, target);

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, appContext.getString(R.string.share_logs_subject));
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    Intent chooser = Intent.createChooser(
                            shareIntent,
                            appContext.getString(R.string.share_logs_chooser_title)
                    );
                    if (!(context instanceof Activity)) {
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    context.startActivity(chooser);
                    i("Logger.shareLogFile", "Opened Android system share chooser for log file (" + fileLen + " bytes)");
                } catch (Exception e) {
                    e("Logger.shareLogFile", "Failed to launch log share chooser: " + formatException(e), e);
                    Toast.makeText(appContext, R.string.share_logs_error_toast, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([a-zA-Z0-9_.+-]{1,3})([a-zA-Z0-9_.+-]*)@([a-zA-Z0-9-.]+)(\\.[a-zA-Z]{2,})");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^&\\s,]+");
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}*]+");

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

    // mask first name, surname, or full name keeping first and last letters of each word
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

    // mask password credentials, emails, and student names or surnames in logs
    public static String maskSensitiveData(String message) {
        if (message == null || message.isEmpty()) return "";

        try {
            String masked = PASSWORD_PATTERN.matcher(message).replaceAll("$1=***");

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

            Matcher unquotedMatcher = UNQUOTED_NAME_KEY_PATTERN.matcher(masked);
            StringBuffer unquotedSb = new StringBuffer();
            while (unquotedMatcher.find()) {
                String prefix = unquotedMatcher.group(1);
                String rawName = unquotedMatcher.group(2);
                unquotedMatcher.appendReplacement(unquotedSb, Matcher.quoteReplacement(prefix + maskName(rawName)));
            }
            unquotedMatcher.appendTail(unquotedSb);
            masked = unquotedSb.toString();

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

    private static void flushWriterLocked() {
        if (bufferedWriter != null) {
            try {
                bufferedWriter.flush();
                writesSinceFlush = 0;
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeWriterLocked() {
        if (bufferedWriter != null) {
            try {
                bufferedWriter.flush();
                bufferedWriter.close();
            } catch (IOException ignored) {
            } finally {
                bufferedWriter = null;
                writesSinceFlush = 0;
            }
        }
    }

    // utf-8 safe log rotation trimming oldest entries while keeping the newest 2 mb intact
    private static void trimLogFileIfNeededLocked() {
        if (logFile == null || !logFile.exists() || logFile.length() <= MAX_LOG_SIZE) return;

        closeWriterLocked();
        try {
            File parentDir = logFile.getParentFile();
            File tempFile = new File(parentDir, "app_log_temp.txt");
            long keepBytes = MAX_LOG_SIZE / 5;
            long skipBytes = Math.max(0, logFile.length() - keepBytes);

            try (FileInputStream fis = new FileInputStream(logFile);
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(new FileOutputStream(tempFile, false), StandardCharsets.UTF_8))) {
                long remaining = skipBytes;
                while (remaining > 0) {
                    long skipped = fis.skip(remaining);
                    if (skipped <= 0) break;
                    remaining -= skipped;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
                    // discard potentially truncated first utf-8 line after seek
                    reader.readLine();
                    writer.write("--- [Log rotated: oldest entries trimmed to maintain 10MB limit] ---\n");
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (!tempFile.renameTo(logFile)) {
                    Files.move(tempFile.toPath(), logFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                tempFile.delete();
            }
        } catch (Exception e) {
            logFallbackError("Logger.trim", "Error trimming log file: " + formatException(e));
        }
    }

    // offload regex masking, logcat emission, and buffered utf-8 file writes to background logger thread
    private static void enqueueLog(int priority, String level, String tag, String rawMsg, boolean flushImmediately) {
        final long timestampMillis = System.currentTimeMillis();
        try {
            LOG_EXECUTOR.execute(() -> {
                String safe = sanitizeLogMessage(rawMsg);
                emitLogcat(priority, level, tag, safe);
                writeToFileSync(timestampMillis, level, tag, safe, flushImmediately);
            });
        } catch (Throwable ignored) {
        }
    }

    private static void emitLogcat(int priority, String level, String tag, String safe) {
        try {
            switch (priority) {
                case Log.VERBOSE:
                    Log.v(tag, safe);
                    break;
                case Log.DEBUG:
                    Log.d(tag, safe);
                    break;
                case Log.INFO:
                    Log.i(tag, safe);
                    break;
                case Log.WARN:
                    Log.w(tag, safe);
                    break;
                case Log.ERROR:
                    Log.e(tag, safe);
                    break;
                case Log.ASSERT:
                    Log.wtf(tag, safe);
                    break;
                default:
                    Log.d(tag, safe);
                    break;
            }
        } catch (Throwable ignored) {
            if (priority >= Log.ERROR) {
                System.err.println("[" + level + "|" + tag + "]: " + safe);
            } else {
                System.out.println("[" + level + "|" + tag + "]: " + safe);
            }
        }
    }

    private static void writeToFileSync(long timestampMillis, String level, String tag, String safeMsg, boolean flushImmediately) {
        File targetFile = logFile;
        if (targetFile == null) return;

        synchronized (FILE_LOCK) {
            trimLogFileIfNeededLocked();

            String time = LOG_DATE_FORMAT.format(new Date(timestampMillis));
            String fullMessage = time + " | [" + level + "|" + tag + "]: " + safeMsg + "\n";

            try {
                if (bufferedWriter == null) {
                    bufferedWriter = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(targetFile, true), StandardCharsets.UTF_8),
                            8192
                    );
                }
                bufferedWriter.write(fullMessage);
                writesSinceFlush++;
                if (flushImmediately || writesSinceFlush >= FLUSH_BATCH_SIZE) {
                    flushWriterLocked();
                }
            } catch (IOException e) {
                closeWriterLocked();
                logFallbackError("Logger.writeToFile", "Error writing to file: " + e.toString());
            }
        }
    }

    private static void logFallbackError(String tag, String msg) {
        try {
            Log.e(tag, msg);
        } catch (Throwable ignored) {
            System.err.println(tag + ": " + msg);
        }
    }

    public static void d(String tag, String msg) {
        enqueueLog(Log.DEBUG, "DEBUG", tag, msg, false);
    }

    public static void i(String tag, String msg) {
        enqueueLog(Log.INFO, "INFO", tag, msg, false);
    }

    public static void w(String tag, String msg) {
        enqueueLog(Log.WARN, "WARN", tag, msg, false);
    }

    public static void w(String tag, String msg, Throwable t) {
        String detail = (t != null) ? (msg + " | Exception: " + formatException(t)) : msg;
        w(tag, detail);
    }

    public static void e(String tag, String msg) {
        enqueueLog(Log.ERROR, "ERROR", tag, msg, true);
    }

    public static void e(String tag, String msg, Throwable t) {
        String detail = (t != null) ? (msg + " | Exception: " + formatException(t)) : msg;
        e(tag, detail);
    }

    public static void v(String tag, String msg) {
        enqueueLog(Log.VERBOSE, "VERBOSE", tag, msg, false);
    }

    public static void wtf(String tag, String msg) {
        enqueueLog(Log.ASSERT, "WTF", tag, msg, true);
    }
}
