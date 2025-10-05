package com.vintro.wsplanner;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class GetPlanWidget extends AppWidgetProvider {

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        Intent intent = new Intent(context, GetPlanWidget.class);
        intent.setAction("com.vintro.wsplanner.ACTION_CLICK");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        Log.d("GetPlanWidget.updateAppWidget()", "Intent created: " + intent);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Log.d("GetPlanWidget.updateAppWidget()", "PendingIntent created: " + pendingIntent);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.get_plan_widget);
        views.setImageViewResource(R.id.widget_download_button, R.drawable.baseline_download_24);
        views.setOnClickPendingIntent(R.id.widget_download_button, pendingIntent);
        Log.d("GetPlanWidget.updateAppWidget()", "onClick set");

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.w("GetPlanWidget", "onUpdate called, ids count: " + appWidgetIds.length);

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("GetPlanWidget", "onReceive called, action: " + intent.getAction());
        super.onReceive(context, intent);
        if ("com.vintro.wsplanner.ACTION_CLICK".equals(intent.getAction())) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            Intent serviceIntent = new Intent(context, GetPlanService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            GetPlanService.enqueueWork(context, serviceIntent);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Utils.deletePrefs(context, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {

    }

    @Override
    public void onDisabled(Context context) {

    }
}