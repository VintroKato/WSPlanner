package com.vintro.wsplanner.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.ui.activities.MainActivity;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// app widget displaying full daily lesson schedule list
public class DailyScheduleWidget extends AppWidgetProvider {

    public static final String ACTION_DAILY_REFRESH = "com.vintro.wsplanner.ACTION_DAILY_REFRESH";
    public static final String ACTION_DAILY_OPEN_EXCEL = "com.vintro.wsplanner.ACTION_DAILY_OPEN_EXCEL";
    public static final String ACTION_DAILY_ITEM_CLICK = "com.vintro.wsplanner.ACTION_DAILY_ITEM_CLICK";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd.MM");

    private static String formatLessonCount(Context context, int count) {
        String lang = Locale.getDefault().getLanguage();
        if ("ru".equals(lang) || "uk".equals(lang)) {
            int rem10 = count % 10;
            int rem100 = count % 100;
            String word;
            if ("ru".equals(lang)) {
                if (rem10 == 1 && rem100 != 11) word = "пара";
                else if (rem10 >= 2 && rem10 <= 4 && (rem100 < 10 || rem100 >= 20)) word = "пары";
                else word = "пар";
                return count + " " + word + " на сегодня";
            } else {
                if (rem10 == 1 && rem100 != 11) word = "пара";
                else if (rem10 >= 2 && rem10 <= 4 && (rem100 < 10 || rem100 >= 20)) word = "пари";
                else word = "пар";
                return count + " " + word + " на сьогодні";
            }
        } else if ("pl".equals(lang)) {
            if (count == 1) return "1 zajęcia na dzisiaj";
            return count + " zajęć na dzisiaj";
        }
        return count == 1 ? "1 class today" : count + " classes today";
    }

    // update all daily schedule widget instances and schedule next tick
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Logger.d("DailyScheduleWidget.onUpdate", "onUpdate called for " + appWidgetIds.length + " widgets");
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
        WidgetUpdateManager.scheduleNextAlarm(context);
    }

    // respond to widget resizing or options change
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    // process user actions from daily widget buttons and list items
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        Logger.d("DailyScheduleWidget.onReceive", "Received widget action: " + action);

        if (ACTION_DAILY_OPEN_EXCEL.equals(action)) {
            WidgetActionHelper.openExcelSchedule(context);
        } else if (ACTION_DAILY_REFRESH.equals(action)) {
            if (!PreferencesManager.isOnboardingCompleted(context)
                    || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
                openMainActivity(context);
                return;
            }
            Logger.d("DailyScheduleWidget.onReceive", "Daily widget manual refresh requested");
            ScheduleRepository.getInstance(context).getScheduleForDate(LocalDate.now(), true, new ScheduleRepository.ScheduleCallback<DaySchedule>() {
                @Override
                public void onSuccess(DaySchedule result) {
                    Logger.i("DailyScheduleWidget.onReceive", "Daily widget refresh completed successfully");
                    WidgetUpdateManager.updateAllWidgets(context);
                }

                @Override
                public void onError(Exception e) {
                    Logger.w("DailyScheduleWidget.onReceive", "Daily refresh error (offline fallback): " + e.getMessage());
                }
            });
        } else if (ACTION_DAILY_ITEM_CLICK.equals(action)) {
            String subjectName = intent.getStringExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME);
            if (subjectName == null) subjectName = intent.getStringExtra("subject_name");
            String lessonType = intent.getStringExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE);
            if (lessonType == null) lessonType = intent.getStringExtra("lesson_type");

            if (subjectName != null && !subjectName.isEmpty()) {
                Logger.d("DailyScheduleWidget.onReceive", "Opening lesson details for: " + subjectName);
                Intent detailsIntent = new Intent(context, SubjectDetailsActivity.class);
                detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, subjectName);
                detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lessonType);
                detailsIntent.putExtra("subject_name", subjectName);
                detailsIntent.putExtra("lesson_type", lessonType);
                detailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(detailsIntent);
            } else {
                openMainActivity(context);
            }
        }
    }

    private static void openMainActivity(Context context) {
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(mainIntent);
    }

    // render daily schedule widget layout, header and remote list adapter
    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_daily_schedule);

        // Apply Theme
        applyTheme(context, views);

        // Header Date & Subtitle
        LocalDate today = LocalDate.now();
        String formattedDate = today.format(DATE_FORMATTER);
        formattedDate = formattedDate.substring(0, 1).toUpperCase(Locale.getDefault()) + formattedDate.substring(1);
        views.setTextViewText(R.id.widget_daily_title, formattedDate);

        // Onboarding Check
        if (!PreferencesManager.isOnboardingCompleted(context)
                || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
            views.setTextViewText(R.id.widget_daily_subtitle, context.getString(R.string.widget_setup_required_title));
            views.setViewVisibility(R.id.widget_daily_list, View.GONE);
            views.setViewVisibility(R.id.widget_daily_empty, View.VISIBLE);
            views.setTextViewText(R.id.widget_daily_empty_text, context.getString(R.string.widget_setup_required_title));
            views.setTextViewText(R.id.widget_daily_empty_subtext, context.getString(R.string.widget_setup_required_desc));
            views.setViewVisibility(R.id.widget_daily_empty_subtext, View.VISIBLE);

            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent appPendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId + 200,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            views.setOnClickPendingIntent(R.id.widget_daily_empty, appPendingIntent);
            appWidgetManager.updateAppWidget(widgetId, views);
            return;
        }

        DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
        int lessonCount = schedule != null ? schedule.getLessonCount() : 0;
        Logger.d("DailyScheduleWidget.updateWidget", "Updating widget " + widgetId + " (lessonCount=" + lessonCount + ")");
        if (lessonCount > 0) {
            views.setTextViewText(R.id.widget_daily_subtitle, formatLessonCount(context, lessonCount));
            views.setViewVisibility(R.id.widget_daily_subtitle, View.VISIBLE);
            views.setViewVisibility(R.id.widget_daily_list, View.VISIBLE);
            views.setViewVisibility(R.id.widget_daily_empty, View.GONE);
        } else {
            views.setTextViewText(R.id.widget_daily_subtitle, "");
            views.setViewVisibility(R.id.widget_daily_subtitle, View.GONE);
            views.setViewVisibility(R.id.widget_daily_list, View.GONE);
            views.setViewVisibility(R.id.widget_daily_empty, View.VISIBLE);
            views.setTextViewText(R.id.widget_daily_empty_text, context.getString(R.string.widget_no_classes_today));
            views.setViewVisibility(R.id.widget_daily_empty_subtext, View.GONE);
        }

        // Action Buttons Setup
        boolean showExcel = PreferencesManager.isWidgetShowExcel(context);
        boolean showRefresh = PreferencesManager.isWidgetShowRefresh(context);
        boolean showApp = PreferencesManager.isWidgetShowApp(context);

        views.setViewVisibility(R.id.widget_daily_btn_excel, showExcel ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_btn_refresh, showRefresh ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_btn_app, showApp ? View.VISIBLE : View.GONE);

        views.setOnClickPendingIntent(R.id.widget_daily_btn_excel, createActionPendingIntent(context, widgetId, ACTION_DAILY_OPEN_EXCEL));
        views.setOnClickPendingIntent(R.id.widget_daily_btn_refresh, createActionPendingIntent(context, widgetId, ACTION_DAILY_REFRESH));

        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent appPendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 200,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_daily_btn_app, appPendingIntent);
        views.setOnClickPendingIntent(R.id.widget_daily_empty, appPendingIntent);

        // Remote Adapter Setup for ListView
        Intent serviceIntent = new Intent(context, DailyScheduleRemoteViewsService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_daily_list, serviceIntent);

        // Template Pending Intent for list items
        Intent itemClickIntent = new Intent(context, DailyScheduleWidget.class);
        itemClickIntent.setAction(ACTION_DAILY_ITEM_CLICK);
        itemClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        PendingIntent itemClickPending = PendingIntent.getBroadcast(
                context,
                widgetId + 300,
                itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_daily_list, itemClickPending);

        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_daily_list);
        appWidgetManager.updateAppWidget(widgetId, views);
    }

    private static void applyTheme(Context context, RemoteViews views) {
        String theme = PreferencesManager.getWidgetTheme(context);
        if (PreferencesManager.WIDGET_THEME_LIGHT.equals(theme)) {
            views.setInt(R.id.widget_daily_root, "setBackgroundResource", R.drawable.bg_widget_light);
            views.setTextColor(R.id.widget_daily_title, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_daily_subtitle, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
            views.setTextColor(R.id.widget_daily_empty_text, ContextCompat.getColor(context, R.color.widget_text_light_primary));
        } else {
            views.setInt(R.id.widget_daily_root, "setBackgroundResource", R.drawable.bg_widget_dark);
            views.setTextColor(R.id.widget_daily_title, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_daily_subtitle, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
            views.setTextColor(R.id.widget_daily_empty_text, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
        }
    }

    private static PendingIntent createActionPendingIntent(Context context, int widgetId, String action) {
        Intent intent = new Intent(context, DailyScheduleWidget.class);
        intent.setAction(action);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(
                context,
                widgetId + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
