package com.vintro.wsplanner.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
            Log.e("Logger.init", "Error getting file: " + e.toString());
        }
    }

    private static void writeToFile(String level, String tag, String message) {
        if (logFile == null) return;

        String time = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
        String fullMessage = String.format("%s | [%s|%s]: %s\n", time, level, tag, message);

        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(fullMessage);
        } catch (IOException e) {
            Log.e("Logger.writeToFile", "Error writing to file: " + e.toString());
        }
    }
    public static void d(String tag, String msg) {
        Log.d(tag, msg);
        writeToFile("DEBUG", tag, msg);
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        writeToFile("INFO", tag, msg);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        writeToFile("WARN", tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(tag, msg);
        writeToFile("ERROR", tag, msg);
    }

    public static void v(String tag, String msg) {
        Log.v(tag, msg);
        writeToFile("VERBOSE", tag, msg);
    }

    public static void wtf(String tag, String msg) {
        Log.wtf(tag, msg);
        writeToFile("WTF", tag, msg);
    }
}
