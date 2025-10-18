package com.vintro.wsplanner;

import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.widget.EditText;

import com.google.android.material.card.MaterialCardView;

class AnimationHelper {
    static void animateInputBackground(Activity activity, EditText input, GetPlanWidgetConfigureActivity.InputState state, GetPlanWidgetConfigureActivity.InputState oldState) {
        Resources res = activity.getResources();
        GradientDrawable drawable = (GradientDrawable) input.getBackground().mutate();

        int startBgColor = drawable.getColor().getDefaultColor();
        int startBorderColor = getInputBorderColor(res, oldState, startBgColor);

        int endBgColor = input.isFocused() ?
                res.getColor(R.color.card_checked_background_dark) :
                res.getColor(R.color.input_bg_dark);

        int endBorderColor = getEndBorderColor(res, state, input.isFocused());

        if (startBgColor == endBgColor && startBorderColor == endBorderColor) {
            return;
        }

        animateColors(drawable, startBgColor, endBgColor, startBorderColor, endBorderColor, res);
    }

    static void animateCardSelection(Activity activity, MaterialCardView card, boolean checked) {
        Resources res = activity.getResources();

        int startBorderColor = card.isChecked() ?
                res.getColor(R.color.input_border_active_dark) :
                res.getColor(R.color.input_border_dark);
        int startBgColor = card.isChecked() ?
                res.getColor(R.color.card_checked_background_dark) :
                res.getColor(R.color.input_bg_dark);

        int endBorderColor = checked ?
                res.getColor(R.color.input_border_active_dark) :
                res.getColor(R.color.input_border_dark);
        int endBgColor = checked ?
                res.getColor(R.color.card_checked_background_dark) :
                res.getColor(R.color.input_bg_dark);

        if (startBorderColor == endBorderColor && startBgColor == endBgColor) {
            card.setChecked(checked);
            return;
        }

        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, checked);
    }

    private static int getInputBorderColor(Resources res, GetPlanWidgetConfigureActivity.InputState state, int bgColor) {
        switch (state) {
            case NORMAL:
                return bgColor == res.getColor(R.color.card_checked_background_dark) ?
                        res.getColor(R.color.input_border_active_dark) :
                        res.getColor(R.color.input_border_dark);
            case OK:
                return res.getColor(R.color.input_border_ok_dark);
            case ERROR:
                return res.getColor(R.color.input_border_error_dark);
            default:
                return res.getColor(R.color.input_border_dark);
        }
    }

    private static int getEndBorderColor(Resources res, GetPlanWidgetConfigureActivity.InputState state, boolean isFocused) {
        switch (state) {
            case NORMAL:
                return isFocused ?
                        res.getColor(R.color.input_border_active_dark) :
                        res.getColor(R.color.input_border_dark);
            case OK:
                return res.getColor(R.color.input_border_ok_dark);
            case ERROR:
                return res.getColor(R.color.input_border_error_dark);
            default:
                return res.getColor(R.color.input_border_dark);
        }
    }

    private static void animateColors(GradientDrawable drawable, int startBg, int endBg,
                                      int startBorder, int endBorder, Resources res) {
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                drawable::setColor);

        int borderWidth = (int) (2 * res.getDisplayMetrics().density);
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
        animator.setDuration(300);
        return animator;
    }

    @FunctionalInterface
    private interface ColorUpdateListener {
        void onColorUpdate(int color);
    }
}
