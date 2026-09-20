package com.vintro.wsplanner.ui.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.ui.adapters.ScheduleAdapter;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ImageButton buttonOpenFile;
    private ImageButton buttonMenu;
    private ImageButton buttonPrevDay;
    private ImageButton buttonToday;
    private ImageButton buttonNextDay;
    private ImageButton buttonCalendarPicker;
    private TextView selectedDateText;
    private TextView lastSyncText;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerSchedule;

    private ScheduleAdapter scheduleAdapter;
    private ScheduleRepository scheduleRepository;

    private LocalDate selectedDate = LocalDate.now();
    private DaySchedule currentDaySchedule;

    // timer handler for minute updates
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private Runnable tickerRunnable;

    private final DateTimeFormatter syncTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter syncDateFormatter = DateTimeFormatter.ofPattern("dd.MM, HH:mm");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // check onboarding status
        String login = PreferencesManager.getGlobalLoginPref(this);
        String major = PreferencesManager.getGlobalMajorPref(this);
        String stage = PreferencesManager.getOnboardingStage(this);
        boolean isSpecialtyDone = PreferencesManager.isGlobalSpecialtyConfigured(this);

        if (login == null || PreferencesManager.ONBOARDING_STAGE_LOGIN.equals(stage)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (major == null || PreferencesManager.ONBOARDING_STAGE_COURSE.equals(stage)
                || PreferencesManager.getGlobalDegreeLevelPref(this) == null
                || PreferencesManager.getGlobalStudyModePref(this) == null) {
            startActivity(new Intent(this, SetupCourseActivity.class));
            finish();
            return;
        }

        if (!isSpecialtyDone || PreferencesManager.ONBOARDING_STAGE_SPECIALTY.equals(stage)) {
            startActivity(new Intent(this, SetupSpecialtyActivity.class));
            finish();
            return;
        }

        if (PreferencesManager.ONBOARDING_STAGE_ADDITIONAL.equals(stage)) {
            startActivity(new Intent(this, SetupAdditionalActivity.class));
            finish();
            return;
        }

        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_coordinator), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        scheduleRepository = ScheduleRepository.getInstance(this);

        initViews();
        setupRecyclerView();
        setupListeners();
        loadSchedule(false);
    }

    private void initViews() {
        buttonOpenFile = findViewById(R.id.button_open_file);
        buttonMenu = findViewById(R.id.button_menu);
        buttonPrevDay = findViewById(R.id.button_prev_day);
        buttonToday = findViewById(R.id.button_today);
        buttonNextDay = findViewById(R.id.button_next_day);
        buttonCalendarPicker = findViewById(R.id.button_calendar_picker);
        selectedDateText = findViewById(R.id.selected_date_text);
        lastSyncText = findViewById(R.id.last_sync_text);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        recyclerSchedule = findViewById(R.id.recycler_schedule);
    }

    private void setupRecyclerView() {
        scheduleAdapter = new ScheduleAdapter(this);
        recyclerSchedule.setLayoutManager(new LinearLayoutManager(this));
        recyclerSchedule.setAdapter(scheduleAdapter);
        recyclerSchedule.setItemAnimator(new DefaultItemAnimator());
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_slide_up);
        recyclerSchedule.setLayoutAnimation(animation);
    }

    private void setupListeners() {
        // open excel file button
        buttonOpenFile.setOnClickListener(v -> openScheduleFile());

        // popup menu
        buttonMenu.setOnClickListener(this::showPopupMenu);

        // day navigation buttons
        buttonPrevDay.setOnClickListener(v -> {
            selectedDate = selectedDate.minusDays(1);
            if (scheduleAdapter != null) {
                scheduleAdapter.clear();
            }
            updateDateUI();
            loadSchedule(false);
        });

        buttonNextDay.setOnClickListener(v -> {
            selectedDate = selectedDate.plusDays(1);
            if (scheduleAdapter != null) {
                scheduleAdapter.clear();
            }
            updateDateUI();
            loadSchedule(false);
        });

        buttonToday.setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            if (scheduleAdapter != null) {
                scheduleAdapter.clear();
            }
            updateDateUI();
            loadSchedule(false);
        });

        // calendar picker dialog
        buttonCalendarPicker.setOnClickListener(v -> openCalendarPicker());
        selectedDateText.setOnClickListener(v -> openCalendarPicker());

        // swipe to refresh
        swipeRefreshLayout.setOnRefreshListener(() -> handleManualRefresh(true));
    }

    private void updateDateUI() {
        LocalDate today = LocalDate.now();
        buttonToday.setVisibility(selectedDate.isEqual(today) ? View.INVISIBLE : View.VISIBLE);

        // format day of week and date
        String dayOfWeek = selectedDate.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL,
                Locale.getDefault()
        );
        if (!dayOfWeek.isEmpty()) {
            dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase(Locale.getDefault()) + dayOfWeek.substring(1);
        }
        String formatted = dayOfWeek + ", " + selectedDate.format(DateTimeFormatter.ofPattern("dd.MM"));
        selectedDateText.setText(formatted);
    }

    private void updateLastSyncUI() {
        LocalDateTime syncTime = scheduleRepository.getLastSyncTime();
        if (syncTime == null) {
            lastSyncText.setVisibility(View.GONE);
            return;
        }

        lastSyncText.setVisibility(View.VISIBLE);
        LocalDate syncDate = syncTime.toLocalDate();
        LocalDate today = LocalDate.now();
        String timeStr = syncTime.format(syncTimeFormatter);

        String text;
        if (syncDate.isEqual(today)) {
            text = getString(R.string.schedule_updated_prefix, getString(R.string.schedule_updated_today, timeStr));
        } else if (syncDate.isEqual(today.minusDays(1))) {
            text = getString(R.string.schedule_updated_prefix, getString(R.string.schedule_updated_yesterday, timeStr));
        } else {
            text = getString(R.string.schedule_updated_prefix, syncTime.format(syncDateFormatter));
        }
        lastSyncText.setText(text);
    }

    private void loadSchedule(boolean forceRefresh) {
        swipeRefreshLayout.setRefreshing(true);
        updateDateUI();

        scheduleRepository.getScheduleForDate(selectedDate, forceRefresh, new ScheduleRepository.ScheduleCallback<DaySchedule>() {
            @Override
            public void onSuccess(DaySchedule result) {
                currentDaySchedule = result;
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    scheduleAdapter.submitDaySchedule(result);
                    recyclerSchedule.scheduleLayoutAnimation();
                    updateLastSyncUI();
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e(TAG, "Failed to load schedule: " + e.getMessage());
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    scheduleAdapter.clear();
                    Toast.makeText(MainActivity.this, "Error loading schedule: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    updateLastSyncUI();
                });
            }
        });
    }

    private void handleManualRefresh(boolean fromSwipe) {
        long cooldownLeft = scheduleRepository.getCooldownRemainingSeconds();
        if (cooldownLeft > 0) {
            if (fromSwipe) swipeRefreshLayout.setRefreshing(false);
            Toast.makeText(this, getString(R.string.schedule_cooldown_message, cooldownLeft), Toast.LENGTH_SHORT).show();
            return;
        }

        loadSchedule(true);
    }

    private void openScheduleFile() {
        Toast.makeText(this, getString(R.string.schedule_opening_file), Toast.LENGTH_SHORT).show();

        scheduleRepository.getScheduleFileForOpen(new ScheduleRepository.FileReadyCallback() {
            @Override
            public void onFileReady(File file) {
                runOnUiThread(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".provider", file);
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent, getString(R.string.menu_refresh_schedule)));
                    } catch (Exception e) {
                        Logger.e(TAG, "Error opening schedule file: " + e.getMessage());
                        Toast.makeText(MainActivity.this, getString(R.string.schedule_open_file_error), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.schedule_open_file_error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openCalendarPicker() {
        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
                    if (scheduleAdapter != null) {
                        scheduleAdapter.clear();
                    }
                    updateDateUI();
                    loadSchedule(false);
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth()
        );
        picker.show();
    }

    private void showPopupMenu(View anchor) {
        Context wrapper = new ContextThemeWrapper(this, R.style.CustomPopupMenuTheme);
        PopupMenu popup = new PopupMenu(wrapper, anchor, Gravity.END);
        popup.setForceShowIcon(true);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());

        int iconColor = UIHelper.getThemeColor(this, R.attr.app_text);
        for (int i = 0; i < popup.getMenu().size(); i++) {
            MenuItem item = popup.getMenu().getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                Drawable wrapped = DrawableCompat.wrap(icon.mutate());
                DrawableCompat.setTint(wrapped, iconColor);
                item.setIcon(wrapped);
            }
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                handleManualRefresh(false);
                return true;
            } else if (id == R.id.action_change_group) {
                Intent setupIntent = new Intent(this, SetupCourseActivity.class);
                startActivity(setupIntent);
                return true;
            } else if (id == R.id.action_settings) {
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            }
            return false;
        });

        popup.show();
    }

    // tick every minute on the clock minute boundary
    private void startMinuteTicker() {
        stopMinuteTicker();

        long nowMillis = System.currentTimeMillis();
        long delayToNextMinute = 60000 - (nowMillis % 60000);

        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                onMinuteTick();
                tickerHandler.postDelayed(this, 60000);
            }
        };

        tickerHandler.postDelayed(tickerRunnable, delayToNextMinute);
    }

    private void stopMinuteTicker() {
        if (tickerRunnable != null) {
            tickerHandler.removeCallbacks(tickerRunnable);
            tickerRunnable = null;
        }
    }

    private void onMinuteTick() {
        if (selectedDate.isEqual(LocalDate.now()) && currentDaySchedule != null) {
            // refresh schedule items for timer updates
            scheduleAdapter.submitDaySchedule(currentDaySchedule);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDateUI();
        updateLastSyncUI();
        startMinuteTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMinuteTicker();
    }
}
