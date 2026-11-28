package com.vintro.wsplanner.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.ui.widgets.WidgetActionHelper;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.ScheduleFileOpener;

// lightweight translucent activity launching external intents cleanly under android background launch rules
public class WidgetTrampolineActivity extends Activity {
    private static final String TAG = "WidgetTrampolineActivity";

    public static final String EXTRA_TRAMPOLINE_ACTION = "trampoline_action";
    public static final String EXTRA_MAP_QUERY = "trampoline_map_query";
    public static final String ACTION_OPEN_EXCEL = "open_excel";
    public static final String ACTION_OPEN_MAPS = "open_maps";

    public static Intent createOpenExcelIntent(Context context) {
        Intent intent = new Intent(context, WidgetTrampolineActivity.class);
        intent.putExtra(EXTRA_TRAMPOLINE_ACTION, ACTION_OPEN_EXCEL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return intent;
    }

    public static Intent createOpenMapsIntent(Context context, String mapQuery) {
        Intent intent = new Intent(context, WidgetTrampolineActivity.class);
        intent.putExtra(EXTRA_TRAMPOLINE_ACTION, ACTION_OPEN_MAPS);
        intent.putExtra(EXTRA_MAP_QUERY, mapQuery);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent != null ? intent.getStringExtra(EXTRA_TRAMPOLINE_ACTION) : null;
        Logger.d(TAG + ".onCreate", "Executing widget trampoline action: " + action);

        if (ACTION_OPEN_EXCEL.equals(action)) {
            if (!PreferencesManager.isOnboardingCompleted(this)
                    || !ScheduleRepository.getInstance(this).isConfigurationComplete()) {
                openMainApp();
                finish();
                overridePendingTransition(0, 0);
                return;
            }
            Toast.makeText(this, R.string.schedule_opening_file, Toast.LENGTH_SHORT).show();
            ScheduleFileOpener.openExcelSchedule(this, () -> {
                if (!isFinishing() && !isDestroyed()) {
                    finish();
                    overridePendingTransition(0, 0);
                }
            });
            return;
        }

        if (ACTION_OPEN_MAPS.equals(action)) {
            String query = intent != null ? intent.getStringExtra(EXTRA_MAP_QUERY) : null;
            WidgetActionHelper.openLocationInMaps(this, query);
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        openMainApp();
        finish();
        overridePendingTransition(0, 0);
    }

    private void openMainApp() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.putExtra(MainActivity.EXTRA_TARGET_TAB, R.id.nav_schedule);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mainIntent);
    }
}
