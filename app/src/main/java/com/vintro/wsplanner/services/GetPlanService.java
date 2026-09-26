package com.vintro.wsplanner.services;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.JobIntentService;
import androidx.core.content.FileProvider;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.ui.widgets.GetPlanWidget;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.ResponseBody;

// background service to download study plan excel file for widgets
public class GetPlanService extends JobIntentService {
    public static final String outputFileName = "plan";
    private Intent workIntent;

    // enqueue background work execution
    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, GetPlanService.class, 1000, work);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d("GetPlanService.onCreate", "GetPlanService created");
    }

    // handle download or offline opening of study plan excel
    @Override
    protected void onHandleWork(Intent intent) {
        int course = PreferencesManager.getYearPref(this, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
        Logger.d("GetPlanService.onHandleWork", "Handling work for course year " + course + ", intent: " + intent);

        workIntent = intent;

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Logger.w("GetPlanService.onHandleWork", "Device is offline, checking for existing cached plan file");
            File dir = new File(getCacheDir(), "plans");
            File cachedFile = new File(dir, outputFileName + course + ".xlsx");
            if (cachedFile.exists() && cachedFile.length() > 0) {
                Logger.i("GetPlanService.onHandleWork", "Offline fallback: opening cached plan file: " + cachedFile.getAbsolutePath());
                openFile(cachedFile);
            } else {
                Logger.w("GetPlanService.onHandleWork", "No network connection and no cached plan file available for course: " + course);
            }
            endService();
            return;
        }

        updateWidget(true);

        try {
            ResponseBody fileResponse = PUW.downloadFile(workIntent, this);
            if (fileResponse == null) {
                Logger.e("GetPlanService.onHandleWork", "Downloaded file response is null");
                endService();
                return;
            }

            File outputFile = saveToCache(fileResponse, course);
            if (outputFile == null) {
                Logger.e("GetPlanService.onHandleWork", "Failed to save file to cache");
                endService();
                return;
            }

            openFile(outputFile);

        } catch (Exception e) {
            Logger.e("GetPlanService.onHandleWork", "Error during work execution: " + e.getMessage());
        }
        endService();
    }

    // save downloaded stream to cache directory
    private File saveToCache(ResponseBody fileResponse, int course) {
        File dir = new File(getCacheDir(), "plans");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File outputFile = new File(dir, outputFileName + course + ".xlsx");
        try (InputStream in = fileResponse.byteStream();
             FileOutputStream out = new FileOutputStream(outputFile)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Logger.e("GetPlanService.saveToCache", "Error saving file: " + e.getMessage());
            return null;
        }

        Logger.d("GetPlanService.saveToCache", "File saved: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    // open file with external excel viewer
    private void openFile(File outputFile) {
        try {
            Uri localUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", outputFile);
            Logger.d("GetPlanService.openFile", "Opening file via URI: " + localUri);

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setDataAndType(localUri, getContentResolver().getType(localUri));
            startActivity(i);
            Logger.i("GetPlanService.openFile", "File view intent started successfully");
        } catch (Exception e) {
            Logger.e("GetPlanService.openFile", "Error opening file: " + e.getMessage());
        }
    }

    // update widget remote views with progress state
    private void updateWidget(boolean loading) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.get_plan_widget);

        int appWidgetId = workIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (appWidgetId == -1) {
            Logger.e("GetPlanService.updateWidget", "Invalid widget ID in intent: " + workIntent);
            return;
        }

        Logger.d("GetPlanService.updateWidget", "Updating widget " + appWidgetId + " with loading state: " + loading);

        views.setViewVisibility(R.id.widget_progress_bar, loading ? View.VISIBLE : View.GONE);
        views.setBoolean(R.id.widget_download_button, "setEnabled", !loading);

        PendingIntent pendingIntent = GetPlanWidget.createPendingIntent(this, appWidgetId);

        views.setImageViewResource(R.id.widget_download_button, R.drawable.icon_download);
        views.setOnClickPendingIntent(R.id.widget_download_button, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // finish service and restore idle widget state
    private void endService() {
        updateWidget(false);
        Logger.d("GetPlanService.endService", "Ending GetPlanService work");
    }
}
