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
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.ui.activities.MainActivity;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// app widget displaying current or upcoming lesson
public class CurrentLessonWidget extends AppWidgetProvider {

    public static final String ACTION_TOGGLE_LESSON = "com.vintro.wsplanner.ACTION_WIDGET_TOGGLE_LESSON";
    public static final String ACTION_CARD_CLICK = "com.vintro.wsplanner.ACTION_WIDGET_CARD_CLICK";
    public static final String ACTION_OPEN_EXCEL = "com.vintro.wsplanner.ACTION_WIDGET_OPEN_EXCEL";
    public static final String ACTION_REFRESH = "com.vintro.wsplanner.ACTION_WIDGET_REFRESH";
    public static final String ACTION_OPEN_MAPS = "com.vintro.wsplanner.ACTION_WIDGET_OPEN_MAPS";
    public static final String EXTRA_MAP_QUERY = "extra_map_query";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // update all widget instances and reschedule next alarm
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Logger.d("CurrentLessonWidget.onUpdate", "onUpdate called for " + appWidgetIds.length + " widgets");
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
        WidgetUpdateManager.scheduleNextAlarm(context);
    }

    // respond to widget resizing or configuration options change
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    // handle widget user interactions and broadcast actions
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;
        Logger.d("CurrentLessonWidget.onReceive", "Received widget action: " + action);

        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (ACTION_TOGGLE_LESSON.equals(action)) {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                if (!PreferencesManager.isOnboardingCompleted(context)
                        || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
                    openMainActivity(context);
                    return;
                }
                LocalDate today = LocalDate.now();
                LocalDateTime now = LocalDateTime.now();
                DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
                Lesson current = schedule != null ? schedule.getCurrentLesson(now) : null;
                Lesson next = schedule != null ? schedule.getNextLesson(now) : null;

                if (current != null && next != null) {
                    boolean currentlyNext = PreferencesManager.isWidgetShowingForcedNext(context, widgetId);
                    PreferencesManager.setWidgetShowingForcedNext(context, widgetId, !currentlyNext);
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    updateWidget(context, appWidgetManager, widgetId);
                } else if (current != null || next != null) {
                    Lesson target = current != null ? current : next;
                    openLessonDetails(context, target);
                } else {
                    openMainActivity(context);
                }
            }
        } else if (ACTION_CARD_CLICK.equals(action)) {
            handleCardClick(context, widgetId);
        } else if (ACTION_OPEN_EXCEL.equals(action)) {
            WidgetActionHelper.openExcelSchedule(context);
        } else if (ACTION_REFRESH.equals(action)) {
            // trigger background load with force refresh
            Logger.d("CurrentLessonWidget.onReceive", "Manual widget refresh requested");
            ScheduleRepository.getInstance(context).getScheduleForDate(LocalDate.now(), true, new ScheduleRepository.ScheduleCallback<DaySchedule>() {
                @Override
                public void onSuccess(DaySchedule result) {
                    Logger.i("CurrentLessonWidget.onReceive", "Widget refresh succeeded, updating all widgets");
                    WidgetUpdateManager.updateAllWidgets(context);
                }

                @Override
                public void onError(Exception e) {
                    Logger.w("CurrentLessonWidget.onReceive", "Widget refresh failed (offline fallback): " + e.getMessage());
                }
            });
        } else if (ACTION_OPEN_MAPS.equals(action)) {
            String query = intent.getStringExtra(EXTRA_MAP_QUERY);
            WidgetActionHelper.openLocationInMaps(context, query);
        }
    }

    // route card tap based on user preference or toggle current/next
    private void handleCardClick(Context context, int widgetId) {
        if (!PreferencesManager.isOnboardingCompleted(context)
                || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
            openMainActivity(context);
            return;
        }

        String clickAction = PreferencesManager.getWidgetClickAction(context);
        Logger.d("CurrentLessonWidget.handleCardClick", "Handling card click with configured action: " + clickAction);

        if (PreferencesManager.WIDGET_CLICK_OPEN_DETAILS.equals(clickAction)) {
            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();
            DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
            boolean showForcedNext = (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) && PreferencesManager.isWidgetShowingForcedNext(context, widgetId);

            Lesson current = schedule != null ? schedule.getCurrentLesson(now) : null;
            Lesson next = schedule != null ? schedule.getNextLesson(now) : null;
            Lesson target = null;
            if (current != null && !showForcedNext) {
                target = current;
            } else if (next != null) {
                target = next;
            } else if (current != null) {
                target = current;
            }

            if (target != null) {
                openLessonDetails(context, target);
            } else {
                performBreakAction(context);
            }
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_APP.equals(clickAction)) {
            openMainActivity(context);
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_EXCEL.equals(clickAction)) {
            WidgetActionHelper.openExcelSchedule(context);
        } else if (PreferencesManager.WIDGET_CLICK_REFRESH.equals(clickAction)) {
            ScheduleRepository.getInstance(context).getScheduleForDate(LocalDate.now(), true, new ScheduleRepository.ScheduleCallback<DaySchedule>() {
                @Override
                public void onSuccess(DaySchedule result) {
                    Logger.i("CurrentLessonWidget.handleCardClick", "Widget refresh succeeded, updating all widgets");
                    WidgetUpdateManager.updateAllWidgets(context);
                }

                @Override
                public void onError(Exception e) {
                    Logger.w("CurrentLessonWidget.handleCardClick", "Widget refresh failed (offline fallback): " + e.getMessage());
                }
            });
        } else {
            // Default: TOGGLE
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                LocalDate today = LocalDate.now();
                LocalDateTime now = LocalDateTime.now();
                DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
                Lesson current = schedule != null ? schedule.getCurrentLesson(now) : null;
                Lesson next = schedule != null ? schedule.getNextLesson(now) : null;

                if (current != null && next != null) {
                    boolean currentlyNext = PreferencesManager.isWidgetShowingForcedNext(context, widgetId);
                    PreferencesManager.setWidgetShowingForcedNext(context, widgetId, !currentlyNext);
                    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                    updateWidget(context, appWidgetManager, widgetId);
                } else if (current != null || next != null) {
                    Lesson target = current != null ? current : next;
                    openLessonDetails(context, target);
                } else {
                    performBreakAction(context);
                }
            } else {
                openMainActivity(context);
            }
        }
    }

    private static void performBreakAction(Context context) {
        String breakAction = PreferencesManager.getWidgetBreakAction(context);
        if (PreferencesManager.WIDGET_BREAK_OPEN_EXCEL.equals(breakAction)) {
            WidgetActionHelper.openExcelSchedule(context);
        } else {
            openMainActivity(context);
        }
    }

    private static void openLessonDetails(Context context, Lesson lesson) {
        if (lesson != null && lesson.getSubjectName() != null && !lesson.getSubjectName().isEmpty()) {
            Intent detailsIntent = new Intent(context, SubjectDetailsActivity.class);
            detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
            detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
            detailsIntent.putExtra("subject_name", lesson.getSubjectName());
            detailsIntent.putExtra("lesson_type", lesson.getLessonType());
            detailsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(detailsIntent);
        } else {
            openMainActivity(context);
        }
    }

    // render current lesson widget layout and configure tap targets
    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_current_lesson);

        // Apply Background Theme
        applyTheme(context, views);

        // Click listeners on whole card, content, and chip
        views.setOnClickPendingIntent(R.id.widget_root, createActionPendingIntent(context, widgetId, ACTION_CARD_CLICK));
        views.setOnClickPendingIntent(R.id.widget_lesson_content, createActionPendingIntent(context, widgetId, ACTION_CARD_CLICK));
        views.setOnClickPendingIntent(R.id.widget_status_chip, createActionPendingIntent(context, widgetId, ACTION_TOGGLE_LESSON));
        views.setOnClickPendingIntent(R.id.widget_empty_state, createOpenAppPendingIntent(context, widgetId));

        // Onboarding Check
        if (!PreferencesManager.isOnboardingCompleted(context)
                || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
            Logger.d("CurrentLessonWidget.updateWidget", "Widget " + widgetId + ": onboarding incomplete, showing setup prompt");
            views.setViewVisibility(R.id.widget_lesson_content, View.GONE);
            views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE);
            views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_setup_required_title));
            views.setTextViewText(R.id.widget_empty_subtitle, context.getString(R.string.widget_setup_required_desc));
            appWidgetManager.updateAppWidget(widgetId, views);
            return;
        }

        // Load Schedule
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
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
            views.setViewVisibility(R.id.widget_lesson_content, View.VISIBLE);
            views.setViewVisibility(R.id.widget_empty_state, View.GONE);

            // Status chip text (clean badge without time text breaking layout)
            if (isCurrent) {
                views.setTextViewText(R.id.widget_status_text, context.getString(R.string.schedule_badge_now));
            } else {
                views.setTextViewText(R.id.widget_status_text, context.getString(R.string.schedule_badge_next));
            }

            // Format Subject Name with Type as prefix: "Type · Subject" (avoid duplicate if subject already starts with type)
            LessonType type = targetLesson.getResolvedLessonType();
            String typeStr = type != null ? type.getDisplayName() : (targetLesson.getLessonType() != null ? targetLesson.getLessonType() : "");
            String subject = targetLesson.getSubjectName() != null ? targetLesson.getSubjectName().trim() : "";
            if (typeStr != null && !typeStr.trim().isEmpty()) {
                String cleanType = typeStr.trim();
                if (!subject.toLowerCase(Locale.getDefault()).startsWith(cleanType.toLowerCase(Locale.getDefault()))) {
                    views.setTextViewText(R.id.widget_lesson_title, cleanType + " · " + subject);
                } else {
                    views.setTextViewText(R.id.widget_lesson_title, subject);
                }
            } else {
                views.setTextViewText(R.id.widget_lesson_title, subject);
            }

            // Time in top right
            String timeStr = "";
            if (targetLesson.getStartTime() != null && targetLesson.getEndTime() != null) {
                timeStr = targetLesson.getStartTime().format(TIME_FORMATTER) + " – " + targetLesson.getEndTime().format(TIME_FORMATTER);
            }
            views.setTextViewText(R.id.widget_lesson_time, timeStr);

            // Location
            Location loc = targetLesson.getLocation();
            String locDisplay = loc != null ? loc.getDisplayText() : "WSPA";
            views.setTextViewText(R.id.widget_lesson_location, locDisplay);

            // If offsite with map URL, clicking location row opens maps
            if (loc != null && loc.getMapQueryUrl() != null && !loc.getMapQueryUrl().isEmpty()) {
                Intent mapIntent = new Intent(context, CurrentLessonWidget.class);
                mapIntent.setAction(ACTION_OPEN_MAPS);
                mapIntent.putExtra(EXTRA_MAP_QUERY, loc.getMapQueryUrl());
                PendingIntent mapPending = PendingIntent.getBroadcast(
                        context,
                        widgetId + 5000,
                        mapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                views.setOnClickPendingIntent(R.id.widget_location_row, mapPending);
            }

        } else {
            // Empty State
            views.setViewVisibility(R.id.widget_lesson_content, View.GONE);
            views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE);

            if (schedule != null && !schedule.isEmpty()) {
                views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_classes_done));
                views.setTextViewText(R.id.widget_empty_subtitle, context.getString(R.string.schedule_empty_subtitle));
            } else {
                views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_no_classes_today));
                views.setTextViewText(R.id.widget_empty_subtitle, context.getString(R.string.schedule_empty_subtitle));
            }
        }

        appWidgetManager.updateAppWidget(widgetId, views);
    }

    private static void applyTheme(Context context, RemoteViews views) {
        String theme = PreferencesManager.getWidgetTheme(context);
        if (PreferencesManager.WIDGET_THEME_LIGHT.equals(theme)) {
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_light);
            views.setInt(R.id.widget_divider, "setBackgroundColor", ContextCompat.getColor(context, R.color.widget_divider_light));
            views.setTextColor(R.id.widget_lesson_title, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_lesson_time, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_lesson_location, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
            views.setTextColor(R.id.widget_empty_title, ContextCompat.getColor(context, R.color.widget_text_light_primary));
            views.setTextColor(R.id.widget_empty_subtitle, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
        } else {
            // Default: Dark
            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_widget_dark);
            views.setInt(R.id.widget_divider, "setBackgroundColor", ContextCompat.getColor(context, R.color.widget_divider_dark));
            views.setTextColor(R.id.widget_lesson_title, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_lesson_time, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_lesson_location, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
            views.setTextColor(R.id.widget_empty_title, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
            views.setTextColor(R.id.widget_empty_subtitle, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
        }
    }

    private static PendingIntent createActionPendingIntent(Context context, int widgetId, String action) {
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                widgetId + 100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static void openMainActivity(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
