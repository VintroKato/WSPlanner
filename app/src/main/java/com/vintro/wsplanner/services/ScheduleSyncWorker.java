package com.vintro.wsplanner.services;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.ui.widgets.DailyScheduleWidget;
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

// workmanager worker performing battery-friendly periodic schedule sync and manual widget refresh
public class ScheduleSyncWorker extends Worker {
    private static final String TAG = "ScheduleSyncWorker";
    private static final String UNIQUE_PERIODIC_WORK_NAME = "wsplanner_periodic_schedule_sync";
    private static final String UNIQUE_MANUAL_WIDGET_WORK_NAME = "wsplanner_manual_widget_refresh";
    private static final String KEY_MANUAL_WIDGET_REFRESH = "manual_widget_refresh";
    private static final String KEY_SHOW_DAILY_FEEDBACK = "show_daily_feedback";
    private static final long PERIODIC_SYNC_HOURS = 12L;

    public ScheduleSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    // ensure periodic background sync is registered when onboarding is complete
    public static void ensurePeriodicSyncScheduled(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        if (!PreferencesManager.isOnboardingCompleted(appContext)
                || !ScheduleRepository.getInstance(appContext).isConfigurationComplete()) {
            cancelPeriodicSync(appContext);
            return;
        }

        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build();

            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    ScheduleSyncWorker.class,
                    PERIODIC_SYNC_HOURS,
                    TimeUnit.HOURS
            )
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
            );
            Logger.d(TAG + ".ensurePeriodicSyncScheduled", "Ensured periodic WorkManager sync (" + PERIODIC_SYNC_HOURS + "h)");
        } catch (Exception e) {
            Logger.w(TAG + ".ensurePeriodicSyncScheduled", "Failed scheduling periodic sync: " + Logger.formatException(e));
        }
    }

    // cancel periodic background sync on logout or reset
    public static void cancelPeriodicSync(Context context) {
        if (context == null) return;
        try {
            WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME);
            Logger.d(TAG + ".cancelPeriodicSync", "Cancelled periodic WorkManager schedule sync");
        } catch (Exception e) {
            Logger.w(TAG + ".cancelPeriodicSync", "Failed cancelling periodic sync: " + Logger.formatException(e));
        }
    }

    // enqueue expedited one-time refresh triggered by user tapping refresh on a widget
    public static void enqueueManualWidgetRefresh(Context context, boolean showDailyFeedback) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        try {
            Data input = new Data.Builder()
                    .putBoolean(KEY_MANUAL_WIDGET_REFRESH, true)
                    .putBoolean(KEY_SHOW_DAILY_FEEDBACK, showDailyFeedback)
                    .build();

            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ScheduleSyncWorker.class)
                    .setInputData(input)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build();

            WorkManager.getInstance(appContext).enqueueUniqueWork(
                    UNIQUE_MANUAL_WIDGET_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
            );
            Logger.d(TAG + ".enqueueManualWidgetRefresh", "Enqueued expedited widget refresh work (dailyFeedback=" + showDailyFeedback + ")");
        } catch (Exception e) {
            Logger.w(TAG + ".enqueueManualWidgetRefresh", "Failed enqueueing manual widget refresh: " + Logger.formatException(e));
            DailyScheduleWidget.setRefreshState(appContext, DailyScheduleWidget.RefreshState.IDLE);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        Context appContext = getApplicationContext();
        boolean isManualWidget = getInputData().getBoolean(KEY_MANUAL_WIDGET_REFRESH, false);
        boolean showDailyFeedback = getInputData().getBoolean(KEY_SHOW_DAILY_FEEDBACK, false);

        if (!PreferencesManager.isOnboardingCompleted(appContext)
                || !ScheduleRepository.getInstance(appContext).isConfigurationComplete()) {
            Logger.d(TAG + ".doWork", "Configuration incomplete, skipping schedule sync");
            if (showDailyFeedback) {
                DailyScheduleWidget.setRefreshState(appContext, DailyScheduleWidget.RefreshState.IDLE);
            }
            return Result.success();
        }

        ScheduleRepository repo = ScheduleRepository.getInstance(appContext);

        if (isManualWidget) {
            return runManualWidgetRefresh(appContext, repo, showDailyFeedback);
        }

        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            Logger.d(TAG + ".doWork", "Skipping periodic sync because device is offline");
            return Result.success();
        }

        if (!repo.isScheduleOutdated()) {
            Logger.d(TAG + ".doWork", "Schedule is still fresh, skipping periodic network download");
            return Result.success();
        }

        try {
            Logger.i(TAG + ".doWork", "Running periodic background schedule sync");
            repo.ensureScheduleLoaded(false, false);
            return Result.success();
        } catch (Exception e) {
            Logger.w(TAG + ".doWork", "Periodic schedule sync failed: " + Logger.formatException(e));
            return Result.retry();
        }
    }

    private Result runManualWidgetRefresh(Context appContext, ScheduleRepository repo, boolean showDailyFeedback) {
        boolean success = false;
        boolean fallback = false;
        try {
            try {
                repo.ensureScheduleLoaded(true, true);
                DaySchedule today = repo.getScheduleForDateFromMemory(LocalDate.now());
                fallback = today != null && today.isFromCacheFallback();
                success = !fallback;
                Logger.i(TAG + ".runManualWidgetRefresh", "Manual widget refresh completed (success=" + success + ", fallback=" + fallback + ")");
            } catch (Exception e) {
                Logger.w(TAG + ".runManualWidgetRefresh", "Manual widget refresh failed: " + Logger.formatException(e));
            }

            final boolean showOk = success;
            if (showDailyFeedback) {
                DailyScheduleWidget.setRefreshState(
                        appContext,
                        showOk ? DailyScheduleWidget.RefreshState.SUCCESS : DailyScheduleWidget.RefreshState.ERROR
                );
            }

            // refresh widget lesson data while preserving the active check or error feedback icon
            WidgetUpdateManager.updateAllWidgetsSync(appContext);
            WidgetUpdateManager.scheduleNextAlarm(appContext);

            AppExecutors.runOnMain(() -> Toast.makeText(
                    appContext,
                    showOk ? R.string.schedule_updated : R.string.no_internet_connection,
                    Toast.LENGTH_SHORT
            ).show());

            if (showDailyFeedback) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            return Result.success();
        } finally {
            if (showDailyFeedback) {
                DailyScheduleWidget.setRefreshState(appContext, DailyScheduleWidget.RefreshState.IDLE);
            }
        }
    }
}
