package com.vintro.wsplanner.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.network.PUW;
import com.vintro.wsplanner.enums.InputState;
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.utils.Logger;

public class LoginActivity extends AppCompatActivity {
    ViewGroup layout;
    EditText loginInput;
    EditText passwordInput;
    ProgressBar progressBar;
    TextView errorLabel;
    Button confirmButton;
    Handler handler = new Handler(Looper.getMainLooper());

    private InputState loginInputState = InputState.NORMAL;
    private InputState passwordInputState = InputState.NORMAL;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Logger.d("LoginActivity.onCreate", "LoginActivity created");
        UIHelper.setSelectedTheme(this);
        UIHelper.setSelectedLanguage(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        layout = findViewById(R.id.content_container);
        loginInput = findViewById(R.id.login_input);
        passwordInput = findViewById(R.id.password_input);
        progressBar = findViewById(R.id.progress_bar);
        errorLabel = findViewById(R.id.error_label);
        confirmButton = findViewById(R.id.confirm_button);

        confirmButton.setEnabled(false);
        AnimationHelper.animateAlpha(confirmButton, 0.7f);

        confirmButton.setOnClickListener(this::handleBtnClick);

        loginInput.addTextChangedListener(onChangeHandler(loginInput, this::updateLoginState));
        passwordInput.addTextChangedListener(onChangeHandler(passwordInput, this::updatePasswordState));

        loginInput.setOnFocusChangeListener((v, hasFocus) -> updateInputBackgrounds());
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> updateInputBackgrounds());
    }

    private void updateLoginState() {
        setLoginState(InputState.NORMAL);
    }

    private void updatePasswordState() {
        setPasswordState(InputState.NORMAL);
    }

    private void updateInputBackgrounds() {
        AnimationHelper.animateInputState(this, loginInput, loginInputState, loginInputState);
        AnimationHelper.animateInputState(this, passwordInput, passwordInputState, passwordInputState);
    }

    private void handleBtnClick(View v) {
        String login = loginInput.getText().toString();
        String password = passwordInput.getText().toString();

        Logger.i("LoginActivity.handleBtnClick", "Saving global login credentials for user: " + Logger.maskSensitiveData(login) + ", advancing to SetupCourseActivity");
        PreferencesManager.saveGlobalPrefs(this, login, password, null, null, null, 1, null, null);
        PreferencesManager.setOnboardingStage(this, PreferencesManager.ONBOARDING_STAGE_COURSE);
        
        Intent intent = new Intent(this, SetupCourseActivity.class);
        startActivity(intent);
        finish();
    }

    private TextWatcher onChangeHandler(EditText input, Runnable stateUpdater) {
        return new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                setConfirmButtonEnabled(false);
                handler.removeCallbacksAndMessages(null);
                resetFields();
                stateUpdater.run();

                if (!editable.toString().isBlank()) {
                    handler.postDelayed(LoginActivity.this::checkData, 800);
                }
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
    }

    private void checkData() {
        setConfirmButtonEnabled(false);

        String login = loginInput.getText().toString();
        String password = passwordInput.getText().toString();

        if (login.isBlank() || password.isBlank()) {
            return;
        }

        Logger.d("LoginActivity.checkData", "Verifying credentials for user: " + Logger.maskSensitiveData(login));

        resetFields();

        progressBar.setVisibility(View.VISIBLE);
        errorLabel.setVisibility(View.VISIBLE);
        errorLabel.setText(R.string.configure_status_checking);
        errorLabel.setTextColor(UIHelper.getThemeColor(this, R.attr.app_text));

        new Thread(() -> {
            int result = PUW.checkLogin(login, password);
            Logger.d("LoginActivity.checkData", "Checking data in thread, result: " + result);

            runOnUiThread(() -> {
                Logger.d("LoginActivity.checkData", "Checking data in UI thread, result: " + result);

                if (result == -1) {
                    Toast.makeText(this, "Network error occurred", Toast.LENGTH_SHORT).show();
                    setCheckingError();
                } else if (result == 0) {
                    setCheckingError();
                } else if (result == 1) {
                    setCheckingOk();
                }
            });
        }).start();
    }

    private void setCheckingOk() {
        Logger.i("LoginActivity.setCheckingOk", "Credentials valid, confirm button enabled");
        setConfirmButtonEnabled(true);
        setLoginState(InputState.OK);
        setPasswordState(InputState.OK);
        progressBar.setVisibility(View.GONE);
        errorLabel.setText(R.string.configure_status_ok);
        errorLabel.setTextColor(UIHelper.getThemeColor(this, R.attr.input_border_ok));

        hideKeyboard();
    }

    private void hideKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            confirmButton.requestFocus();
            currentFocus.clearFocus();
            
        }
    }

    private void setCheckingError() {
        Logger.w("LoginActivity.setCheckingError", "Credentials check failed or invalid");
        progressBar.setVisibility(View.GONE);
        errorLabel.setText(R.string.configure_status_error);
        errorLabel.setTextColor(UIHelper.getThemeColor(this, R.attr.input_border_error));
        setLoginState(InputState.ERROR);
        setPasswordState(InputState.ERROR);
    }

    private void resetFields() {
        Transition transition = new ChangeBounds();
        transition.excludeTarget(errorLabel, true);
        transition.excludeTarget(progressBar, true);
        transition.setDuration(200);
        TransitionManager.beginDelayedTransition(layout, transition);

        progressBar.setVisibility(View.GONE);
        errorLabel.setVisibility(View.GONE);

        setLoginState(InputState.NORMAL);
        setPasswordState(InputState.NORMAL);
    }

    private void setLoginState(InputState state) {
        AnimationHelper.animateInputState(this, loginInput, state, loginInputState);
        loginInputState = state;
    }

    private void setPasswordState(InputState state) {
        AnimationHelper.animateInputState(this, passwordInput, state, passwordInputState);
        passwordInputState = state;
    }

    private void setConfirmButtonEnabled(boolean enabled) {
        confirmButton.setEnabled(enabled);
        AnimationHelper.animateAlpha(confirmButton, enabled ? 1f : 0.5f);
    }
}
