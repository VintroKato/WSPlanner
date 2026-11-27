package com.vintro.wsplanner.ui.activities;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.services.UpdateManager;
import com.vintro.wsplanner.ui.fragments.AllCoursesFragment;
import com.vintro.wsplanner.ui.fragments.ScheduleFragment;
import com.vintro.wsplanner.ui.fragments.SettingsFragment;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;

public class MainActivity extends AppCompatActivity implements SettingsFragment.SettingsHost {
    private static final String TAG = "MainActivity";
    public static final String EXTRA_TARGET_TAB = "extra_target_tab";

    private static final String STATE_ACTIVE_TAB = "state_active_tab";
    private static final String TAG_SCHEDULE = "tab_schedule";
    private static final String TAG_COURSES = "tab_courses";
    private static final String TAG_SETTINGS = "tab_settings";
    private static final long BOTTOM_NAV_ANIM_DURATION_MS = 260L;

    private View mainRoot;
    private View fragmentContainer;
    private View bottomNavDivider;
    private View bottomNavigationBar;
    private View bottomNavActiveIndicator;
    private View bottomNavTabsRow;

    private View tabCourses;
    private ImageView iconCourses;
    private TextView labelCourses;

    private View tabSchedule;
    private ImageView iconSchedule;
    private TextView labelSchedule;

    private View tabSettings;
    private ImageView iconSettings;
    private TextView labelSettings;
    private View badgeSettingsDot;

    private ScheduleFragment scheduleFragment;
    private AllCoursesFragment allCoursesFragment;
    private SettingsFragment settingsFragment;
    private Fragment activeFragment;

    private int activeTabId = R.id.nav_schedule;
    private boolean currentNightMode;
    private boolean isThemeAnimationRunning = false;
    private boolean pendingRecreateHiddenFragments = false;
    private boolean isIndicatorPositioned = false;
    private ValueAnimator animTabCourses;
    private ValueAnimator animTabSchedule;
    private ValueAnimator animTabSettings;
    private final FastOutSlowInInterpolator bottomNavInterpolator = new FastOutSlowInInterpolator();

    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d(TAG + ".onCreate", "MainActivity created");

        ScheduleRepository scheduleRepository = ScheduleRepository.getInstance(this);

        // check keystore availability
        if (!PreferencesManager.checkKeystoreHealth(this)) {
            Logger.e(TAG + ".onCreate", "Keystore health check failed");
            showKeystoreErrorDialog();
            return;
        }

        // check onboarding status
        String login = PreferencesManager.getGlobalLoginPref(this);
        String major = PreferencesManager.getGlobalMajorPref(this);
        String stage = PreferencesManager.getOnboardingStage(this);
        boolean isSpecialtyDone = PreferencesManager.isGlobalSpecialtyConfigured(this);

        Logger.d(TAG + ".onCreate", "Onboarding state: stage=" + stage + ", login=" + (login != null) + ", major=" + major + ", specialtyConfigured=" + isSpecialtyDone);

        if (login == null || PreferencesManager.ONBOARDING_STAGE_LOGIN.equals(stage)) {
            Logger.d(TAG + ".onCreate", "Redirecting to LoginActivity");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (major == null || PreferencesManager.ONBOARDING_STAGE_COURSE.equals(stage)
                || PreferencesManager.getGlobalDegreeLevelPref(this) == null
                || PreferencesManager.getGlobalStudyModePref(this) == null) {
            Logger.d(TAG + ".onCreate", "Redirecting to SetupCourseActivity");
            startActivity(new Intent(this, SetupCourseActivity.class));
            finish();
            return;
        }

        if (!isSpecialtyDone || PreferencesManager.ONBOARDING_STAGE_SPECIALTY.equals(stage)) {
            if (PreferencesManager.getGlobalDegreeLevelPref(this) == DegreeLevel.ZERO_YEAR || PreferencesManager.getGlobalYearPref(this) == 1) {
                Logger.d(TAG + ".onCreate", "Year 1 or Rok Zerowy student skipping specialty setup, advancing to SetupAdditionalActivity");
                PreferencesManager.setGlobalSpecialtyPref(this, null);
                PreferencesManager.setGlobalSpecialtyConfigured(this, true);
                PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_ADDITIONAL);
                startActivity(new Intent(this, SetupAdditionalActivity.class));
            } else {
                Logger.d(TAG + ".onCreate", "Redirecting to SetupSpecialtyActivity");
                startActivity(new Intent(this, SetupSpecialtyActivity.class));
            }
            finish();
            return;
        }

        if (PreferencesManager.ONBOARDING_STAGE_ADDITIONAL.equals(stage)) {
            Logger.d(TAG + ".onCreate", "Redirecting to SetupAdditionalActivity");
            startActivity(new Intent(this, SetupAdditionalActivity.class));
            finish();
            return;
        }

        if (!PreferencesManager.isOnboardingCompleted(this) || !scheduleRepository.isConfigurationComplete()) {
            Logger.d(TAG + ".onCreate", "Onboarding incomplete, redirecting to LoginActivity");
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        currentNightMode = UIHelper.isNightMode(this);
        UIHelper.setupSystemBars(this, currentNightMode);
        setContentView(R.layout.activity_main);

        mainRoot = findViewById(R.id.main_root);
        fragmentContainer = findViewById(R.id.fragment_container);
        bottomNavDivider = findViewById(R.id.bottom_nav_divider);
        bottomNavigationBar = findViewById(R.id.bottom_navigation);
        bottomNavActiveIndicator = findViewById(R.id.bottom_nav_active_indicator);
        bottomNavTabsRow = findViewById(R.id.bottom_nav_tabs_row);

        tabCourses = findViewById(R.id.nav_courses);
        iconCourses = findViewById(R.id.nav_courses_icon);
        labelCourses = findViewById(R.id.nav_courses_label);

        tabSchedule = findViewById(R.id.nav_schedule);
        iconSchedule = findViewById(R.id.nav_schedule_icon);
        labelSchedule = findViewById(R.id.nav_schedule_label);

        tabSettings = findViewById(R.id.nav_settings);
        iconSettings = findViewById(R.id.nav_settings_icon);
        labelSettings = findViewById(R.id.nav_settings_label);
        badgeSettingsDot = findViewById(R.id.nav_settings_update_dot);

        setupEdgeToEdgeInsets();
        setupBackNavigation();
        restoreOrInitFragments(savedInstanceState);
        setupBottomNavigation();
        updateSettingsUpdateBadge();
        checkAppUpdates();
    }

    private void setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        final float density = getResources().getDisplayMetrics().density;
        final int basePadH = Math.round(12 * density);
        final int basePadV = Math.round(6 * density);

        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(basePadH + bars.left, basePadV, basePadH + bars.right, basePadV + bars.bottom);
            return insets;
        });
    }

    private void setupBackNavigation() {
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (activeTabId != R.id.nav_schedule) {
                    Logger.d(TAG, "System back pressed on tab " + activeTabId + ", switching to schedule tab");
                    selectTab(R.id.nav_schedule, true);
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void restoreOrInitFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment restoredSchedule = fm.findFragmentByTag(TAG_SCHEDULE);
        Fragment restoredCourses = fm.findFragmentByTag(TAG_COURSES);
        Fragment restoredSettings = fm.findFragmentByTag(TAG_SETTINGS);

        if (restoredSchedule instanceof ScheduleFragment) {
            scheduleFragment = (ScheduleFragment) restoredSchedule;
        }
        if (restoredCourses instanceof AllCoursesFragment) {
            allCoursesFragment = (AllCoursesFragment) restoredCourses;
        }
        if (restoredSettings instanceof SettingsFragment) {
            settingsFragment = (SettingsFragment) restoredSettings;
        }

        if (savedInstanceState != null) {
            activeTabId = savedInstanceState.getInt(STATE_ACTIVE_TAB, R.id.nav_schedule);
        } else if (getIntent() != null && getIntent().hasExtra(EXTRA_TARGET_TAB)) {
            activeTabId = getIntent().getIntExtra(EXTRA_TARGET_TAB, R.id.nav_schedule);
        } else {
            activeTabId = R.id.nav_schedule;
        }

        switchToTab(activeTabId, false);
    }

    private void setupBottomNavigation() {
        applyBottomNavTabStates(false);

        if (bottomNavTabsRow != null) {
            bottomNavTabsRow.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                boolean sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop);
                if (!isIndicatorPositioned || sizeChanged) {
                    positionBottomNavIndicator(activeTabId, false);
                }
            });
        }

        tabCourses.setOnClickListener(v -> onBottomTabClicked(R.id.nav_courses));
        tabSchedule.setOnClickListener(v -> onBottomTabClicked(R.id.nav_schedule));
        tabSettings.setOnClickListener(v -> onBottomTabClicked(R.id.nav_settings));
    }

    private void onBottomTabClicked(int tabId) {
        if (tabId == activeTabId) {
            if (tabId == R.id.nav_schedule && scheduleFragment != null) {
                scheduleFragment.onTabReselected();
            } else if (tabId == R.id.nav_courses && allCoursesFragment != null) {
                allCoursesFragment.onTabReselected();
            }
            return;
        }
        selectTab(tabId, true);
    }

    private void selectTab(int tabId, boolean animate) {
        int previousTabId = activeTabId;
        switchToTab(tabId, animate);
        if (animate && previousTabId != activeTabId) {
            animateBottomNavTransition(previousTabId, activeTabId);
        } else {
            applyBottomNavTabStates(false);
        }
    }

    private void applyBottomNavTabStates(boolean animate) {
        cancelBottomNavAnimators();
        int activeBg = UIHelper.getThemeColor(this, R.attr.bottom_nav_indicator);
        int activeColor = UIHelper.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary);
        int inactiveColor = UIHelper.getThemeColor(this, R.attr.text_muted);

        applyBottomNavIndicatorColor(activeBg);
        applySingleTabState(iconCourses, labelCourses, activeTabId == R.id.nav_courses, activeColor, inactiveColor);
        applySingleTabState(iconSchedule, labelSchedule, activeTabId == R.id.nav_schedule, activeColor, inactiveColor);
        applySingleTabState(iconSettings, labelSettings, activeTabId == R.id.nav_settings, activeColor, inactiveColor);
        positionBottomNavIndicator(activeTabId, animate);
    }

    private void applyBottomNavIndicatorColor(int activeBg) {
        if (bottomNavActiveIndicator == null) return;
        Drawable bg = bottomNavActiveIndicator.getBackground();
        if (bg instanceof GradientDrawable) {
            GradientDrawable mutable = (GradientDrawable) bg.mutate();
            mutable.setColor(activeBg);
            bottomNavActiveIndicator.setBackground(mutable);
        }
        bottomNavActiveIndicator.setTag(AnimationHelper.ROLE_BOTTOM_NAV_PILL_ACTIVE);
    }

    private void positionBottomNavIndicator(int tabId, boolean animate) {
        View targetTab = getTabView(tabId);
        if (bottomNavActiveIndicator == null || targetTab == null) return;
        int targetWidth = targetTab.getWidth();
        int targetHeight = targetTab.getHeight();
        if (targetWidth <= 0 || targetHeight <= 0) {
            return;
        }

        ViewGroup.LayoutParams lp = bottomNavActiveIndicator.getLayoutParams();
        if (lp.width != targetWidth || lp.height != targetHeight) {
            lp.width = targetWidth;
            lp.height = targetHeight;
            bottomNavActiveIndicator.setLayoutParams(lp);
        }

        float targetX = targetTab.getLeft();
        float targetY = targetTab.getTop();
        bottomNavActiveIndicator.animate().cancel();

        if (!animate || !isIndicatorPositioned) {
            bottomNavActiveIndicator.setTranslationX(targetX);
            bottomNavActiveIndicator.setTranslationY(targetY);
            isIndicatorPositioned = true;
            return;
        }

        bottomNavActiveIndicator.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(BOTTOM_NAV_ANIM_DURATION_MS)
                .setInterpolator(bottomNavInterpolator)
                .start();
    }

    private void cancelBottomNavAnimators() {
        if (bottomNavActiveIndicator != null) {
            bottomNavActiveIndicator.animate().cancel();
        }
        if (iconCourses != null) iconCourses.animate().cancel();
        if (iconSchedule != null) iconSchedule.animate().cancel();
        if (iconSettings != null) iconSettings.animate().cancel();
        if (animTabCourses != null) {
            animTabCourses.cancel();
            animTabCourses = null;
        }
        if (animTabSchedule != null) {
            animTabSchedule.cancel();
            animTabSchedule = null;
        }
        if (animTabSettings != null) {
            animTabSettings.cancel();
            animTabSettings = null;
        }
    }

    private void cancelTabAnimator(int tabId) {
        if (tabId == R.id.nav_courses && animTabCourses != null) {
            animTabCourses.cancel();
            animTabCourses = null;
        } else if (tabId == R.id.nav_settings && animTabSettings != null) {
            animTabSettings.cancel();
            animTabSettings = null;
        } else if (tabId == R.id.nav_schedule && animTabSchedule != null) {
            animTabSchedule.cancel();
            animTabSchedule = null;
        }
    }

    private void storeTabAnimator(int tabId, ValueAnimator animator) {
        if (tabId == R.id.nav_courses) {
            animTabCourses = animator;
        } else if (tabId == R.id.nav_settings) {
            animTabSettings = animator;
        } else {
            animTabSchedule = animator;
        }
    }

    private void applySingleTabState(ImageView icon, TextView label, boolean selected,
                                     int activeColor, int inactiveColor) {
        if (icon == null || label == null) return;
        int tint = selected ? activeColor : inactiveColor;
        String role = selected ? AnimationHelper.ROLE_PRIMARY : AnimationHelper.ROLE_TEXT_MUTED;
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setTag(role);
        icon.setScaleX(1f);
        icon.setScaleY(1f);
        label.setTextColor(tint);
        label.setTag(role);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void animateBottomNavTransition(int oldTabId, int newTabId) {
        int activeColor = UIHelper.getThemeColor(this, androidx.appcompat.R.attr.colorPrimary);
        int inactiveColor = UIHelper.getThemeColor(this, R.attr.text_muted);

        ImageView oldIcon = getTabIcon(oldTabId);
        TextView oldLabel = getTabLabel(oldTabId);

        ImageView newIcon = getTabIcon(newTabId);
        TextView newLabel = getTabLabel(newTabId);

        if (oldIcon != null && oldLabel != null) {
            cancelTabAnimator(oldTabId);
            oldIcon.setTag(AnimationHelper.ROLE_TEXT_MUTED);
            oldLabel.setTag(AnimationHelper.ROLE_TEXT_MUTED);
            oldLabel.setTypeface(null, Typeface.NORMAL);
            int startTint = oldLabel.getCurrentTextColor();
            ValueAnimator outAnim = animateSingleTabForeground(oldIcon, oldLabel, startTint, inactiveColor);
            storeTabAnimator(oldTabId, outAnim);
        }

        if (newIcon != null && newLabel != null) {
            cancelTabAnimator(newTabId);
            newIcon.setTag(AnimationHelper.ROLE_PRIMARY);
            newLabel.setTag(AnimationHelper.ROLE_PRIMARY);
            newLabel.setTypeface(null, Typeface.BOLD);
            int startTint = newLabel.getCurrentTextColor();
            ValueAnimator inAnim = animateSingleTabForeground(newIcon, newLabel, startTint, activeColor);
            storeTabAnimator(newTabId, inAnim);

            newIcon.animate().cancel();
            newIcon.setScaleX(0.88f);
            newIcon.setScaleY(0.88f);
            newIcon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(BOTTOM_NAV_ANIM_DURATION_MS)
                    .setInterpolator(bottomNavInterpolator)
                    .start();
        }

        positionBottomNavIndicator(newTabId, true);
    }

    private ValueAnimator animateSingleTabForeground(ImageView icon, TextView label, int startTint, int endTint) {
        ArgbEvaluator evaluator = new ArgbEvaluator();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(BOTTOM_NAV_ANIM_DURATION_MS);
        animator.setInterpolator(bottomNavInterpolator);
        animator.addUpdateListener(anim -> {
            float fraction = (float) anim.getAnimatedValue();
            int fgColor = (int) evaluator.evaluate(fraction, startTint, endTint);
            icon.setImageTintList(ColorStateList.valueOf(fgColor));
            label.setTextColor(fgColor);
        });
        animator.start();
        return animator;
    }

    private View getTabView(int tabId) {
        if (tabId == R.id.nav_courses) return tabCourses;
        if (tabId == R.id.nav_settings) return tabSettings;
        return tabSchedule;
    }

    private ImageView getTabIcon(int tabId) {
        if (tabId == R.id.nav_courses) return iconCourses;
        if (tabId == R.id.nav_settings) return iconSettings;
        return iconSchedule;
    }

    private TextView getTabLabel(int tabId) {
        if (tabId == R.id.nav_courses) return labelCourses;
        if (tabId == R.id.nav_settings) return labelSettings;
        return labelSchedule;
    }

    private void switchToTab(int tabId, boolean animate) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) {
            return;
        }

        Fragment target = getOrCreateFragmentForTab(tabId);
        String targetTag = getTagForTab(tabId);
        FragmentTransaction tx = fm.beginTransaction();

        if (animate) {
            tx.setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out);
        }

        // hide all other added fragments to keep only one visible
        if (scheduleFragment != null && scheduleFragment != target && scheduleFragment.isAdded()) {
            tx.hide(scheduleFragment);
        }
        if (allCoursesFragment != null && allCoursesFragment != target && allCoursesFragment.isAdded()) {
            tx.hide(allCoursesFragment);
        }
        if (settingsFragment != null && settingsFragment != target && settingsFragment.isAdded()) {
            tx.hide(settingsFragment);
        }

        if (!target.isAdded()) {
            tx.add(R.id.fragment_container, target, targetTag);
        } else {
            tx.show(target);
        }

        tx.commit();
        activeFragment = target;
        activeTabId = tabId;

        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(activeTabId != R.id.nav_schedule);
        }
        Logger.d(TAG + ".switchToTab", "Switched active tab to " + targetTag);
    }

    private Fragment getOrCreateFragmentForTab(int tabId) {
        if (tabId == R.id.nav_courses) {
            if (allCoursesFragment == null) {
                allCoursesFragment = AllCoursesFragment.newInstance(null);
            }
            return allCoursesFragment;
        } else if (tabId == R.id.nav_settings) {
            if (settingsFragment == null) {
                settingsFragment = SettingsFragment.newInstance(false);
            }
            return settingsFragment;
        } else {
            if (scheduleFragment == null) {
                scheduleFragment = ScheduleFragment.newInstance(null);
            }
            return scheduleFragment;
        }
    }

    private String getTagForTab(int tabId) {
        if (tabId == R.id.nav_courses) return TAG_COURSES;
        if (tabId == R.id.nav_settings) return TAG_SETTINGS;
        return TAG_SCHEDULE;
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int requestedTab = intent.getIntExtra(EXTRA_TARGET_TAB, R.id.nav_schedule);
        Logger.d(TAG + ".onNewIntent", "Received new intent targeting tab=" + requestedTab);
        if (requestedTab != activeTabId) {
            selectTab(requestedTab, true);
        } else if (requestedTab == R.id.nav_schedule && scheduleFragment != null) {
            scheduleFragment.onActivatedFromWidget();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_ACTIVE_TAB, activeTabId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;

        if (pendingRecreateHiddenFragments) {
            pendingRecreateHiddenFragments = false;
            recreateHiddenFragments();
        }

        updateSettingsUpdateBadge();
    }

    @Override
    public View getThemeAnimationRoot() {
        return mainRoot;
    }

    @Override
    public void onThemeAnimationStarted(boolean isNewNight) {
        cancelBottomNavAnimators();
        isThemeAnimationRunning = true;
        currentNightMode = isNewNight;
    }

    @Override
    public void onThemeChangedCompleted(boolean isNewNight) {
        currentNightMode = isNewNight;
        UIHelper.setSelectedTheme(this);
        UIHelper.applyNightModeWithoutRecreate(this, isNewNight);
        isThemeAnimationRunning = false;
        applyBottomNavThemeColors();
        updateSettingsUpdateBadge();
        recreateHiddenFragments();
    }

    private void applyBottomNavThemeColors() {
        if (mainRoot != null) {
            mainRoot.setBackgroundColor(UIHelper.getThemeColor(this, R.attr.app_bg));
        }
        if (bottomNavDivider != null) {
            bottomNavDivider.setBackgroundColor(UIHelper.getThemeColor(this, R.attr.divider_color));
        }
        if (bottomNavigationBar != null) {
            bottomNavigationBar.setBackgroundColor(UIHelper.getThemeColor(this, R.attr.bottom_nav_bg));
        }
        applyBottomNavTabStates(false);
    }

    private void recreateHiddenFragments() {
        if (isFinishing() || isDestroyed()) return;
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) {
            pendingRecreateHiddenFragments = true;
            return;
        }

        boolean hadSchedule = scheduleFragment != null && scheduleFragment != activeFragment && scheduleFragment.isAdded();
        boolean hadCourses = allCoursesFragment != null && allCoursesFragment != activeFragment && allCoursesFragment.isAdded();
        if (!hadSchedule && !hadCourses) {
            return;
        }

        LocalDate savedDate = hadSchedule ? scheduleFragment.getSelectedDate() : null;
        Bundle savedCoursesState = hadCourses ? allCoursesFragment.exportFilterState() : null;

        FragmentTransaction tx = fm.beginTransaction();
        if (hadSchedule) {
            tx.remove(scheduleFragment);
            scheduleFragment = ScheduleFragment.newInstance(savedDate);
            tx.add(R.id.fragment_container, scheduleFragment, TAG_SCHEDULE);
            tx.hide(scheduleFragment);
        }
        if (hadCourses) {
            tx.remove(allCoursesFragment);
            allCoursesFragment = AllCoursesFragment.newInstance(savedCoursesState);
            tx.add(R.id.fragment_container, allCoursesFragment, TAG_COURSES);
            tx.hide(allCoursesFragment);
        }
        tx.commitAllowingStateLoss();
        Logger.d(TAG + ".recreateHiddenFragments", "Silently recreated hidden fragments in updated theme (schedule=" + hadSchedule + ", courses=" + hadCourses + ")");
    }

    @Override
    public void onLanguageChanged() {
        Logger.i(TAG + ".onLanguageChanged", "Recreating MainActivity without animation to apply new locale on settings tab");
        UIHelper.setSelectedLanguage(this);
        if (isFinishing() || isDestroyed()) return;

        Intent restartIntent = new Intent(this, MainActivity.class);
        restartIntent.putExtra(EXTRA_TARGET_TAB, R.id.nav_settings);
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(restartIntent);
        finish();
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    @Override
    public void onUpdateStatusChanged() {
        updateSettingsUpdateBadge();
    }

    @Override
    public void onVisibleWeekdaysChanged() {
        if (scheduleFragment != null && scheduleFragment.isAdded()) {
            scheduleFragment.onVisibleWeekdaysChanged();
        }
    }

    private void updateSettingsUpdateBadge() {
        if (badgeSettingsDot == null) return;
        boolean hasUpdate = UpdateManager.hasPendingUpdate(this);
        if (hasUpdate) {
            badgeSettingsDot.setBackgroundResource(R.drawable.bg_update_dot);
            badgeSettingsDot.setVisibility(View.VISIBLE);
        } else {
            badgeSettingsDot.setVisibility(View.GONE);
        }
    }

    private void checkAppUpdates() {
        Logger.d(TAG, "checkAppUpdates() initiated");
        UpdateManager.checkAutoUpdate(this, new UpdateManager.UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String releaseUrl) {
                Logger.i(TAG, "Auto update check: update available (" + newVersion + ")");
                runOnUiThread(() -> {
                    updateSettingsUpdateBadge();
                    if (settingsFragment != null && settingsFragment.isAdded()) {
                        settingsFragment.refreshUpdateUI();
                    }
                });
            }

            @Override
            public void onUpToDate(String currentVersion) {
                Logger.d(TAG, "Auto update check: app is up to date (" + currentVersion + ")");
                runOnUiThread(() -> {
                    updateSettingsUpdateBadge();
                    if (settingsFragment != null && settingsFragment.isAdded()) {
                        settingsFragment.refreshUpdateUI();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Logger.w(TAG, "Auto update check error: " + errorMessage);
                runOnUiThread(MainActivity.this::updateSettingsUpdateBadge);
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isThemeAnimationRunning) {
            return;
        }
        boolean newNight = UIHelper.isNightMode(this);
        if (newNight != currentNightMode) {
            Logger.d(TAG + ".onConfigurationChanged", "System uiMode changed externally, syncing theme (isNight=" + newNight + ")");
            currentNightMode = newNight;
            UIHelper.applyNightModeWithoutRecreate(this, newNight);
            recreate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelBottomNavAnimators();
        AnimationHelper.cancelActiveThemeAnimators();
    }

    private void showKeystoreErrorDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Защищенное хранилище")
                .setMessage("Сбой защищенного хранилища ключей Android (Keystore). Для безопасности ваших данных необходимо заново выполнить вход в аккаунт.")
                .setCancelable(false)
                .setPositiveButton("Войти заново", (dialog, which) -> {
                    PreferencesManager.resetCorruptedStorage(this);
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .show();
    }
}
