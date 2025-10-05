package com.vintro.wsplanner;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.vintro.wsplanner.databinding.GetPlanWidgetConfigureBinding;


public class GetPlanWidgetConfigureActivity extends Activity {
    EditText loginInput;
    EditText passwordInput;
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
        confirmButton = findViewById(R.id.confirm_button);

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
    }
}