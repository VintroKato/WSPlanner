package com.vintro.wsplanner.ui.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
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

// app widget displaying current or upcoming lesson with bal-safe click actions
public class CurrentLessonWidget extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_LESSON = "com.vintro.wsplanner.ACTION_WIDGET_TOGGLE_LESSON";
    public static final String ACTION_REFRESH = "com.vintro.wsplanner.ACTION_WIDGET_REFRESH";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // update widget instances off the main thread and schedule next lesson transition alarm
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) return;
        Logger.d("CurrentLessonWidget.onUpdate", "onUpdate called for " + appWidgetIds.length + " widgets");
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
                Logger.e("CurrentLessonWidget.onUpdate", "Error updating widgets: " + Logger.formatException(e));
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }

    // respond to widget resizing or configuration options change
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> updateWidget(appContext, AppWidgetManager.getInstance(appContext), appWidgetId));
    }

    // clean up per-widget toggle preference when instances are removed
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        if (appWidgetIds == null) return;
        for (int widgetId : appWidgetIds) {
            PreferencesManager.clearWidgetForcedNext(context, widgetId);
        }
        Logger.d("CurrentLessonWidget.onDeleted", "Cleaned up state for " + appWidgetIds.length + " removed widget(s)");
    }

    // cancel transition alarms when no home screen widgets remain
    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (!WidgetUpdateManager.hasActiveWidgets(context)) {
            WidgetUpdateManager.cancelScheduledAlarm(context);
            Logger.i("CurrentLessonWidget.onDisabled", "Last widget removed; cancelled transition alarm");
        }
    }

    // handle non-activity widget broadcast actions (toggle and workmanager refresh)
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        Logger.d("CurrentLessonWidget.onReceive", "Received widget action: " + action);

        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        final Context appContext = context.getApplicationContext();

        if (ACTION_TOGGLE_LESSON.equals(action)) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
            final PendingResult pendingResult = goAsync();
            AppExecutors.runIo(() -> {
                try {
                    if (!WidgetActionHelper.isScheduleConfigured(appContext)) {
                        return;
                    }
                    LocalDate today = LocalDate.now();
                    LocalDateTime now = LocalDateTime.now();
                    DaySchedule schedule = ScheduleRepository.getInstance(appContext).getScheduleForDateSync(today);
                    Lesson current = schedule != null ? schedule.getCurrentLesson(now) : null;
                    Lesson next = schedule != null ? schedule.getNextLesson(now) : null;

                    if (current != null && next != null) {
                        boolean currentlyNext = PreferencesManager.isWidgetShowingForcedNext(appContext, widgetId);
                        PreferencesManager.setWidgetShowingForcedNext(appContext, widgetId, !currentlyNext);
                    }
                    updateWidget(appContext, AppWidgetManager.getInstance(appContext), widgetId);
                } catch (Exception e) {
                    Logger.e("CurrentLessonWidget.onReceive", "Error toggling widget lesson: " + Logger.formatException(e));
                } finally {
                    try {
                        pendingResult.finish();
                    } catch (Exception ignored) {
                    }
                }
            });
        } else if (ACTION_REFRESH.equals(action)) {
            if (!WidgetActionHelper.isScheduleConfigured(appContext)) {
                return;
            }
            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                Toast.makeText(appContext, R.string.no_internet_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            Logger.d("CurrentLessonWidget.onReceive", "Manual widget refresh requested via WorkManager");
            Toast.makeText(appContext, R.string.widget_syncing, Toast.LENGTH_SHORT).show();
            ScheduleSyncWorker.enqueueManualWidgetRefresh(appContext, false);
        }
    }

    // render current lesson widget layout and bind bal-safe tap targets
    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_current_lesson);
        applyTheme(context, views);

        // adapt padding for compact launcher grid cells
        Bundle options = appWidgetManager.getAppWidgetOptions(widgetId);
        int minHeight = 110;
        if (options != null) {
            minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            if (minHeight <= 0) {
                minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110);
            }
        }
        boolean isCompact = minHeight > 0 && minHeight < 95;
        int padHorizontal = dpToPx(context, isCompact ? 10 : 12);
        int padVertical = dpToPx(context, isCompact ? 8 : 11);
        views.setViewPadding(R.id.widget_root, padHorizontal, padVertical, padHorizontal, padVertical);

        // onboarding check
        if (!WidgetActionHelper.isScheduleConfigured(context)) {
            Logger.d("CurrentLessonWidget.updateWidget", "Widget " + widgetId + ": onboarding incomplete, showing setup prompt");
            views.setViewVisibility(R.id.widget_top_bar, View.GONE);
            views.setViewVisibility(R.id.widget_divider, View.GONE);
            views.setViewVisibility(R.id.widget_lesson_content, View.GONE);
            WidgetActionHelper.bindEmptyStateSetup(context, views);

            PendingIntent openApp = createOpenAppPendingIntent(context, widgetId);
            views.setOnClickPendingIntent(R.id.widget_root, openApp);
            views.setOnClickPendingIntent(R.id.widget_empty_state, openApp);
            appWidgetManager.updateAppWidget(widgetId, views);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        DaySchedule schedule;
        try {
            schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
        } catch (Exception e) {
            Logger.e("CurrentLessonWidget.updateWidget", "Failed loading schedule for widget: " + Logger.formatException(e));
            schedule = null;
        }

        Lesson currentLesson = schedule != null ? schedule.getCurrentLesson(now) : null;
        Lesson nextLesson = schedule != null ? schedule.getNextLesson(now) : null;
        boolean forcedNext = PreferencesManager.isWidgetShowingForcedNext(context, widgetId);

        Lesson targetLesson = null;
        boolean isCurrent = false;

        if (forcedNext) {
            targetLesson = nextLesson != null ? nextLesson : currentLesson;
            isCurrent = (targetLesson != null && targetLesson == currentLesson);
        } else {
            if (currentLesson != null) {
                targetLesson = currentLesson;
                isCurrent = true;
            } else if (nextLesson != null) {
                targetLesson = nextLesson;
                isCurrent = false;
            }
        }

        Logger.d("CurrentLessonWidget.updateWidget", "Widget " + widgetId + " update: targetLesson=" + (targetLesson != null ? targetLesson.getSubjectName() : "none") + " (isCurrent=" + isCurrent + ")");

        if (targetLesson != null) {
            views.setViewVisibility(R.id.widget_top_bar, View.VISIBLE);
            views.setViewVisibility(R.id.widget_divider, View.VISIBLE);
            views.setViewVisibility(R.id.widget_lesson_content, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty_state, View.GONE);

            views.setTextViewText(
                    R.id.widget_status_text,
                    context.getString(isCurrent ? R.string.schedule_badge_now : R.string.schedule_badge_next)
            );

            // format localized type prefix while omitting generic unknown type
            LessonType type = targetLesson.getResolvedLessonType();
            String typeStr = (type != null && type != LessonType.INNE) ? type.getLocalizedDisplayName(context) : "";
            String subject = targetLesson.getSubjectName() != null ? targetLesson.getSubjectName().trim() : "";
            if (!typeStr.isEmpty()) {
                String cleanType = typeStr.trim();
                if (!subject.toLowerCase(Locale.getDefault()).startsWith(cleanType.toLowerCase(Locale.getDefault()))) {
                    views.setTextViewText(R.id.widget_lesson_title, cleanType + " · " + subject);
                } else {
                    views.setTextViewText(R.id.widget_lesson_title, subject);
                }
            } else {
                views.setTextViewText(R.id.widget_lesson_title, subject);
            }

            String timeStr = "";
            if (targetLesson.getStartTime() != null && targetLesson.getEndTime() != null) {
                timeStr = targetLesson.getStartTime().format(TIME_FORMATTER) + " – " + targetLesson.getEndTime().format(TIME_FORMATTER);
            }
            views.setTextViewText(R.id.widget_lesson_time, timeStr);

            Location loc = targetLesson.getLocation();
            String locDisplay = loc != null ? loc.getDisplayText() : "WSPA";
            views.setTextViewText(R.id.widget_lesson_location, locDisplay);

            boolean canToggle = currentLesson != null && nextLesson != null;
            PendingIntent cardPending = createLessonClickPendingIntent(context, widgetId, targetLesson, canToggle);
            PendingIntent topPending = canToggle
                    ? createBroadcastPendingIntent(context, widgetId, ACTION_TOGGLE_LESSON)
                    : cardPending;

            views.setOnClickPendingIntent(R.id.widget_root, cardPending);
            views.setOnClickPendingIntent(R.id.widget_lesson_content, cardPending);
            views.setOnClickPendingIntent(R.id.widget_top_bar, topPending);
            views.setOnClickPendingIntent(R.id.widget_status_chip, topPending);

            if (loc != null && loc.getMapQueryUrl() != null && !loc.getMapQueryUrl().isEmpty()) {
                PendingIntent mapPending = createOpenMapsPendingIntent(context, widgetId, loc.getMapQueryUrl());
                views.setOnClickPendingIntent(R.id.widget_location_row, mapPending);
            } else {
                views.setOnClickPendingIntent(R.id.widget_location_row, cardPending);
            }
        } else {
            // empty state hides top bar and divider cleanly
            views.setViewVisibility(R.id.widget_top_bar, View.GONE);
            views.setViewVisibility(R.id.widget_divider, View.GONE);
            views.setViewVisibility(R.id.widget_lesson_content, View.GONE);

            String emptyTitle = (schedule != null && !schedule.isEmpty())
                    ? context.getString(R.string.widget_classes_done)
                    : context.getString(R.string.widget_no_classes_today);
            WidgetActionHelper.bindEmptyStateNoLessons(context, views, emptyTitle, now, isCompact ? 1 : 2);

            PendingIntent breakPending = createBreakActionPendingIntent(context, widgetId);
            views.setOnClickPendingIntent(R.id.widget_root, breakPending);
            views.setOnClickPendingIntent(R.id.widget_empty_state, breakPending);
        }

        appWidgetManager.updateAppWidget(widgetId, views);
    }

    private static void applyTheme(Context context, RemoteViews views) {
        boolean isLight = WidgetActionHelper.isWidgetLightTheme(context);
        if (isLight) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_light);
            views.setInt(R.id.widget_divider, "setBackgroundColor", ContextCompat.getColor(context, R.color.widget_divider_light));
            views.setInt(R.id.widget_status_chip, "setBackgroundResource", R.drawable.bg_badge_pill_light);
            views.setTextColor(R.id.widget_status_text, ContextCompat.getColor(context, R.color.widget_badge_type_text_light));
            views.setTextColor(R.id.widget_lesson_title, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_lesson_time, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_lesson_location, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
        } else {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_dark);
            views.setInt(R.id.widget_divider, "setBackgroundColor", ContextCompat.getColor(context, R.color.widget_divider_dark));
            views.setInt(R.id.widget_status_chip, "setBackgroundResource", R.drawable.bg_badge_pill);
            views.setTextColor(R.id.widget_status_text, ContextCompat.getColor(context, R.color.badge_now_text));
            views.setTextColor(R.id.widget_lesson_title, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_lesson_time, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_lesson_location, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
        }
        WidgetActionHelper.applyEmptyStateTheme(context, views, isLight);
    }

    private static PendingIntent createLessonClickPendingIntent(Context context, int widgetId, Lesson lesson, boolean canToggle) {
        String clickAction = PreferencesManager.getWidgetClickAction(context);
        if (PreferencesManager.WIDGET_CLICK_OPEN_DETAILS.equals(clickAction)) {
            if (lesson != null && lesson.getSubjectName() != null && !lesson.getSubjectName().isEmpty()) {
                return createOpenDetailsPendingIntent(context, widgetId, lesson);
            }
            return createBreakActionPendingIntent(context, widgetId);
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_APP.equals(clickAction)) {
            return createOpenAppPendingIntent(context, widgetId);
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_EXCEL.equals(clickAction)) {
            return createOpenExcelPendingIntent(context, widgetId);
        } else if (PreferencesManager.WIDGET_CLICK_REFRESH.equals(clickAction)) {
            return createBroadcastPendingIntent(context, widgetId, ACTION_REFRESH);
        } else {
            if (canToggle) {
                return createBroadcastPendingIntent(context, widgetId, ACTION_TOGGLE_LESSON);
            }
            return createBreakActionPendingIntent(context, widgetId);
        }
    }

    private static PendingIntent createBreakActionPendingIntent(Context context, int widgetId) {
        String breakAction = PreferencesManager.getWidgetBreakAction(context);
        if (PreferencesManager.WIDGET_BREAK_OPEN_EXCEL.equals(breakAction)) {
            return createOpenExcelPendingIntent(context, widgetId);
        }
        return createOpenAppPendingIntent(context, widgetId);
    }

    private static PendingIntent createBroadcastPendingIntent(Context context, int widgetId, String action) {
        Intent intent = new Intent(context, CurrentLessonWidget.class);
        intent.setAction(action);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        return PendingIntent.getBroadcast(
                context,
                widgetId + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent createOpenAppPendingIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                context,
                widgetId + 100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent createOpenDetailsPendingIntent(Context context, int widgetId, Lesson lesson) {
        Intent detailsIntent = new Intent(context, SubjectDetailsActivity.class);
        detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
        detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
        detailsIntent.putExtra("subject_name", lesson.getSubjectName());
        detailsIntent.putExtra("lesson_type", lesson.getLessonType());
        detailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                widgetId + 2000,
                detailsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent createOpenExcelPendingIntent(Context context, int widgetId) {
        Intent intent = WidgetTrampolineActivity.createOpenExcelIntent(context);
        return PendingIntent.getActivity(
                context,
                widgetId + 3000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent createOpenMapsPendingIntent(Context context, int widgetId, String mapQuery) {
        Intent intent = WidgetTrampolineActivity.createOpenMapsIntent(context, mapQuery);
        return PendingIntent.getActivity(
                context,
                widgetId + 5000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static int dpToPx(Context context, float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }
}
