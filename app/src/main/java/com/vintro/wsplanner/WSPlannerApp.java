package com.vintro.wsplanner;

import android.app.Application;
import android.os.Build;

import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

// application entry point initializing logging, global error handling, language, and theme
public class WSPlannerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // initialize file-based logger
        Logger.init(this);

        // install top-level uncaught exception handler to capture all runtime crashes into app logs
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String errorDetails = Logger.formatException(throwable);
            Logger.e("WSPlannerApp.uncaughtException", "CRASH: Uncaught exception in thread '" + thread.getName() + "': " + errorDetails, throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        // log startup device and system details
        Logger.i("WSPlannerApp.onCreate", "Application initialized on Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ", Device: " + Build.MANUFACTURER + " " + Build.MODEL + ")");

        // apply saved language and visual theme
        UIHelper.setSelectedLanguage(this);
        UIHelper.setSelectedTheme(this);
        Logger.d("WSPlannerApp.onCreate", "Theme and language configuration applied successfully");
    }
}
