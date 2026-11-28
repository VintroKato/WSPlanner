package com.vintro.wsplanner.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// receives scheduled reminder alarms, posts due online class notifications, and arms the next alarm
public class OnlineLessonNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "OnlineLessonNotificationReceiver";
    private static final long DELIVERED_HISTORY_RETENTION_DAYS = 2L;
    private static final Object DELIVERY_LOCK = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!OnlineLessonNotificationScheduler.ACTION_ONLINE_LESSON_REMINDER.equals(action)) {
            return;
        }
        Logger.i(TAG + ".onReceive", "Online lesson reminder alarm triggered");

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();

        AppExecutors.runIo(() -> {
            try {
                synchronized (DELIVERY_LOCK) {
                    processReminderTick(appContext);
                }
            } catch (Exception e) {
                Logger.e(TAG + ".onReceive", "Error processing online lesson reminder alarm: " + Logger.formatException(e), e);
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void processReminderTick(Context appContext) {
        if (!PreferencesManager.isOnlineNotificationsEnabled(appContext)) {
            Logger.d(TAG + ".onReceive", "Notifications disabled when alarm fired, cancelling future alarms");
            OnlineLessonNotificationScheduler.cancelScheduledAlarm(appContext);
            return;
        }

        if (!OnlineLessonNotificationManager.hasNotificationPermission(appContext)) {
            Logger.w(TAG + ".onReceive", "Notification permission revoked, skipping delivery");
            OnlineLessonNotificationScheduler.cancelScheduledAlarm(appContext);
            return;
        }

        if (!PreferencesManager.isOnboardingCompleted(appContext)
                || !ScheduleRepository.getInstance(appContext).isConfigurationComplete()) {
            Logger.d(TAG + ".onReceive", "Student configuration incomplete, skipping reminder delivery");
            OnlineLessonNotificationScheduler.cancelScheduledAlarm(appContext);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Lesson> allLessons = ScheduleRepository.getInstance(appContext).getAllLessonsSync();
        List<Integer> leadOffsets = PreferencesManager.getNotificationLeadMinutes(appContext);
        Set<String> deliveredKeys = new HashSet<>(PreferencesManager.getDeliveredNotificationKeys(appContext));

        OnlineLessonNotificationScheduler.DueBatch batch =
                OnlineLessonNotificationScheduler.evaluateDueBatch(allLessons, leadOffsets, deliveredKeys, now);

        Logger.d(TAG + ".onReceive", "Evaluated due reminders: count=" + batch.eventsToNotify.size());
        boolean deliveredChanged = false;
        for (OnlineLessonNotificationScheduler.ReminderEvent event : batch.eventsToNotify) {
            int remainingMinutes = OnlineLessonNotificationScheduler.computeRemainingMinutesUntilStart(event.lesson, now);
            boolean posted = OnlineLessonNotificationManager.showLessonReminder(
                    appContext,
                    event.lesson,
                    event.leadMinutes,
                    remainingMinutes
            );
            if (posted) {
                List<String> consumedKeys = batch.deliveryKeysByLessonId.get(event.lesson.getId());
                if (consumedKeys != null && !consumedKeys.isEmpty()) {
                    deliveredKeys.addAll(consumedKeys);
                } else {
                    deliveredKeys.add(event.deliveryKey);
                }
                deliveredChanged = true;
            }
        }

        Set<String> pruned = OnlineLessonNotificationScheduler.pruneDeliveredKeys(
                deliveredKeys,
                LocalDate.now().minusDays(DELIVERED_HISTORY_RETENTION_DAYS)
        );
        if (deliveredChanged || pruned.size() != deliveredKeys.size()) {
            PreferencesManager.setDeliveredNotificationKeys(appContext, pruned);
        }

        OnlineLessonNotificationScheduler.rescheduleNext(appContext);
    }
}
