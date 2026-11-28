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
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

// coordinates widget updates and schedules non-wakeup alarms strictly on lesson transitions
public class WidgetUpdateManager extends BroadcastReceiver {
    public static final String ACTION_SCHEDULE_ALARM_TICK = "com.vintro.wsplanner.ACTION_SCHEDULE_ALARM_TICK";
    public static final String ACTION_REFRESH_WIDGETS = "com.vintro.wsplanner.ACTION_REFRESH_WIDGETS";

    private static final int ALARM_REQUEST_CODE = 1001;
    private static final long INEXACT_WINDOW_MILLIS = 60_000L;

    // run cold-start disk parsing and widget refresh off the main thread using goAsync
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Logger.d("WidgetUpdateManager.onReceive", "onReceive triggered with action: " + action);

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        AppExecutors.runIo(() -> {
            try {
                if (!hasActiveWidgets(appContext)) {
                    cancelScheduledAlarm(appContext);
                    return;
                }
                updateAllWidgetsSync(appContext);
                scheduleNextAlarm(appContext);
            } catch (Exception e) {
                Logger.e("WidgetUpdateManager.onReceive", "Error processing widget tick: " + Logger.formatException(e));
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }

    // check whether at least one home screen widget of either provider is active
    public static boolean hasActiveWidgets(Context context) {
        if (context == null) return false;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] currentIds = manager.getAppWidgetIds(new ComponentName(context, CurrentLessonWidget.class));
            if (currentIds != null && currentIds.length > 0) {
                return true;
            }
            int[] dailyIds = manager.getAppWidgetIds(new ComponentName(context, DailyScheduleWidget.class));
            return dailyIds != null && dailyIds.length > 0;
        } catch (Exception e) {
            Logger.w("WidgetUpdateManager.hasActiveWidgets", "Failed checking active widgets: " + Logger.formatException(e));
            return false;
        }
    }

    // cancel the pending widget transition alarm
    public static void cancelScheduledAlarm(Context context) {
        if (context == null) return;
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;
            PendingIntent pendingIntent = createAlarmPendingIntent(context, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
                Logger.d("WidgetUpdateManager.cancelScheduledAlarm", "Cancelled scheduled widget alarm");
            }
        } catch (Exception e) {
            Logger.w("WidgetUpdateManager.cancelScheduledAlarm", "Failed cancelling widget alarm: " + Logger.formatException(e));
        }
    }

    // update all active widget instances asynchronously off the main thread
    public static void updateAllWidgets(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> updateAllWidgetsSync(appContext));
    }

    // update all active widget instances synchronously on a background thread without triggering extra alarm loops
    public static void updateAllWidgetsSync(Context context) {
        if (context == null) return;
        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

            int[] currentWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, CurrentLessonWidget.class));
            if (currentWidgetIds != null && currentWidgetIds.length > 0) {
                Logger.d("WidgetUpdateManager.updateAllWidgetsSync", "Updating " + currentWidgetIds.length + " CurrentLessonWidget instances");
                for (int widgetId : currentWidgetIds) {
                    CurrentLessonWidget.updateWidget(context, appWidgetManager, widgetId);
                }
            }

            int[] dailyWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, DailyScheduleWidget.class));
            if (dailyWidgetIds != null && dailyWidgetIds.length > 0) {
                Logger.d("WidgetUpdateManager.updateAllWidgetsSync", "Updating " + dailyWidgetIds.length + " DailyScheduleWidget instances");
                for (int widgetId : dailyWidgetIds) {
                    DailyScheduleWidget.updateWidget(context, appWidgetManager, widgetId);
                }
            }
        } catch (Exception e) {
            Logger.e("WidgetUpdateManager.updateAllWidgetsSync", "Error updating active widgets: " + Logger.formatException(e));
        }
    }

    // schedule next alarm asynchronously off the calling thread
    public static void scheduleNextAlarmAsync(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> scheduleNextAlarm(appContext));
    }

    // schedule a non-wakeup rtc alarm at the next real lesson boundary or midnight rollover
    public static void scheduleNextAlarm(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();

        if (!hasActiveWidgets(appContext)) {
            cancelScheduledAlarm(appContext);
            return;
        }

        boolean configured;
        try {
            configured = PreferencesManager.isOnboardingCompleted(appContext)
                    && ScheduleRepository.getInstance(appContext).isConfigurationComplete();
        } catch (Exception e) {
            Logger.w("WidgetUpdateManager.scheduleNextAlarm", "Unable to inspect configuration state: " + Logger.formatException(e));
            cancelScheduledAlarm(appContext);
            return;
        }

        if (!configured) {
            Logger.d("WidgetUpdateManager.scheduleNextAlarm", "Skipping alarm scheduling: student configuration not completed");
            cancelScheduledAlarm(appContext);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetDateTime = null;

        try {
            DaySchedule todaySchedule = ScheduleRepository.getInstance(appContext).getScheduleForDateSync(today);
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
                        LocalDateTime endDateTime = LocalDateTime.of(today, lesson.getEndTime()).plusSeconds(1);
                        if (endDateTime.isAfter(now)) {
                            if (targetDateTime == null || endDateTime.isBefore(targetDateTime)) {
                                targetDateTime = endDateTime;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("WidgetUpdateManager.scheduleNextAlarm", "Error calculating target alarm time: " + Logger.formatException(e));
        }

        // if no more transitions today, wake cleanly after midnight for the next day rollover
        LocalDateTime nextMidnight = today.plusDays(1).atTime(0, 0, 5);
        if (targetDateTime == null || targetDateTime.isAfter(nextMidnight)) {
            targetDateTime = nextMidnight;
        }

        long triggerAtMillis = targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long minTrigger = System.currentTimeMillis() + 5_000L;
        if (triggerAtMillis < minTrigger) {
            triggerAtMillis = minTrigger;
        }

        PendingIntent pendingIntent = createAlarmPendingIntent(
                appContext,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            // use non-wakeup rtc so widgets never wake the cpu while the screen is off
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.setWindow(AlarmManager.RTC, triggerAtMillis, INEXACT_WINDOW_MILLIS, pendingIntent);
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC, triggerAtMillis, pendingIntent);
            }
            Logger.d("WidgetUpdateManager.scheduleNextAlarm", "Next non-wakeup widget alarm scheduled at " + targetDateTime);
        } catch (SecurityException se) {
            alarmManager.setWindow(AlarmManager.RTC, triggerAtMillis, INEXACT_WINDOW_MILLIS, pendingIntent);
            Logger.w("WidgetUpdateManager.scheduleNextAlarm", "Fallback to inexact RTC window alarm: " + Logger.formatException(se));
        }
    }

    private static PendingIntent createAlarmPendingIntent(Context context, int flags) {
        Intent intent = new Intent(context, WidgetUpdateManager.class);
        intent.setAction(ACTION_SCHEDULE_ALARM_TICK);
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);
    }
}
