package com.vintro.wsplanner;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.JobIntentService;
import androidx.core.content.FileProvider;

import okhttp3.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetPlanService extends JobIntentService {
    public GetPlanService() {
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, GetPlanService.class, 1000, work);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
        Logger.d("GetPlanService", "GetPlanService started");

    }

    @Override
    protected void onHandleWork(Intent intent) {
        Logger.d("GetPlanService", "GetPlanService.onHandleWork, intent: " + intent);

        if (!isNetworkAvailable()) {
            Logger.w("GetPlanService", "No network connection");
            endService();
            return;
        }

        updateWidget(true);

        try {
            ResponseBody fileResponse = downloadFile(intent);
            if (fileResponse == null) {
                endService();
                return;
            }

            File outputFile = saveToCache(fileResponse);
            if (outputFile == null) {
                endService();
                return;
            }

            openFile(outputFile);


        } catch (Exception e) {
            Logger.e("GetPlanService", "Error: " + e.getMessage());
        }
        endService();
    }

    private ResponseBody downloadFile(Intent intent) {
        OkHttpClient client = Utils.login(intent, this);
        if (client == null) {
            Logger.e("GetPlanService", "Downloading file error: got null from Utils.login, intent: " + intent);
            return null;
        }

        Request fileRequest = new Request.Builder()
                .url(Utils.fileUrl)
                .build();

        try {
            Response fileResponse = client.newCall(fileRequest).execute();

            if (!fileResponse.isSuccessful()) {
                Logger.e("GetPlanService", "Downloading error: " + fileResponse.code());
                fileResponse.close();
                return null;
            }

            ResponseBody file = fileResponse.body();

            if (file == null || file.contentLength() == 0) {
                Logger.e("GetPlanService", "File body is null");
                fileResponse.close();
                return null;
            }

            Logger.d("GetPlanService", "File downloaded, size: " + file.contentLength());
            return file;

        } catch (Exception e) {
            Logger.e("GetPlanService", "Downloading error: " + e.getMessage());
            return null;
        }
    }

    private File saveToCache(ResponseBody fileResponse) {
        File outputFile = new File(getCacheDir(), Utils.outputFileName);
        try (InputStream in = fileResponse.byteStream();
             FileOutputStream out = new FileOutputStream(outputFile)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Logger.e("GetPlanService", "Error saving file: " + e.getMessage());
            return null;
        }

        Logger.d("GetPlanService", "File saved: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    private void openFile(File outputFile) {
        try {
            Uri localUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", outputFile);
            Logger.d("GetPlanService", "File uri: " + localUri);

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setDataAndType(localUri, getContentResolver().getType(localUri));
            startActivity(i);
            Logger.d("GetPlanService", "File opened");
        } catch (Exception e) {
            Logger.e("GetPlanService", "Error opening file: " + e.getMessage());
        }
    }

    private void updateWidget(boolean loading) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.get_plan_widget);

        views.setViewVisibility(R.id.widget_progress_bar, loading ? View.VISIBLE : View.GONE);
        views.setBoolean(R.id.widget_download_button, "setEnabled", !loading);

        ComponentName widget = new ComponentName(this, GetPlanWidget.class);
        appWidgetManager.updateAppWidget(widget, views);
    }

    private void endService() {
        updateWidget(false);
        Logger.d("GetPlanService", "Ending service");
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