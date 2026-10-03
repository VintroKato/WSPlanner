package com.vintro.wsplanner.ui.activities;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.DailyScheduleWidget;
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.Logger;

// activity for configuring widget appearance and click actions
public class WidgetConfigureActivity extends AppCompatActivity {
    private MaterialButtonToggleGroup widgetTypeToggleGroup;

    // Small widget preview views
    private LinearLayout containerSmallPreview;
    private FrameLayout previewContainer;
    private TextView previewTime;
    private View previewDivider;
    private TextView previewSubject;
    private TextView previewDetails;

    // Daily schedule widget preview views
    private LinearLayout containerDailyPreview;
    private FrameLayout previewDailyRoot;
    private TextView previewDailyTitle;
    private TextView previewDailySubtitle;
    private ImageView previewBtnExcel;
    private ImageView previewBtnRefresh;
    private ImageView previewBtnApp;
    private LinearLayout previewDailyCard1;
    private TextView previewCard1Time;
    private TextView previewCard1Subject;
    private TextView previewCard1Room;

    // Sections
    private LinearLayout sectionSmallWidgetOptions;
    private LinearLayout sectionDailyWidgetOptions;

    // Theme Options
    private RadioGroup themeRadioGroup;
    private RadioButton radioThemeDark;
    private RadioButton radioThemeLight;

    // Small Widget Actions
    private RadioGroup clickActionRadioGroup;
    private RadioButton radioClickDetails;
    private RadioButton radioClickToggle;
    private RadioButton radioClickApp;
    private RadioButton radioClickExcel;
    private RadioButton radioClickRefresh;

    private TextView breakActionLabel;
    private RadioGroup breakActionRadioGroup;
    private RadioButton radioBreakApp;
    private RadioButton radioBreakExcel;

    // Daily Widget Buttons
    private MaterialSwitch switchShowExcel;
    private MaterialSwitch switchShowRefresh;
    private MaterialSwitch switchShowApp;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_widget_configure);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Check if launched from system widget host
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        Logger.d("WidgetConfigureActivity.onCreate", "Initializing widget configuration (widgetId=" + appWidgetId + ")");

        initViews();
        loadSettings();
        setupListeners();
        determineInitialTab();
        updatePreview();
    }

    // initialize preview and options views
    private void initViews() {
        ImageButton buttonBack = findViewById(R.id.button_back);
        buttonBack.setOnClickListener(v -> finish());

        widgetTypeToggleGroup = findViewById(R.id.widget_type_toggle_group);

        // Small Widget Preview
        containerSmallPreview = findViewById(R.id.container_small_preview);
        previewContainer = findViewById(R.id.preview_container);
        previewTime = findViewById(R.id.preview_time);
        previewDivider = findViewById(R.id.preview_divider);
        previewSubject = findViewById(R.id.preview_subject);
        previewDetails = findViewById(R.id.preview_details);

        // Daily Widget Preview
        containerDailyPreview = findViewById(R.id.container_daily_preview);
        previewDailyRoot = findViewById(R.id.preview_daily_root);
        previewDailyTitle = findViewById(R.id.preview_daily_title);
        previewDailySubtitle = findViewById(R.id.preview_daily_subtitle);
        previewBtnExcel = findViewById(R.id.preview_btn_excel);
        previewBtnRefresh = findViewById(R.id.preview_btn_refresh);
        previewBtnApp = findViewById(R.id.preview_btn_app);
        previewDailyCard1 = findViewById(R.id.preview_daily_card_1);
        previewCard1Time = findViewById(R.id.preview_card_1_time);
        previewCard1Subject = findViewById(R.id.preview_card_1_subject);
        previewCard1Room = findViewById(R.id.preview_card_1_room);

        // Sections
        sectionSmallWidgetOptions = findViewById(R.id.section_small_widget_options);
        sectionDailyWidgetOptions = findViewById(R.id.section_daily_widget_options);

        // Themes
        themeRadioGroup = findViewById(R.id.theme_radio_group);
        radioThemeDark = findViewById(R.id.radio_theme_dark);
        radioThemeLight = findViewById(R.id.radio_theme_light);

        // Small Widget Actions
        clickActionRadioGroup = findViewById(R.id.click_action_radio_group);
        radioClickDetails = findViewById(R.id.radio_click_details);
        radioClickToggle = findViewById(R.id.radio_click_toggle);
        radioClickApp = findViewById(R.id.radio_click_app);
        radioClickExcel = findViewById(R.id.radio_click_excel);
        radioClickRefresh = findViewById(R.id.radio_click_refresh);

        breakActionLabel = findViewById(R.id.break_action_label);
        breakActionRadioGroup = findViewById(R.id.break_action_radio_group);
        radioBreakApp = findViewById(R.id.radio_break_app);
        radioBreakExcel = findViewById(R.id.radio_break_excel);

        // Daily Widget Buttons
        switchShowExcel = findViewById(R.id.switch_show_excel);
        switchShowRefresh = findViewById(R.id.switch_show_refresh);
        switchShowApp = findViewById(R.id.switch_show_app);

        MaterialButton buttonSave = findViewById(R.id.button_save);
        buttonSave.setOnClickListener(v -> saveSettings());
    }

    // determine which widget configuration tab to show
    private void determineInitialTab() {
        TextView titleLabel = findViewById(R.id.title_label);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId);
            boolean isDaily = (info != null && info.provider != null && DailyScheduleWidget.class.getName().equals(info.provider.getClassName()));
            Logger.d("WidgetConfigureActivity.determineInitialTab", "Configuring specific widget " + appWidgetId + " (isDaily=" + isDaily + ")");

            // Hide the toggle group completely when opened for a specific widget
            widgetTypeToggleGroup.setVisibility(View.GONE);

            if (isDaily) {
                if (titleLabel != null) titleLabel.setText(R.string.widget_daily_schedule_name);
                showDailyWidgetUI();
            } else {
                if (titleLabel != null) titleLabel.setText(R.string.widget_current_lesson_name);
                showSmallWidgetUI();
            }
            return;
        }

        Logger.d("WidgetConfigureActivity.determineInitialTab", "Opened from settings: showing general widget tabs");
        // Opened from SettingsActivity: show tabs so user can choose
        widgetTypeToggleGroup.setVisibility(View.VISIBLE);
        showSmallWidgetUI();
    }

    // display UI options for daily schedule widget
    private void showDailyWidgetUI() {
        containerSmallPreview.setVisibility(View.GONE);
        containerDailyPreview.setVisibility(View.VISIBLE);
        sectionSmallWidgetOptions.setVisibility(View.GONE);
        sectionDailyWidgetOptions.setVisibility(View.VISIBLE);
    }

    // display UI options for small current lesson widget
    private void showSmallWidgetUI() {
        containerSmallPreview.setVisibility(View.VISIBLE);
        containerDailyPreview.setVisibility(View.GONE);
        sectionSmallWidgetOptions.setVisibility(View.VISIBLE);
        sectionDailyWidgetOptions.setVisibility(View.GONE);
    }

    // load existing preferences into UI controls
    private void loadSettings() {
        // Theme (only Dark and Light)
        String theme = PreferencesManager.getWidgetTheme(this);
        if (PreferencesManager.WIDGET_THEME_LIGHT.equals(theme)) {
            radioThemeLight.setChecked(true);
        } else {
            radioThemeDark.setChecked(true);
        }

        // Click Action
        String clickAction = PreferencesManager.getWidgetClickAction(this);
        if (PreferencesManager.WIDGET_CLICK_OPEN_DETAILS.equals(clickAction)) {
            radioClickDetails.setChecked(true);
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_APP.equals(clickAction)) {
            radioClickApp.setChecked(true);
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_EXCEL.equals(clickAction)) {
            radioClickExcel.setChecked(true);
        } else if (PreferencesManager.WIDGET_CLICK_REFRESH.equals(clickAction)) {
            radioClickRefresh.setChecked(true);
        } else {
            radioClickToggle.setChecked(true);
        }

        // Break Action
        String breakAction = PreferencesManager.getWidgetBreakAction(this);
        if (PreferencesManager.WIDGET_BREAK_OPEN_EXCEL.equals(breakAction)) {
            radioBreakExcel.setChecked(true);
        } else {
            radioBreakApp.setChecked(true);
        }

        updateBreakVisibility(radioClickToggle.isChecked() || radioClickDetails.isChecked());

        // Big Widget Action Buttons Visibility
        switchShowExcel.setChecked(PreferencesManager.isWidgetShowExcel(this));
        switchShowRefresh.setChecked(PreferencesManager.isWidgetShowRefresh(this));
        switchShowApp.setChecked(PreferencesManager.isWidgetShowApp(this));

        Logger.d("WidgetConfigureActivity.loadSettings", "Loaded widget configuration: theme=" + theme + ", clickAction=" + clickAction + ", breakAction=" + breakAction);
    }

    private void setupListeners() {
        widgetTypeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean isDaily = checkedId == R.id.tab_daily_widget;
            if (isDaily) {
                showDailyWidgetUI();
            } else {
                showSmallWidgetUI();
            }
        });

        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> updatePreview());

        clickActionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean needsBreakOption = (checkedId == R.id.radio_click_toggle || checkedId == R.id.radio_click_details);
            updateBreakVisibility(needsBreakOption);
        });

        switchShowExcel.setOnCheckedChangeListener((btn, checked) -> {
            if (previewBtnExcel != null) previewBtnExcel.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        switchShowRefresh.setOnCheckedChangeListener((btn, checked) -> {
            if (previewBtnRefresh != null) previewBtnRefresh.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
        switchShowApp.setOnCheckedChangeListener((btn, checked) -> {
            if (previewBtnApp != null) previewBtnApp.setVisibility(checked ? View.VISIBLE : View.GONE);
        });
    }

    private void updateBreakVisibility(boolean showBreakOption) {
        breakActionLabel.setVisibility(showBreakOption ? View.VISIBLE : View.GONE);
        breakActionRadioGroup.setVisibility(showBreakOption ? View.VISIBLE : View.GONE);
    }

    // update on-screen visual preview of widgets based on selected theme and button options
    private void updatePreview() {
        boolean isLight = radioThemeLight.isChecked();
        Logger.d("WidgetConfigureActivity.updatePreview", "Updating widget preview (isLight=" + isLight + ")");

        // Small Widget Preview update
        if (isLight) {
            previewContainer.setBackgroundResource(R.drawable.bg_widget_light);
            if (previewTime != null) {
                previewTime.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
            }
            if (previewDivider != null) {
                previewDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.widget_divider_light));
            }
            previewSubject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
            previewDetails.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_secondary));
        } else {
            previewContainer.setBackgroundResource(R.drawable.bg_widget_dark);
            if (previewTime != null) {
                previewTime.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
            }
            if (previewDivider != null) {
                previewDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.widget_divider_dark));
            }
            previewSubject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
            previewDetails.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_secondary));
        }

        // Daily Widget Preview update
        if (previewDailyRoot != null) {
            if (isLight) {
                previewDailyRoot.setBackgroundResource(R.drawable.bg_widget_light);
                previewDailyTitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                previewDailySubtitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_secondary));
                previewDailyCard1.setBackgroundResource(R.drawable.bg_widget_item_light);
                previewCard1Time.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                previewCard1Subject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
            } else {
                previewDailyRoot.setBackgroundResource(R.drawable.bg_widget_dark);
                previewDailyTitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                previewDailySubtitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_secondary));
                previewDailyCard1.setBackgroundResource(R.drawable.bg_widget_item_dark);
                previewCard1Time.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                previewCard1Subject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
            }
            previewBtnExcel.setVisibility(switchShowExcel.isChecked() ? View.VISIBLE : View.GONE);
            previewBtnRefresh.setVisibility(switchShowRefresh.isChecked() ? View.VISIBLE : View.GONE);
            previewBtnApp.setVisibility(switchShowApp.isChecked() ? View.VISIBLE : View.GONE);
        }
    }

    // persist widget preferences and broadcast update to all widget instances
    private void saveSettings() {
        // Save Theme
        String theme = radioThemeLight.isChecked() ? PreferencesManager.WIDGET_THEME_LIGHT : PreferencesManager.WIDGET_THEME_DARK;
        PreferencesManager.setWidgetTheme(this, theme);

        // Save Click Action
        String clickAction = PreferencesManager.WIDGET_CLICK_TOGGLE;
        if (radioClickDetails.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_OPEN_DETAILS;
        else if (radioClickApp.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_OPEN_APP;
        else if (radioClickExcel.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_OPEN_EXCEL;
        else if (radioClickRefresh.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_REFRESH;
        PreferencesManager.setWidgetClickAction(this, clickAction);

        // Save Break Action
        String breakAction = radioBreakExcel.isChecked() ? PreferencesManager.WIDGET_BREAK_OPEN_EXCEL : PreferencesManager.WIDGET_BREAK_OPEN_APP;
        PreferencesManager.setWidgetBreakAction(this, breakAction);

        // Save Big Widget Quick Action Buttons
        PreferencesManager.setWidgetShowExcel(this, switchShowExcel.isChecked());
        PreferencesManager.setWidgetShowRefresh(this, switchShowRefresh.isChecked());
        PreferencesManager.setWidgetShowApp(this, switchShowApp.isChecked());

        Logger.i("WidgetConfigureActivity.saveSettings", "Saved widget settings: theme=" + theme + ", click=" + clickAction + ", break=" + breakAction);

        // Update all widget instances immediately
        WidgetUpdateManager.updateAllWidgets(this);

        // If opened as configuration activity for widget placement
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
        }

        Toast.makeText(this, R.string.subject_notes_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
