package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.InputState;
import com.vintro.wsplanner.services.CourseSetupService;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;
import com.vintro.wsplanner.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class SetupSpecialtyActivity extends AppCompatActivity {
    private ImageButton backButton;
    private View loadingLayout;
    private LinearLayout contentLayout;
    private LinearLayout specialtiesContainer;
    private View noSpecialtiesCard;
    private MaterialSwitch manualSpecialtySwitch;
    private EditText manualSpecialtyInput;
    private Button confirmButton;
    private Button skipButton;

    private List<String> availableSpecialties = new ArrayList<>();
    private String selectedSpecialty = null;
    private MaterialCardView selectedCard = null;

    private final CourseSetupService courseSetupService = CourseSetupService.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("SetupSpecialtyActivity.onCreate", "SetupSpecialtyActivity created");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        UIHelper.setupSystemBars(this);
        setContentView(R.layout.activity_setup_specialty);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        UIHelper.bindOnboardingSteps(this, 3);

        backButton = findViewById(R.id.back_button);
        loadingLayout = findViewById(R.id.loading_layout);
        contentLayout = findViewById(R.id.content_layout);
        specialtiesContainer = findViewById(R.id.specialties_container);
        noSpecialtiesCard = findViewById(R.id.no_specialties_card);
        manualSpecialtySwitch = findViewById(R.id.manual_specialty_switch);
        manualSpecialtyInput = findViewById(R.id.manual_specialty_input);
        confirmButton = findViewById(R.id.confirm_button);
        skipButton = findViewById(R.id.skip_button);

        backButton.setOnClickListener(v -> finish());

        manualSpecialtySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TransitionManager.beginDelayedTransition(contentLayout, new AutoTransition());
            manualSpecialtyInput.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                deselectAllCards();
                selectedSpecialty = null;
            }
            updateButtonsState();
        });

        manualSpecialtyInput.setOnFocusChangeListener((v, hasFocus) ->
                AnimationHelper.animateInputState(this, manualSpecialtyInput, InputState.NORMAL, InputState.NORMAL));

        manualSpecialtyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonsState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        confirmButton.setOnClickListener(v -> continueToAdditional(false));
        skipButton.setOnClickListener(v -> continueToAdditional(true));

        fetchSpecialties();
    }

    private void updateButtonsState() {
        boolean hasItems = (availableSpecialties != null && !availableSpecialties.isEmpty());
        boolean isManual = manualSpecialtySwitch.isChecked()
                && !manualSpecialtyInput.getText().toString().trim().isEmpty();
        boolean isCard = (selectedSpecialty != null && !selectedSpecialty.trim().isEmpty());
        boolean isChosen = isManual || isCard;

        if (isChosen) {
            // specialty selected or entered: show only next
            confirmButton.setVisibility(View.VISIBLE);
            confirmButton.setEnabled(true);
            confirmButton.setAlpha(1.0f);
            skipButton.setVisibility(View.GONE);
        } else if (!hasItems) {
            // no options available and nothing entered manually: show only skip
            confirmButton.setVisibility(View.GONE);
            skipButton.setVisibility(View.VISIBLE);
        } else {
            // options available but not selected: show both, next disabled
            confirmButton.setVisibility(View.VISIBLE);
            confirmButton.setEnabled(false);
            confirmButton.setAlpha(0.5f);
            skipButton.setVisibility(View.VISIBLE);
        }
    }

    private void fetchSpecialties() {
        Logger.d("SetupSpecialtyActivity.fetchSpecialties", "Starting fetch specialties request");
        loadingLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        courseSetupService.loadSpecialties(this, new CourseSetupService.OnSpecialtiesLoadedCallback() {
            @Override
            public void onSuccess(List<String> specialties) {
                Logger.i("SetupSpecialtyActivity.fetchSpecialties", "Specialties loaded successfully (" + (specialties != null ? specialties.size() : 0) + " options)");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.main), new AutoTransition());
                    loadingLayout.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);
                    populateSpecialties(specialties);
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e("SetupSpecialtyActivity.fetchSpecialties", "loadSpecialties error: " + Logger.formatException(e));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.main), new AutoTransition());
                    loadingLayout.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);
                    populateSpecialties(new ArrayList<>());
                });
            }
        });
    }

    private void populateSpecialties(List<String> specializations) {
        availableSpecialties = specializations != null ? specializations : new ArrayList<>();
        specialtiesContainer.removeAllViews();

        if (availableSpecialties.isEmpty()) {
            noSpecialtiesCard.setVisibility(View.VISIBLE);
            TextView titleView = findViewById(R.id.no_specialties_title);
            TextView descView = findViewById(R.id.no_specialties_desc);
            if (!NetworkUtils.isNetworkAvailable(this)) {
                Logger.w("SetupSpecialtyActivity.populateSpecialties", "Offline mode detected: showing no internet UI for specialties");
                if (titleView != null) titleView.setText(R.string.no_internet_title);
                if (descView != null) descView.setText(R.string.no_internet_specialty_desc);
            } else {
                Logger.d("SetupSpecialtyActivity.populateSpecialties", "No specialties available for this course, displaying fallback card");
                if (titleView != null) titleView.setText(R.string.onboarding_no_specialties);
                if (descView != null) descView.setText(R.string.onboarding_no_specialties_desc);
            }
        } else {
            noSpecialtiesCard.setVisibility(View.GONE);
            for (String spec : availableSpecialties) {
                MaterialCardView card = createSpecialtyCard(spec);
                card.setOnClickListener(v -> {
                    if (manualSpecialtySwitch.isChecked()) {
                        manualSpecialtySwitch.setChecked(false);
                    }
                    selectCard(card, spec);
                });
                specialtiesContainer.addView(card);
            }
        }

        selectedCard = null;
        selectedSpecialty = null;
        updateButtonsState();
    }

    private MaterialCardView createSpecialtyCard(String text) {
        MaterialCardView card = new MaterialCardView(this);
        float density = getResources().getDisplayMetrics().density;

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int marginVertical = Math.round(4 * density);
        params.setMargins(0, marginVertical, 0, marginVertical);
        card.setLayoutParams(params);
        card.setMinimumHeight(Math.round(52 * density));

        card.setCheckable(true);
        card.setCheckedIcon(null);
        card.setCardBackgroundColor(UIHelper.getThemeColor(this, R.attr.card_bg));
        card.setStrokeColor(UIHelper.getThemeColor(this, R.attr.card_border));
        card.setStrokeWidth(Math.max(1, Math.round(1.0f * density)));
        card.setRadius(14 * density);
        card.setCardElevation(0);

        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        textView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textView.setGravity(Gravity.CENTER);
        int padH = Math.round(16 * density);
        int padV = Math.round(14 * density);
        textView.setPadding(padH, padV, padH, padV);
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        card.addView(textView);
        return card;
    }

    private void selectCard(MaterialCardView cardToSelect, String spec) {
        deselectAllCards();
        selectedCard = cardToSelect;
        selectedSpecialty = spec;
        AnimationHelper.animateCardSelection(this, cardToSelect, true);
        updateButtonsState();
    }

    private void deselectAllCards() {
        for (int i = 0; i < specialtiesContainer.getChildCount(); i++) {
            MaterialCardView card = (MaterialCardView) specialtiesContainer.getChildAt(i);
            if (card.isChecked()) {
                AnimationHelper.animateCardSelection(this, card, false);
            }
        }
        selectedCard = null;
        updateButtonsState();
    }

    private void continueToAdditional(boolean skipped) {
        String finalSpecialty = null;
        if (!skipped) {
            if (manualSpecialtySwitch.isChecked()) {
                String manualText = manualSpecialtyInput.getText().toString().trim();
                finalSpecialty = manualText.isEmpty() ? null : manualText;
            } else {
                finalSpecialty = selectedSpecialty;
            }
        }

        boolean isReOnboarding = getIntent().getBooleanExtra(SetupCourseActivity.EXTRA_FROM_STUDENT_PANEL, false);
        PreferencesManager.setGlobalSpecialtyPref(this, finalSpecialty);
        PreferencesManager.setGlobalSpecialtyConfigured(this, true);
        if (!isReOnboarding) {
            PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_ADDITIONAL);
        }
        Logger.i("SetupSpecialtyActivity.continueToAdditional", "Continuing to additional setup with specialty: " + finalSpecialty + " (skipped=" + skipped + ", reOnboarding=" + isReOnboarding + ")");

        Intent intent = new Intent(this, SetupAdditionalActivity.class);
        if (isReOnboarding) {
            intent.putExtra(SetupCourseActivity.EXTRA_FROM_STUDENT_PANEL, true);
        }
        startActivity(intent);
    }
}
