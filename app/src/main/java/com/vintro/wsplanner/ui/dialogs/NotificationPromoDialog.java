package com.vintro.wsplanner.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.utils.Logger;

// one-time discovery dialog offering to enable online class reminders right after entering the app
public class NotificationPromoDialog {
    private static final String TAG = "NotificationPromoDialog";

    public interface Callback {
        void onEnableClicked();
        void onDismissed();
    }

    public static void show(@NonNull Activity activity, @Nullable Callback callback) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            Logger.w(TAG + ".show", "Activity not active, skipping notification promo dialog");
            return;
        }

        Logger.i(TAG + ".show", "Displaying one-time notification discovery dialog");

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_notification_promo, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.60f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                if (wm != null && wm.isCrossWindowBlurEnabled()) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.setBlurBehindRadius(24);
                    window.setAttributes(lp);
                }
            }
        }

        final boolean[] handled = new boolean[]{false};

        MaterialButton btnEnable = view.findViewById(R.id.dialog_notif_btn_enable);
        MaterialButton btnLater = view.findViewById(R.id.dialog_notif_btn_later);
        ImageButton btnClose = view.findViewById(R.id.dialog_notif_close);

        if (btnEnable != null) {
            btnEnable.setOnClickListener(v -> {
                handled[0] = true;
                Logger.i(TAG, "User accepted online class reminders from promo dialog");
                dialog.dismiss();
                if (callback != null) {
                    callback.onEnableClicked();
                }
            });
        }

        View.OnClickListener dismissAction = v -> {
            handled[0] = true;
            Logger.i(TAG, "User dismissed notification promo dialog");
            dialog.dismiss();
            if (callback != null) {
                callback.onDismissed();
            }
        };

        if (btnLater != null) {
            btnLater.setOnClickListener(dismissAction);
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(dismissAction);
        }

        dialog.setOnCancelListener(d -> {
            if (!handled[0]) {
                handled[0] = true;
                Logger.i(TAG, "User cancelled notification promo dialog");
                if (callback != null) {
                    callback.onDismissed();
                }
            }
        });

        dialog.show();
    }
}
