package com.vintro.wsplanner.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.services.GetPlanService;
import com.vintro.wsplanner.utils.Logger;

public class GetPlanWidget extends AppWidgetProvider {
    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Logger.d("GetPlanWidget.updateAppWidget", "Updating widget " + appWidgetId);
        PendingIntent pendingIntent = GetPlanWidget.createPendingIntent(context, appWidgetId);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.get_plan_widget);
        views.setImageViewResource(R.id.widget_download_button, R.drawable.icon_download);
        views.setOnClickPendingIntent(R.id.widget_download_button, pendingIntent);
        Logger.d("GetPlanWidget.updateAppWidget()", "onClick set");

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Logger.d("GetPlanWidget", "onUpdate called, ids count: " + appWidgetIds.length);

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static PendingIntent createPendingIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, GetPlanWidget.class);
        intent.setAction("com.vintro.wsplanner.ACTION_CLICK");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        Logger.d("GetPlanWidget.createPendingIntent()", "Intent created for widget " + appWidgetId + ": " + intent);

        return PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d("GetPlanWidget", "onReceive called, action: " + intent.getAction());
        super.onReceive(context, intent);
        if ("com.vintro.wsplanner.ACTION_CLICK".equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            Logger.d("GetPlanWidget.onReceive", "Click received from widget " + appWidgetId);
            Intent serviceIntent = new Intent(context, GetPlanService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            GetPlanService.enqueueWork(context, serviceIntent);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Logger.d("GetPlanWidget.onDeleted", "Deleting widget " + appWidgetId);
            PreferencesManager.deletePrefs(context, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {

    }

    @Override
    public void onDisabled(Context context) {

    }
}
