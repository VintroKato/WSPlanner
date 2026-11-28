package com.vintro.wsplanner.ui.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.vintro.wsplanner.BuildConfig;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.network.StudyPlanScraper;
import com.vintro.wsplanner.notifications.OnlineLessonNotificationManager;
import com.vintro.wsplanner.notifications.OnlineLessonNotificationScheduler;
import com.vintro.wsplanner.services.TeacherEmailService;
import com.vintro.wsplanner.services.UpdateManager;
import com.vintro.wsplanner.ui.activities.StudentPanelActivity;
import com.vintro.wsplanner.ui.activities.WidgetConfigureActivity;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.AppExecutors;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SettingsFragment extends Fragment {
    private static final String TAG = "SettingsFragment";
    public static final String ARG_PRE_ONBOARDING = "arg_pre_onboarding";

    public interface SettingsHost {
        View getThemeAnimationRoot();
        void onThemeAnimationStarted(boolean isNewNight);
        void onThemeChangedCompleted(boolean isNewNight);
        void onLanguageChanged();
        void onUpdateStatusChanged();
        void onVisibleWeekdaysChanged();
    }

    private boolean isPreOnboarding = false;
    private boolean currentNightMode;
    private boolean themeAnimationInProgress = false;

    private View settingsRoot;
    private ImageButton buttonBack;
    private MaterialCardView cardStudentProfile;
    private TextView tvProfileMajor;
    private TextView tvProfileSubtitle;

    private MaterialCardView cardAutoTheme;
    private MaterialCardView cardDarkTheme;
    private MaterialCardView cardLightTheme;

    private MaterialCardView cardPolish;
    private MaterialCardView cardEnglish;
    private MaterialCardView cardRussian;
    private MaterialCardView cardUkrainian;

    private View dividerVisibleDays;
    private View layoutVisibleDaysSection;
    private final Map<DayOfWeek, MaterialCardView> dayCards = new EnumMap<>(DayOfWeek.class);
    private Set<DayOfWeek> visibleWeekdays = EnumSet.noneOf(DayOfWeek.class);

    private View dividerWidgets;
    private View rowWidgetSettings;

    private View notificationsSectionHeader;
    private MaterialCardView cardNotificationsGroup;
    private ViewGroup layoutNotificationsCardContent;
    private View rowNotificationsToggle;
    private MaterialSwitch switchOnlineNotifications;
    private View layoutNotificationsOptions;
    private final Map<Integer, MaterialCardView> notificationPresetCards = new LinkedHashMap<>();
    private EditText editNotifCustom;
    private TextView tvNotificationsSummary;
    private final Set<Integer> selectedPresetOffsets = new TreeSet<>();
    @Nullable
    private Integer customLeadMinutes = null;
    private boolean isSyncingNotificationUi = false;
    private boolean pendingEnableAfterSystemSettings = false;

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    private View rowCheckUpdate;
    private View viewUpdateDot;
    private TextView tvAppVersion;
    private View dividerClearCache;
    private View rowClearCache;

    public static SettingsFragment newInstance(boolean preOnboarding) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_PRE_ONBOARDING, preOnboarding);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isPreOnboarding = getArguments().getBoolean(ARG_PRE_ONBOARDING, false);
        }
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                this::handleNotificationPermissionResult
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();
        currentNightMode = UIHelper.isNightMode(context);

        settingsRoot = view;
        buttonBack = view.findViewById(R.id.button_back);
        cardStudentProfile = view.findViewById(R.id.card_student_profile);
        tvProfileMajor = view.findViewById(R.id.tv_profile_major);
        tvProfileSubtitle = view.findViewById(R.id.tv_profile_subtitle);

        cardAutoTheme = view.findViewById(R.id.card_auto_theme);
        cardDarkTheme = view.findViewById(R.id.card_dark_theme);
        cardLightTheme = view.findViewById(R.id.card_light_theme);

        cardPolish = view.findViewById(R.id.card_polish);
        cardEnglish = view.findViewById(R.id.card_english);
        cardRussian = view.findViewById(R.id.card_russian);
        cardUkrainian = view.findViewById(R.id.card_ukrainian);

        dividerVisibleDays = view.findViewById(R.id.divider_visible_days);
        layoutVisibleDaysSection = view.findViewById(R.id.layout_visible_days_section);
        dividerWidgets = view.findViewById(R.id.divider_widgets);
        rowWidgetSettings = view.findViewById(R.id.row_widget_settings);

        notificationsSectionHeader = view.findViewById(R.id.notifications_section_header);
        cardNotificationsGroup = view.findViewById(R.id.card_notifications_group);
        layoutNotificationsCardContent = view.findViewById(R.id.layout_notifications_card_content);
        rowNotificationsToggle = view.findViewById(R.id.row_notifications_toggle);
        switchOnlineNotifications = view.findViewById(R.id.switch_online_notifications);
        layoutNotificationsOptions = view.findViewById(R.id.layout_notifications_options);
        editNotifCustom = view.findViewById(R.id.edit_notif_custom);
        tvNotificationsSummary = view.findViewById(R.id.tv_notifications_summary);

        rowCheckUpdate = view.findViewById(R.id.row_check_update);
        viewUpdateDot = view.findViewById(R.id.view_update_dot);
        tvAppVersion = view.findViewById(R.id.tv_app_version);
        dividerClearCache = view.findViewById(R.id.divider_clear_cache);
        rowClearCache = view.findViewById(R.id.row_clear_cache);

        boolean hidePostOnboardingSections = isPreOnboarding || !PreferencesManager.isOnboardingCompleted(context);

        if (buttonBack != null) {
            if (isPreOnboarding) {
                buttonBack.setVisibility(View.VISIBLE);
                buttonBack.setOnClickListener(v -> {
                    Logger.d(TAG, "User tapped back in pre-onboarding settings");
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                });
            } else {
                buttonBack.setVisibility(View.GONE);
            }
        }

        if (hidePostOnboardingSections) {
            if (cardStudentProfile != null) cardStudentProfile.setVisibility(View.GONE);
            if (dividerVisibleDays != null) dividerVisibleDays.setVisibility(View.GONE);
            if (layoutVisibleDaysSection != null) layoutVisibleDaysSection.setVisibility(View.GONE);
            if (dividerWidgets != null) dividerWidgets.setVisibility(View.GONE);
            if (rowWidgetSettings != null) rowWidgetSettings.setVisibility(View.GONE);
            if (notificationsSectionHeader != null) notificationsSectionHeader.setVisibility(View.GONE);
            if (cardNotificationsGroup != null) cardNotificationsGroup.setVisibility(View.GONE);
            if (dividerClearCache != null) dividerClearCache.setVisibility(View.GONE);
            if (rowClearCache != null) rowClearCache.setVisibility(View.GONE);
        } else {
            if (cardStudentProfile != null) {
                cardStudentProfile.setVisibility(View.VISIBLE);
                cardStudentProfile.setOnClickListener(v -> {
                    Logger.i(TAG, "User tapped student profile card");
                    startActivity(new Intent(requireContext(), StudentPanelActivity.class));
                });
                populateProfileSummary();
            }
            setupVisibleWeekdaysControls(view);
            setupNotificationControls(view);
            if (rowWidgetSettings != null) {
                rowWidgetSettings.setOnClickListener(v -> {
                    Logger.d(TAG, "Navigating to WidgetConfigureActivity");
                    startActivity(new Intent(requireContext(), WidgetConfigureActivity.class));
                });
            }
            if (dividerClearCache != null) dividerClearCache.setVisibility(View.VISIBLE);
            if (rowClearCache != null) {
                rowClearCache.setVisibility(View.VISIBLE);
                rowClearCache.setOnClickListener(v -> handleClearCacheClick());
            }
        }

        if (rowCheckUpdate != null) {
            rowCheckUpdate.setOnClickListener(v -> handleUpdateClick());
        }

        View rowShareLogs = view.findViewById(R.id.row_share_logs);
        if (rowShareLogs != null) {
            rowShareLogs.setOnClickListener(v -> {
                Logger.i(TAG, "User tapped Share Logs row");
                Logger.shareLogFile(requireContext());
            });
        }

        MaterialButton btnOpenGithub = view.findViewById(R.id.btn_open_github);
        if (btnOpenGithub != null) {
            btnOpenGithub.setOnClickListener(v -> {
                Logger.i(TAG, "User tapped GitHub button in Settings");
                UpdateManager.openGitHubRepo(requireContext());
            });
        }

        MaterialButton btnContactDev = view.findViewById(R.id.btn_contact_dev);
        if (btnContactDev != null) {
            btnContactDev.setOnClickListener(v -> openTelegramContact());
        }

        AppTheme theme = PreferencesManager.getThemePref(context);
        Language language = PreferencesManager.getLanguagePref(context);
        Logger.d(TAG + ".onViewCreated", "SettingsFragment created (preOnboarding=" + isPreOnboarding + ", theme=" + theme + ", language=" + language + ")");

        UIHelper.setSelectableCardChecked(context, cardAutoTheme, theme == AppTheme.AUTO);
        UIHelper.setSelectableCardChecked(context, cardDarkTheme, theme == AppTheme.DARK);
        UIHelper.setSelectableCardChecked(context, cardLightTheme, theme == AppTheme.LIGHT);

        UIHelper.setSelectableCardChecked(context, cardPolish, language == Language.POLISH);
        UIHelper.setSelectableCardChecked(context, cardEnglish, language == Language.ENGLISH);
        UIHelper.setSelectableCardChecked(context, cardRussian, language == Language.RUSSIAN);
        UIHelper.setSelectableCardChecked(context, cardUkrainian, language == Language.UKRAINIAN);

        bindThemeOnClick(cardAutoTheme, AppTheme.AUTO);
        bindThemeOnClick(cardDarkTheme, AppTheme.DARK);
        bindThemeOnClick(cardLightTheme, AppTheme.LIGHT);

        bindLanguageOnClick(cardPolish, Language.POLISH);
        bindLanguageOnClick(cardEnglish, Language.ENGLISH);
        bindLanguageOnClick(cardRussian, Language.RUSSIAN);
        bindLanguageOnClick(cardUkrainian, Language.UKRAINIAN);

        refreshUpdateUI();
    }

    private void setupVisibleWeekdaysControls(View root) {
        dayCards.clear();
        Context context = requireContext();
        Locale locale = Locale.getDefault();

        int[] cardIds = new int[]{
                R.id.card_day_mon,
                R.id.card_day_tue,
                R.id.card_day_wed,
                R.id.card_day_thu,
                R.id.card_day_fri,
                R.id.card_day_sat,
                R.id.card_day_sun
        };
        int[] textIds = new int[]{
                R.id.tv_day_mon,
                R.id.tv_day_tue,
                R.id.tv_day_wed,
                R.id.tv_day_thu,
                R.id.tv_day_fri,
                R.id.tv_day_sat,
                R.id.tv_day_sun
        };
        DayOfWeek[] orderedDays = DayOfWeek.values();

        visibleWeekdays = EnumSet.copyOf(PreferencesManager.getVisibleWeekdaysPref(context));

        for (int i = 0; i < orderedDays.length; i++) {
            DayOfWeek day = orderedDays[i];
            MaterialCardView card = root.findViewById(cardIds[i]);
            TextView label = root.findViewById(textIds[i]);
            if (card == null) continue;

            if (label != null) {
                label.setText(formatShortWeekday(day, locale));
            }
            dayCards.put(day, card);
            UIHelper.setSelectableCardChecked(context, card, visibleWeekdays.contains(day));

            card.setOnClickListener(v -> toggleVisibleWeekday(day, card));
        }
    }

    private void refreshVisibleWeekdaysSelection() {
        if (!isAdded() || dayCards.isEmpty()) return;
        Context context = requireContext();
        visibleWeekdays = EnumSet.copyOf(PreferencesManager.getVisibleWeekdaysPref(context));
        for (Map.Entry<DayOfWeek, MaterialCardView> entry : dayCards.entrySet()) {
            UIHelper.setSelectableCardChecked(context, entry.getValue(), visibleWeekdays.contains(entry.getKey()));
        }
    }

    private void toggleVisibleWeekday(DayOfWeek day, MaterialCardView card) {
        if (!isAdded()) return;
        Context context = requireContext();
        boolean isEnabled = visibleWeekdays.contains(day);
        if (isEnabled) {
            if (visibleWeekdays.size() <= 1) {
                Logger.d(TAG, "Prevented disabling last visible schedule weekday: " + day);
                Toast.makeText(context, R.string.settings_visible_days_min_one, Toast.LENGTH_SHORT).show();
                return;
            }
            visibleWeekdays.remove(day);
            AnimationHelper.animateCardSelection(context, card, false);
        } else {
            visibleWeekdays.add(day);
            AnimationHelper.animateCardSelection(context, card, true);
        }

        PreferencesManager.setVisibleWeekdaysPref(context, visibleWeekdays);
        Logger.i(TAG, "User toggled schedule weekday " + day + " -> enabled=" + !isEnabled);

        SettingsHost host = getHostCallback();
        if (host != null) {
            host.onVisibleWeekdaysChanged();
        }
    }

    private String formatShortWeekday(DayOfWeek day, Locale locale) {
        String name = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale);
        if (name == null || name.isEmpty()) return "";
        name = name.replace(".", "").trim();
        if (name.isEmpty()) return "";
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1).toLowerCase(locale);
    }

    private void setupNotificationControls(View root) {
        if (notificationsSectionHeader != null) {
            notificationsSectionHeader.setVisibility(View.VISIBLE);
        }
        if (cardNotificationsGroup != null) {
            cardNotificationsGroup.setVisibility(View.VISIBLE);
        }

        notificationPresetCards.clear();
        bindNotificationPresetCard(root, R.id.card_notif_0, 0);
        bindNotificationPresetCard(root, R.id.card_notif_5, 5);
        bindNotificationPresetCard(root, R.id.card_notif_10, 10);
        bindNotificationPresetCard(root, R.id.card_notif_15, 15);

        if (rowNotificationsToggle != null && switchOnlineNotifications != null) {
            rowNotificationsToggle.setOnClickListener(v -> switchOnlineNotifications.performClick());
        }

        if (switchOnlineNotifications != null) {
            switchOnlineNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isSyncingNotificationUi || !isAdded()) {
                    return;
                }
                handleNotificationSwitchToggle(isChecked);
            });
        }

        if (editNotifCustom != null) {
            editNotifCustom.setOnFocusChangeListener((v, hasFocus) -> {
                if (!isAdded()) {
                    return;
                }
                if (hasFocus) {
                    AnimationHelper.applySelectableInputStyle(requireContext(), editNotifCustom, true, customLeadMinutes != null, true);
                } else {
                    finalizeCustomNotificationInput();
                }
            });

            editNotifCustom.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    editNotifCustom.clearFocus();
                    hideKeyboard(editNotifCustom);
                    return true;
                }
                return false;
            });

            editNotifCustom.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isSyncingNotificationUi || !isAdded()) {
                        return;
                    }
                    handleCustomNotificationTextChanged(s != null ? s.toString() : "");
                }
            });
        }

        refreshNotificationUI(false);
    }

    private void bindNotificationPresetCard(View root, int cardId, int minutes) {
        MaterialCardView card = root.findViewById(cardId);
        if (card == null) {
            return;
        }
        notificationPresetCards.put(minutes, card);
        card.setOnClickListener(v -> toggleNotificationPreset(minutes, card));
    }

    public void refreshNotificationUI() {
        refreshNotificationUI(false);
    }

    private void refreshNotificationUI(boolean animateExpand) {
        if (!isAdded() || switchOnlineNotifications == null) {
            return;
        }
        Context context = requireContext();
        boolean hasPermission = OnlineLessonNotificationManager.hasNotificationPermission(context);

        if (pendingEnableAfterSystemSettings) {
            pendingEnableAfterSystemSettings = false;
            if (hasPermission) {
                Logger.i(TAG, "User granted notification permission in system settings, enabling reminders");
                enableOnlineNotifications(true);
                return;
            }
        }

        boolean prefEnabled = PreferencesManager.isOnlineNotificationsEnabled(context);
        boolean effectiveEnabled = prefEnabled && hasPermission;
        if (prefEnabled && !hasPermission) {
            Logger.w(TAG, "Notification permission revoked externally, turning off in-app toggle");
            PreferencesManager.setOnlineNotificationsEnabled(context, false);
            OnlineLessonNotificationScheduler.cancelScheduledAlarm(context);
        }

        selectedPresetOffsets.clear();
        customLeadMinutes = null;

        List<Integer> savedOffsets = PreferencesManager.getNotificationLeadMinutes(context);
        Integer savedCustom = PreferencesManager.getNotificationCustomMinutes(context);
        for (int minutes : savedOffsets) {
            if (notificationPresetCards.containsKey(minutes)) {
                selectedPresetOffsets.add(minutes);
            } else if (customLeadMinutes == null) {
                customLeadMinutes = minutes;
            }
        }
        if (customLeadMinutes == null && savedCustom != null && !notificationPresetCards.containsKey(savedCustom)
                && savedOffsets.contains(savedCustom)) {
            customLeadMinutes = savedCustom;
        }
        if (selectedPresetOffsets.isEmpty() && customLeadMinutes == null) {
            selectedPresetOffsets.add(OnlineLessonNotificationScheduler.DEFAULT_LEAD_MINUTES);
        }

        isSyncingNotificationUi = true;
        try {
            switchOnlineNotifications.setChecked(effectiveEnabled);
            for (Map.Entry<Integer, MaterialCardView> entry : notificationPresetCards.entrySet()) {
                UIHelper.setSelectableCardChecked(context, entry.getValue(), selectedPresetOffsets.contains(entry.getKey()));
            }
            if (editNotifCustom != null) {
                if (customLeadMinutes != null) {
                    editNotifCustom.setText(String.valueOf(customLeadMinutes));
                } else {
                    editNotifCustom.setText("");
                }
                AnimationHelper.applySelectableInputStyle(
                        context,
                        editNotifCustom,
                        editNotifCustom.hasFocus(),
                        customLeadMinutes != null,
                        false
                );
            }
        } finally {
            isSyncingNotificationUi = false;
        }

        setNotificationOptionsExpanded(effectiveEnabled, animateExpand);
        updateNotificationsSummary();
    }

    private void handleNotificationSwitchToggle(boolean isChecked) {
        Context context = requireContext();
        if (!isChecked) {
            Logger.i(TAG, "User disabled online class notifications");
            PreferencesManager.setOnlineNotificationsEnabled(context, false);
            PreferencesManager.clearDeliveredNotificationKeys(context);
            if (editNotifCustom != null) {
                editNotifCustom.clearFocus();
                hideKeyboard(editNotifCustom);
            }
            setNotificationOptionsExpanded(false, true);
            OnlineLessonNotificationScheduler.cancelScheduledAlarm(context);
            return;
        }

        if (OnlineLessonNotificationManager.hasNotificationPermission(context)) {
            enableOnlineNotifications(true);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean runtimeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
            if (runtimeGranted) {
                setNotificationSwitchSilently(false);
                showOpenNotificationSettingsDialog();
                return;
            }

            boolean alreadyRequested = PreferencesManager.wasNotificationPermissionRequested(context);
            boolean canShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
            if (!alreadyRequested || canShowRationale) {
                setNotificationSwitchSilently(false);
                PreferencesManager.setNotificationPermissionRequested(context, true);
                Logger.i(TAG, "Requesting POST_NOTIFICATIONS runtime permission from Settings");
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                setNotificationSwitchSilently(false);
                showOpenNotificationSettingsDialog();
            }
            return;
        }

        setNotificationSwitchSilently(false);
        showOpenNotificationSettingsDialog();
    }

    private void handleNotificationPermissionResult(boolean isGranted) {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        Logger.i(TAG, "POST_NOTIFICATIONS permission result in Settings: granted=" + isGranted);
        if (isGranted && OnlineLessonNotificationManager.hasNotificationPermission(context)) {
            enableOnlineNotifications(true);
            return;
        }

        setNotificationSwitchSilently(false);
        setNotificationOptionsExpanded(false, true);

        boolean permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
        if (permanentlyDenied) {
            showOpenNotificationSettingsDialog();
        } else {
            Toast.makeText(context, R.string.settings_notifications_denied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void enableOnlineNotifications(boolean animate) {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        Logger.i(TAG, "Enabling online class notifications in Settings");
        PreferencesManager.setOnlineNotificationsEnabled(context, true);
        if (selectedPresetOffsets.isEmpty() && customLeadMinutes == null) {
            selectedPresetOffsets.add(OnlineLessonNotificationScheduler.DEFAULT_LEAD_MINUTES);
        }
        persistNotificationOffsets(context);
        OnlineLessonNotificationManager.ensureChannel(context);

        setNotificationSwitchSilently(true);
        for (Map.Entry<Integer, MaterialCardView> entry : notificationPresetCards.entrySet()) {
            UIHelper.setSelectableCardChecked(context, entry.getValue(), selectedPresetOffsets.contains(entry.getKey()));
        }
        if (editNotifCustom != null) {
            AnimationHelper.applySelectableInputStyle(
                    context,
                    editNotifCustom,
                    editNotifCustom.hasFocus(),
                    customLeadMinutes != null,
                    false
            );
        }
        setNotificationOptionsExpanded(true, animate);
        updateNotificationsSummary();
        OnlineLessonNotificationScheduler.rescheduleNextAsync(context);
    }

    private void setNotificationSwitchSilently(boolean checked) {
        if (switchOnlineNotifications == null) {
            return;
        }
        isSyncingNotificationUi = true;
        try {
            switchOnlineNotifications.setChecked(checked);
        } finally {
            isSyncingNotificationUi = false;
        }
    }

    private void setNotificationOptionsExpanded(boolean expanded, boolean animate) {
        if (layoutNotificationsOptions == null) {
            return;
        }
        int targetVisibility = expanded ? View.VISIBLE : View.GONE;
        if (layoutNotificationsOptions.getVisibility() == targetVisibility) {
            return;
        }
        if (animate && settingsRoot instanceof ViewGroup) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition((ViewGroup) settingsRoot, transition);
        }
        layoutNotificationsOptions.setVisibility(targetVisibility);
    }

    private void toggleNotificationPreset(int minutes, MaterialCardView card) {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        boolean wasPresetSelected = selectedPresetOffsets.contains(minutes);
        boolean matchedFocusedCustom = customLeadMinutes != null && customLeadMinutes == minutes;

        if (editNotifCustom != null && editNotifCustom.hasFocus()) {
            editNotifCustom.clearFocus();
            hideKeyboard(editNotifCustom);
        }

        if (matchedFocusedCustom && !wasPresetSelected) {
            return;
        }

        boolean isSelected = selectedPresetOffsets.contains(minutes);
        if (isSelected) {
            if (selectedPresetOffsets.size() <= 1 && customLeadMinutes == null) {
                Logger.d(TAG, "Prevented unselecting last notification lead time: " + minutes);
                Toast.makeText(context, R.string.settings_notifications_min_one, Toast.LENGTH_SHORT).show();
                return;
            }
            selectedPresetOffsets.remove(minutes);
            AnimationHelper.animateCardSelection(context, card, false);
        } else {
            selectedPresetOffsets.add(minutes);
            AnimationHelper.animateCardSelection(context, card, true);
            if (customLeadMinutes != null && customLeadMinutes == minutes) {
                clearCustomInputField(context);
            }
        }

        Logger.i(TAG, "User toggled notification lead preset " + minutes + " min -> selected=" + !isSelected);
        persistNotificationOffsets(context);
        updateNotificationsSummary();
        OnlineLessonNotificationScheduler.rescheduleNextAsync(context);
    }

    private void handleCustomNotificationTextChanged(String rawText) {
        Context context = requireContext();
        String trimmed = rawText.trim();
        if (trimmed.isEmpty()) {
            customLeadMinutes = null;
            AnimationHelper.applySelectableInputStyle(context, editNotifCustom, editNotifCustom.hasFocus(), false, true);
            if (!selectedPresetOffsets.isEmpty()) {
                persistNotificationOffsets(context);
                updateNotificationsSummary();
                OnlineLessonNotificationScheduler.rescheduleNextAsync(context);
            }
            return;
        }

        int parsed;
        try {
            parsed = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return;
        }

        if (parsed > OnlineLessonNotificationScheduler.MAX_LEAD_MINUTES) {
            parsed = OnlineLessonNotificationScheduler.MAX_LEAD_MINUTES;
            isSyncingNotificationUi = true;
            try {
                String clampedStr = String.valueOf(parsed);
                editNotifCustom.setText(clampedStr);
                editNotifCustom.setSelection(clampedStr.length());
            } finally {
                isSyncingNotificationUi = false;
            }
        }

        customLeadMinutes = parsed;
        AnimationHelper.applySelectableInputStyle(context, editNotifCustom, editNotifCustom.hasFocus(), true, true);
        persistNotificationOffsets(context);
        updateNotificationsSummary();
        OnlineLessonNotificationScheduler.rescheduleNextAsync(context);
    }

    private void finalizeCustomNotificationInput() {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        if (customLeadMinutes != null && notificationPresetCards.containsKey(customLeadMinutes)) {
            int matchedPreset = customLeadMinutes;
            selectedPresetOffsets.add(matchedPreset);
            MaterialCardView presetCard = notificationPresetCards.get(matchedPreset);
            if (presetCard != null) {
                AnimationHelper.animateCardSelection(context, presetCard, true);
            }
            clearCustomInputField(context);
        } else if (editNotifCustom != null) {
            AnimationHelper.applySelectableInputStyle(context, editNotifCustom, false, customLeadMinutes != null, true);
        }

        if (selectedPresetOffsets.isEmpty() && customLeadMinutes == null) {
            int fallback = OnlineLessonNotificationScheduler.DEFAULT_LEAD_MINUTES;
            selectedPresetOffsets.add(fallback);
            MaterialCardView defaultCard = notificationPresetCards.get(fallback);
            if (defaultCard != null) {
                AnimationHelper.animateCardSelection(context, defaultCard, true);
            }
        }

        persistNotificationOffsets(context);
        updateNotificationsSummary();
        OnlineLessonNotificationScheduler.rescheduleNextAsync(context);
    }

    private void clearCustomInputField(Context context) {
        customLeadMinutes = null;
        if (editNotifCustom != null) {
            isSyncingNotificationUi = true;
            try {
                editNotifCustom.setText("");
            } finally {
                isSyncingNotificationUi = false;
            }
            AnimationHelper.applySelectableInputStyle(context, editNotifCustom, editNotifCustom.hasFocus(), false, true);
        }
    }

    private void persistNotificationOffsets(Context context) {
        TreeSet<Integer> combined = new TreeSet<>(selectedPresetOffsets);
        if (customLeadMinutes != null) {
            combined.add(customLeadMinutes);
        }
        if (combined.isEmpty()) {
            combined.add(OnlineLessonNotificationScheduler.DEFAULT_LEAD_MINUTES);
        }
        PreferencesManager.setNotificationLeadMinutes(context, combined);
        PreferencesManager.setNotificationCustomMinutes(context, customLeadMinutes);
    }

    private void updateNotificationsSummary() {
        if (!isAdded() || tvNotificationsSummary == null) {
            return;
        }
        TreeSet<Integer> combined = new TreeSet<>(selectedPresetOffsets);
        if (customLeadMinutes != null) {
            combined.add(customLeadMinutes);
        }
        if (combined.isEmpty()) {
            combined.add(OnlineLessonNotificationScheduler.DEFAULT_LEAD_MINUTES);
        }

        List<Integer> chronological = new ArrayList<>(combined);
        chronological.sort(Collections.reverseOrder());

        if (chronological.size() == 1) {
            int only = chronological.get(0);
            if (only == 0) {
                tvNotificationsSummary.setText(R.string.settings_notifications_summary_single_start);
            } else {
                tvNotificationsSummary.setText(getString(R.string.settings_notifications_summary_single_before, only));
            }
            return;
        }

        StringBuilder tokens = new StringBuilder();
        for (int i = 0; i < chronological.size(); i++) {
            int minutes = chronological.get(i);
            if (i > 0) {
                tokens.append(", ");
            }
            if (minutes == 0) {
                tokens.append(getString(R.string.settings_notifications_token_start));
            } else {
                tokens.append(getString(R.string.settings_notifications_token_before, minutes));
            }
        }
        tvNotificationsSummary.setText(getString(
                R.string.settings_notifications_summary_multi,
                chronological.size(),
                tokens.toString()
        ));
    }

    private void showOpenNotificationSettingsDialog() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        Context context = requireContext();
        Logger.i(TAG, "Showing dialog directing user to Android notification settings");
        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_notifications_permission_dialog_title)
                .setMessage(R.string.settings_notifications_permission_dialog_desc)
                .setPositiveButton(R.string.settings_notifications_open_system_settings, (dialog, which) -> {
                    pendingEnableAfterSystemSettings = true;
                    openAppNotificationSettings();
                })
                .setNegativeButton(R.string.settings_notifications_cancel, null)
                .show();
    }

    private void openAppNotificationSettings() {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            Logger.w(TAG, "Failed opening notification settings directly, falling back to app details: " + Logger.formatException(e));
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.fromParts("package", context.getPackageName(), null));
                startActivity(fallback);
            } catch (Exception ex) {
                Logger.e(TAG, "Failed opening app details settings: " + Logger.formatException(ex));
            }
        }
    }

    private void hideKeyboard(@Nullable View view) {
        if (!isAdded() || view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Nullable
    private SettingsHost getHostCallback() {
        Activity activity = getActivity();
        if (activity instanceof SettingsHost) {
            return (SettingsHost) activity;
        }
        return null;
    }

    private void populateProfileSummary() {
        if (!isAdded() || tvProfileMajor == null || tvProfileSubtitle == null) return;
        Context context = requireContext();
        String major = PreferencesManager.getGlobalMajorPref(context);
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(context);
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(context);
        int year = PreferencesManager.getGlobalYearPref(context);

        if (degreeLevel == DegreeLevel.ZERO_YEAR) {
            tvProfileMajor.setText("ROK ZEROWY");
            tvProfileSubtitle.setVisibility(View.GONE);
            return;
        }

        tvProfileMajor.setText(major != null && !major.trim().isEmpty() ? major.trim() : "—");
        StringBuilder subtitle = new StringBuilder();
        if (degreeLevel != null) {
            subtitle.append(formatDegreeShort(degreeLevel));
        }
        if (studyMode != null) {
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(formatStudyModeShort(studyMode));
        }
        if (year > 0) {
            if (subtitle.length() > 0) subtitle.append(" · ");
            subtitle.append(getString(R.string.onboarding_year_label)).append(" ").append(year);
        }
        if (subtitle.length() > 0) {
            tvProfileSubtitle.setText(subtitle.toString());
            tvProfileSubtitle.setVisibility(View.VISIBLE);
        } else {
            tvProfileSubtitle.setVisibility(View.GONE);
        }
    }

    private String formatDegreeShort(DegreeLevel level) {
        switch (level) {
            case BACHELORS:
                return getString(R.string.degree_level_bachelors);
            case MASTERS:
                return getString(R.string.degree_level_masters);
            case LONG_MASTERS:
                return getString(R.string.degree_level_long_masters);
            case ZERO_YEAR:
                return "ROK ZEROWY";
            default:
                return level.name();
        }
    }

    private String formatStudyModeShort(StudyMode mode) {
        switch (mode) {
            case FULL_TIME:
                return getString(R.string.study_mode_full_time);
            case PART_TIME:
                return getString(R.string.study_mode_part_time);
            case PART_TIME_PUW:
                return getString(R.string.study_mode_online);
            default:
                return mode.name();
        }
    }

    private void selectExclusiveCard(MaterialCardView selectedCard) {
        if (!isAdded() || !(selectedCard.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) selectedCard.getParent();
        if (selectedCard.isChecked()) {
            return;
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) child;
                if (card != selectedCard && card.isChecked()) {
                    AnimationHelper.animateCardSelection(requireContext(), card, false);
                }
            }
        }
        AnimationHelper.animateCardSelection(requireContext(), selectedCard, true);
    }

    private void bindThemeOnClick(MaterialCardView card, AppTheme theme) {
        card.setOnClickListener(v -> {
            if (!isAdded() || getActivity() == null) return;
            Context context = requireContext();
            AppTheme currentTheme = PreferencesManager.getThemePref(context);
            if (card.isChecked() && currentTheme == theme) {
                return;
            }

            Logger.i(TAG + ".bindThemeOnClick", "User selected theme: " + theme);
            boolean isOldNight = currentNightMode;
            boolean isNewNight = UIHelper.isNightTheme(context, theme);

            PreferencesManager.setThemePref(context, theme);

            if (isOldNight != isNewNight) {
                currentNightMode = isNewNight;
                themeAnimationInProgress = true;
                Activity activity = requireActivity();
                SettingsHost host = getHostCallback();
                if (host != null) {
                    host.onThemeAnimationStarted(isNewNight);
                }
                View animationRoot = (host != null && host.getThemeAnimationRoot() != null)
                        ? host.getThemeAnimationRoot()
                        : settingsRoot;

                Context oldContext = UIHelper.getThemedContext(activity, isOldNight);
                Context newContext = UIHelper.getThemedContext(activity, isNewNight);
                AnimationHelper.animateThemeChange(activity, oldContext, newContext, animationRoot, card, () -> {
                    themeAnimationInProgress = false;
                    if (!isAdded() || getActivity() == null) return;
                    SettingsHost callback = getHostCallback();
                    if (callback != null) {
                        callback.onThemeChangedCompleted(isNewNight);
                    } else {
                        UIHelper.setSelectedTheme(getActivity());
                        UIHelper.applyNightModeWithoutRecreate(getActivity(), isNewNight);
                    }
                    if (buttonBack != null && buttonBack.getVisibility() == View.VISIBLE) {
                        buttonBack.setBackgroundResource(R.drawable.bg_icon_btn);
                    }
                    refreshUpdateUI();
                });
            } else {
                selectExclusiveCard(card);
                UIHelper.setSelectedTheme(requireActivity());
            }
        });
    }

    private void bindLanguageOnClick(MaterialCardView card, Language language) {
        card.setOnClickListener(v -> {
            if (!isAdded()) return;
            Context context = requireContext();
            Language currentLanguage = PreferencesManager.getLanguagePref(context);
            if (card.isChecked() && currentLanguage == language) {
                return;
            }

            Logger.i(TAG + ".bindLanguageOnClick", "User selected language: " + language);
            selectExclusiveCard(card);
            PreferencesManager.setLanguagePref(context, language);

            card.postDelayed(() -> {
                if (!isAdded() || getActivity() == null) return;
                SettingsHost host = getHostCallback();
                if (host != null) {
                    host.onLanguageChanged();
                } else {
                    UIHelper.setSelectedLanguage(getActivity());
                }
            }, 150);
        });
    }

    private void handleUpdateClick() {
        if (!isAdded() || getActivity() == null) return;
        Context context = requireContext();
        Logger.i(TAG, "User tapped check updates row in Settings");

        boolean hasPending = UpdateManager.hasPendingUpdate(context);
        boolean isOffline = !NetworkUtils.isNetworkAvailable(context);
        if (hasPending && (isOffline || UpdateManager.isRecentCheckWithinCooldown(context))) {
            String pendingVersion = UpdateManager.getSavedLatestVersion(context);
            String pendingUrl = UpdateManager.getSavedReleaseUrl(context);
            String pendingApk = UpdateManager.getSavedApkUrl(context);
            Logger.i(TAG, "Opening cached update dialog immediately (version=" + pendingVersion + ", offline=" + isOffline + ")");
            UpdateManager.showUpdateDialog(requireActivity(), pendingVersion, pendingUrl, pendingApk);
            return;
        }

        if (!hasPending && UpdateManager.isCooldownActive()) {
            int remaining = UpdateManager.getRemainingCooldownSeconds();
            Logger.w(TAG, "Manual update check blocked by cooldown (" + remaining + "s remaining)");
            Toast.makeText(context, getString(R.string.update_cooldown_toast, remaining), Toast.LENGTH_SHORT).show();
            return;
        }

        if (rowCheckUpdate != null) {
            rowCheckUpdate.setEnabled(false);
        }
        Logger.i(TAG, "Initiating manual update check from Settings UI");
        Toast.makeText(context, getString(R.string.update_checking_toast), Toast.LENGTH_SHORT).show();

        UpdateManager.checkManualUpdate(context, new UpdateManager.UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String releaseUrl) {
                Logger.i(TAG, "Manual update check -> onUpdateAvailable: " + newVersion);
                if (!isAdded() || getActivity() == null) return;
                if (rowCheckUpdate != null) rowCheckUpdate.setEnabled(true);
                refreshUpdateUI();
                SettingsHost host = getHostCallback();
                if (host != null) host.onUpdateStatusChanged();
                UpdateManager.showUpdateDialog(requireActivity(), newVersion, releaseUrl);
            }

            @Override
            public void onUpToDate(String currentVersion) {
                Logger.i(TAG, "Manual update check -> onUpToDate: " + currentVersion);
                if (!isAdded() || getActivity() == null) return;
                if (rowCheckUpdate != null) rowCheckUpdate.setEnabled(true);
                refreshUpdateUI();
                SettingsHost host = getHostCallback();
                if (host != null) host.onUpdateStatusChanged();
                Toast.makeText(requireContext(), getString(R.string.update_latest_installed_toast, currentVersion), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                Logger.w(TAG, "Manual update check -> onError: " + errorMessage);
                if (!isAdded() || getActivity() == null) return;
                if (rowCheckUpdate != null) rowCheckUpdate.setEnabled(true);
                refreshUpdateUI();
                SettingsHost host = getHostCallback();
                if (host != null) host.onUpdateStatusChanged();
                if (UpdateManager.hasPendingUpdate(requireContext())) {
                    String pendingVer = UpdateManager.getSavedLatestVersion(requireContext());
                    String pendingUrl = UpdateManager.getSavedReleaseUrl(requireContext());
                    UpdateManager.showUpdateDialog(requireActivity(), pendingVer, pendingUrl);
                } else {
                    Toast.makeText(requireContext(), getString(R.string.update_check_error_toast), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void handleClearCacheClick() {
        if (!isAdded()) return;
        Context context = requireContext();
        Context appContext = context.getApplicationContext();
        Logger.i(TAG, "User tapped Clear cache and refresh row in Settings");

        if (rowClearCache != null) {
            rowClearCache.setEnabled(false);
        }

        ScheduleRepository repo = ScheduleRepository.getInstance(appContext);
        repo.invalidateCache();
        TeacherEmailService.getInstance(appContext).clearCache();
        StudyPlanScraper.clearUrlCache(appContext);

        if (NetworkUtils.isNetworkAvailable(appContext) && repo.isConfigurationComplete()) {
            Toast.makeText(context, R.string.settings_cache_cleared_toast, Toast.LENGTH_SHORT).show();
            repo.getScheduleForDate(LocalDate.now(), true, new ScheduleRepository.ScheduleCallback<DaySchedule>() {
                @Override
                public void onSuccess(DaySchedule result) {
                    AppExecutors.runOnMain(() -> {
                        if (isAdded() && rowClearCache != null) {
                            rowClearCache.setEnabled(true);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    Logger.w(TAG, "Schedule refresh after cache clear failed: " + Logger.formatException(e));
                    AppExecutors.runOnMain(() -> {
                        if (isAdded() && rowClearCache != null) {
                            rowClearCache.setEnabled(true);
                        }
                    });
                }
            });
        } else {
            if (WidgetUpdateManager.hasActiveWidgets(appContext)) {
                WidgetUpdateManager.updateAllWidgets(appContext);
            }
            Toast.makeText(context, R.string.settings_cache_cleared_offline_toast, Toast.LENGTH_SHORT).show();
            if (rowClearCache != null) {
                rowClearCache.setEnabled(true);
            }
        }
    }

    private void openTelegramContact() {
        if (!isAdded()) return;
        Logger.i(TAG, "User tapped Telegram button in Settings");
        try {
            Intent tgIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=VintroKato"));
            startActivity(tgIntent);
        } catch (Exception e) {
            Logger.w(TAG, "Telegram app not available, falling back to web link: " + Logger.formatException(e));
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/VintroKato"));
                startActivity(webIntent);
            } catch (Exception ex) {
                Logger.e(TAG, "Failed to open Telegram web contact link: " + Logger.formatException(ex), ex);
            }
        }
    }

    public void refreshUpdateUI() {
        if (!isAdded() || tvAppVersion == null) return;
        Context context = requireContext();
        boolean hasUpdate = UpdateManager.hasPendingUpdate(context);
        if (hasUpdate) {
            String latest = UpdateManager.getSavedLatestVersion(context);
            String label = formatVersionTag(latest != null && !latest.trim().isEmpty() ? latest : BuildConfig.VERSION_NAME);
            tvAppVersion.setText(label);
            tvAppVersion.setTextColor(UIHelper.getThemeColor(context, R.attr.update_alert_tint));
            tvAppVersion.setTag(AnimationHelper.ROLE_SKIP);
            if (viewUpdateDot != null) {
                viewUpdateDot.setBackgroundResource(R.drawable.bg_update_dot);
                viewUpdateDot.setVisibility(View.VISIBLE);
            }
        } else {
            tvAppVersion.setText(formatVersionTag(BuildConfig.VERSION_NAME));
            tvAppVersion.setTextColor(UIHelper.getThemeColor(context, R.attr.text_muted));
            tvAppVersion.setTag(AnimationHelper.ROLE_TEXT_MUTED);
            if (viewUpdateDot != null) {
                viewUpdateDot.setVisibility(View.GONE);
            }
        }
    }

    private String formatVersionTag(String version) {
        if (version == null) return "";
        String clean = version.trim();
        if (clean.isEmpty()) return "";
        if (clean.startsWith("v") || clean.startsWith("V")) {
            return clean;
        }
        return "v" + clean;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            currentNightMode = UIHelper.isNightMode(requireContext());
            refreshUpdateUI();
            if (!isPreOnboarding && PreferencesManager.isOnboardingCompleted(requireContext())) {
                populateProfileSummary();
                refreshVisibleWeekdaysSelection();
                refreshNotificationUI(false);
            }
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            if (editNotifCustom != null && editNotifCustom.hasFocus()) {
                editNotifCustom.clearFocus();
                hideKeyboard(editNotifCustom);
            }
        } else if (isResumed()) {
            currentNightMode = UIHelper.isNightMode(requireContext());
            refreshUpdateUI();
            if (!isPreOnboarding && PreferencesManager.isOnboardingCompleted(requireContext())) {
                populateProfileSummary();
                refreshVisibleWeekdaysSelection();
                refreshNotificationUI(false);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (editNotifCustom != null && editNotifCustom.hasFocus()) {
            editNotifCustom.clearFocus();
            hideKeyboard(editNotifCustom);
        }
        if (themeAnimationInProgress && getActivity() != null) {
            themeAnimationInProgress = false;
            AnimationHelper.cancelActiveThemeAnimators();
            SettingsHost host = getHostCallback();
            if (host != null) {
                host.onThemeChangedCompleted(currentNightMode);
            } else {
                UIHelper.setSelectedTheme(getActivity());
                UIHelper.applyNightModeWithoutRecreate(getActivity(), currentNightMode);
            }
        }
    }
}
