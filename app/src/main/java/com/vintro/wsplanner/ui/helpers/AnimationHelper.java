package com.vintro.wsplanner.ui.helpers;

import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.InputState;
import com.vintro.wsplanner.utils.Logger;

public class AnimationHelper {

    private static final int animation_duration = 500;

    public static void animateInputState(Context context, EditText input, InputState state, InputState oldState) {
        GradientDrawable drawable = (GradientDrawable) input.getBackground().mutate();


        int startBgColor = drawable.getColor().getDefaultColor();
        int startBorderColor = getInputBorderColor(context, oldState, startBgColor);

        int endBgColor = input.isFocused() ?
                UIHelper.getThemeColor(context, R.attr.input_bg_active) :
                UIHelper.getThemeColor(context, R.attr.input_bg);

        int endBorderColor = getEndBorderColor(context, state, input.isFocused());

        if (startBgColor == endBgColor && startBorderColor == endBorderColor) {
            return;
        }

        animateInputColors(context, drawable, startBgColor, endBgColor, startBorderColor, endBorderColor);
    }

    public static void animateCardSelection(Context context, MaterialCardView card, boolean checked) {
        if (card.isChecked() == checked) {
            return;
        }
        int startBorderColor = checked ?
                UIHelper.getThemeColor(context, R.attr.card_border) :
                UIHelper.getThemeColor(context, R.attr.card_checked_border);
        int startBgColor = checked ?
                 UIHelper.getThemeColor(context, R.attr.card_bg) :
                 UIHelper.getThemeColor(context, R.attr.card_checked_bg);

        int endBorderColor = checked ?
                UIHelper.getThemeColor(context, R.attr.card_checked_border) :
                UIHelper.getThemeColor(context, R.attr.card_border);
        int endBgColor = checked ?
                UIHelper.getThemeColor(context, R.attr.card_checked_bg) :
                UIHelper.getThemeColor(context, R.attr.card_bg);

        if (startBorderColor == endBorderColor && startBgColor == endBgColor) {
            card.setChecked(checked);
            return;
        }

        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, checked);
    }

    public static void animateThemeChange(Activity context, Context oldContext, ConstraintLayout root) {
        AnimationHelper.animateBgThemeChange(context, oldContext, root);
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);

            if (child instanceof Button) {
                AnimationHelper.animateButtonThemeChange(context, oldContext, (Button) child);
            } else if (child instanceof TextView) {
                AnimationHelper.animateTextThemeChange(context, oldContext, (TextView) child);
            } else if (child instanceof GridLayout) {
                GridLayout subChild = (GridLayout) root.getChildAt(i);
                for (int j = 0; j < subChild.getChildCount(); j++) {
                    MaterialCardView cardChild = (MaterialCardView) subChild.getChildAt(j);
                    AnimationHelper.animateCardThemeChange(context, oldContext, cardChild);
                }
            }
        }
        animateStatusBarIconsThemeChange(context);
    }

    private static void animateStatusBarIconsThemeChange(Activity activity) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());

        boolean isStatusBarLight /* so icons are dark */
                = !UIHelper.isNightMode(activity);

        controller.setAppearanceLightStatusBars(isStatusBarLight);
    }

    private static void animateBgThemeChange(Context context, Context oldContext, View root) {
        int startColor = UIHelper.getThemeColor(oldContext, R.attr.app_bg);
        int endColor = UIHelper.getThemeColor(context, R.attr.app_bg);

        ValueAnimator animator = createColorAnimator(startColor, endColor, root::setBackgroundColor);
        animator.start();
    }

    private static void animateCardThemeChange(Context context, Context oldContext, MaterialCardView card) {
        int startBgColor = card.isChecked() ?
                UIHelper.getThemeColor(oldContext, R.attr.card_checked_bg) :
                UIHelper.getThemeColor(oldContext, R.attr.card_bg);
        int startBorderColor = card.isChecked() ?
                UIHelper.getThemeColor(oldContext, R.attr.card_checked_border) :
                UIHelper.getThemeColor(oldContext, R.attr.card_border);

        int endBorderColor = card.isChecked() ?
                UIHelper.getThemeColor(context, R.attr.card_checked_border) :
                UIHelper.getThemeColor(context, R.attr.card_border);
        int endBgColor = card.isChecked() ?
                UIHelper.getThemeColor(context, R.attr.card_checked_bg) :
                UIHelper.getThemeColor(context, R.attr.card_bg);


        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, card.isChecked());
        animateTextThemeChange(context, oldContext, (TextView) card.getChildAt(0));
    }

    private static void animateTextThemeChange(Context context, Context oldContext, TextView text) {
        int startColor = UIHelper.getThemeColor(oldContext, R.attr.app_text);
        int endColor = UIHelper.getThemeColor(context, R.attr.app_text);

        ValueAnimator animator = createColorAnimator(startColor, endColor, text::setTextColor);
        animator.start();
    }

    private static void animateButtonThemeChange(Context context, Context oldContext, Button button) {
        int startBgColor = UIHelper.getThemeColor(oldContext, R.attr.button_bg);
        int startTextColor = UIHelper.getThemeColor(oldContext, R.attr.button_text);

        int endBgColor = UIHelper.getThemeColor(context, R.attr.button_bg);
        int endTextColor = UIHelper.getThemeColor(context, R.attr.button_text);

        animateButtonColors(button, startBgColor, endBgColor, startTextColor, endTextColor);

    }

    private static int getInputBorderColor(Context context, InputState state, int bgColor) {
        switch (state) {
            case NORMAL:
                return bgColor == UIHelper.getThemeColor(context, R.attr.input_bg_active) ?
                        UIHelper.getThemeColor(context, R.attr.input_border_active) :
                        UIHelper.getThemeColor(context, R.attr.input_border);
            case OK:
                return UIHelper.getThemeColor(context, R.attr.input_border_ok);
            case ERROR:
                return UIHelper.getThemeColor(context, R.attr.input_border_error);
            default:
                return UIHelper.getThemeColor(context, R.attr.input_border);
        }
    }

    private static int getEndBorderColor(Context context, InputState state, boolean isFocused) {
        switch (state) {
            case NORMAL:
                return isFocused ?
                        UIHelper.getThemeColor(context, R.attr.input_border_active) :
                        UIHelper.getThemeColor(context, R.attr.input_border);
            case OK:
                return UIHelper.getThemeColor(context, R.attr.input_border_ok);
            case ERROR:
                return UIHelper.getThemeColor(context, R.attr.input_border_error);
            default:
                return UIHelper.getThemeColor(context, R.attr.input_border);
        }
    }

    private static void animateInputColors(Context context, GradientDrawable drawable, int startBg, int endBg,
                                           int startBorder, int endBorder) {
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                drawable::setColor);

        int borderWidth = (int) (2 * context.getResources().getDisplayMetrics().density);
        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder,
                color -> drawable.setStroke(borderWidth, color));

        bgAnimator.start();
        borderAnimator.start();
    }

    private static void animateCardColors(MaterialCardView card, int startBorder, int endBorder,
                                          int startBg, int endBg, boolean checked) {
        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder,
                card::setStrokeColor);

        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                card::setCardBackgroundColor);

        borderAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                card.setChecked(checked);
            }
        });

        borderAnimator.start();
        bgAnimator.start();
    }

    private static void animateButtonColors(Button button, int startBg, int endBg, int startText, int endText) {
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                button::setBackgroundColor);

        ValueAnimator textAnimator = createColorAnimator(startText, endText,
                button::setTextColor);

        bgAnimator.start();
        textAnimator.start();
    }

    private static ValueAnimator createColorAnimator(int startColor, int endColor, ColorUpdateListener listener) {
        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        animator.addUpdateListener(anim -> listener.onColorUpdate((int) anim.getAnimatedValue()));
        animator.setDuration(animation_duration);
        return animator;
    }

    public static void animateAlpha(View v, float alpha) {
        v.animate().alpha(alpha).setDuration(animation_duration).start();
    }

    @FunctionalInterface
    private interface ColorUpdateListener {
        void onColorUpdate(int color);
    }
}
