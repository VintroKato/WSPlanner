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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import okhttp3.ResponseBody;

public class GetPlanService extends JobIntentService {
    public static final String outputFileName = "plan";
    private Intent workIntent;

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, GetPlanService.class, 1000, work);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d("GetPlanService.onCreate", "GetPlanService created");
    }

    @Override
    protected void onHandleWork(Intent intent) {
        int course = PreferencesManager.getYearPref(this, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
        Logger.d("GetPlanService.onHandleWork", "Handling work for course year " + course + ", intent: " + intent);

        workIntent = intent;

        if (!isNetworkAvailable()) {
            Logger.w("GetPlanService.onHandleWork", "No network connection available");
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

    private File saveToCache(ResponseBody fileResponse, int course) {
        File outputFile = new File(getCacheDir(), outputFileName + course + ".xlsx");
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

    private void endService() {
        updateWidget(false);
        Logger.d("GetPlanService.endService", "Ending GetPlanService work");
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }
}
