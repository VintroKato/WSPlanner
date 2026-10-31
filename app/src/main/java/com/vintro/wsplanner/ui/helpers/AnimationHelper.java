package com.vintro.wsplanner.ui.helpers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.InputState;
import com.vintro.wsplanner.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class AnimationHelper {

    private static final int animation_duration = 500;
    private static final List<ValueAnimator> activeThemeAnimators = new ArrayList<>();

    public static synchronized void cancelActiveThemeAnimators() {
        for (ValueAnimator animator : new ArrayList<>(activeThemeAnimators)) {
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
        }
        activeThemeAnimators.clear();
    }

    private static synchronized void trackThemeAnimator(ValueAnimator animator) {
        if (animator != null) {
            activeThemeAnimators.add(animator);
        }
    }

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
        animateThemeChange(context, oldContext, context, root, null, null);
    }

    public static void animateThemeChange(Activity activity, Context oldContext, Context newContext, View root, Runnable onComplete) {
        animateThemeChange(activity, oldContext, newContext, root, null, onComplete);
    }

    public static void animateThemeChange(Activity activity, Context oldContext, Context newContext, View root,
                                          MaterialCardView newlySelectedCard, Runnable onComplete) {
        cancelActiveThemeAnimators();

        ValueAnimator masterAnimator = animateBgThemeChange(oldContext, newContext, root);

        if (root != null && root.getParent() instanceof View) {
            animateBgThemeChange(oldContext, newContext, (View) root.getParent());
        }
        if (activity != null && activity.getWindow() != null) {
            animateBgThemeChange(oldContext, newContext, activity.getWindow().getDecorView());
        }

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
            }
        }

        boolean isNewNight = UIHelper.isNightMode(newContext);
        UIHelper.setupSystemBars(activity, isNewNight);

        if (masterAnimator != null && onComplete != null) {
            masterAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onComplete.run();
                }
            });
        } else if (onComplete != null) {
            onComplete.run();
        }
    }

    private static void animateViewHierarchy(Context oldContext, Context newContext, View view, MaterialCardView newlySelectedCard) {
        if (view == null) return;

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            if (newlySelectedCard != null && card.getParent() == newlySelectedCard.getParent()) {
                if (card == newlySelectedCard) {
                    int startBorder = UIHelper.getThemeColor(oldContext, R.attr.card_border);
                    int endBorder = UIHelper.getThemeColor(newContext, R.attr.card_checked_border);
                    int startBg = UIHelper.getThemeColor(oldContext, R.attr.card_bg);
                    int endBg = UIHelper.getThemeColor(newContext, R.attr.card_checked_bg);
                    animateCardColors(card, startBorder, endBorder, startBg, endBg, true);
                } else if (card.isChecked()) {
                    int startBorder = UIHelper.getThemeColor(oldContext, R.attr.card_checked_border);
                    int endBorder = UIHelper.getThemeColor(newContext, R.attr.card_border);
                    int startBg = UIHelper.getThemeColor(oldContext, R.attr.card_checked_bg);
                    int endBg = UIHelper.getThemeColor(newContext, R.attr.card_bg);
                    animateCardColors(card, startBorder, endBorder, startBg, endBg, false);
                } else {
                    animateCardThemeChange(oldContext, newContext, card);
                }
            } else {
                animateCardThemeChange(oldContext, newContext, card);
            }
            for (int i = 0; i < card.getChildCount(); i++) {
                animateViewHierarchy(oldContext, newContext, card.getChildAt(i), newlySelectedCard);
            }
            return;
        }

        if (view instanceof MaterialButton) {
            MaterialButton mb = (MaterialButton) view;
            if (mb.getStrokeWidth() > 0) {
                animateSecondaryButtonThemeChange(oldContext, newContext, mb);
            } else {
                animateButtonThemeChange(oldContext, newContext, mb);
            }
            return;
        }

        if (view instanceof Button) {
            animateButtonThemeChange(oldContext, newContext, (Button) view);
            return;
        }

        if (view instanceof TextView) {
            animateTextThemeChange(oldContext, newContext, (TextView) view);
            return;
        }

        if (view instanceof ImageView) {
            animateImageViewTintThemeChange(oldContext, newContext, (ImageView) view);
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
            }
        }
    }

    private static void animateImageViewTintThemeChange(Context oldContext, Context newContext, ImageView iv) {
        if (iv.getId() == R.id.widget_card_arrow || iv.getImageTintList() != null) {
            int startColor = UIHelper.getThemeColor(oldContext, R.attr.app_text);
            int endColor = UIHelper.getThemeColor(newContext, R.attr.app_text);
            ValueAnimator animator = createColorAnimator(startColor, endColor, color -> iv.setImageTintList(ColorStateList.valueOf(color)));
            trackThemeAnimator(animator);
            animator.start();
        }
    }

    private static ValueAnimator animateBgThemeChange(Context oldContext, Context newContext, View root) {
        if (root == null) return null;
        int startColor = UIHelper.getThemeColor(oldContext, R.attr.app_bg);
        int endColor = UIHelper.getThemeColor(newContext, R.attr.app_bg);

        ValueAnimator animator = createColorAnimator(startColor, endColor, root::setBackgroundColor);
        trackThemeAnimator(animator);
        animator.start();
        return animator;
    }

    private static void animateCardThemeChange(Context oldContext, Context newContext, MaterialCardView card) {
        int startBgColor = card.isChecked() ?
                UIHelper.getThemeColor(oldContext, R.attr.card_checked_bg) :
                UIHelper.getThemeColor(oldContext, R.attr.card_bg);
        int startBorderColor = card.isChecked() ?
                UIHelper.getThemeColor(oldContext, R.attr.card_checked_border) :
                UIHelper.getThemeColor(oldContext, R.attr.card_border);

        int endBorderColor = card.isChecked() ?
                UIHelper.getThemeColor(newContext, R.attr.card_checked_border) :
                UIHelper.getThemeColor(newContext, R.attr.card_border);
        int endBgColor = card.isChecked() ?
                UIHelper.getThemeColor(newContext, R.attr.card_checked_bg) :
                UIHelper.getThemeColor(newContext, R.attr.card_bg);

        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, card.isChecked());
        if (card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView) {
            animateTextThemeChange(oldContext, newContext, (TextView) card.getChildAt(0));
        }
    }

    private static void animateTextThemeChange(Context oldContext, Context newContext, TextView text) {
        int startColor = UIHelper.getThemeColor(oldContext, R.attr.app_text);
        int endColor = UIHelper.getThemeColor(newContext, R.attr.app_text);

        ValueAnimator animator = createColorAnimator(startColor, endColor, text::setTextColor);
        trackThemeAnimator(animator);
        animator.start();
    }

    private static void animateButtonThemeChange(Context oldContext, Context newContext, Button button) {
        int startBgColor = UIHelper.getThemeColor(oldContext, R.attr.button_bg);
        int startTextColor = UIHelper.getThemeColor(oldContext, R.attr.button_text);

        int endBgColor = UIHelper.getThemeColor(newContext, R.attr.button_bg);
        int endTextColor = UIHelper.getThemeColor(newContext, R.attr.button_text);

        animateButtonColors(button, startBgColor, endBgColor, startTextColor, endTextColor);
    }

    private static void animateSecondaryButtonThemeChange(Context oldContext, Context newContext, MaterialButton button) {
        int startBg = UIHelper.getThemeColor(oldContext, R.attr.card_bg);
        int endBg = UIHelper.getThemeColor(newContext, R.attr.card_bg);
        int startStroke = UIHelper.getThemeColor(oldContext, R.attr.card_border);
        int endStroke = UIHelper.getThemeColor(newContext, R.attr.card_border);
        int startText = UIHelper.getThemeColor(oldContext, R.attr.app_text);
        int endText = UIHelper.getThemeColor(newContext, R.attr.app_text);

        ValueAnimator bgAnim = createColorAnimator(startBg, endBg, color -> button.setBackgroundTintList(ColorStateList.valueOf(color)));
        ValueAnimator strokeAnim = createColorAnimator(startStroke, endStroke, color -> button.setStrokeColor(ColorStateList.valueOf(color)));
        ValueAnimator textAnim = createColorAnimator(startText, endText, color -> {
            button.setTextColor(color);
            button.setIconTint(ColorStateList.valueOf(color));
        });

        trackThemeAnimator(bgAnim);
        trackThemeAnimator(strokeAnim);
        trackThemeAnimator(textAnim);

        bgAnim.start();
        strokeAnim.start();
        textAnim.start();
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
        card.setStrokeColor(startBorder);
        card.setCardBackgroundColor(startBg);
        card.setChecked(checked);

        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder,
                card::setStrokeColor);

        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg,
                card::setCardBackgroundColor);

        borderAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                card.setStrokeColor(endBorder);
            }
        });
        bgAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                card.setCardBackgroundColor(endBg);
            }
        });

        trackThemeAnimator(borderAnimator);
        trackThemeAnimator(bgAnimator);

        borderAnimator.start();
        bgAnimator.start();
    }

    private static void animateButtonColors(Button button, int startBg, int endBg, int startText, int endText) {
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg, color -> {
            if (button instanceof MaterialButton) {
                ((MaterialButton) button).setBackgroundTintList(ColorStateList.valueOf(color));
            } else {
                button.setBackgroundColor(color);
            }
        });

        ValueAnimator textAnimator = createColorAnimator(startText, endText,
                button::setTextColor);

        trackThemeAnimator(bgAnimator);
        trackThemeAnimator(textAnimator);

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
