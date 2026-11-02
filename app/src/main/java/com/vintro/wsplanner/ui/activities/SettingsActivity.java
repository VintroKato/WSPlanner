package com.vintro.wsplanner.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.Language;
import com.vintro.wsplanner.enums.AppTheme;
import com.vintro.wsplanner.services.UpdateManager;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

public class SettingsActivity extends AppCompatActivity {
    public static final String EXTRA_HIDE_WIDGET_SETTINGS = "hide_widget_settings";

    ConstraintLayout root;
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
    private MaterialButton btnCheckUpdate;
    private boolean currentNightMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        currentNightMode = UIHelper.isNightMode(this);
        UIHelper.setupSystemBars(this, currentNightMode);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_scroll), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        root = findViewById(R.id.main);

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

        TextView widgetSectionHeader = findViewById(R.id.widget_section_header);
        MaterialCardView cardWidgetSettings = findViewById(R.id.card_widget_settings);
        boolean hideWidgets = getIntent().getBooleanExtra(EXTRA_HIDE_WIDGET_SETTINGS, false)
                || !PreferencesManager.isOnboardingCompleted(this);

        if (hideWidgets) {
            if (widgetSectionHeader != null) {
                widgetSectionHeader.setVisibility(View.GONE);
            }
            if (cardWidgetSettings != null) {
                cardWidgetSettings.setVisibility(View.GONE);
            }
        } else if (cardWidgetSettings != null) {
            cardWidgetSettings.setOnClickListener(v -> {
                Logger.d("SettingsActivity.cardWidgetSettings", "Navigating to WidgetConfigureActivity");
                Intent widgetConfigIntent = new Intent(this, WidgetConfigureActivity.class);
                startActivity(widgetConfigIntent);
            });
        }

        btnCheckUpdate = findViewById(R.id.btn_check_update);
        MaterialButton btnOpenGithub = findViewById(R.id.btn_open_github);

        refreshUpdateUI();

        if (btnOpenGithub != null) {
            btnOpenGithub.setOnClickListener(v -> {
                Logger.i("SettingsActivity", "User tapped 'GitHub' button in Settings");
                UpdateManager.openGitHubRepo(this);
            });
        }

        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> {
                Logger.i("SettingsActivity", "User tapped update button in Settings");
                if (UpdateManager.hasPendingUpdate(this)) {
                    String pendingVersion = UpdateManager.getSavedLatestVersion(this);
                    String pendingUrl = UpdateManager.getSavedReleaseUrl(this);
                    String pendingApk = UpdateManager.getSavedApkUrl(this);
                    Logger.i("SettingsActivity", "Opening update dialog with cached update: version=" + pendingVersion);
                    UpdateManager.showUpdateDialog(this, pendingVersion, pendingUrl, pendingApk);
                    return;
                }

                if (UpdateManager.isCooldownActive()) {
                    int remaining = UpdateManager.getRemainingCooldownSeconds();
                    Logger.w("SettingsActivity", "Manual update check blocked by cooldown (" + remaining + "s remaining)");
                    Toast.makeText(this, getString(R.string.update_cooldown_toast, remaining), Toast.LENGTH_SHORT).show();
                    return;
                }

                btnCheckUpdate.setEnabled(false);
                Logger.i("SettingsActivity", "Initiating manual update check from Settings UI");
                Toast.makeText(this, getString(R.string.update_checking_toast), Toast.LENGTH_SHORT).show();

                UpdateManager.checkManualUpdate(this, new UpdateManager.UpdateCheckCallback() {
                    @Override
                    public void onUpdateAvailable(String newVersion, String releaseUrl) {
                        Logger.i("SettingsActivity", "Manual update check callback -> onUpdateAvailable: " + newVersion + " (" + releaseUrl + ")");
                        btnCheckUpdate.setEnabled(true);
                        refreshUpdateUI();
                        UpdateManager.showUpdateDialog(SettingsActivity.this, newVersion, releaseUrl);
                    }

                    @Override
                    public void onUpToDate(String currentVersion) {
                        Logger.i("SettingsActivity", "Manual update check callback -> onUpToDate: " + currentVersion);
                        btnCheckUpdate.setEnabled(true);
                        refreshUpdateUI();
                        Toast.makeText(SettingsActivity.this, getString(R.string.update_latest_installed_toast, currentVersion), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Logger.e("SettingsActivity", "Manual update check callback -> onError: " + errorMessage);
                        btnCheckUpdate.setEnabled(true);
                        refreshUpdateUI();
                        Toast.makeText(SettingsActivity.this, getString(R.string.update_check_error_toast), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        MaterialButton btnContactDev = findViewById(R.id.btn_contact_dev);
        if (btnContactDev != null) {
            btnContactDev.setOnClickListener(v -> {
                Logger.i("SettingsActivity", "User tapped Contact Developer button (target: @VintroKato)");
                try {
                    Intent tgIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=VintroKato"));
                    startActivity(tgIntent);
                    Logger.i("SettingsActivity", "Successfully dispatched intent to Telegram app for @VintroKato (tg://resolve?domain=VintroKato)");
                } catch (Exception e) {
                    Logger.w("SettingsActivity", "Telegram app not available or intent failed: " + Logger.formatException(e) + ". Falling back to web link https://t.me/VintroKato");
                    try {
                        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/VintroKato"));
                        startActivity(webIntent);
                        Logger.i("SettingsActivity", "Successfully opened Telegram web contact in browser (https://t.me/VintroKato)");
                    } catch (Exception ex) {
                        Logger.e("SettingsActivity", "Failed to open Telegram web contact link: " + Logger.formatException(ex), ex);
                    }
                }
            });
        }

        MaterialButton btnShareLogs = findViewById(R.id.btn_share_logs);
        if (btnShareLogs != null) {
            btnShareLogs.setOnClickListener(v -> {
                Logger.i("SettingsActivity", "User tapped Share Logs button");
                Logger.shareLogFile(this);
            });
        }

        AppTheme theme = PreferencesManager.getThemePref(this);
        Language language = PreferencesManager.getLanguagePref(this);
        Logger.d("SettingsActivity.onCreate", "SettingsActivity created (theme=" + theme + ", language=" + language + ")");

        switch (theme) {
            case AUTO:
                cardAutoTheme.setChecked(true);
                break;
            case DARK:
                cardDarkTheme.setChecked(true);
                break;
            case LIGHT:
                cardLightTheme.setChecked(true);
                break;
        }

        switch (language) {
            case ENGLISH:
                cardEnglish.setChecked(true);
                break;
            case RUSSIAN:
                cardRussian.setChecked(true);
                break;
            case UKRAINIAN:
                cardUkrainian.setChecked(true);
                break;
            case POLISH:
                cardPolish.setChecked(true);
                break;
        }

        bindThemeOnClick(cardAutoTheme, AppTheme.AUTO);
        bindThemeOnClick(cardDarkTheme, AppTheme.DARK);
        bindThemeOnClick(cardLightTheme, AppTheme.LIGHT);

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

    private void bindThemeOnClick(MaterialCardView card, AppTheme theme) {
        card.setOnClickListener(v -> {
            AppTheme currentTheme = PreferencesManager.getThemePref(this);
            if (card.isChecked() && currentTheme == theme) {
                return;
            }

            Logger.i("SettingsActivity.bindThemeOnClick", "User selected theme: " + theme);
            boolean isOldNight = currentNightMode;
            boolean isNewNight = UIHelper.isNightTheme(this, theme);

            PreferencesManager.setThemePref(this, theme);

            if (isOldNight != isNewNight) {
                currentNightMode = isNewNight;
                Context oldContext = UIHelper.getThemedContext(this, isOldNight);
                Context newContext = UIHelper.getThemedContext(this, isNewNight);
                AnimationHelper.animateThemeChange(this, oldContext, newContext, root, card, () -> {
                    UIHelper.setSelectedTheme(this);
                });
            } else {
                selectCard(card);
                UIHelper.setSelectedTheme(this);
            }
        });
    }

    private void bindLanguageOnClick(MaterialCardView card, Language language) {
        card.setOnClickListener(v -> {
            Language currentLanguage = PreferencesManager.getLanguagePref(this);
            if (card.isChecked() && currentLanguage == language) {
                return;
            }

            Logger.i("SettingsActivity.bindLanguageOnClick", "User selected language: " + language);
            selectCard(card);
            PreferencesManager.setLanguagePref(this, language);
            UIHelper.setSelectedLanguage(this);

            card.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                finish();
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra(EXTRA_HIDE_WIDGET_SETTINGS, getIntent().getBooleanExtra(EXTRA_HIDE_WIDGET_SETTINGS, false));
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
                    overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
                } else {
                    overridePendingTransition(0, 0);
                }
            }, 200);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUpdateUI();
    }

    private void refreshUpdateUI() {
        if (btnCheckUpdate == null) return;
        boolean hasUpdate = UpdateManager.hasPendingUpdate(this);
        if (hasUpdate) {
            String pendingVersion = UpdateManager.getSavedLatestVersion(this);
            Logger.d("SettingsActivity", "refreshUpdateUI: pending update detected (" + pendingVersion + "), setting button to 'Update'");
            btnCheckUpdate.setText(R.string.settings_update_action_button);
            btnCheckUpdate.setIconResource(R.drawable.ic_update_alert);
        } else {
            Logger.d("SettingsActivity", "refreshUpdateUI: no pending update, setting button to 'Check'");
            btnCheckUpdate.setText(R.string.settings_update_check_button);
            btnCheckUpdate.setIconResource(R.drawable.ic_update_check);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        UIHelper.setSelectedTheme(this);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handled smoothly via animation; prevent activity recreation
    }
}
