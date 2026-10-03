package com.vintro.wsplanner.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;

import java.io.File;

// helper utility to open schedule excel file with external viewer or system chooser
public class ScheduleFileOpener {

    // request cached schedule file and open with external excel viewer
    public static void openExcelSchedule(Context context) {
        Logger.d("ScheduleFileOpener.openExcelSchedule", "Requesting schedule file to open");

        if (!ScheduleRepository.getInstance(context).isConfigurationComplete()) {
            Logger.d("ScheduleFileOpener.openExcelSchedule", "Student configuration not completed, aborting file open");
            runOnMainThread(context, () -> Toast.makeText(
                    context,
                    R.string.widget_setup_required_title,
                    Toast.LENGTH_SHORT
            ).show());
            return;
        }

        ScheduleRepository.getInstance(context).getScheduleFileForOpen(new ScheduleRepository.FileReadyCallback() {
            @Override
            public void onFileReady(File file) {
                Logger.i("ScheduleFileOpener.openExcelSchedule", "Schedule file ready: " + file.getAbsolutePath());
                runOnMainThread(context, () -> openFileWithChooserFallback(context, file));
            }

            @Override
            public void onError(Exception e) {
                Logger.e("ScheduleFileOpener.openExcelSchedule", "Failed to get schedule file: " + e.getMessage());
                runOnMainThread(context, () -> Toast.makeText(
                        context,
                        context.getString(R.string.schedule_open_file_error) + (e.getMessage() != null ? ": " + e.getMessage() : ""),
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    // attempt direct action view or fall back to system chooser dialog
    private static void openFileWithChooserFallback(Context context, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent directIntent = new Intent(Intent.ACTION_VIEW);
            directIntent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            directIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!(context instanceof Activity)) {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(directIntent);
            Logger.i("ScheduleFileOpener.openFileWithChooserFallback", "Schedule file opened directly");
        } catch (ActivityNotFoundException e) {
            Logger.w("ScheduleFileOpener.openFileWithChooserFallback", "Direct open failed, falling back to Android chooser dialog: " + e.getMessage());
            try {
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooserIntent = Intent.createChooser(viewIntent, context.getString(R.string.schedule_opening_file));
                if (!(context instanceof Activity)) {
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(chooserIntent);
                Logger.i("ScheduleFileOpener.openFileWithChooserFallback", "System chooser dialog opened");
            } catch (Exception ex) {
                Logger.e("ScheduleFileOpener.openFileWithChooserFallback", "Chooser fallback also failed: " + ex.getMessage());
                Toast.makeText(context, context.getString(R.string.schedule_open_file_error), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Logger.e("ScheduleFileOpener.openFileWithChooserFallback", "Error opening schedule file: " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.schedule_open_file_error), Toast.LENGTH_SHORT).show();
        }
    }

    private static void runOnMainThread(Context context, Runnable runnable) {
        if (context instanceof Activity) {
            ((Activity) context).runOnUiThread(runnable);
        } else {
            android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
            mainHandler.post(runnable);
        }
    }
}
