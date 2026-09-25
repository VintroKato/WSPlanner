package com.vintro.wsplanner;

import android.app.Application;

import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

// application entry point initializing logging, language, and theme
public class WSPlannerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // initialize file-based logger
        Logger.init(this);
        Logger.i("WSPlannerApp.onCreate", "Application initialized");
        // apply saved language and theme
        UIHelper.setSelectedLanguage(this);
        UIHelper.setSelectedTheme(this);
    }
}
