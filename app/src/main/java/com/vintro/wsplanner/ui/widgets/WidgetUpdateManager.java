package com.vintro.wsplanner.ui.widgets;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// receiver and coordinator scheduling exact alarms and updating all active widget instances
public class WidgetUpdateManager extends BroadcastReceiver {
    public static final String ACTION_SCHEDULE_ALARM_TICK = "com.vintro.wsplanner.ACTION_SCHEDULE_ALARM_TICK";
    public static final String ACTION_REFRESH_WIDGETS = "com.vintro.wsplanner.ACTION_REFRESH_WIDGETS";

    // trigger widget update and schedule the next alarm on wake
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Logger.d("WidgetUpdateManager.onReceive", "onReceive triggered with action: " + action);

        // Update all widget instances
        updateAllWidgets(context);

        // Schedule next exact alarm based on upcoming lesson boundaries
        scheduleNextAlarm(context);
    }

    // broadcast update intents to all active widget providers
    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        // Update CurrentLessonWidget
        ComponentName currentLessonComponent = new ComponentName(context, CurrentLessonWidget.class);
        int[] currentWidgetIds = appWidgetManager.getAppWidgetIds(currentLessonComponent);
        if (currentWidgetIds != null && currentWidgetIds.length > 0) {
            Logger.d("WidgetUpdateManager.updateAllWidgets", "Broadcasting update to " + currentWidgetIds.length + " CurrentLessonWidget instances");
            Intent updateIntent = new Intent(context, CurrentLessonWidget.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, currentWidgetIds);
            context.sendBroadcast(updateIntent);
        }

        // Update DailyScheduleWidget
        ComponentName dailyScheduleComponent = new ComponentName(context, DailyScheduleWidget.class);
        int[] dailyWidgetIds = appWidgetManager.getAppWidgetIds(dailyScheduleComponent);
        if (dailyWidgetIds != null && dailyWidgetIds.length > 0) {
            Logger.d("WidgetUpdateManager.updateAllWidgets", "Broadcasting update to " + dailyWidgetIds.length + " DailyScheduleWidget instances");
            Intent updateIntent = new Intent(context, DailyScheduleWidget.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, dailyWidgetIds);
            context.sendBroadcast(updateIntent);
        }
    }

    // schedule exact alarm at lesson start/end boundary or morning rollover
    public static void scheduleNextAlarm(Context context) {
        if (!PreferencesManager.isOnboardingCompleted(context)
                || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
            Logger.d("WidgetUpdateManager.scheduleNextAlarm", "Skipping alarm scheduling: student configuration not completed");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // Calculate next target time (exact boundary of current or next lesson)
        LocalDateTime targetDateTime = null;

        try {
            DaySchedule todaySchedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
            if (todaySchedule != null && !todaySchedule.isEmpty()) {
                List<Lesson> lessons = todaySchedule.getLessons();
                for (Lesson lesson : lessons) {
                    if (lesson.getStartTime() != null) {
                        LocalDateTime startDateTime = LocalDateTime.of(today, lesson.getStartTime());
                        if (startDateTime.isAfter(now)) {
                            if (targetDateTime == null || startDateTime.isBefore(targetDateTime)) {
                                targetDateTime = startDateTime;
                            }
                        }
                    }
                    if (lesson.getEndTime() != null) {
                        LocalDateTime endDateTime = LocalDateTime.of(today, lesson.getEndTime());
                        if (endDateTime.isAfter(now)) {
                            if (targetDateTime == null || endDateTime.isBefore(targetDateTime)) {
                                targetDateTime = endDateTime;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("WidgetUpdateManager.scheduleNextAlarm", "Error calculating target alarm time: " + e.getMessage());
        }

        // If no more lessons today, schedule for next morning at 07:00
        if (targetDateTime == null) {
            targetDateTime = LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(7, 0));
        }

        long triggerAtMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        Intent intent = new Intent(context, WidgetUpdateManager.class);
        intent.setAction(ACTION_SCHEDULE_ALARM_TICK);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Logger.d("WidgetUpdateManager.scheduleNextAlarm", "Next widget alarm scheduled at " + targetDateTime);
        } catch (SecurityException se) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            Logger.w("WidgetUpdateManager.scheduleNextAlarm", "Fallback to standard alarm: " + se.getMessage());
        }
    }
}
