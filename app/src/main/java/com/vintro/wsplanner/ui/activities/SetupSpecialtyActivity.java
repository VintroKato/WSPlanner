package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.services.CourseSetupService;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class SetupSpecialtyActivity extends AppCompatActivity {
    private ImageButton backButton;
    private View loadingLayout;
    private LinearLayout contentLayout;
    private LinearLayout specialtiesContainer;
    private TextView noSpecialtiesLabel;
    private SwitchMaterial manualSpecialtySwitch;
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
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_setup_specialty);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        backButton = findViewById(R.id.back_button);
        loadingLayout = findViewById(R.id.loading_layout);
        contentLayout = findViewById(R.id.content_layout);
        specialtiesContainer = findViewById(R.id.specialties_container);
        noSpecialtiesLabel = findViewById(R.id.no_specialties_label);
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
            // specialty selected or entered
            confirmButton.setVisibility(View.VISIBLE);
            confirmButton.setEnabled(true);
            confirmButton.setAlpha(1.0f);
            skipButton.setVisibility(View.VISIBLE); // allow skipping if clicked by mistake
        } else if (!hasItems) {
            // no options available
            confirmButton.setVisibility(View.GONE);
            skipButton.setVisibility(View.VISIBLE);
        } else {
            // options available but not selected
            confirmButton.setVisibility(View.VISIBLE);
            confirmButton.setEnabled(false);
            confirmButton.setAlpha(0.5f);
            skipButton.setVisibility(View.VISIBLE);
        }
    }

    private void fetchSpecialties() {
        loadingLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);

        courseSetupService.loadSpecialties(this, new CourseSetupService.OnSpecialtiesLoadedCallback() {
            @Override
            public void onSuccess(List<String> specialties) {
                runOnUiThread(() -> {
                    TransitionManager.beginDelayedTransition((ViewGroup) findViewById(R.id.main), new AutoTransition());
                    loadingLayout.setVisibility(View.GONE);
                    contentLayout.setVisibility(View.VISIBLE);
                    populateSpecialties(specialties);
                });
            }

            @Override
            public void onError(Exception e) {
                Logger.e("SetupSpecialtyActivity", "loadSpecialties error: " + e.getMessage());
                runOnUiThread(() -> {
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
            noSpecialtiesLabel.setVisibility(View.VISIBLE);
        } else {
            noSpecialtiesLabel.setVisibility(View.GONE);
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
                (int) (52 * density)
        );
        int marginVertical = (int) (4 * density);
        int marginHorizontal = (int) (8 * density);
        params.setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical);
        card.setLayoutParams(params);

        card.setCheckable(true);
        card.setCheckedIcon(null);
        card.setCardBackgroundColor(UIHelper.getThemeColor(this, R.attr.card_bg));
        card.setStrokeColor(UIHelper.getThemeColor(this, R.attr.card_border));
        card.setStrokeWidth((int) (2 * density));
        card.setRadius(16 * density);
        card.setCardElevation(2 * density);

        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));
        textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding((int) (16 * density), 0, (int) (16 * density), 0);
        textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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

        PreferencesManager.setGlobalSpecialtyPref(this, finalSpecialty);
        PreferencesManager.setGlobalSpecialtyConfigured(this, true);
        PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_ADDITIONAL);
        Logger.d("SetupSpecialtyActivity", "Continuing to additional setup with specialty: " + finalSpecialty);

        Intent intent = new Intent(this, SetupAdditionalActivity.class);
        startActivity(intent);
    }
}
