package com.vintro.wsplanner.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.services.ScheduleSyncWorker;
import com.vintro.wsplanner.ui.activities.MainActivity;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.ui.activities.WidgetTrampolineActivity;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

// app widget displaying full daily lesson schedule list with bal-safe click targets
public class DailyScheduleWidget extends AppWidgetProvider {

    public static final String ACTION_DAILY_REFRESH = "com.vintro.wsplanner.ACTION_DAILY_REFRESH";

    // visual states of the header refresh button
    public enum RefreshState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR
    }

    private static final long FEEDBACK_DISPLAY_MS = 1500L;
    private static final AtomicReference<RefreshState> currentRefreshState = new AtomicReference<>(RefreshState.IDLE);

    // update all daily schedule widget instances off the main thread and schedule next alarm
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        Logger.d("DailyScheduleWidget.onUpdate", "onUpdate called for " + appWidgetIds.length + " widgets");
        final Context appContext = context.getApplicationContext();
        final int[] idsCopy = appWidgetIds.clone();
        final PendingResult pendingResult = goAsync();
        AppExecutors.runIo(() -> {
            try {
                AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
                for (int widgetId : idsCopy) {
                    updateWidget(appContext, manager, widgetId);
                }
                WidgetUpdateManager.scheduleNextAlarm(appContext);
            } catch (Exception e) {
                Logger.e("DailyScheduleWidget.onUpdate", "Error updating daily widgets: " + Logger.formatException(e));
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }

    // respond to widget resizing or options change
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> updateWidget(appContext, AppWidgetManager.getInstance(appContext), appWidgetId));
    }

    // cancel widget transition alarm when no widgets remain on the home screen
    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (!WidgetUpdateManager.hasActiveWidgets(context)) {
            WidgetUpdateManager.cancelScheduledAlarm(context);
            Logger.i("DailyScheduleWidget.onDisabled", "Last widget removed; cancelled transition alarm");
        }
    }

    // process refresh button broadcast via workmanager with double-tap guard
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        Logger.d("DailyScheduleWidget.onReceive", "Received widget action: " + action);

        if (ACTION_DAILY_REFRESH.equals(action)) {
            final Context appContext = context.getApplicationContext();
            if (!WidgetActionHelper.isScheduleConfigured(appContext)) {
                return;
            }
            if (!currentRefreshState.compareAndSet(RefreshState.IDLE, RefreshState.LOADING)) {
                Logger.d("DailyScheduleWidget.onReceive", "Manual refresh already in progress, ignoring double tap");
                return;
            }

            Logger.d("DailyScheduleWidget.onReceive", "Daily widget manual refresh requested");

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                Toast.makeText(appContext, R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
                setRefreshState(appContext, RefreshState.ERROR);
                final PendingResult pendingResult = goAsync();
                AppExecutors.mainHandler().postDelayed(() -> {
                    setRefreshState(appContext, RefreshState.IDLE);
                    try {
                        pendingResult.finish();
                    } catch (Exception ignored) {
                    }
                }, FEEDBACK_DISPLAY_MS);
                return;
            }

            // show themed spinner immediately while workmanager performs the sync
            setRefreshState(appContext, RefreshState.LOADING);
            ScheduleSyncWorker.enqueueManualWidgetRefresh(appContext, true);
        }
    }

    // update refresh button state and partially redraw all daily widget instances
    public static void setRefreshState(Context context, RefreshState state) {
        RefreshState target = state != null ? state : RefreshState.IDLE;
        currentRefreshState.set(target);
        if (context == null) return;
        try {
            Context appContext = context.getApplicationContext();
            AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
            int[] widgetIds = manager.getAppWidgetIds(new ComponentName(appContext, DailyScheduleWidget.class));
            if (widgetIds == null || widgetIds.length == 0) return;

            for (int id : widgetIds) {
                RemoteViews views = new RemoteViews(appContext.getPackageName(), R.layout.widget_daily_schedule);
                applyRefreshStateViews(appContext, views, target);
                manager.partiallyUpdateAppWidget(id, views);
            }
        } catch (Exception e) {
            Logger.w("DailyScheduleWidget.setRefreshState", "Failed updating refresh state to " + target + ": " + Logger.formatException(e));
        }
    }

    // apply refresh icon, themed spinner, check, or error visibility to remote views
    private static void applyRefreshStateViews(Context context, RemoteViews views, RefreshState state) {
        boolean isLight = WidgetActionHelper.isWidgetLightTheme(context);
        views.setViewVisibility(R.id.widget_daily_refresh_icon, state == RefreshState.IDLE ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_refresh_progress, state == RefreshState.LOADING && !isLight ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_refresh_progress_light, state == RefreshState.LOADING && isLight ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_refresh_check, state == RefreshState.SUCCESS ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_refresh_error, state == RefreshState.ERROR ? View.VISIBLE : View.GONE);
    }

    // render daily schedule widget layout, header, and bal-safe remote list adapter
    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_daily_schedule);
        applyTheme(context, views);

        // build date formatter dynamically with the active locale
        LocalDate today = LocalDate.now();
        Locale locale = Locale.getDefault();
        String formattedDate = today.format(DateTimeFormatter.ofPattern("EEE, dd.MM", locale));
        if (!formattedDate.isEmpty()) {
            formattedDate = formattedDate.substring(0, 1).toUpperCase(locale) + formattedDate.substring(1);
        }
        views.setTextViewText(R.id.widget_daily_title, formattedDate);
        views.setViewVisibility(R.id.widget_daily_subtitle, View.GONE);

        PendingIntent appPendingIntent = createOpenAppPendingIntent(context, widgetId);

        // onboarding check hides header action buttons cleanly
        if (!WidgetActionHelper.isScheduleConfigured(context)) {
            views.setViewVisibility(R.id.widget_daily_btn_excel, View.GONE);
            views.setViewVisibility(R.id.widget_daily_btn_refresh, View.GONE);
            views.setViewVisibility(R.id.widget_daily_btn_app, View.GONE);
            views.setViewVisibility(R.id.widget_daily_list, View.GONE);
            WidgetActionHelper.bindEmptyStateSetup(context, views);

            views.setOnClickPendingIntent(R.id.widget_daily_root, appPendingIntent);
            views.setOnClickPendingIntent(R.id.widget_empty_state, appPendingIntent);
            appWidgetManager.updateAppWidget(widgetId, views);
            return;
        }

        DaySchedule schedule;
        try {
            schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
        } catch (Exception e) {
            Logger.e("DailyScheduleWidget.updateWidget", "Failed loading schedule for daily widget: " + Logger.formatException(e));
            schedule = null;
        }

        int lessonCount = schedule != null ? schedule.getLessonCount() : 0;
        Logger.d("DailyScheduleWidget.updateWidget", "Updating widget " + widgetId + " (lessonCount=" + lessonCount + ")");
        if (lessonCount > 0) {
            views.setViewVisibility(R.id.widget_daily_list, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty_state, View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_daily_list, View.GONE);
            WidgetActionHelper.bindEmptyStateNoLessons(
                    context,
                    views,
                    context.getString(R.string.widget_no_classes_today),
                    LocalDateTime.now(),
                    2
            );
        }

        boolean showExcel = PreferencesManager.isWidgetShowExcel(context);
        boolean showRefresh = PreferencesManager.isWidgetShowRefresh(context);
        boolean showApp = PreferencesManager.isWidgetShowApp(context);

        views.setViewVisibility(R.id.widget_daily_btn_excel, showExcel ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_daily_btn_refresh, showRefresh ? View.VISIBLE : View.GONE);
        applyRefreshStateViews(context, views, currentRefreshState.get());
        views.setViewVisibility(R.id.widget_daily_btn_app, showApp ? View.VISIBLE : View.GONE);

        // bind bal-safe activity pending intents for excel and app launch
        PendingIntent excelPending = PendingIntent.getActivity(
                context,
                widgetId + 4000,
                WidgetTrampolineActivity.createOpenExcelIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_daily_btn_excel, excelPending);
        views.setOnClickPendingIntent(R.id.widget_daily_btn_refresh, createBroadcastPendingIntent(context, widgetId, ACTION_DAILY_REFRESH));
        views.setOnClickPendingIntent(R.id.widget_daily_btn_app, appPendingIntent);
        views.setOnClickPendingIntent(R.id.widget_empty_state, appPendingIntent);

        // remote adapter setup for lesson list
        Intent serviceIntent = new Intent(context, DailyScheduleRemoteViewsService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_daily_list, serviceIntent);

        // direct activity template pending intent so list clicks comply with android background launch limits
        String dailyAction = PreferencesManager.getDailyWidgetLessonAction(context);
        Intent templateIntent;
        if (PreferencesManager.DAILY_WIDGET_CLICK_OPEN_APP.equals(dailyAction)) {
            templateIntent = new Intent(context, MainActivity.class);
            templateIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
            templateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            templateIntent = new Intent(context, SubjectDetailsActivity.class);
            templateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        PendingIntent itemClickPending = PendingIntent.getActivity(
                context,
                widgetId + 300,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_daily_list, itemClickPending);

        // update widget views first so samsung and huawei launchers bind the adapter before notifying data change
        appWidgetManager.updateAppWidget(widgetId, views);
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_daily_list);
    }

    private static void applyTheme(Context context, RemoteViews views) {
        boolean isLight = WidgetActionHelper.isWidgetLightTheme(context);
        int actionIconColor = ContextCompat.getColor(context, isLight ? R.color.widget_text_light_primary : R.color.widget_text_dark_primary);

        if (isLight) {
            views.setInt(R.id.widget_daily_root, "setBackgroundResource", R.drawable.bg_widget_light);
            views.setTextColor(R.id.widget_daily_title, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_daily_subtitle, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
        } else {
            views.setInt(R.id.widget_daily_root, "setBackgroundResource", R.drawable.bg_widget_dark);
            views.setTextColor(R.id.widget_daily_title, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_daily_subtitle, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
        }
        WidgetActionHelper.applyEmptyStateTheme(context, views, isLight);
        views.setInt(R.id.widget_daily_refresh_icon, "setColorFilter", actionIconColor);
        views.setInt(R.id.widget_daily_btn_app, "setColorFilter", actionIconColor);
    }

    private static PendingIntent createOpenAppPendingIntent(Context context, int widgetId) {
        Intent appIntent = new Intent(context, MainActivity.class);
        appIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                widgetId + 200,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent createBroadcastPendingIntent(Context context, int widgetId, String action) {
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
