package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.InputState;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.services.CourseSetupService;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.WidgetUpdateManager;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

public class StudentPanelActivity extends AppCompatActivity {
    private ImageButton backButton;
    private LinearLayout contentLayout;

    // Onboarding course info views
    private TextView tvMajorValue;
    private View courseDetailsDivider;
    private View layoutCourseMetrics;
    private TextView tvLevelValue;
    private TextView tvModeValue;
    private TextView tvYearValue;
    private View layoutSpecialtyRow;
    private TextView tvSpecialtyValue;

    // Action buttons
    private MaterialButton btnReOnboarding;
    private MaterialButton btnChangeCredentials;

    // Inline personal parameters views
    private View searchingContainer;
    private EditText studentSurnameInput;
    private View englishContainer;
    private SwitchMaterial englishSwitch;
    private EditText englishInput;
    private View seminarContainer;
    private SwitchMaterial seminarSwitch;
    private EditText seminarInput;
    private Button confirmButton;

    private Language currentLanguage;
    private AppTheme currentTheme;
    private final CourseSetupService courseSetupService = CourseSetupService.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("StudentPanelActivity.onCreate", "StudentPanelActivity created");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_student_panel);

        currentLanguage = PreferencesManager.getLanguagePref(this);
        currentTheme = PreferencesManager.getThemePref(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupListeners();
        populateCourseInfo();
        initPersonalParameters();
    }

    private void initViews() {
        backButton = findViewById(R.id.back_button);
        contentLayout = findViewById(R.id.content_layout);

        tvMajorValue = findViewById(R.id.tv_major_value);
        courseDetailsDivider = findViewById(R.id.course_details_divider);
        layoutCourseMetrics = findViewById(R.id.layout_course_metrics);
        tvLevelValue = findViewById(R.id.tv_level_value);
        tvModeValue = findViewById(R.id.tv_mode_value);
        tvYearValue = findViewById(R.id.tv_year_value);
        layoutSpecialtyRow = findViewById(R.id.layout_specialty_row);
        tvSpecialtyValue = findViewById(R.id.tv_specialty_value);

        btnReOnboarding = findViewById(R.id.btn_re_onboarding);
        btnChangeCredentials = findViewById(R.id.btn_change_credentials);

        searchingContainer = findViewById(R.id.searching_container);
        studentSurnameInput = findViewById(R.id.student_surname_input);
        englishContainer = findViewById(R.id.english_container);
        englishSwitch = findViewById(R.id.english_switch);
        englishInput = findViewById(R.id.english_input);
        seminarContainer = findViewById(R.id.seminar_container);
        seminarSwitch = findViewById(R.id.seminar_switch);
        seminarInput = findViewById(R.id.seminar_input);
        confirmButton = findViewById(R.id.confirm_button);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());

        btnReOnboarding.setOnClickListener(v -> {
            Logger.i("StudentPanelActivity", "User tapped Change course / Re-onboarding");
            Intent intent = new Intent(this, SetupCourseActivity.class);
            intent.putExtra(SetupCourseActivity.EXTRA_FROM_STUDENT_PANEL, true);
            startActivity(intent);
        });

        btnChangeCredentials.setOnClickListener(v -> {
            Logger.i("StudentPanelActivity", "User tapped Change login and password");
            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_FROM_STUDENT_PANEL, true);
            startActivity(intent);
        });

        englishSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
            englishInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        seminarSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
            seminarInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (v instanceof EditText) {
                AnimationHelper.animateInputState(this, (EditText) v, InputState.NORMAL, InputState.NORMAL);
            }
        };
        studentSurnameInput.setOnFocusChangeListener(focusListener);
        englishInput.setOnFocusChangeListener(focusListener);
        seminarInput.setOnFocusChangeListener(focusListener);

        confirmButton.setOnClickListener(v -> saveChanges());
    }

    private void populateCourseInfo() {
        String major = PreferencesManager.getGlobalMajorPref(this);
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(this);
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(this);
        int year = PreferencesManager.getGlobalYearPref(this);
        String specialty = PreferencesManager.getGlobalSpecialtyPref(this);

        boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR);

        if (isZeroYear) {
            tvMajorValue.setText("ROK ZEROWY");
            courseDetailsDivider.setVisibility(View.GONE);
            layoutCourseMetrics.setVisibility(View.GONE);
            layoutSpecialtyRow.setVisibility(View.GONE);
        } else {
            tvMajorValue.setText(major != null && !major.trim().isEmpty() ? major.trim() : "—");
            courseDetailsDivider.setVisibility(View.VISIBLE);
            layoutCourseMetrics.setVisibility(View.VISIBLE);

            tvLevelValue.setText(formatDegreeLevel(degreeLevel));
            tvModeValue.setText(formatStudyMode(studyMode));
            tvYearValue.setText(String.valueOf(year));

            if (specialty != null && !specialty.trim().isEmpty()) {
                layoutSpecialtyRow.setVisibility(View.VISIBLE);
                tvSpecialtyValue.setText(specialty.trim());
            } else {
                layoutSpecialtyRow.setVisibility(View.GONE);
            }
        }
    }

    private String formatDegreeLevel(DegreeLevel level) {
        if (level == null) return "—";
        switch (level) {
            case BACHELORS:
                return getString(R.string.degree_level_bachelors) + " (" + getString(R.string.degree_level_bachelors_sub) + ")";
            case MASTERS:
                return getString(R.string.degree_level_masters) + " (" + getString(R.string.degree_level_masters_sub) + ")";
            case LONG_MASTERS:
                return getString(R.string.degree_level_long_masters) + " (" + getString(R.string.degree_level_long_masters_sub) + ")";
            case ZERO_YEAR:
                return "ROK ZEROWY";
            default:
                return level.name();
        }
    }

    private String formatStudyMode(StudyMode mode) {
        if (mode == null) return "—";
        switch (mode) {
            case FULL_TIME:
                return getString(R.string.study_mode_full_time);
            case PART_TIME:
                return getString(R.string.study_mode_part_time);
            case PART_TIME_PUW:
                return getString(R.string.study_mode_online);
            default:
                return mode.getValue();
        }
    }

    private void initPersonalParameters() {
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(this);
        boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR);

        // Pre-fill student surname
        String currentSurname = PreferencesManager.getGlobalStudentSurnamePref(this);
        if (currentSurname == null || currentSurname.trim().isEmpty()) {
            currentSurname = PreferencesManager.getDetectedStudentSurname(this);
        }
        if (currentSurname != null && !currentSurname.trim().isEmpty()) {
            studentSurnameInput.setText(currentSurname.trim());
        } else {
            String fullName = PreferencesManager.getGlobalStudentNamePref(this);
            if (fullName == null || fullName.trim().isEmpty()) {
                fullName = PreferencesManager.getDetectedStudentName(this);
            }
            if (fullName != null && !fullName.trim().isEmpty()) {
                String[] parts = fullName.trim().split("\\s+");
                if (parts.length > 0) {
                    studentSurnameInput.setText(parts[parts.length - 1]);
                }
            }
        }

        if (isZeroYear) {
            englishContainer.setVisibility(View.GONE);
            seminarContainer.setVisibility(View.GONE);
            if (searchingContainer != null) {
                searchingContainer.setVisibility(View.GONE);
            }
            return;
        }

        englishContainer.setVisibility(View.VISIBLE);
        seminarContainer.setVisibility(View.VISIBLE);

        // Pre-fill English group
        String savedEnglish = PreferencesManager.getGlobalEnglishGroupPref(this);
        if (savedEnglish != null && !savedEnglish.trim().isEmpty()) {
            englishSwitch.setChecked(true);
            englishInput.setText(savedEnglish.trim());
            englishInput.setVisibility(View.VISIBLE);
        } else {
            englishSwitch.setChecked(false);
            englishInput.setText("");
            englishInput.setVisibility(View.GONE);
        }

        // Pre-fill Seminar teacher
        String savedSeminar = PreferencesManager.getGlobalSeminarTeacherPref(this);
        if (savedSeminar != null && !savedSeminar.trim().isEmpty()) {
            seminarSwitch.setChecked(true);
            seminarInput.setText(savedSeminar.trim());
            seminarInput.setVisibility(View.VISIBLE);
        } else {
            seminarSwitch.setChecked(false);
            seminarInput.setText("");
            seminarInput.setVisibility(View.GONE);
        }

        // If surname is missing and device is online, try background detection
        boolean missingSurname = studentSurnameInput.getText() == null || studentSurnameInput.getText().toString().trim().isEmpty();
        if (missingSurname && NetworkUtils.isNetworkAvailable(this)) {
            if (searchingContainer != null) {
                searchingContainer.setVisibility(View.VISIBLE);
            }
            courseSetupService.loadAdditionalSetupData(this, new CourseSetupService.OnAdditionalDataLoadedCallback() {
                @Override
                public void onSuccess(Integer engGroup, String fullName, String semTeacher) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (searchingContainer != null) {
                            TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
                            searchingContainer.setVisibility(View.GONE);
                        }
                        if (studentSurnameInput.getText() == null || studentSurnameInput.getText().toString().trim().isEmpty()) {
                            if (fullName != null && !fullName.trim().isEmpty()) {
                                String[] parts = fullName.trim().split("\\s+");
                                if (parts.length > 0) {
                                    studentSurnameInput.setText(parts[parts.length - 1]);
                                }
                            }
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (searchingContainer != null) {
                            searchingContainer.setVisibility(View.GONE);
                        }
                    });
                }
            });
        } else if (searchingContainer != null) {
            searchingContainer.setVisibility(View.GONE);
        }
    }

    private void saveChanges() {
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(this);
        boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR);

        String enteredSurname = studentSurnameInput.getText() != null
                ? studentSurnameInput.getText().toString().trim()
                : "";
        PreferencesManager.setGlobalStudentSurnamePref(this, enteredSurname.isEmpty() ? null : enteredSurname);

        String englishGroup = null;
        if (!isZeroYear && englishSwitch.isChecked() && englishInput.getText() != null) {
            String val = englishInput.getText().toString().trim();
            if (!val.isEmpty()) {
                englishGroup = val;
            }
        }
        PreferencesManager.setGlobalEnglishGroupPref(this, englishGroup);

        String seminarTeacher = null;
        if (!isZeroYear && seminarSwitch.isChecked() && seminarInput.getText() != null) {
            String val = seminarInput.getText().toString().trim();
            if (!val.isEmpty()) {
                seminarTeacher = val;
            }
        }
        PreferencesManager.setGlobalSeminarTeacherPref(this, seminarTeacher);

        Logger.i("StudentPanelActivity.saveChanges", "Saved student panel parameters: surname="
                + Logger.maskSurname(enteredSurname) + ", englishGroup=" + englishGroup + ", seminarTeacher=" + seminarTeacher);

        ScheduleRepository.getInstance(this).invalidateMemoryCache();
        WidgetUpdateManager.updateAllWidgets(this);

        Toast.makeText(this, R.string.student_panel_saved_toast, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Language lang = PreferencesManager.getLanguagePref(this);
        AppTheme theme = PreferencesManager.getThemePref(this);
        if (lang != currentLanguage || theme != currentTheme) {
            recreate();
            return;
        }
        populateCourseInfo();
    }
}
