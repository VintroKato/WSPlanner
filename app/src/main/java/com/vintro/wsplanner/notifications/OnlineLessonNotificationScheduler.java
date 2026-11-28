package com.vintro.wsplanner.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// schedules a single wakeup alarm for the earliest upcoming online class reminder
public class OnlineLessonNotificationScheduler {
    private static final String TAG = "OnlineLessonNotificationScheduler";
    public static final String ACTION_ONLINE_LESSON_REMINDER = "com.vintro.wsplanner.ACTION_ONLINE_LESSON_REMINDER";

    public static final int DEFAULT_LEAD_MINUTES = 0;
    public static final int MIN_LEAD_MINUTES = 0;
    public static final int MAX_LEAD_MINUTES = 120;

    static final long EARLY_TRIGGER_TOLERANCE_SECONDS = 15L;
    static final long LATE_START_GRACE_MINUTES = 5L;

    private static final int ALARM_REQUEST_CODE = 2001;
    private static final long MIN_ALARM_DELAY_MS = 2_000L;

    // immutable reminder occurrence combining a lesson, lead offset, and exact trigger timestamp
    public static final class ReminderEvent {
        @NonNull
        public final Lesson lesson;
        public final int leadMinutes;
        @NonNull
        public final LocalDateTime triggerTime;
        @NonNull
        public final String deliveryKey;

        public ReminderEvent(
                @NonNull Lesson lesson,
                int leadMinutes,
                @NonNull LocalDateTime triggerTime,
                @NonNull String deliveryKey
        ) {
            this.lesson = lesson;
            this.leadMinutes = leadMinutes;
            this.triggerTime = triggerTime;
            this.deliveryKey = deliveryKey;
        }
    }

    // deduplicated batch of notifications to display alongside all overdue keys to mark as consumed
    public static final class DueBatch {
        @NonNull
        public final List<ReminderEvent> eventsToNotify;
        @NonNull
        public final Map<String, List<String>> deliveryKeysByLessonId;

        public DueBatch(
                @NonNull List<ReminderEvent> eventsToNotify,
                @NonNull Map<String, List<String>> deliveryKeysByLessonId
        ) {
            this.eventsToNotify = eventsToNotify;
            this.deliveryKeysByLessonId = deliveryKeysByLessonId;
        }
    }

    // single filter predicate for eligible lessons, designed to scale to future location or subject filters
    public static boolean isLessonEligible(@Nullable Lesson lesson) {
        if (lesson == null || lesson.getDate() == null || lesson.getStartTime() == null) {
            return false;
        }
        if (lesson.getSubjectName() == null || lesson.getSubjectName().trim().isEmpty()) {
            return false;
        }
        return lesson.getLocation() != null && lesson.getLocation().isOnline();
    }

    // build deduplication key prefixed with lesson date for straightforward expiration pruning
    @NonNull
    public static String buildDeliveryKey(@NonNull Lesson lesson, int leadMinutes) {
        String datePart = lesson.getDate() != null ? lesson.getDate().toString() : "nodate";
        String idPart = lesson.getId() != null ? lesson.getId() : "noid";
        return datePart + "|" + idPart + "|" + leadMinutes;
    }

    // clamp and deduplicate configured reminder lead times
    @NonNull
    public static List<Integer> normalizeLeadOffsets(@Nullable Collection<Integer> rawOffsets) {
        TreeSet<Integer> normalized = new TreeSet<>();
        if (rawOffsets != null) {
            for (Integer value : rawOffsets) {
                if (value != null) {
                    int clamped = Math.max(MIN_LEAD_MINUTES, Math.min(MAX_LEAD_MINUTES, value));
                    normalized.add(clamped);
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add(DEFAULT_LEAD_MINUTES);
        }
        return new ArrayList<>(normalized);
    }

    // find all eligible online reminders due now, collapsing multiple overdue offsets per lesson into one alert
    @NonNull
    public static List<ReminderEvent> findDueEvents(
            @Nullable List<Lesson> lessons,
            @Nullable Collection<Integer> leadOffsets,
            @Nullable Set<String> deliveredKeys,
            @NonNull LocalDateTime now
    ) {
        return evaluateDueBatch(lessons, leadOffsets, deliveredKeys, now).eventsToNotify;
    }

    // evaluate due reminders, selecting only the latest due offset per lesson while tracking all skipped overdue keys
    @NonNull
    public static DueBatch evaluateDueBatch(
            @Nullable List<Lesson> lessons,
            @Nullable Collection<Integer> leadOffsets,
            @Nullable Set<String> deliveredKeys,
            @NonNull LocalDateTime now
    ) {
        if (lessons == null || lessons.isEmpty()) {
            return new DueBatch(Collections.emptyList(), Collections.emptyMap());
        }
        List<Integer> offsets = normalizeLeadOffsets(leadOffsets);
        Map<String, ReminderEvent> latestPerLesson = new LinkedHashMap<>();
        Map<String, List<String>> keysByLesson = new LinkedHashMap<>();
        LocalDateTime upperTriggerBound = now.plusSeconds(EARLY_TRIGGER_TOLERANCE_SECONDS);

        for (Lesson lesson : lessons) {
            if (!isLessonEligible(lesson)) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(lesson.getDate(), lesson.getStartTime());
            LocalDateTime lateCutoff = start.plusMinutes(LATE_START_GRACE_MINUTES);
            if (!now.isBefore(lateCutoff)) {
                continue;
            }
            if (lesson.getEndTime() != null) {
                LocalDateTime end = LocalDateTime.of(lesson.getDate(), lesson.getEndTime());
                if (!now.isBefore(end)) {
                    continue;
                }
            }

            for (int offset : offsets) {
                LocalDateTime trigger = start.minusMinutes(offset);
                if (trigger.isAfter(upperTriggerBound)) {
                    continue;
                }
                String key = buildDeliveryKey(lesson, offset);
                if (deliveredKeys != null && deliveredKeys.contains(key)) {
                    continue;
                }

                String lessonId = lesson.getId();
                List<String> lessonKeys = keysByLesson.computeIfAbsent(lessonId, k -> new ArrayList<>());
                if (!lessonKeys.contains(key)) {
                    lessonKeys.add(key);
                }

                ReminderEvent candidate = new ReminderEvent(lesson, offset, trigger, key);
                ReminderEvent existing = latestPerLesson.get(lessonId);
                if (existing == null || candidate.leadMinutes < existing.leadMinutes) {
                    latestPerLesson.put(lessonId, candidate);
                }
            }
        }

        List<ReminderEvent> due = new ArrayList<>(latestPerLesson.values());
        due.sort(Comparator
                .comparing((ReminderEvent e) -> e.triggerTime)
                .thenComparing(e -> LocalDateTime.of(e.lesson.getDate(), e.lesson.getStartTime()))
                .thenComparing(e -> e.lesson.getId()));
        return new DueBatch(due, keysByLesson);
    }

    // find the earliest upcoming online reminder strictly after now
    @Nullable
    public static ReminderEvent findNextScheduledEvent(
            @Nullable List<Lesson> lessons,
            @Nullable Collection<Integer> leadOffsets,
            @Nullable Set<String> deliveredKeys,
            @NonNull LocalDateTime now
    ) {
        if (lessons == null || lessons.isEmpty()) {
            return null;
        }
        List<Integer> offsets = normalizeLeadOffsets(leadOffsets);
        ReminderEvent earliest = null;

        for (Lesson lesson : lessons) {
            if (!isLessonEligible(lesson)) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(lesson.getDate(), lesson.getStartTime());
            LocalDateTime end = lesson.getEndTime() != null
                    ? LocalDateTime.of(lesson.getDate(), lesson.getEndTime())
                    : null;

            for (int offset : offsets) {
                LocalDateTime trigger = start.minusMinutes(offset);
                if (!trigger.isAfter(now)) {
                    continue;
                }
                if (end != null && !trigger.isBefore(end)) {
                    continue;
                }
                String key = buildDeliveryKey(lesson, offset);
                if (deliveredKeys != null && deliveredKeys.contains(key)) {
                    continue;
                }
                if (earliest == null || trigger.isBefore(earliest.triggerTime)) {
                    earliest = new ReminderEvent(lesson, offset, trigger, key);
                }
            }
        }

        return earliest;
    }

    // compute rounded minutes remaining until lesson start for user-facing notification copy
    public static int computeRemainingMinutesUntilStart(@NonNull Lesson lesson, @NonNull LocalDateTime now) {
        if (lesson.getDate() == null || lesson.getStartTime() == null) {
            return 0;
        }
        LocalDateTime start = LocalDateTime.of(lesson.getDate(), lesson.getStartTime());
        long seconds = Duration.between(now, start).getSeconds();
        if (seconds <= 30L) {
            return 0;
        }
        return (int) ((seconds + 30L) / 60L);
    }

    // filter out delivered reminder keys older than the cutoff date
    @NonNull
    public static Set<String> pruneDeliveredKeys(@Nullable Set<String> keys, @NonNull LocalDate cutoffDate) {
        Set<String> kept = new HashSet<>();
        if (keys == null || keys.isEmpty()) {
            return kept;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            int sep = key.indexOf('|');
            if (sep <= 0) {
                continue;
            }
            String dateStr = key.substring(0, sep);
            try {
                LocalDate keyDate = LocalDate.parse(dateStr);
                if (!keyDate.isBefore(cutoffDate)) {
                    kept.add(key);
                }
            } catch (Exception ignored) {
            }
        }
        return kept;
    }

    // reschedule next reminder alarm off the calling thread
    public static void rescheduleNextAsync(@Nullable Context context) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        AppExecutors.runIo(() -> rescheduleNext(appContext));
    }

    // compute and arm the single wakeup alarm for the next upcoming online class reminder
    public static void rescheduleNext(@Nullable Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();

        if (!PreferencesManager.isOnlineNotificationsEnabled(appContext)) {
            Logger.d(TAG + ".rescheduleNext", "Online notifications disabled, cancelling any scheduled alarm");
            cancelScheduledAlarm(appContext);
            return;
        }

        if (!OnlineLessonNotificationManager.hasNotificationPermission(appContext)) {
            Logger.w(TAG + ".rescheduleNext", "Notification permission missing, cancelling scheduled alarm");
            cancelScheduledAlarm(appContext);
            return;
        }

        boolean configured;
        try {
            configured = PreferencesManager.isOnboardingCompleted(appContext)
                    && ScheduleRepository.getInstance(appContext).isConfigurationComplete();
        } catch (Exception e) {
            Logger.w(TAG + ".rescheduleNext", "Unable to inspect configuration state: " + Logger.formatException(e));
            cancelScheduledAlarm(appContext);
            return;
        }

        if (!configured) {
            Logger.d(TAG + ".rescheduleNext", "Skipping reminder scheduling because onboarding is incomplete");
            cancelScheduledAlarm(appContext);
            return;
        }

        OnlineLessonNotificationManager.ensureChannel(appContext);

        List<Lesson> allLessons;
        try {
            allLessons = ScheduleRepository.getInstance(appContext).getAllLessonsSync();
        } catch (Exception e) {
            Logger.e(TAG + ".rescheduleNext", "Failed loading local lessons for scheduling: " + Logger.formatException(e));
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Set<String> deliveredKeys = PreferencesManager.getDeliveredNotificationKeys(appContext);
        List<Integer> leadOffsets = PreferencesManager.getNotificationLeadMinutes(appContext);

        ReminderEvent nextEvent = findNextScheduledEvent(allLessons, leadOffsets, deliveredKeys, now);
        if (nextEvent == null) {
            Logger.i(TAG + ".rescheduleNext", "No upcoming online class reminders found, cancelling alarm");
            cancelScheduledAlarm(appContext);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Logger.w(TAG + ".rescheduleNext", "AlarmManager unavailable");
            return;
        }

        long triggerAtMillis = nextEvent.triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long minTrigger = System.currentTimeMillis() + MIN_ALARM_DELAY_MS;
        if (triggerAtMillis < minTrigger) {
            triggerAtMillis = minTrigger;
        }

        PendingIntent pendingIntent = createAlarmPendingIntent(
                appContext,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            boolean useExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms();
            if (useExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            Logger.i(TAG + ".rescheduleNext", "Scheduled next online class reminder at " + nextEvent.triggerTime
                    + " (exact=" + useExact + ", leadMin=" + nextEvent.leadMinutes
                    + ", deliveredCache=" + deliveredKeys.size()
                    + ", subject='" + nextEvent.lesson.getSubjectName() + "')");
        } catch (SecurityException se) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            Logger.w(TAG + ".rescheduleNext", "Fallback to inexact wakeup alarm after SecurityException: " + Logger.formatException(se));
        }
    }

    // cancel any pending online lesson reminder alarm
    public static void cancelScheduledAlarm(@Nullable Context context) {
        if (context == null) {
            return;
        }
        try {
            AlarmManager alarmManager = (AlarmManager) context.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                return;
            }
            PendingIntent pendingIntent = createAlarmPendingIntent(
                    context.getApplicationContext(),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
                Logger.d(TAG + ".cancelScheduledAlarm", "Cancelled scheduled online class reminder alarm");
            }
        } catch (Exception e) {
            Logger.w(TAG + ".cancelScheduledAlarm", "Failed cancelling reminder alarm: " + Logger.formatException(e));
        }
    }

    private static PendingIntent createAlarmPendingIntent(Context context, int flags) {
        Intent intent = new Intent(context, OnlineLessonNotificationReceiver.class);
        intent.setAction(ACTION_ONLINE_LESSON_REMINDER);
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, flags);
    }
}
