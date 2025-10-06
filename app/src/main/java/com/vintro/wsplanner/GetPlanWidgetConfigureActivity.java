package com.vintro.wsplanner;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vintro.wsplanner.databinding.GetPlanWidgetConfigureBinding;


public class GetPlanWidgetConfigureActivity extends Activity {
    EditText loginInput;
    EditText passwordInput;
    ProgressBar progressBar;
    TextView errorLabel;
    Button confirmButton;

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

        loginInput = findViewById(R.id.login_input);
        passwordInput = findViewById(R.id.password_input);
        progressBar = findViewById(R.id.progress_bar);
        errorLabel = findViewById(R.id.error_label);
        confirmButton = findViewById(R.id.confirm_button);

        Utils.animateAlpha(confirmButton, 0.7f);
        confirmButton.setEnabled(false);

        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

                Utils.savePrefs(GetPlanWidgetConfigureActivity.this, widgetId, login, password);

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(GetPlanWidgetConfigureActivity.this);
                GetPlanWidget.updateAppWidget(GetPlanWidgetConfigureActivity.this, appWidgetManager, widgetId);

                setResult(RESULT_OK, resultValue);
                finishAndRemoveTask();
            }
        });

        Handler handler = new Handler(Looper.getMainLooper());

        loginInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                resetFields();
                handler.removeCallbacksAndMessages(null);
                if (editable.length() == 0) {
                    Utils.animateAlpha(confirmButton, 0.7f);
                    confirmButton.setEnabled(false);
                    loginInput.setBackgroundResource(R.drawable.shape_input_error);
                } else {
                    handler.postDelayed(() -> checkData(), 500);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
        });

        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                resetFields();
                handler.removeCallbacksAndMessages(null);
                if (editable.length() == 0) {
                    passwordInput.setBackgroundResource(R.drawable.shape_input_error);
                } else {
                    handler.postDelayed(() -> checkData(), 500);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
        });
    }

    private void checkData() {
        confirmButton.setEnabled(false);
        Utils.animateAlpha(confirmButton, 0.5f);

        String login = loginInput.getText().toString();
        String password = passwordInput.getText().toString();

        if (login.length() == 0 || password.length() == 0) {
            return;
        }

        resetFields();
        progressBar.setVisibility(View.VISIBLE);
        errorLabel.setVisibility(View.VISIBLE);
        errorLabel.setText("Проверка данных");
        errorLabel.setTextColor(getResources().getColor(R.color.text_dark));


        new Thread(() -> {
            int result = Utils.checkLogin(login, password);
            Log.d("ConfigActivity.checkData", "Checking data in thread, result: " + result);


            runOnUiThread(() -> {
                Log.d("ConfigActivity.checkData", "Checking data in UI thread, result: " + result);
                if (result == -1) {
                    Log.e("ConfigActivity.checkData", "Got -1 from checkLogin");
                    Toast.makeText(this, "Произошла ошибка, обратитесь к разрабу", Toast.LENGTH_SHORT).show();
                } else if (result == 0) {
                    setError();
                } else if (result == 1) {
                    confirmButton.setEnabled(true);
                    Utils.animateAlpha(confirmButton, 1f);
                    loginInput.setBackgroundResource(R.drawable.shape_input_ok);
                    passwordInput.setBackgroundResource(R.drawable.shape_input_ok);
                    progressBar.setVisibility(View.GONE);
                    errorLabel.setText("Данные верны");
                    errorLabel.setTextColor(getResources().getColor(R.color.input_border_ok_dark));
                } else {
                    Toast.makeText(this, "Произошла совершенно непонятная ошибка, которая физически не могла случиться, обратитесь к разрабу", Toast.LENGTH_LONG).show();
                    Log.wtf("ConfigActivity.checkData", "Got strange result from checkLogin: " + result);
                }
            });
        }).start();
    }

    private void setError() {
        progressBar.setVisibility(View.GONE);
        errorLabel.setText("Неверный логин или пароль");
        errorLabel.setTextColor(getResources().getColor(R.color.input_border_error_dark));
        loginInput.setBackgroundResource(R.drawable.shape_input_error);
        passwordInput.setBackgroundResource(R.drawable.shape_input_error);
    }

    private void resetFields() {
        confirmButton.setEnabled(false);
        Utils.animateAlpha(confirmButton, 0.5f);
        progressBar.setVisibility(View.GONE);
        errorLabel.setVisibility(View.GONE);
        passwordInput.setBackgroundResource(passwordInput.isFocused() ?
                R.drawable.shape_input_focused :
                R.drawable.shape_input_default);
        loginInput.setBackgroundResource(loginInput.isFocused() ?
                R.drawable.shape_input_focused :
                R.drawable.shape_input_default);
    }

}