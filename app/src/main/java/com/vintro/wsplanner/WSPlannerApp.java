package com.vintro.wsplanner;

import android.app.Application;

public class WSPlannerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
    }
}
