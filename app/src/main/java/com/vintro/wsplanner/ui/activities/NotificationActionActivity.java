package com.vintro.wsplanner.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.network.MoodleAutologinManager;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.notifications.OnlineLessonNotificationManager;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

import okhttp3.OkHttpClient;

// translucent trampoline activity handling notification action buttons without background launch blocks
public class NotificationActionActivity extends Activity {
    private static final String TAG = "NotificationActionActivity";

    public static final String EXTRA_ACTION = "notif_action";
    public static final String EXTRA_SUBJECT_NAME = "notif_subject_name";
    public static final String EXTRA_LESSON_TYPE = "notif_lesson_type";
    public static final String EXTRA_NOTIFICATION_ID = "notif_id";

    public static final String ACTION_OPEN_PUW_COURSE = "open_puw_course";

    public static Intent createOpenPuwCourseIntent(
            Context context,
            @Nullable String subjectName,
            @Nullable String lessonType,
            int notificationId
    ) {
        Intent intent = new Intent(context, NotificationActionActivity.class);
        intent.putExtra(EXTRA_ACTION, ACTION_OPEN_PUW_COURSE);
        intent.putExtra(EXTRA_SUBJECT_NAME, subjectName);
        intent.putExtra(EXTRA_LESSON_TYPE, lessonType);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent != null ? intent.getStringExtra(EXTRA_ACTION) : null;
        int notificationId = intent != null ? intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0) : 0;

        if (notificationId != 0) {
            try {
                NotificationManagerCompat.from(this).cancel(notificationId);
            } catch (Exception ignored) {
            }
        }

        Logger.i(TAG + ".onCreate", "Executing notification action: " + action);

        if (!ACTION_OPEN_PUW_COURSE.equals(action)) {
            openMainApp();
            finishSilently();
            return;
        }

        if (!PreferencesManager.isOnboardingCompleted(this)
                || !ScheduleRepository.getInstance(this).isConfigurationComplete()) {
            openMainApp();
            finishSilently();
            return;
        }

        final String subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME);
        final String rawLessonType = intent.getStringExtra(EXTRA_LESSON_TYPE);
        final Context appContext = getApplicationContext();
        final Context localized = OnlineLessonNotificationManager.getLocalizedContext(appContext);

        Toast.makeText(localized, localized.getString(R.string.subject_opening_puw_course), Toast.LENGTH_SHORT).show();

        AppExecutors.runIo(() -> {
            String foundCourseUrl = null;
            try {
                OkHttpClient client = PUW.globalLogin(appContext);
                if (client != null && subjectName != null && !subjectName.trim().isEmpty()) {
                    foundCourseUrl = StudyPlanScraper.findCourseUrlCached(appContext, client, subjectName, rawLessonType);
                }
            } catch (Exception e) {
                Logger.e(TAG + ".openPuwCourse", "Error resolving PUW course URL: " + Logger.formatException(e));
            }

            final String resolvedCourseUrl = foundCourseUrl;
            String targetUrl = MoodleAutologinManager.sanitizeTargetUrl(resolvedCourseUrl);
            MoodleAutologinManager.ResolutionResult resolution =
                    MoodleAutologinManager.resolveAutologinUriSync(appContext, targetUrl);

            AppExecutors.runOnMain(() -> {
                if (resolvedCourseUrl == null) {
                    Toast.makeText(localized, localized.getString(R.string.subject_course_not_found), Toast.LENGTH_SHORT).show();
                }
                Context launchContext = (!isFinishing() && !isDestroyed()) ? NotificationActionActivity.this : appContext;
                MoodleAutologinManager.launchExternalBrowser(launchContext, resolution);
                finishSilently();
            });
        });
    }

    private void openMainApp() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mainIntent);
    }

    private void finishSilently() {
        if (!isFinishing() && !isDestroyed()) {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
