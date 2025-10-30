package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Contacts;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.transition.TransitionManager;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.Theme;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;

public class SettingsActivity extends AppCompatActivity {
    TextView titleLabel;
    TextView themeHeader;
    TextView languageHeader;

    MaterialCardView cardAutoTheme;
    MaterialCardView cardDarkTheme;
    MaterialCardView cardLightTheme;

    MaterialCardView cardEnglish;
    MaterialCardView cardRussian;
    MaterialCardView cardUkrainian;
    MaterialCardView cardPolish;

    GridLayout languageLayout;
    GridLayout themeLayout;

    Button confirmButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        titleLabel = findViewById(R.id.settings_title_label);
        themeHeader = findViewById(R.id.theme_header);
        languageHeader = findViewById(R.id.language_header);

        cardAutoTheme = findViewById(R.id.card_auto_theme);
        cardDarkTheme = findViewById(R.id.card_dark_theme);
        cardLightTheme = findViewById(R.id.card_light_theme);

        cardEnglish = findViewById(R.id.card_english);
        cardRussian = findViewById(R.id.card_russian);
        cardUkrainian = findViewById(R.id.card_ukrainian);
        cardPolish = findViewById(R.id.card_polish);

        languageLayout = findViewById(R.id.language_layout);
        themeLayout = findViewById(R.id.theme_layout);

        confirmButton = findViewById(R.id.confirm_button);

        confirmButton.setOnClickListener(v -> {
            finish();
        });

        Theme theme = PreferencesManager.getThemePref(this);
        Language language = PreferencesManager.getLanguagePref(this);

        switch (theme) {
            case AUTO:
                selectCard(cardAutoTheme);
                break;
            case DARK:
                selectCard(cardDarkTheme);
                break;
            case LIGHT:
                selectCard(cardLightTheme);
                break;
        }

        switch (language) {
            case ENGLISH:
                selectCard(cardEnglish);
                break;
            case RUSSIAN:
                selectCard(cardRussian);
                break;
            case UKRAINIAN:
                selectCard(cardUkrainian);
                break;
            case POLISH:
                selectCard(cardPolish);
                break;
        }

        bindThemeOnClick(cardAutoTheme, Theme.AUTO);
        bindThemeOnClick(cardDarkTheme, Theme.DARK);
        bindThemeOnClick(cardLightTheme, Theme.LIGHT);

        bindLanguageOnClick(cardEnglish, Language.ENGLISH);
        bindLanguageOnClick(cardRussian, Language.RUSSIAN);
        bindLanguageOnClick(cardUkrainian, Language.UKRAINIAN);
        bindLanguageOnClick(cardPolish, Language.POLISH);
    }

    private void selectCard(MaterialCardView selectedCard) {
        GridLayout cardLayout = (GridLayout) selectedCard.getParent();

        if (selectedCard.isChecked()) {
            return;
        }

        for (int i = 0; i < cardLayout.getChildCount(); i++) {
            MaterialCardView card = (MaterialCardView) cardLayout.getChildAt(i);
            if (card != selectedCard) {
                if (card.isChecked()) {
                    AnimationHelper.animateCardSelection(this, card, false);
                }
            }
        }
        AnimationHelper.animateCardSelection(this, selectedCard, true);
    }

    private void bindThemeOnClick(MaterialCardView card, Theme theme) {
        card.setOnClickListener(v -> {
            selectCard(card);
            PreferencesManager.setThemePref(this, theme);
            UIHelper.setSelectedTheme(this);
        });
    }

    private void bindLanguageOnClick(MaterialCardView card, Language language) {
        card.setOnClickListener(v -> {
            selectCard(card);
            PreferencesManager.setLanguagePref(this, language);
//            UIHelper.setSelectedLanguage(this);
            recreate();
        });
    }
}
