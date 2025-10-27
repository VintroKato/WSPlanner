package com.vintro.wsplanner.ui.helpers;

import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.InputState;

public class AnimationHelper {

    private static final int animation_duration = 500;

    public static void animateInputBackground(Context context, EditText input, InputState state, InputState oldState) {
        GradientDrawable drawable = (GradientDrawable) input.getBackground().mutate();

        int startBgColor = input.isFocused() ?
                UIHelper.getThemeColor(context, R.attr.input_bg_active) :
                UIHelper.getThemeColor(context, R.attr.input_bg);
        int startBorderColor = getInputBorderColor(context, oldState, startBgColor);

        int endBgColor = input.isFocused() ?
                UIHelper.getThemeColor(context, R.attr.input_bg_active) :
                UIHelper.getThemeColor(context, R.attr.input_bg);

        int endBorderColor = getEndBorderColor(context, state, input.isFocused());

        if (startBgColor == endBgColor && startBorderColor == endBorderColor) {
            return;
        }

        animateColors(context, drawable, startBgColor, endBgColor, startBorderColor, endBorderColor);
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

    private static void animateColors(Context context, GradientDrawable drawable, int startBg, int endBg,
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
