package com.vintro.wsplanner.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.ui.fragments.SettingsFragment;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

public class SettingsActivity extends AppCompatActivity implements SettingsFragment.SettingsHost {
    private static final String TAG = "SettingsActivity";
    public static final String EXTRA_HIDE_WIDGET_SETTINGS = "hide_widget_settings";

    private View rootContainer;
    private boolean currentNightMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIHelper.setSelectedLanguage(this);
        EdgeToEdge.enable(this);
        currentNightMode = UIHelper.isNightMode(this);
        UIHelper.setupSystemBars(this, currentNightMode);
        setContentView(R.layout.activity_settings);

        rootContainer = findViewById(R.id.settings_activity_root);
        View fragmentContainer = findViewById(R.id.settings_fragment_container);

        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            Logger.d(TAG + ".onCreate", "Hosting SettingsFragment in pre-onboarding mode");
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_fragment_container, SettingsFragment.newInstance(true))
                    .commit();
        }
    }

    @Override
    public View getThemeAnimationRoot() {
        return rootContainer;
    }

    @Override
    public void onThemeAnimationStarted(boolean isNewNight) {
        currentNightMode = isNewNight;
    }

    @Override
    public void onThemeChangedCompleted(boolean isNewNight) {
        currentNightMode = isNewNight;
        UIHelper.setSelectedTheme(this);
        UIHelper.applyNightModeWithoutRecreate(this, isNewNight);
    }

    @Override
    public void onLanguageChanged() {
        Logger.i(TAG + ".onLanguageChanged", "Restarting SettingsActivity without animation to apply new locale");
        UIHelper.setSelectedLanguage(this);
        if (isFinishing() || isDestroyed()) return;

        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra(EXTRA_HIDE_WIDGET_SETTINGS, getIntent().getBooleanExtra(EXTRA_HIDE_WIDGET_SETTINGS, true));
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
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
        // standalone pre-onboarding settings has no bottom navigation badge
    }

    @Override
    public void onVisibleWeekdaysChanged() {
        // pre-onboarding settings does not host schedule fragment
    }

    @Override
    protected void onPause() {
        super.onPause();
        UIHelper.setSelectedTheme(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AnimationHelper.cancelActiveThemeAnimators();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // handled smoothly via animation to prevent activity recreation
    }
}
