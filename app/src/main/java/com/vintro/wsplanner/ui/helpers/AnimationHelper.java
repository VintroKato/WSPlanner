package com.vintro.wsplanner.ui.helpers;

import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.ui.activities.GetPlanWidgetConfigureActivity;

public class AnimationHelper {
    public static void animateInputBackground(Activity activity, EditText input, GetPlanWidgetConfigureActivity.InputState state, GetPlanWidgetConfigureActivity.InputState oldState) {
        GradientDrawable drawable = (GradientDrawable) input.getBackground().mutate();

        int startBgColor = drawable.getColor().getDefaultColor();
        int startBorderColor = getInputBorderColor(activity, oldState, startBgColor);

        int endBgColor = input.isFocused() ?
                AnimationHelper.getThemeColor(activity, R.attr.input_bg_active) :
                AnimationHelper.getThemeColor(activity, R.attr.input_bg);

        int endBorderColor = getEndBorderColor(activity, state, input.isFocused());

        if (startBgColor == endBgColor && startBorderColor == endBorderColor) {
            return;
        }

        animateColors(activity, drawable, startBgColor, endBgColor, startBorderColor, endBorderColor);
    }

    public static void animateCardSelection(Activity activity, MaterialCardView card, boolean checked) {
        int startBorderColor = card.isChecked() ?
                AnimationHelper.getThemeColor(activity, R.attr.card_checked_border) :
                AnimationHelper.getThemeColor(activity, R.attr.card_border);
        int startBgColor = card.isChecked() ?
                AnimationHelper.getThemeColor(activity, R.attr.card_checked_bg) :
                AnimationHelper.getThemeColor(activity, R.attr.card_bg);

        int endBorderColor = checked ?
                AnimationHelper.getThemeColor(activity, R.attr.card_checked_border) :
                AnimationHelper.getThemeColor(activity, R.attr.card_border);
        int endBgColor = checked ?
                AnimationHelper.getThemeColor(activity, R.attr.card_checked_bg) :
                AnimationHelper.getThemeColor(activity, R.attr.card_bg);

        if (startBorderColor == endBorderColor && startBgColor == endBgColor) {
            card.setChecked(checked);
            return;
        }

        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, checked);
    }

    private static int getInputBorderColor(Context context, GetPlanWidgetConfigureActivity.InputState state, int bgColor) {
        switch (state) {
            case NORMAL:
                return bgColor == AnimationHelper.getThemeColor(context, R.attr.input_bg_active) ?
                        AnimationHelper.getThemeColor(context, R.attr.input_border_active) :
                        AnimationHelper.getThemeColor(context, R.attr.input_border);
            case OK:
                return AnimationHelper.getThemeColor(context, R.attr.input_border_ok);
            case ERROR:
                return AnimationHelper.getThemeColor(context, R.attr.input_border_error);
            default:
                return AnimationHelper.getThemeColor(context, R.attr.input_border);
        }
    }

    private static int getEndBorderColor(Context context, GetPlanWidgetConfigureActivity.InputState state, boolean isFocused) {
        switch (state) {
            case NORMAL:
                return isFocused ?
                        AnimationHelper.getThemeColor(context, R.attr.input_border_active) :
                        AnimationHelper.getThemeColor(context, R.attr.input_border);
            case OK:
                return AnimationHelper.getThemeColor(context, R.attr.input_border_ok);
            case ERROR:
                return AnimationHelper.getThemeColor(context, R.attr.input_border_error);
            default:
                return AnimationHelper.getThemeColor(context, R.attr.input_border);
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
        animator.setDuration(300);
        return animator;
    }

    public static void animateAlpha(View v, float alpha) {
        v.animate().alpha(alpha).setDuration(300).start();
    }

    public static int getThemeColor(Context context, int colorAttr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(colorAttr, typedValue, true);
        return typedValue.data;
    }

    @FunctionalInterface
    private interface ColorUpdateListener {
        void onColorUpdate(int color);
    }
}
