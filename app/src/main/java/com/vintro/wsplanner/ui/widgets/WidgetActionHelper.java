package com.vintro.wsplanner.ui.widgets;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.utils.Logger;

import java.io.File;

// helper utility dispatching common widget actions such as opening excel or maps
public class WidgetActionHelper {

    // open excel schedule file from widget
    public static void openExcelSchedule(Context context) {
        Logger.d("WidgetActionHelper.openExcelSchedule", "Opening Excel file from widget action via ScheduleFileOpener");
        com.vintro.wsplanner.utils.ScheduleFileOpener.openExcelSchedule(context);
    }

    // launch external maps application for location navigation
    public static void openLocationInMaps(Context context, String query) {
        if (query == null || query.trim().isEmpty()) return;
        try {
            Logger.d("WidgetActionHelper.openLocationInMaps", "Opening maps with query: " + query);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(query));
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(mapIntent);
        } catch (Exception e) {
            Logger.e("WidgetActionHelper.openLocationInMaps", "Error opening map: " + e.getMessage());
        }
    }
}
