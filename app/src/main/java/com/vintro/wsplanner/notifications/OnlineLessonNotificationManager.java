package com.vintro.wsplanner.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.ui.activities.MainActivity;
import com.vintro.wsplanner.ui.activities.NotificationActionActivity;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.utils.Logger;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

// builds and posts localized notifications for upcoming online classes
public class OnlineLessonNotificationManager {
    private static final String TAG = "OnlineLessonNotificationManager";
    public static final String CHANNEL_ID_ONLINE_LESSONS = "online_lessons_reminders";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // check both runtime permission on android 13+ and system notification toggle
    public static boolean hasNotificationPermission(@Nullable Context context) {
        if (context == null) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // create a configuration context using the app language selected in preferences
    @NonNull
    public static Context getLocalizedContext(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Language language = PreferencesManager.getLanguagePref(appContext);
        if (language == null) {
            return appContext;
        }
        Locale locale = new Locale(language.code);
        Configuration config = new Configuration(appContext.getResources().getConfiguration());
        config.setLocale(locale);
        return appContext.createConfigurationContext(config);
    }

    // create or update the high-importance notification channel in the current app language
    public static void ensureChannel(@Nullable Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        Context appContext = context.getApplicationContext();
        NotificationManager manager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        Context localized = getLocalizedContext(appContext);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_ONLINE_LESSONS,
                localized.getString(R.string.notification_channel_online_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(localized.getString(R.string.notification_channel_online_desc));
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
        Logger.d(TAG + ".ensureChannel", "Notification channel ensured: " + CHANNEL_ID_ONLINE_LESSONS);
    }

    // compute deterministic notification id per lesson and reminder offset
    public static int computeNotificationId(@NonNull String lessonId, int leadMinutes) {
        int hash = (lessonId + "|" + leadMinutes).hashCode();
        return hash == 0 ? 1 : hash;
    }

    // post reminder notification with subject details click and open in puw action
    public static boolean showLessonReminder(
            @NonNull Context context,
            @NonNull Lesson lesson,
            int configuredLeadMinutes,
            int actualMinutesUntilStart
    ) {
        Context appContext = context.getApplicationContext();
        if (!hasNotificationPermission(appContext)) {
            Logger.w(TAG + ".showLessonReminder", "Skipping reminder because notification permission is missing");
            return false;
        }

        ensureChannel(appContext);
        Context localized = getLocalizedContext(appContext);

        String subject = lesson.getSubjectName() != null ? lesson.getSubjectName().trim() : "";
        if (subject.isEmpty()) {
            Logger.w(TAG + ".showLessonReminder", "Skipping reminder because lesson subject is empty");
            return false;
        }

        String startStr = lesson.getStartTime() != null ? lesson.getStartTime().format(TIME_FORMATTER) : "";
        String endStr = lesson.getEndTime() != null ? lesson.getEndTime().format(TIME_FORMATTER) : "";
        String localizedType = resolveLocalizedLessonType(localized, lesson);
        String teacher = lesson.hasTeacher() ? lesson.getTeacherName().trim() : "";

        String title = !localizedType.isEmpty()
                ? subject + " • " + localizedType
                : subject;

        String collapsedText;
        if (actualMinutesUntilStart <= 0) {
            collapsedText = localized.getString(R.string.notification_online_starts_now, startStr);
        } else {
            collapsedText = localized.getString(R.string.notification_online_starts_in, actualMinutesUntilStart, startStr);
        }

        String bigStatusLine = actualMinutesUntilStart <= 0
                ? localized.getString(R.string.notification_online_big_starts_now)
                : localized.getString(R.string.notification_online_big_starts_in, actualMinutesUntilStart);

        String timeRange = (!startStr.isEmpty() && !endStr.isEmpty())
                ? startStr + " – " + endStr
                : startStr;
        String bottomMetaLine = buildMetaLine(timeRange, teacher);

        StringBuilder bigTextBuilder = new StringBuilder(bigStatusLine);
        if (!bottomMetaLine.isEmpty()) {
            bigTextBuilder.append("\n").append(bottomMetaLine);
        }

        int notificationId = computeNotificationId(lesson.getId(), configuredLeadMinutes);

        PendingIntent contentIntent = buildDetailsContentIntent(appContext, lesson, notificationId);
        PendingIntent puwActionIntent = buildOpenPuwActionIntent(appContext, lesson, notificationId);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID_ONLINE_LESSONS)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setColor(ContextCompat.getColor(appContext, R.color.color_primary_light))
                .setContentTitle(title)
                .setContentText(collapsedText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigTextBuilder.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .addAction(
                        R.drawable.ic_arrow_outward,
                        localized.getString(R.string.notification_action_open_puw),
                        puwActionIntent
                );

        try {
            NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
            Logger.i(TAG + ".showLessonReminder", "Posted online lesson reminder (id=" + notificationId
                    + ", subject='" + subject + "', configuredLead=" + configuredLeadMinutes
                    + ", actualLead=" + actualMinutesUntilStart + ")");
            return true;
        } catch (SecurityException se) {
            Logger.w(TAG + ".showLessonReminder", "SecurityException posting reminder: " + Logger.formatException(se));
            return false;
        }
    }

    private static PendingIntent buildDetailsContentIntent(Context appContext, Lesson lesson, int requestCode) {
        Intent mainIntent = new Intent(appContext, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
        if (lesson.getDate() != null) {
            mainIntent.putExtra(MainActivity.EXTRA_TARGET_DATE, lesson.getDate().toString());
        }

        Intent detailsIntent = new Intent(appContext, SubjectDetailsActivity.class);
        detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
        detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
        detailsIntent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_ID, lesson.getId());

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(appContext);
        stackBuilder.addNextIntent(mainIntent);
        stackBuilder.addNextIntent(detailsIntent);

        return stackBuilder.getPendingIntent(
                requestCode,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent buildOpenPuwActionIntent(Context appContext, Lesson lesson, int notificationId) {
        Intent puwIntent = NotificationActionActivity.createOpenPuwCourseIntent(
                appContext,
                lesson.getSubjectName(),
                lesson.getLessonType(),
                notificationId
        );
        return PendingIntent.getActivity(
                appContext,
                notificationId ^ 0x5A5A5A5A,
                puwIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static String resolveLocalizedLessonType(Context localized, Lesson lesson) {
        LessonType resolved = lesson.getResolvedLessonType();
        if (resolved != null && resolved != LessonType.INNE) {
            return resolved.getLocalizedDisplayName(localized);
        }
        String raw = lesson.getLessonType();
        if (raw != null && !raw.trim().isEmpty()
                && !raw.trim().equalsIgnoreCase("inne")
                && !raw.trim().equalsIgnoreCase("other")) {
            return raw.trim();
        }
        return "";
    }

    private static String buildMetaLine(String timeRange, String teacher) {
        if (!timeRange.isEmpty() && !teacher.isEmpty()) {
            return timeRange + " • " + teacher;
        }
        if (!timeRange.isEmpty()) {
            return timeRange;
        }
        return teacher;
    }
}
