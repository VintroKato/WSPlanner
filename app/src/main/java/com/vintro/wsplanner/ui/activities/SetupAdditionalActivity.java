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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.services.CourseSetupService;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

public class SetupAdditionalActivity extends AppCompatActivity {
    private ImageButton backButton;
    private LinearLayout contentLayout;
    private SwitchMaterial englishSwitch;
    private EditText englishInput;
    private LinearLayout seminarContainer;
    private SwitchMaterial seminarSwitch;
    private EditText seminarInput;
    private EditText studentSurnameInput;
    private Button confirmButton;

    private final CourseSetupService courseSetupService = CourseSetupService.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_setup_additional);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        backButton = findViewById(R.id.back_button);
        contentLayout = findViewById(R.id.content_layout);
        englishSwitch = findViewById(R.id.english_switch);
        englishInput = findViewById(R.id.english_input);
        seminarContainer = findViewById(R.id.seminar_container);
        seminarSwitch = findViewById(R.id.seminar_switch);
        seminarInput = findViewById(R.id.seminar_input);
        studentSurnameInput = findViewById(R.id.student_surname_input);
        confirmButton = findViewById(R.id.confirm_button);

        backButton.setOnClickListener(v -> finish());

        englishSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
            englishInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        seminarSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
            seminarInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        confirmButton.setOnClickListener(v -> finishSetup());

        initData();
    }

    private void initData() {
        // pre-fill english group
        String savedEnglish = PreferencesManager.getGlobalEnglishGroupPref(this);
        Integer detectedEnglish = courseSetupService.getCachedEnglishGroup();
        String effectiveEnglish = (savedEnglish != null && !savedEnglish.trim().isEmpty())
                ? savedEnglish
                : (detectedEnglish != null ? String.valueOf(detectedEnglish) : null);

        if (effectiveEnglish != null && !effectiveEnglish.trim().isEmpty()) {
            englishSwitch.setChecked(true);
            englishInput.setText(effectiveEnglish);
            englishInput.setVisibility(View.VISIBLE);
        } else {
            englishSwitch.setChecked(false);
            englishInput.setVisibility(View.GONE);
        }

        // pre-fill diploma seminar for senior year
        int year = PreferencesManager.getGlobalYearPref(this);
        String savedSeminar = PreferencesManager.getGlobalSeminarTeacherPref(this);
        String detectedSeminar = courseSetupService.getCachedSeminarTeacher();
        String effectiveSeminar = (savedSeminar != null && !savedSeminar.trim().isEmpty())
                ? savedSeminar
                : detectedSeminar;

        boolean isSeniorYear = (year >= 3 || year <= 0);
        if (isSeniorYear || (effectiveSeminar != null && !effectiveSeminar.isEmpty())) {
            seminarContainer.setVisibility(View.VISIBLE);
            if (effectiveSeminar != null && !effectiveSeminar.trim().isEmpty()) {
                seminarSwitch.setChecked(true);
                seminarInput.setText(effectiveSeminar);
                seminarInput.setVisibility(View.VISIBLE);
            } else {
                seminarSwitch.setChecked(false);
                seminarInput.setVisibility(View.GONE);
            }
        } else {
            seminarContainer.setVisibility(View.GONE);
        }

        // pre-fill student surname
        String currentSurname = PreferencesManager.getGlobalStudentSurnamePref(this);
        if (currentSurname != null && !currentSurname.trim().isEmpty()) {
            studentSurnameInput.setText(currentSurname.trim());
        } else {
            String fullName = courseSetupService.getCachedStudentFullName();
            if (fullName != null && !fullName.trim().isEmpty()) {
                String[] parts = fullName.trim().split("\\s+");
                if (parts.length > 0) {
                    studentSurnameInput.setText(parts[parts.length - 1]);
                }
            }
        }
    }

    private void finishSetup() {
        String englishGroup = null;
        if (englishSwitch.isChecked()) {
            String val = englishInput.getText().toString().trim();
            if (!val.isEmpty()) {
                englishGroup = val;
            }
        }
        PreferencesManager.setGlobalEnglishGroupPref(this, englishGroup);

        String seminarTeacher = null;
        if (seminarContainer.getVisibility() == View.VISIBLE && seminarSwitch.isChecked()) {
            String val = seminarInput.getText().toString().trim();
            if (!val.isEmpty()) {
                seminarTeacher = val;
            }
        }
        PreferencesManager.setGlobalSeminarTeacherPref(this, seminarTeacher);

        String enteredSurname = studentSurnameInput.getText().toString().trim();
        PreferencesManager.setGlobalStudentSurnamePref(this, enteredSurname.isEmpty() ? null : enteredSurname);

        // save global preferences
        String login = PreferencesManager.getGlobalLoginPref(this);
        String password = PreferencesManager.getGlobalPasswordPref(this);
        String major = PreferencesManager.getGlobalMajorPref(this);
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(this);
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(this);
        int year = PreferencesManager.getGlobalYearPref(this);
        String specialty = PreferencesManager.getGlobalSpecialtyPref(this);

        PreferencesManager.saveGlobalPrefs(this, login, password, major, degreeLevel, studyMode, year, specialty, englishGroup);
        PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_COMPLETED);

        // clear cached schedule
        ScheduleRepository.getInstance(this).invalidateCache();

        Logger.d("SetupAdditionalActivity", "Onboarding setup completed. Navigating to MainActivity.");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
