package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.services.CourseSetupService;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class SetupCourseActivity extends AppCompatActivity {
    private View loadingLayout;
    private LinearLayout contentLayout;
    private MaterialCardView majorCard;
    private TextView majorText;
    private TextView levelLabel, modeLabel, yearLabel;
    private GridLayout levelLayout, modeLayout, yearLayout;
    private Button confirmButton;

    private ParsedCourse selectedCourse;
    private DegreeLevel selectedLevel;
    private StudyMode selectedMode;
    private int selectedYear = -1;

    private List<ParsedCourse> availableCourses = new ArrayList<>();
    private String loadedStudentFullName = null;
    private final CourseSetupService courseSetupService = CourseSetupService.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("SetupCourseActivity.onCreate", "SetupCourseActivity created");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_setup_course);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        loadingLayout = findViewById(R.id.loading_layout);
        contentLayout = findViewById(R.id.content_layout);
        majorCard = findViewById(R.id.major_card);
        majorText = findViewById(R.id.major_text);
        levelLabel = findViewById(R.id.level_label);
        modeLabel = findViewById(R.id.mode_label);
        yearLabel = findViewById(R.id.year_label);
        levelLayout = findViewById(R.id.level_layout);
        modeLayout = findViewById(R.id.mode_layout);
        yearLayout = findViewById(R.id.year_layout);
        confirmButton = findViewById(R.id.confirm_button);

        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.5f);

        confirmButton.setOnClickListener(v -> saveAndContinue());

        fetchCourses();
    }

    private void fetchCourses() {
        Logger.d("SetupCourseActivity.fetchCourses", "Fetching available courses from service");
        loadingLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
        findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
        TextView loadingLabel = findViewById(R.id.loading_label);
        loadingLabel.setText(R.string.onboarding_loading_courses);
        Button retryButton = findViewById(R.id.button_retry);
        if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }

        if (!NetworkUtils.isNetworkAvailable(this)) {
            Logger.w("SetupCourseActivity.fetchCourses", "Device is offline, cannot load courses from PUW");
            showCourseLoadError(getString(R.string.no_internet_onboarding_desc));
            return;
        }

        courseSetupService.loadCourseSetupData(this, new CourseSetupService.OnSetupDataLoadedCallback() {
            @Override
            public void onSuccess(List<ParsedCourse> courses, Integer englishGroup, String studentFullName, String seminarTeacher) {
                Logger.i("SetupCourseActivity.fetchCourses", "Loaded " + courses.size() + " courses. Full name: '" + studentFullName + "'");
                availableCourses = courses;
                loadedStudentFullName = studentFullName;
                runOnUiThread(() -> {
                    TransitionManager.beginDelayedTransition((android.view.ViewGroup) findViewById(R.id.main), new AutoTransition());
                    loadingLayout.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);
                    populateUI(englishGroup, seminarTeacher);
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e("SetupCourseActivity.fetchCourses", "Error loading courses: " + Logger.formatException(e));
                runOnUiThread(() -> {
                    showCourseLoadError(getString(R.string.no_internet_onboarding_desc));
                });
            }
        });
    }

    private void showCourseLoadError(String message) {
        Logger.w("SetupCourseActivity.showCourseLoadError", "Displaying course load error: " + message);
        findViewById(R.id.progress_bar).setVisibility(View.GONE);
        TextView loadingLabel = findViewById(R.id.loading_label);
        loadingLabel.setText(message);
        Button retryButton = findViewById(R.id.button_retry);
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
            retryButton.setOnClickListener(v -> fetchCourses());
        }
    }

    private void populateUI(Integer englishGroup, String seminarTeacher) {
        if (availableCourses.isEmpty()) {
            Toast.makeText(this, "No courses found", Toast.LENGTH_SHORT).show();
            return;
        }

        // get primary course
        selectedCourse = courseSetupService.determinePrimaryCourse(availableCourses);
        if (selectedCourse != null && selectedCourse.fieldOfStudy != null) {
            majorText.setText(selectedCourse.degreeLevel == DegreeLevel.ZERO_YEAR ? "ROK ZEROWY" : selectedCourse.fieldOfStudy);
            majorCard.setChecked(true);
        }

        // if Rok Zerowy, skip degree/mode/year configuration
        if (selectedCourse != null && selectedCourse.degreeLevel == DegreeLevel.ZERO_YEAR) {
            Logger.i("SetupCourseActivity.populateUI", "Rok Zerowy detected; auto-configuring DegreeLevel=ZERO_YEAR, Mode=FULL_TIME, Year=1");
            selectedLevel = DegreeLevel.ZERO_YEAR;
            selectedMode = StudyMode.FULL_TIME;
            selectedYear = 1;

            levelLabel.setVisibility(View.GONE);
            levelLayout.setVisibility(View.GONE);
            modeLabel.setVisibility(View.GONE);
            modeLayout.setVisibility(View.GONE);
            yearLabel.setVisibility(View.GONE);
            yearLayout.setVisibility(View.GONE);

            confirmButton.setEnabled(true);
            confirmButton.setAlpha(1.0f);
            return;
        }

        // load degree levels
        String fieldOfStudy = selectedCourse != null ? selectedCourse.fieldOfStudy : null;
        List<DegreeLevel> levels = courseSetupService.getAvailableDegreeLevels(availableCourses, fieldOfStudy);

        levelLayout.removeAllViews();
        levelLayout.setColumnCount(Math.max(1, levels.size()));
        for (DegreeLevel level : levels) {
            MaterialCardView card = createTwoLineCard(getDegreeLevelTitle(level), getDegreeLevelSubtitle(level));
            card.setOnClickListener(v -> {
                TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
                selectCard(levelLayout, card);
                selectedLevel = level;
                selectedCourse = courseSetupService.findCourse(availableCourses, fieldOfStudy, selectedLevel);
                updateModes();
            });
            levelLayout.addView(card);
        }

        selectedLevel = null;
        selectedMode = null;
        selectedYear = -1;
        modeLabel.setVisibility(View.GONE);
        modeLayout.setVisibility(View.GONE);
        yearLabel.setVisibility(View.GONE);
        yearLayout.setVisibility(View.GONE);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.5f);

        if (levels.size() == 1) {
            MaterialCardView singleCard = (MaterialCardView) levelLayout.getChildAt(0);
            singleCard.performClick();
        }
    }

    private void updateModes() {
        modeLayout.removeAllViews();
        yearLayout.removeAllViews();
        selectedMode = null;
        selectedYear = -1;
        yearLabel.setVisibility(View.GONE);
        yearLayout.setVisibility(View.GONE);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.5f);

        List<StudyMode> validModes = courseSetupService.getAvailableStudyModes(selectedCourse);
        if (validModes.isEmpty()) return;

        modeLayout.setColumnCount(Math.max(1, validModes.size()));
        for (StudyMode mode : validModes) {
            MaterialCardView card = createTwoLineCard(getStudyModeTitle(mode), getStudyModeSubtitle(mode));
            card.setOnClickListener(v -> {
                TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
                selectCard(modeLayout, card);
                selectedMode = mode;
                updateYears();
            });
            modeLayout.addView(card);
        }

        modeLabel.setVisibility(View.VISIBLE);
        modeLayout.setVisibility(View.VISIBLE);

        if (validModes.size() == 1) {
            MaterialCardView singleCard = (MaterialCardView) modeLayout.getChildAt(0);
            singleCard.performClick();
        }
    }

    private void updateYears() {
        yearLayout.removeAllViews();
        selectedYear = -1;
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.5f);

        List<Integer> years = courseSetupService.getAvailableYears(selectedCourse, selectedMode);
        if (years.isEmpty()) return;

        yearLayout.setColumnCount(Math.max(1, years.size()));
        for (Integer year : years) {
            MaterialCardView card = createCard(String.valueOf(year));
            card.setOnClickListener(v -> {
                TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
                selectCard(yearLayout, card);
                selectedYear = year;

                confirmButton.setEnabled(true);
                confirmButton.setAlpha(1.0f);
            });
            yearLayout.addView(card);
        }

        yearLabel.setVisibility(View.VISIBLE);
        yearLayout.setVisibility(View.VISIBLE);

        if (years.size() == 1) {
            MaterialCardView singleCard = (MaterialCardView) yearLayout.getChildAt(0);
            singleCard.performClick();
        }
    }

    private String getDegreeLevelTitle(DegreeLevel level) {
        if (level == null) return "";
        switch (level) {
            case BACHELORS:
                return getString(R.string.degree_level_bachelors_code);
            case MASTERS:
                return getString(R.string.degree_level_masters_code);
            case LONG_MASTERS:
                return getString(R.string.degree_level_long_masters_code);
            case ZERO_YEAR:
                return "ROK ZEROWY";
            default:
                return level.name();
        }
    }

    private String getDegreeLevelSubtitle(DegreeLevel level) {
        if (level == null) return "";
        switch (level) {
            case BACHELORS:
                return getString(R.string.degree_level_bachelors_sub);
            case MASTERS:
                return getString(R.string.degree_level_masters_sub);
            case LONG_MASTERS:
                return getString(R.string.degree_level_long_masters_sub);
            default:
                return "";
        }
    }

    private String getStudyModeTitle(StudyMode mode) {
        if (mode == null) return "";
        switch (mode) {
            case FULL_TIME:
                return getString(R.string.study_mode_st_code);
            case PART_TIME:
                return getString(R.string.study_mode_nst_code);
            case PART_TIME_PUW:
                return getString(R.string.study_mode_puw_code);
            default:
                return mode.getValue();
        }
    }

    private String getStudyModeSubtitle(StudyMode mode) {
        if (mode == null) return "";
        switch (mode) {
            case FULL_TIME:
                return getString(R.string.study_mode_full_time_sub);
            case PART_TIME:
                return getString(R.string.study_mode_part_time_sub);
            case PART_TIME_PUW:
                return getString(R.string.study_mode_online_sub);
            default:
                return "";
        }
    }

    private MaterialCardView createTwoLineCard(String title, String subtitle) {
        MaterialCardView card = new MaterialCardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (4 * density);
        params.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(params);

        card.setCheckable(true);
        card.setCheckedIcon(null);
        card.setCardElevation(0);
        card.setStrokeWidth((int) (2 * density));
        card.setStrokeColor(UIHelper.getThemeColor(this, R.attr.card_border));
        card.setCardBackgroundColor(UIHelper.getThemeColor(this, R.attr.card_bg));
        card.setRadius(16 * density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding((int) (6 * density), (int) (12 * density), (int) (6 * density), (int) (12 * density));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvTitle.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(null, Typeface.BOLD);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvSubtitle.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));
        tvSubtitle.setTextSize(12);
        tvSubtitle.setAlpha(0.7f);
        tvSubtitle.setPadding(0, (int) (2 * density), 0, 0);

        layout.addView(tvTitle);
        layout.addView(tvSubtitle);
        card.addView(layout);
        return card;
    }

    private MaterialCardView createCard(String text) {
        MaterialCardView card = new MaterialCardView(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (4 * density);
        params.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(params);

        card.setCheckable(true);
        card.setCheckedIcon(null);
        card.setCardElevation(0);
        card.setStrokeWidth((int) (2 * density));
        card.setStrokeColor(UIHelper.getThemeColor(this, R.attr.card_border));
        card.setCardBackgroundColor(UIHelper.getThemeColor(this, R.attr.card_bg));
        card.setRadius(16 * density);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setPadding(0, (int) (14 * density), 0, (int) (14 * density));
        tv.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));
        tv.setTextSize(18);
        tv.setTypeface(null, Typeface.BOLD);

        card.addView(tv);
        return card;
    }

    private void selectCard(GridLayout layout, MaterialCardView selectedCard) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            MaterialCardView card = (MaterialCardView) layout.getChildAt(i);
            if (card.isChecked()) {
                AnimationHelper.animateCardSelection(this, card, false);
            }
        }
        AnimationHelper.animateCardSelection(this, selectedCard, true);
    }

    private void saveAndContinue() {
        if (selectedCourse == null || selectedLevel == null || selectedMode == null || selectedYear == -1) {
            Toast.makeText(this, "Please select all options", Toast.LENGTH_SHORT).show();
            return;
        }

        PreferencesManager.saveGlobalPrefs(
                this,
                PreferencesManager.getGlobalLoginPref(this),
                PreferencesManager.getGlobalPasswordPref(this),
                selectedCourse.fieldOfStudy,
                selectedLevel,
                selectedMode,
                selectedYear,
                PreferencesManager.getGlobalSpecialtyPref(this),
                PreferencesManager.getGlobalEnglishGroupPref(this)
        );

        if (loadedStudentFullName != null && !loadedStudentFullName.trim().isEmpty()) {
            PreferencesManager.setGlobalStudentNamePref(this, loadedStudentFullName.trim());
            PreferencesManager.setDetectedStudentName(this, loadedStudentFullName.trim());
            String[] parts = loadedStudentFullName.trim().split("\\s+");
            if (parts.length > 0) {
                // assume last word is surname
                String surname = parts[parts.length - 1];
                PreferencesManager.setGlobalStudentSurnamePref(this, surname);
                PreferencesManager.setDetectedStudentSurname(this, surname);
            }
        }

        Logger.i("SetupCourseActivity.saveAndContinue", "Saved configuration: " + selectedCourse.fieldOfStudy + ", " + selectedLevel + ", " + selectedMode + ", year " + selectedYear);

        if (selectedYear == 1) {
            PreferencesManager.setGlobalSpecialtyPref(this, null);
            PreferencesManager.setGlobalSpecialtyConfigured(this, true);
            PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_ADDITIONAL);

            Intent intent = new Intent(this, SetupAdditionalActivity.class);
            startActivity(intent);
        } else {
            // move to specialty step
            PreferencesManager.setGlobalSpecialtyConfigured(this, false);
            PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_SPECIALTY);

            Intent intent = new Intent(this, SetupSpecialtyActivity.class);
            startActivity(intent);
        }
    }
}
