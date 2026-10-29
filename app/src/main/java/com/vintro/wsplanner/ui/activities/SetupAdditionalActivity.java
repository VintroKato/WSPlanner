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
import com.vintro.wsplanner.utils.NetworkUtils;

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

    private View searchingContainer;
    private final CourseSetupService courseSetupService = CourseSetupService.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("SetupAdditionalActivity.onCreate", "SetupAdditionalActivity created");
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
        searchingContainer = findViewById(R.id.searching_container);
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
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(this);
        boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR);

        String effectiveEnglish = null;
        String effectiveSeminar = null;
        int year = PreferencesManager.getGlobalYearPref(this);
        final boolean isSeniorYear = !isZeroYear && (year >= 3 || year <= 0);

        if (isZeroYear) {
            englishSwitch.setVisibility(View.GONE);
            englishInput.setVisibility(View.GONE);
            seminarContainer.setVisibility(View.GONE);
        } else {
            // pre-fill english group
            String savedEnglish = PreferencesManager.getGlobalEnglishGroupPref(this);
            Integer detectedEnglish = courseSetupService.getCachedEnglishGroup();
            if (detectedEnglish == null) {
                detectedEnglish = PreferencesManager.getDetectedEnglishGroup(this);
            }
            effectiveEnglish = (savedEnglish != null && !savedEnglish.trim().isEmpty())
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
            String savedSeminar = PreferencesManager.getGlobalSeminarTeacherPref(this);
            String detectedSeminar = courseSetupService.getCachedSeminarTeacher();
            if (detectedSeminar == null) {
                detectedSeminar = PreferencesManager.getDetectedSeminarTeacher(this);
            }
            effectiveSeminar = (savedSeminar != null && !savedSeminar.trim().isEmpty())
                    ? savedSeminar
                    : detectedSeminar;

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
        }

        // pre-fill student surname
        String currentSurname = PreferencesManager.getGlobalStudentSurnamePref(this);
        if (currentSurname == null || currentSurname.trim().isEmpty()) {
            currentSurname = PreferencesManager.getDetectedStudentSurname(this);
        }
        if (currentSurname != null && !currentSurname.trim().isEmpty()) {
            studentSurnameInput.setText(currentSurname.trim());
        } else {
            String fullName = courseSetupService.getCachedStudentFullName();
            if (fullName == null) {
                fullName = PreferencesManager.getDetectedStudentName(this);
            }
            if (fullName != null && !fullName.trim().isEmpty()) {
                String[] parts = fullName.trim().split("\\s+");
                if (parts.length > 0) {
                    studentSurnameInput.setText(parts[parts.length - 1]);
                }
            }
        }

        // Check if we need to search PUW in the background for missing data
        boolean missingEnglish = (effectiveEnglish == null);
        boolean missingSeminar = isSeniorYear && (effectiveSeminar == null || effectiveSeminar.trim().isEmpty());
        boolean missingSurname = (studentSurnameInput.getText() == null || studentSurnameInput.getText().toString().trim().isEmpty());

        if (isZeroYear) {
            // For Rok Zerowy, English group and seminar teacher are not applicable
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
            return;
        }

        if ((missingEnglish || missingSeminar || missingSurname) && NetworkUtils.isNetworkAvailable(this)) {
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
                        if (engGroup != null && !englishSwitch.isChecked() && englishInput.getText().toString().trim().isEmpty()) {
                            englishSwitch.setChecked(true);
                            englishInput.setText(String.valueOf(engGroup));
                            englishInput.setVisibility(View.VISIBLE);
                        }
                        if (semTeacher != null && !semTeacher.trim().isEmpty() && isSeniorYear) {
                            seminarContainer.setVisibility(View.VISIBLE);
                            if (!seminarSwitch.isChecked() && seminarInput.getText().toString().trim().isEmpty()) {
                                seminarSwitch.setChecked(true);
                                seminarInput.setText(semTeacher.trim());
                                seminarInput.setVisibility(View.VISIBLE);
                            }
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
        } else {
            if (searchingContainer != null) {
                searchingContainer.setVisibility(View.GONE);
            }
        }
    }

    private void finishSetup() {
        DegreeLevel degreeLevel = PreferencesManager.getGlobalDegreeLevelPref(this);
        boolean isZeroYear = (degreeLevel == DegreeLevel.ZERO_YEAR);

        String englishGroup = null;
        if (!isZeroYear && englishSwitch.isChecked()) {
            String val = englishInput.getText().toString().trim();
            if (!val.isEmpty()) {
                englishGroup = val;
            }
        }
        PreferencesManager.setGlobalEnglishGroupPref(this, englishGroup);

        String seminarTeacher = null;
        if (!isZeroYear && seminarContainer.getVisibility() == View.VISIBLE && seminarSwitch.isChecked()) {
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
        StudyMode studyMode = PreferencesManager.getGlobalStudyModePref(this);
        int year = PreferencesManager.getGlobalYearPref(this);
        String specialty = PreferencesManager.getGlobalSpecialtyPref(this);

        PreferencesManager.saveGlobalPrefs(this, login, password, major, degreeLevel, studyMode, year, specialty, englishGroup);
        PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_COMPLETED);

        // clear cached schedule
        ScheduleRepository.getInstance(this).invalidateCache();

        Logger.i("SetupAdditionalActivity.finishSetup", "Onboarding setup completed successfully (major=" + major + ", degree=" + degreeLevel + ", mode=" + studyMode + ", year=" + year + ", surname=" + Logger.maskSurname(enteredSurname) + "). Navigating to MainActivity.");

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
