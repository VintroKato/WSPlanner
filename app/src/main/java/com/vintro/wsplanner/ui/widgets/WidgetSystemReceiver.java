package com.vintro.wsplanner.ui.widgets;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.vintro.wsplanner.services.ScheduleSyncWorker;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;

// receives protected system broadcasts to restore widget schedules after reboot, update, or clock changes
public class WidgetSystemReceiver extends BroadcastReceiver {
    private static final String TAG = "WidgetSystemReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) {
            return;
        }
        Logger.i(TAG + ".onReceive", "Received system event: " + action);

        boolean isSupportedEvent = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action);

        if (!isSupportedEvent) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        final PendingResult pendingResult = goAsync();
        AppExecutors.runIo(() -> {
            try {
                if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                    ScheduleSyncWorker.ensurePeriodicSyncScheduled(appContext);
                }
                if (WidgetUpdateManager.hasActiveWidgets(appContext)) {
                    WidgetUpdateManager.updateAllWidgetsSync(appContext);
                    WidgetUpdateManager.scheduleNextAlarm(appContext);
                } else {
                    WidgetUpdateManager.cancelScheduledAlarm(appContext);
                }
            } catch (Exception e) {
                Logger.e(TAG + ".onReceive", "Error handling system event " + action + ": " + Logger.formatException(e));
            } finally {
                try {
                    pendingResult.finish();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
