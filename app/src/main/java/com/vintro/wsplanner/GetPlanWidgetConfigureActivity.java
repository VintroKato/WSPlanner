package com.vintro.wsplanner;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.transition.ChangeBounds;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.card.MaterialCardView;


public class GetPlanWidgetConfigureActivity extends Activity {
    ConstraintLayout layout;
    EditText loginInput;
    EditText passwordInput;
    ProgressBar progressBar;
    TextView errorLabel;
    MaterialCardView courseCard1, courseCard2, courseCard3, courseCard4;
    Button confirmButton;
    Handler handler = new Handler(Looper.getMainLooper());

    enum InputState {
        NORMAL,
        OK,
        ERROR
    }

    private InputState loginInputState = InputState.NORMAL;
    private InputState passwordInputState = InputState.NORMAL;
    private int course = 3;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.get_plan_widget_configure);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setResult(RESULT_CANCELED);

        layout = findViewById(R.id.main);

        loginInput = findViewById(R.id.login_input);
        passwordInput = findViewById(R.id.password_input);
        progressBar = findViewById(R.id.progress_bar);
        errorLabel = findViewById(R.id.error_label);
        courseCard1 = findViewById(R.id.course_card_1);
        courseCard2 = findViewById(R.id.course_card_2);
        courseCard3 = findViewById(R.id.course_card_3);
        courseCard4 = findViewById(R.id.course_card_4);
        confirmButton = findViewById(R.id.confirm_button);

        selectCard(courseCard3, course);

        confirmButton.setEnabled(false);
        Utils.animateAlpha(confirmButton, 0.7f);

        confirmButton.setOnClickListener(v -> handleBtnClick(v));

        loginInput.addTextChangedListener(onChangeHandler(loginInput, this::updateLoginState));

        passwordInput.addTextChangedListener(onChangeHandler(passwordInput, this::updatePasswordState));

        loginInput.setOnFocusChangeListener((v, hasFocus) -> updateInputBackgrounds());
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> updateInputBackgrounds());

        courseCard1.setOnClickListener(v -> selectCard(courseCard1, 1));
        courseCard2.setOnClickListener(v -> selectCard(courseCard2, 2));
        courseCard3.setOnClickListener(v -> selectCard(courseCard3, 3));
        courseCard4.setOnClickListener(v -> selectCard(courseCard4, 4));
    }

    private void updateLoginState() {
        boolean isEmpty = loginInput.getText().toString().isBlank();
        setLoginState(isEmpty ? InputState.ERROR : InputState.NORMAL);
    }

    private void updatePasswordState() {
        boolean isEmpty = passwordInput.getText().toString().isBlank();
        setPasswordState(isEmpty ? InputState.ERROR : InputState.NORMAL);
    }

    private void updateInputBackgrounds() {
        AnimationHelper.animateInputBackground(this, loginInput, loginInputState, loginInputState);
        AnimationHelper.animateInputBackground(this, passwordInput, passwordInputState, passwordInputState);
    }

    private void handleBtnClick(View v) {
        String login = loginInput.getText().toString();
        String password = passwordInput.getText().toString();

        int widgetId;

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras == null) {
            finishAndRemoveTask();
            return;
        }

        widgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finishAndRemoveTask();
            return;
        }

        Utils.savePrefs(GetPlanWidgetConfigureActivity.this, widgetId, login, password, course);

        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(GetPlanWidgetConfigureActivity.this);
        GetPlanWidget.updateAppWidget(GetPlanWidgetConfigureActivity.this, appWidgetManager, widgetId);

        setResult(RESULT_OK, resultValue);
        finishAndRemoveTask();
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
                    handler.postDelayed(() -> checkData(), 500);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        };
    }

    private void selectCard(MaterialCardView selectedCard, int newCourse) {
        course = newCourse;

        GridLayout cardLayout = (GridLayout) selectedCard.getParent();

        if (selectedCard.isChecked()) {
            return;
        }

        for (int i = 0; i < cardLayout.getChildCount(); i++) {
            MaterialCardView card = (MaterialCardView) cardLayout.getChildAt(i);
            if (card != selectedCard) {
                AnimationHelper.animateCardSelection(this, card, false);
            }
        }

        AnimationHelper.animateCardSelection(this, selectedCard, true);
    }

    private void checkData() {
        setConfirmButtonEnabled(false);

        String login = loginInput.getText().toString();
        String password = passwordInput.getText().toString();

        if (login.isBlank() || password.isBlank()) {
            return;
        }

        resetFields();

        progressBar.setVisibility(View.VISIBLE);
        errorLabel.setVisibility(View.VISIBLE);
        errorLabel.setText("Проверка данных");
        errorLabel.setTextColor(ThemeUtils.getThemeColor(this, R.attr.app_text));

        new Thread(() -> {
            int result = PUW.checkLogin(login, password);
            Logger.d("ConfigActivity.checkData", "Checking data in thread, result: " + result);


            runOnUiThread(() -> {
                Logger.d("ConfigActivity.checkData", "Checking data in UI thread, result: " + result);
                if (result == -1) {
                    Logger.e("ConfigActivity.checkData", "Got -1 from checkLogin");
                    Toast.makeText(this, "Произошла ошибка, обратитесь к разрабу", Toast.LENGTH_SHORT).show();
                } else if (result == 0) {
                    setCheckingError();
                } else if (result == 1) {
                    setCheckingOk();
                } else {
                    Toast.makeText(this, "Произошла совершенно непонятная ошибка, которая физически не могла случиться, обратитесь к разрабу", Toast.LENGTH_LONG).show();
                    Logger.wtf("ConfigActivity.checkData", "Got strange result from checkLogin: " + result);
                }
            });
        }).start();
    }

    private void setCheckingOk() {
        setConfirmButtonEnabled(true);

        setLoginState(InputState.OK);
        setPasswordState(InputState.OK);

        progressBar.setVisibility(View.GONE);
        errorLabel.setText("Данные верны");
        errorLabel.setTextColor(ThemeUtils.getThemeColor(this, R.attr.input_border_ok));
    }

    private void setCheckingError() {
        progressBar.setVisibility(View.GONE);

        errorLabel.setText("Неверный логин или пароль");
        errorLabel.setTextColor(ThemeUtils.getThemeColor(this, R.attr.input_border_error));

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
        AnimationHelper.animateInputBackground(this, loginInput, state, loginInputState);
        loginInputState = state;
    }

    private void setPasswordState(InputState state) {
        AnimationHelper.animateInputBackground(this, passwordInput, state, passwordInputState);
        passwordInputState = state;
    }

    private void setConfirmButtonEnabled(boolean enabled) {
        confirmButton.setEnabled(enabled);
        Utils.animateAlpha(confirmButton, enabled ? 1f : 0.5f);
    }
}
