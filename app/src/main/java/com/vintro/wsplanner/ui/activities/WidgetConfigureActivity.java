package com.vintro.wsplanner.ui.activities;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.CurrentLessonWidget;
import com.vintro.wsplanner.ui.widgets.DailyScheduleWidget;
import com.vintro.wsplanner.ui.widgets.WidgetActionHelper;
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.Logger;

// activity for configuring widget appearance and click actions
public class WidgetConfigureActivity extends AppCompatActivity {
    public static final String EXTRA_PIN_SKIP_CONFIGURE = "pin_skip_configure";

    private ViewGroup configureContentContainer;
    private View widgetTypeCapsule;
    private TextView tabSmallWidget;
    private TextView tabDailyWidget;
    private boolean isDailyTabSelected = false;

    // small widget preview views
    private LinearLayout containerSmallPreview;
    private FrameLayout previewContainer;
    private TextView previewStatusChip;
    private TextView previewTime;
    private View previewDivider;
    private TextView previewSubject;
    private TextView previewDetails;

    // daily schedule widget preview views
    private LinearLayout containerDailyPreview;
    private FrameLayout previewDailyRoot;
    private TextView previewDailyTitle;
    private TextView previewDailySubtitle;
    private ImageView previewBtnExcel;
    private ImageView previewBtnRefresh;
    private ImageView previewBtnApp;
    private LinearLayout previewDailyCard1;
    private TextView previewCard1Time;
    private TextView previewCard1Type;
    private TextView previewCard1Subject;
    private TextView previewCard1Room;
    private LinearLayout previewDailyCard2;
    private TextView previewCard2Time;
    private TextView previewCard2Type;
    private TextView previewCard2Subject;
    private TextView previewCard2Room;

    // preview pin hint
    private TextView previewPinHint;
    private View bottomSaveContainer;

    // sections
    private LinearLayout sectionSmallWidgetOptions;
    private LinearLayout sectionDailyWidgetOptions;

    // theme tiles
    private MaterialCardView cardThemeAuto;
    private MaterialCardView cardThemeDark;
    private MaterialCardView cardThemeLight;
    private String selectedWidgetTheme = PreferencesManager.WIDGET_THEME_SYSTEM;

    // small widget actions
    private RadioGroup clickActionRadioGroup;
    private RadioButton radioClickDetails;
    private RadioButton radioClickToggle;
    private RadioButton radioClickApp;
    private RadioButton radioClickExcel;
    private RadioButton radioClickRefresh;

    private View breakActionContainer;
    private RadioGroup breakActionRadioGroup;
    private RadioButton radioBreakApp;
    private RadioButton radioBreakExcel;

    // daily widget options
    private RadioGroup dailyClickActionRadioGroup;
    private RadioButton radioDailyClickDetails;
    private RadioButton radioDailyClickApp;
    private MaterialSwitch switchShowExcel;
    private MaterialSwitch switchShowRefresh;
    private MaterialSwitch switchShowApp;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        UIHelper.setupSystemBars(this);
        setContentView(R.layout.activity_widget_configure);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // check if launched from system widget host
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        Logger.d("WidgetConfigureActivity.onCreate", "Initializing widget configuration (widgetId=" + appWidgetId + ")");

        // if placed as a widget, check if it was placed via settings pin request
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED);

            boolean fromPin = false;
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            if (options != null && options.getBoolean(EXTRA_PIN_SKIP_CONFIGURE, false)) {
                fromPin = true;
            }
            if (!fromPin && PreferencesManager.wasPinWidgetRequestedRecently(this)) {
                fromPin = true;
            }

            if (fromPin) {
                Logger.i("WidgetConfigureActivity.onCreate", "Widget " + appWidgetId + " was added via settings pin request. Skipping configuration.");
                PreferencesManager.clearPinWidgetRequested(this);
                WidgetUpdateManager.updateAllWidgets(this);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
                return;
            }
        }

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

        configureContentContainer = findViewById(R.id.configure_content_container);
        widgetTypeCapsule = findViewById(R.id.widget_type_capsule);
        tabSmallWidget = findViewById(R.id.tab_small_widget);
        tabDailyWidget = findViewById(R.id.tab_daily_widget);

        // small widget preview
        containerSmallPreview = findViewById(R.id.container_small_preview);
        previewContainer = findViewById(R.id.preview_container);
        previewStatusChip = findViewById(R.id.preview_status_chip);
        previewTime = findViewById(R.id.preview_time);
        previewDivider = findViewById(R.id.preview_divider);
        previewSubject = findViewById(R.id.preview_subject);
        previewDetails = findViewById(R.id.preview_details);

        // daily widget preview
        containerDailyPreview = findViewById(R.id.container_daily_preview);
        previewDailyRoot = findViewById(R.id.preview_daily_root);
        previewDailyTitle = findViewById(R.id.preview_daily_title);
        previewDailySubtitle = findViewById(R.id.preview_daily_subtitle);
        previewBtnExcel = findViewById(R.id.preview_btn_excel);
        previewBtnRefresh = findViewById(R.id.preview_btn_refresh);
        previewBtnApp = findViewById(R.id.preview_btn_app);
        previewDailyCard1 = findViewById(R.id.preview_daily_card_1);
        previewCard1Time = findViewById(R.id.preview_card_1_time);
        previewCard1Type = findViewById(R.id.preview_card_1_type);
        previewCard1Subject = findViewById(R.id.preview_card_1_subject);
        previewCard1Room = findViewById(R.id.preview_card_1_room);
        previewDailyCard2 = findViewById(R.id.preview_daily_card_2);
        previewCard2Time = findViewById(R.id.preview_card_2_time);
        previewCard2Type = findViewById(R.id.preview_card_2_type);
        previewCard2Subject = findViewById(R.id.preview_card_2_subject);
        previewCard2Room = findViewById(R.id.preview_card_2_room);

        // sections
        sectionSmallWidgetOptions = findViewById(R.id.section_small_widget_options);
        sectionDailyWidgetOptions = findViewById(R.id.section_daily_widget_options);

        // theme tiles
        cardThemeAuto = findViewById(R.id.card_widget_theme_auto);
        cardThemeDark = findViewById(R.id.card_widget_theme_dark);
        cardThemeLight = findViewById(R.id.card_widget_theme_light);

        // small widget actions
        clickActionRadioGroup = findViewById(R.id.click_action_radio_group);
        radioClickDetails = findViewById(R.id.radio_click_details);
        radioClickToggle = findViewById(R.id.radio_click_toggle);
        radioClickApp = findViewById(R.id.radio_click_app);
        radioClickExcel = findViewById(R.id.radio_click_excel);
        radioClickRefresh = findViewById(R.id.radio_click_refresh);

        breakActionContainer = findViewById(R.id.break_action_container);
        breakActionRadioGroup = findViewById(R.id.break_action_radio_group);
        radioBreakApp = findViewById(R.id.radio_break_app);
        radioBreakExcel = findViewById(R.id.radio_break_excel);

        // daily widget options
        dailyClickActionRadioGroup = findViewById(R.id.daily_click_action_radio_group);
        radioDailyClickDetails = findViewById(R.id.radio_daily_click_details);
        radioDailyClickApp = findViewById(R.id.radio_daily_click_app);
        switchShowExcel = findViewById(R.id.switch_show_excel);
        switchShowRefresh = findViewById(R.id.switch_show_refresh);
        switchShowApp = findViewById(R.id.switch_show_app);

        previewPinHint = findViewById(R.id.preview_pin_hint);
        bottomSaveContainer = findViewById(R.id.bottom_save_container);

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

            widgetTypeCapsule.setVisibility(View.GONE);
            if (bottomSaveContainer != null) {
                bottomSaveContainer.setVisibility(View.VISIBLE);
            }
            if (previewPinHint != null) {
                previewPinHint.setVisibility(View.GONE);
            }
            previewContainer.setClickable(false);
            previewDailyRoot.setClickable(false);
            previewContainer.setForeground(null);
            previewDailyRoot.setForeground(null);

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
        widgetTypeCapsule.setVisibility(View.VISIBLE);
        if (bottomSaveContainer != null) {
            bottomSaveContainer.setVisibility(View.GONE);
        }
        setupPinAffordance();
        showSmallWidgetUI();
    }

    private void updateCapsuleTabs(boolean isDaily) {
        isDailyTabSelected = isDaily;
        int activeTextColor = UIHelper.getThemeColor(this, R.attr.button_text);
        int inactiveTextColor = UIHelper.getThemeColor(this, R.attr.text_secondary);

        if (isDaily) {
            tabSmallWidget.setBackgroundResource(android.R.color.transparent);
            tabSmallWidget.setTextColor(inactiveTextColor);
            tabDailyWidget.setBackgroundResource(R.drawable.bg_capsule_tab_active);
            tabDailyWidget.setTextColor(activeTextColor);
        } else {
            tabSmallWidget.setBackgroundResource(R.drawable.bg_capsule_tab_active);
            tabSmallWidget.setTextColor(activeTextColor);
            tabDailyWidget.setBackgroundResource(android.R.color.transparent);
            tabDailyWidget.setTextColor(inactiveTextColor);
        }
    }

    // display UI options for daily schedule widget
    private void showDailyWidgetUI() {
        updateCapsuleTabs(true);
        containerSmallPreview.setVisibility(View.GONE);
        containerDailyPreview.setVisibility(View.VISIBLE);
        sectionSmallWidgetOptions.setVisibility(View.GONE);
        sectionDailyWidgetOptions.setVisibility(View.VISIBLE);
    }

    // display UI options for small current lesson widget
    private void showSmallWidgetUI() {
        updateCapsuleTabs(false);
        containerSmallPreview.setVisibility(View.VISIBLE);
        containerDailyPreview.setVisibility(View.GONE);
        sectionSmallWidgetOptions.setVisibility(View.VISIBLE);
        sectionDailyWidgetOptions.setVisibility(View.GONE);
    }

    // load existing preferences into UI controls
    private void loadSettings() {
        String theme = PreferencesManager.getWidgetTheme(this);
        if (PreferencesManager.WIDGET_THEME_LIGHT.equals(theme)) {
            selectedWidgetTheme = PreferencesManager.WIDGET_THEME_LIGHT;
        } else if (PreferencesManager.WIDGET_THEME_DARK.equals(theme)) {
            selectedWidgetTheme = PreferencesManager.WIDGET_THEME_DARK;
        } else {
            selectedWidgetTheme = PreferencesManager.WIDGET_THEME_SYSTEM;
        }
        UIHelper.setSelectableCardChecked(this, cardThemeAuto, PreferencesManager.WIDGET_THEME_SYSTEM.equals(selectedWidgetTheme));
        UIHelper.setSelectableCardChecked(this, cardThemeDark, PreferencesManager.WIDGET_THEME_DARK.equals(selectedWidgetTheme));
        UIHelper.setSelectableCardChecked(this, cardThemeLight, PreferencesManager.WIDGET_THEME_LIGHT.equals(selectedWidgetTheme));

        // click action
        String clickAction = PreferencesManager.getWidgetClickAction(this);
        if (PreferencesManager.WIDGET_CLICK_OPEN_DETAILS.equals(clickAction)) {
            radioClickDetails.setChecked(true);
        } else if (PreferencesManager.WIDGET_CLICK_TOGGLE.equals(clickAction)) {
            radioClickToggle.setChecked(true);
        } else if (PreferencesManager.WIDGET_CLICK_OPEN_EXCEL.equals(clickAction)) {
            radioClickExcel.setChecked(true);
        } else if (PreferencesManager.WIDGET_CLICK_REFRESH.equals(clickAction)) {
            radioClickRefresh.setChecked(true);
        } else {
            radioClickApp.setChecked(true);
        }

        // break action
        String breakAction = PreferencesManager.getWidgetBreakAction(this);
        if (PreferencesManager.WIDGET_BREAK_OPEN_EXCEL.equals(breakAction)) {
            radioBreakExcel.setChecked(true);
        } else {
            radioBreakApp.setChecked(true);
        }

        updateBreakVisibility(radioClickToggle.isChecked() || radioClickDetails.isChecked());

        // daily widget action buttons visibility
        switchShowExcel.setChecked(PreferencesManager.isWidgetShowExcel(this));
        switchShowRefresh.setChecked(PreferencesManager.isWidgetShowRefresh(this));
        switchShowApp.setChecked(PreferencesManager.isWidgetShowApp(this));

        // daily widget lesson click action
        String dailyAction = PreferencesManager.getDailyWidgetLessonAction(this);
        if (PreferencesManager.DAILY_WIDGET_CLICK_OPEN_APP.equals(dailyAction)) {
            radioDailyClickApp.setChecked(true);
        } else {
            radioDailyClickDetails.setChecked(true);
        }

        Logger.d("WidgetConfigureActivity.loadSettings", "Loaded widget configuration: theme=" + theme + ", clickAction=" + clickAction + ", breakAction=" + breakAction + ", dailyAction=" + dailyAction);
    }

    private void setupListeners() {
        tabSmallWidget.setOnClickListener(v -> {
            if (isDailyTabSelected) {
                Logger.d("WidgetConfigureActivity", "Switched to small widget tab");
                if (configureContentContainer != null) {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(180);
                    TransitionManager.beginDelayedTransition(configureContentContainer, transition);
                }
                showSmallWidgetUI();
            }
        });

        tabDailyWidget.setOnClickListener(v -> {
            if (!isDailyTabSelected) {
                Logger.d("WidgetConfigureActivity", "Switched to daily widget tab");
                if (configureContentContainer != null) {
                    AutoTransition transition = new AutoTransition();
                    transition.setDuration(180);
                    TransitionManager.beginDelayedTransition(configureContentContainer, transition);
                }
                showDailyWidgetUI();
            }
        });

        cardThemeAuto.setOnClickListener(v -> selectWidgetTheme(PreferencesManager.WIDGET_THEME_SYSTEM));
        cardThemeDark.setOnClickListener(v -> selectWidgetTheme(PreferencesManager.WIDGET_THEME_DARK));
        cardThemeLight.setOnClickListener(v -> selectWidgetTheme(PreferencesManager.WIDGET_THEME_LIGHT));

        clickActionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean needsBreakOption = (checkedId == R.id.radio_click_toggle || checkedId == R.id.radio_click_details);
            if (configureContentContainer != null) {
                AutoTransition transition = new AutoTransition();
                transition.setDuration(160);
                TransitionManager.beginDelayedTransition(configureContentContainer, transition);
            }
            updateBreakVisibility(needsBreakOption);
            savePreferences();
        });

        breakActionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> savePreferences());

        dailyClickActionRadioGroup.setOnCheckedChangeListener((group, checkedId) -> savePreferences());

        switchShowExcel.setOnCheckedChangeListener((btn, checked) -> {
            if (previewBtnExcel != null) previewBtnExcel.setVisibility(checked ? View.VISIBLE : View.GONE);
            savePreferences();
        });
        switchShowRefresh.setOnCheckedChangeListener((btn, checked) -> {
            if (previewBtnRefresh != null) previewBtnRefresh.setVisibility(checked ? View.VISIBLE : View.GONE);
            savePreferences();
        });
        switchShowApp.setOnCheckedChangeListener((btn, checked) -> {
            if (previewBtnApp != null) previewBtnApp.setVisibility(checked ? View.VISIBLE : View.GONE);
            savePreferences();
        });
    }

    private void selectWidgetTheme(String theme) {
        if (selectedWidgetTheme.equals(theme)) {
            return;
        }
        selectedWidgetTheme = theme;
        Logger.i("WidgetConfigureActivity.selectWidgetTheme", "Selected widget theme: " + theme);
        AnimationHelper.animateCardSelection(this, cardThemeAuto, PreferencesManager.WIDGET_THEME_SYSTEM.equals(theme));
        AnimationHelper.animateCardSelection(this, cardThemeDark, PreferencesManager.WIDGET_THEME_DARK.equals(theme));
        AnimationHelper.animateCardSelection(this, cardThemeLight, PreferencesManager.WIDGET_THEME_LIGHT.equals(theme));
        updatePreview();
        savePreferences();
    }

    private void updateBreakVisibility(boolean showBreakOption) {
        if (breakActionContainer != null) {
            breakActionContainer.setVisibility(showBreakOption ? View.VISIBLE : View.GONE);
        }
    }

    // update on-screen visual preview of widgets based on selected theme and button options
    private void updatePreview() {
        boolean isLight = WidgetActionHelper.isWidgetLightTheme(this, selectedWidgetTheme);
        Logger.d("WidgetConfigureActivity.updatePreview", "Updating widget preview (theme=" + selectedWidgetTheme + ", isLight=" + isLight + ")");

        // small widget preview update
        if (isLight) {
            previewContainer.setBackgroundResource(R.drawable.bg_widget_light);
            if (previewStatusChip != null) {
                previewStatusChip.setBackgroundResource(R.drawable.bg_badge_pill_light);
                previewStatusChip.setTextColor(ContextCompat.getColor(this, R.color.widget_badge_type_text_light));
            }
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
            if (previewStatusChip != null) {
                previewStatusChip.setBackgroundResource(R.drawable.bg_badge_pill);
                previewStatusChip.setTextColor(ContextCompat.getColor(this, R.color.badge_now_text));
            }
            if (previewTime != null) {
                previewTime.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
            }
            if (previewDivider != null) {
                previewDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.widget_divider_dark));
            }
            previewSubject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
            previewDetails.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_secondary));
        }

        // daily widget preview update
        if (previewDailyRoot != null) {
            if (isLight) {
                previewDailyRoot.setBackgroundResource(R.drawable.bg_widget_light);
                previewDailyTitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                previewDailySubtitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_secondary));
                if (previewDailyCard1 != null) {
                    previewDailyCard1.setBackgroundResource(R.drawable.bg_widget_item_light);
                }
                if (previewCard1Time != null) {
                    previewCard1Time.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                }
                if (previewCard1Subject != null) {
                    previewCard1Subject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                }
                if (previewCard1Type != null) {
                    previewCard1Type.setBackgroundResource(R.drawable.bg_badge_rect_light);
                    previewCard1Type.setTextColor(ContextCompat.getColor(this, R.color.widget_badge_type_text_light));
                }
                if (previewCard1Room != null) {
                    previewCard1Room.setTextColor(ContextCompat.getColor(this, R.color.widget_room_text_light));
                }

                if (previewDailyCard2 != null) {
                    previewDailyCard2.setBackgroundResource(R.drawable.bg_widget_item_light);
                }
                if (previewCard2Time != null) {
                    previewCard2Time.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                }
                if (previewCard2Subject != null) {
                    previewCard2Subject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_light_primary));
                }
                if (previewCard2Type != null) {
                    previewCard2Type.setBackgroundResource(R.drawable.bg_badge_rect_light);
                    previewCard2Type.setTextColor(ContextCompat.getColor(this, R.color.widget_badge_type_text_light));
                }
                if (previewCard2Room != null) {
                    previewCard2Room.setTextColor(ContextCompat.getColor(this, R.color.widget_room_text_light));
                }
            } else {
                previewDailyRoot.setBackgroundResource(R.drawable.bg_widget_dark);
                previewDailyTitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                previewDailySubtitle.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_secondary));
                if (previewDailyCard1 != null) {
                    previewDailyCard1.setBackgroundResource(R.drawable.bg_widget_item_dark);
                }
                if (previewCard1Time != null) {
                    previewCard1Time.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                }
                if (previewCard1Subject != null) {
                    previewCard1Subject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                }
                if (previewCard1Type != null) {
                    previewCard1Type.setBackgroundResource(R.drawable.bg_badge_rect);
                    previewCard1Type.setTextColor(ContextCompat.getColor(this, R.color.badge_now_text));
                }
                if (previewCard1Room != null) {
                    previewCard1Room.setTextColor(ContextCompat.getColor(this, R.color.badge_field_text));
                }

                if (previewDailyCard2 != null) {
                    previewDailyCard2.setBackgroundResource(R.drawable.bg_widget_item_dark);
                }
                if (previewCard2Time != null) {
                    previewCard2Time.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                }
                if (previewCard2Subject != null) {
                    previewCard2Subject.setTextColor(ContextCompat.getColor(this, R.color.widget_text_dark_primary));
                }
                if (previewCard2Type != null) {
                    previewCard2Type.setBackgroundResource(R.drawable.bg_badge_rect);
                    previewCard2Type.setTextColor(ContextCompat.getColor(this, R.color.badge_now_text));
                }
                if (previewCard2Room != null) {
                    previewCard2Room.setTextColor(ContextCompat.getColor(this, R.color.badge_field_text));
                }
            }
            previewBtnExcel.setVisibility(switchShowExcel.isChecked() ? View.VISIBLE : View.GONE);
            previewBtnRefresh.setVisibility(switchShowRefresh.isChecked() ? View.VISIBLE : View.GONE);
            previewBtnApp.setVisibility(switchShowApp.isChecked() ? View.VISIBLE : View.GONE);

            int actionIconColor = ContextCompat.getColor(this, isLight ? R.color.widget_text_light_primary : R.color.widget_text_dark_primary);
            if (previewBtnRefresh != null) {
                previewBtnRefresh.setImageTintList(ColorStateList.valueOf(actionIconColor));
            }
            if (previewBtnApp != null) {
                previewBtnApp.setImageTintList(ColorStateList.valueOf(actionIconColor));
            }
        }
    }

    // setup interactive click-to-pin affordance on preview cards
    private void setupPinAffordance() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) return;

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        boolean isPinSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && appWidgetManager.isRequestPinAppWidgetSupported();

        if (previewPinHint != null) {
            previewPinHint.setVisibility(View.VISIBLE);
            if (isPinSupported) {
                previewPinHint.setText(R.string.widget_preview_tap_to_add);
                previewPinHint.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            } else {
                previewPinHint.setText(R.string.widget_pin_unsupported_hint);
                previewPinHint.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_info, 0, 0, 0);
            }
        }

        previewContainer.setOnClickListener(v -> {
            if (isPinSupported) {
                requestPinWidget(false);
            } else {
                showPinUnsupportedToast();
            }
        });

        previewDailyRoot.setOnClickListener(v -> {
            if (isPinSupported) {
                requestPinWidget(true);
            } else {
                showPinUnsupportedToast();
            }
        });

        if (previewPinHint != null) {
            previewPinHint.setOnClickListener(v -> {
                if (isPinSupported) {
                    requestPinWidget(isDailyTabSelected);
                } else {
                    showPinUnsupportedToast();
                }
            });
        }
    }

    // request system dialog to pin widget to home screen
    private void requestPinWidget(boolean isDaily) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            showPinUnsupportedToast();
            return;
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
            showPinUnsupportedToast();
            return;
        }

        // persist currently selected options first so newly placed widget has latest configuration
        savePreferences();

        Class<?> widgetClass = isDaily ? DailyScheduleWidget.class : CurrentLessonWidget.class;
        ComponentName provider = new ComponentName(this, widgetClass);

        // record pin request timestamp to skip redundant configuration activity when placed
        PreferencesManager.setPinWidgetRequested(this);

        Bundle pinOptions = new Bundle();
        pinOptions.putBoolean(EXTRA_PIN_SKIP_CONFIGURE, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean isLight = WidgetActionHelper.isWidgetLightTheme(this, selectedWidgetTheme);
            RemoteViews preview = new RemoteViews(getPackageName(),
                    isDaily ? R.layout.widget_preview_daily_schedule : R.layout.widget_preview_current_lesson);

            if (isDaily) {
                preview.setViewVisibility(R.id.preview_btn_excel, switchShowExcel.isChecked() ? View.VISIBLE : View.GONE);
                preview.setViewVisibility(R.id.preview_btn_refresh, switchShowRefresh.isChecked() ? View.VISIBLE : View.GONE);
                preview.setViewVisibility(R.id.preview_btn_app, switchShowApp.isChecked() ? View.VISIBLE : View.GONE);

                if (isLight) {
                    int lightIconColor = ContextCompat.getColor(this, R.color.widget_text_light_primary);
                    preview.setInt(R.id.widget_preview_daily_root, "setBackgroundResource", R.drawable.bg_widget_light);
                    preview.setTextColor(R.id.preview_daily_title, lightIconColor);
                    preview.setInt(R.id.preview_btn_refresh, "setColorFilter", lightIconColor);
                    preview.setInt(R.id.preview_btn_app, "setColorFilter", lightIconColor);
                    preview.setInt(R.id.preview_daily_card1, "setBackgroundResource", R.drawable.bg_widget_item_light);
                    preview.setInt(R.id.preview_daily_card2, "setBackgroundResource", R.drawable.bg_widget_item_light);
                    preview.setTextColor(R.id.preview_card1_time, lightIconColor);
                    preview.setTextColor(R.id.preview_card2_time, lightIconColor);
                    preview.setTextColor(R.id.preview_card1_subject, lightIconColor);
                    preview.setTextColor(R.id.preview_card2_subject, lightIconColor);
                    preview.setInt(R.id.preview_card1_type, "setBackgroundResource", R.drawable.bg_badge_rect_light);
                    preview.setInt(R.id.preview_card2_type, "setBackgroundResource", R.drawable.bg_badge_rect_light);
                    preview.setTextColor(R.id.preview_card1_type, ContextCompat.getColor(this, R.color.widget_badge_type_text_light));
                    preview.setTextColor(R.id.preview_card2_type, ContextCompat.getColor(this, R.color.widget_badge_type_text_light));
                    preview.setTextColor(R.id.preview_card1_room, ContextCompat.getColor(this, R.color.widget_room_text_light));
                    preview.setTextColor(R.id.preview_card2_room, ContextCompat.getColor(this, R.color.widget_room_text_light));
                }
            } else {
                if (isLight) {
                    preview.setInt(R.id.widget_preview_card, "setBackgroundResource", R.drawable.bg_widget_light);
                    preview.setInt(R.id.widget_preview_chip, "setBackgroundResource", R.drawable.bg_badge_pill_light);
                    preview.setTextColor(R.id.widget_preview_status, ContextCompat.getColor(this, R.color.widget_badge_type_text_light));
                    preview.setTextColor(R.id.widget_preview_time, ContextCompat.getColor(this, R.color.widget_text_light_primary));
                    preview.setInt(R.id.widget_preview_divider, "setBackgroundColor", ContextCompat.getColor(this, R.color.widget_divider_light));
                    preview.setTextColor(R.id.widget_preview_subject, ContextCompat.getColor(this, R.color.widget_text_light_primary));
                    preview.setTextColor(R.id.widget_preview_room, ContextCompat.getColor(this, R.color.widget_text_light_secondary));
                }
            }

            pinOptions.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview);
        }

        Intent callbackIntent = new Intent(this, WidgetUpdateManager.class);
        callbackIntent.setAction(WidgetUpdateManager.ACTION_REFRESH_WIDGETS);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent successCallback = PendingIntent.getBroadcast(this, 0, callbackIntent, flags);

        Logger.d("WidgetConfigureActivity.requestPinWidget", "Requesting widget pinning for " + widgetClass.getSimpleName());
        boolean success = appWidgetManager.requestPinAppWidget(provider, pinOptions, successCallback);
        if (!success) {
            Logger.w("WidgetConfigureActivity.requestPinWidget", "requestPinAppWidget returned false");
            PreferencesManager.clearPinWidgetRequested(this);
            showPinUnsupportedToast();
        }
    }

    private void showPinUnsupportedToast() {
        Toast.makeText(this, R.string.widget_pin_unsupported_toast, Toast.LENGTH_SHORT).show();
    }

    // persist current widget settings to preferences and broadcast update
    private void savePreferences() {
        String theme = selectedWidgetTheme;
        PreferencesManager.setWidgetTheme(this, theme);

        String clickAction = PreferencesManager.WIDGET_CLICK_TOGGLE;
        if (radioClickDetails.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_OPEN_DETAILS;
        else if (radioClickApp.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_OPEN_APP;
        else if (radioClickExcel.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_OPEN_EXCEL;
        else if (radioClickRefresh.isChecked()) clickAction = PreferencesManager.WIDGET_CLICK_REFRESH;
        PreferencesManager.setWidgetClickAction(this, clickAction);

        String breakAction = radioBreakExcel.isChecked() ? PreferencesManager.WIDGET_BREAK_OPEN_EXCEL : PreferencesManager.WIDGET_BREAK_OPEN_APP;
        PreferencesManager.setWidgetBreakAction(this, breakAction);

        PreferencesManager.setWidgetShowExcel(this, switchShowExcel.isChecked());
        PreferencesManager.setWidgetShowRefresh(this, switchShowRefresh.isChecked());
        PreferencesManager.setWidgetShowApp(this, switchShowApp.isChecked());

        String dailyAction = radioDailyClickApp.isChecked() ? PreferencesManager.DAILY_WIDGET_CLICK_OPEN_APP : PreferencesManager.DAILY_WIDGET_CLICK_OPEN_DETAILS;
        PreferencesManager.setDailyWidgetLessonAction(this, dailyAction);

        Logger.i("WidgetConfigureActivity.savePreferences", "Saved widget settings: theme=" + theme + ", click=" + clickAction + ", break=" + breakAction + ", dailyAction=" + dailyAction);

        WidgetUpdateManager.updateAllWidgets(this);
        WidgetUpdateManager.scheduleNextAlarmAsync(this);
    }

    // persist widget preferences and broadcast update to all widget instances
    private void saveSettings() {
        savePreferences();

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
        }

        Toast.makeText(this, R.string.subject_notes_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
