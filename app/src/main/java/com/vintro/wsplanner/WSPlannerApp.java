package com.vintro.wsplanner;

import android.app.Application;

import com.vintro.wsplanner.utils.Logger;

public class WSPlannerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
    }
}
