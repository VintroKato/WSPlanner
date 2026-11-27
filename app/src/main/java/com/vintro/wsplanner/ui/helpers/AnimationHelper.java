package com.vintro.wsplanner.ui.helpers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.InputState;

import java.util.ArrayList;
import java.util.List;

public class AnimationHelper {

    public static final String ROLE_TEXT_PRIMARY = "text_primary";
    public static final String ROLE_TEXT_SECONDARY = "text_secondary";
    public static final String ROLE_TEXT_MUTED = "text_muted";
    public static final String ROLE_PRIMARY = "primary";
    public static final String ROLE_DIVIDER = "divider";
    public static final String ROLE_SURFACE_RECESSED = "surface_recessed";
    public static final String ROLE_APP_BG = "app_bg";
    public static final String ROLE_BOTTOM_NAV_BAR = "bottom_nav_bar";
    public static final String ROLE_BOTTOM_NAV_PILL_ACTIVE = "bottom_nav_pill_active";
    public static final String ROLE_CARD_INPUT = "card_input";
    public static final String ROLE_SKIP = "skip_theme_anim";

    private static final int animation_duration = 500;
    private static final long DAY_CARD_ANIM_DURATION_MS = 240L;
    private static final long DAY_CARD_PULSE_DURATION_MS = 220L;
    private static final FastOutSlowInInterpolator fastOutSlowInInterpolator = new FastOutSlowInInterpolator();
    private static final List<ValueAnimator> activeThemeAnimators = new ArrayList<>();

    // immutable visual state snapshot for a weekly day strip card
    public static final class DayCardVisualState {
        public final int backgroundColor;
        public final int strokeColor;
        public final int strokeWidthPx;
        public final int weekdayTextColor;
        public final int numberTextColor;
        public final int dotColor;
        public final int dashColor;
        public final float dashAlpha;

        public DayCardVisualState(int backgroundColor, int strokeColor, int strokeWidthPx,
                                  int weekdayTextColor, int numberTextColor,
                                  int dotColor, int dashColor, float dashAlpha) {
            this.backgroundColor = backgroundColor;
            this.strokeColor = strokeColor;
            this.strokeWidthPx = strokeWidthPx;
            this.weekdayTextColor = weekdayTextColor;
            this.numberTextColor = numberTextColor;
            this.dotColor = dotColor;
            this.dashColor = dashColor;
            this.dashAlpha = dashAlpha;
        }

        public boolean visualEquals(DayCardVisualState other) {
            if (this == other) return true;
            if (other == null) return false;
            return backgroundColor == other.backgroundColor
                    && strokeColor == other.strokeColor
                    && strokeWidthPx == other.strokeWidthPx
                    && weekdayTextColor == other.weekdayTextColor
                    && numberTextColor == other.numberTextColor
                    && dotColor == other.dotColor
                    && dashColor == other.dashColor
                    && Float.compare(dashAlpha, other.dashAlpha) == 0;
        }

        public void applyTo(MaterialCardView card, TextView weekdayText, TextView numberText,
                            View emptyDash, View[] dots) {
            if (card != null) {
                card.setCardBackgroundColor(backgroundColor);
                card.setStrokeWidth(strokeWidthPx);
                card.setStrokeColor(strokeColor);
            }
            if (weekdayText != null) {
                weekdayText.setTextColor(weekdayTextColor);
            }
            if (numberText != null) {
                numberText.setTextColor(numberTextColor);
            }
            if (emptyDash != null && emptyDash.getVisibility() == View.VISIBLE) {
                emptyDash.setBackgroundTintList(ColorStateList.valueOf(dashColor));
                emptyDash.setAlpha(dashAlpha);
            }
            if (dots != null) {
                ColorStateList dotTint = ColorStateList.valueOf(dotColor);
                for (View dot : dots) {
                    if (dot != null && dot.getVisibility() == View.VISIBLE) {
                        dot.setBackgroundTintList(dotTint);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface DayCardStateListener {
        void onStateUpdated(DayCardVisualState state);
    }

    // smoothly morph a day strip card between two visual states using a single animator
    public static ValueAnimator animateDayStripCard(MaterialCardView card,
                                                    TextView weekdayText,
                                                    TextView numberText,
                                                    View emptyDash,
                                                    View[] dots,
                                                    DayCardVisualState from,
                                                    DayCardVisualState to,
                                                    boolean pulseScale,
                                                    DayCardStateListener stateListener) {
        if (card == null || from == null || to == null) {
            return null;
        }
        ArgbEvaluator evaluator = new ArgbEvaluator();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DAY_CARD_ANIM_DURATION_MS);
        animator.setInterpolator(fastOutSlowInInterpolator);
        animator.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();
            int bg = (int) evaluator.evaluate(f, from.backgroundColor, to.backgroundColor);
            int strokeColor = (int) evaluator.evaluate(f, from.strokeColor, to.strokeColor);
            int strokeWidth = Math.round(from.strokeWidthPx + (to.strokeWidthPx - from.strokeWidthPx) * f);
            int weekdayColor = (int) evaluator.evaluate(f, from.weekdayTextColor, to.weekdayTextColor);
            int numberColor = (int) evaluator.evaluate(f, from.numberTextColor, to.numberTextColor);
            int dotColor = (int) evaluator.evaluate(f, from.dotColor, to.dotColor);
            int dashColor = (int) evaluator.evaluate(f, from.dashColor, to.dashColor);
            float dashAlpha = from.dashAlpha + (to.dashAlpha - from.dashAlpha) * f;

            DayCardVisualState frameState = new DayCardVisualState(
                    bg, strokeColor, strokeWidth, weekdayColor, numberColor, dotColor, dashColor, dashAlpha
            );
            frameState.applyTo(card, weekdayText, numberText, emptyDash, dots);
            if (stateListener != null) {
                stateListener.onStateUpdated(frameState);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    to.applyTo(card, weekdayText, numberText, emptyDash, dots);
                    if (stateListener != null) {
                        stateListener.onStateUpdated(to);
                    }
                }
            }
        });
        animator.start();

        if (pulseScale) {
            card.animate().cancel();
            card.setScaleX(0.96f);
            card.setScaleY(0.96f);
            card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(DAY_CARD_PULSE_DURATION_MS)
                    .setInterpolator(fastOutSlowInInterpolator)
                    .start();
        }
        return animator;
    }

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
        Drawable background = input.getBackground();
        if (!(background instanceof GradientDrawable)) {
            return;
        }
        GradientDrawable drawable = (GradientDrawable) background.mutate();
        if (drawable.getColor() == null) {
            return;
        }

        int startBgColor = drawable.getColor().getDefaultColor();
        int startBorderColor = getInputBorderColor(context, oldState, startBgColor);

        boolean isCardInput = ROLE_CARD_INPUT.equals(getRoleTag(input));
        int unfocusedBgAttr = isCardInput ? R.attr.surface_recessed : R.attr.input_bg;
        int endBgColor = input.isFocused()
                ? UIHelper.getThemeColor(context, R.attr.input_bg_active)
                : UIHelper.getThemeColor(context, unfocusedBgAttr);

        int endBorderColor = getEndBorderColor(context, state, input.isFocused());

        if (startBgColor == endBgColor && startBorderColor == endBorderColor) {
            return;
        }

        float strokeDp = input.isFocused() ? 1.5f : 1.0f;
        animateInputColors(context, drawable, startBgColor, endBgColor, startBorderColor, endBorderColor, strokeDp);
    }

    public static void animateCardSelection(Context context, MaterialCardView card, boolean checked) {
        if (card.isChecked() == checked) {
            return;
        }
        int startBorderColor = checked
                ? UIHelper.getThemeColor(context, R.attr.card_border)
                : UIHelper.getThemeColor(context, R.attr.card_checked_border);
        int startBgColor = checked
                ? UIHelper.getThemeColor(context, R.attr.card_bg)
                : UIHelper.getThemeColor(context, R.attr.card_checked_bg);

        int endBorderColor = checked
                ? UIHelper.getThemeColor(context, R.attr.card_checked_border)
                : UIHelper.getThemeColor(context, R.attr.card_border);
        int endBgColor = checked
                ? UIHelper.getThemeColor(context, R.attr.card_checked_bg)
                : UIHelper.getThemeColor(context, R.attr.card_bg);

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
                private boolean cancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        onComplete.run();
                    }
                }
            });
        } else if (onComplete != null) {
            onComplete.run();
        }
    }

    private static String getRoleTag(View view) {
        Object tag = view.getTag();
        return tag instanceof String ? (String) tag : null;
    }

    private static int resolveRoleColorAttr(String role) {
        if (ROLE_TEXT_SECONDARY.equals(role)) {
            return R.attr.text_secondary;
        }
        if (ROLE_TEXT_MUTED.equals(role)) {
            return R.attr.text_muted;
        }
        if (ROLE_PRIMARY.equals(role)) {
            return androidx.appcompat.R.attr.colorPrimary;
        }
        return R.attr.app_text;
    }

    private static void animateViewHierarchy(Context oldContext, Context newContext, View view, MaterialCardView newlySelectedCard) {
        if (view == null) return;

        String role = getRoleTag(view);
        if (ROLE_SKIP.equals(role)) {
            return;
        }

        // skip hidden fragment roots inside fragment_container to avoid animating offscreen views
        if (view.getVisibility() == View.GONE && view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            if (parent.getId() == R.id.fragment_container) {
                return;
            }
        }

        if (ROLE_APP_BG.equals(role)) {
            animateSolidBgChange(oldContext, newContext, view, R.attr.app_bg);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
                }
            }
            return;
        }

        if (ROLE_DIVIDER.equals(role)) {
            animateSolidBgChange(oldContext, newContext, view, R.attr.divider_color);
            return;
        }

        if (ROLE_BOTTOM_NAV_BAR.equals(role)) {
            animateSolidBgChange(oldContext, newContext, view, R.attr.bottom_nav_bg);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
                }
            }
            return;
        }

        if (ROLE_BOTTOM_NAV_PILL_ACTIVE.equals(role)) {
            animateSurfaceBackgroundChange(oldContext, newContext, view, R.attr.bottom_nav_indicator);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
                }
            }
            return;
        }

        if (ROLE_SURFACE_RECESSED.equals(role)) {
            animateSurfaceBackgroundChange(oldContext, newContext, view, R.attr.surface_recessed);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
                }
            }
            return;
        }

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
                animateSecondaryButtonThemeChange(oldContext, newContext, mb, role);
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
            animateTextThemeChange(oldContext, newContext, (TextView) view, role);
            return;
        }

        if (view instanceof ImageView) {
            animateImageViewTintThemeChange(oldContext, newContext, (ImageView) view, role);
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                animateViewHierarchy(oldContext, newContext, group.getChildAt(i), newlySelectedCard);
            }
        }
    }

    private static void animateSolidBgChange(Context oldContext, Context newContext, View view, int attr) {
        int startColor = UIHelper.getThemeColor(oldContext, attr);
        int endColor = UIHelper.getThemeColor(newContext, attr);
        ValueAnimator animator = createColorAnimator(startColor, endColor, view::setBackgroundColor);
        trackThemeAnimator(animator);
        animator.start();
    }

    private static void animateSurfaceBackgroundChange(Context oldContext, Context newContext, View view, int attr) {
        int startColor = UIHelper.getThemeColor(oldContext, attr);
        int endColor = UIHelper.getThemeColor(newContext, attr);
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            GradientDrawable mutable = (GradientDrawable) bg.mutate();
            ValueAnimator animator = createColorAnimator(startColor, endColor, mutable::setColor);
            trackThemeAnimator(animator);
            animator.start();
        } else {
            ValueAnimator animator = createColorAnimator(startColor, endColor, view::setBackgroundColor);
            trackThemeAnimator(animator);
            animator.start();
        }
    }

    private static void animateImageViewTintThemeChange(Context oldContext, Context newContext, ImageView iv, String role) {
        if (role != null || iv.getImageTintList() != null) {
            int attr = resolveRoleColorAttr(role);
            int startColor = UIHelper.getThemeColor(oldContext, attr);
            int endColor = UIHelper.getThemeColor(newContext, attr);
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
        boolean isChecked = card.isCheckable() && card.isChecked();
        int startBgColor = isChecked
                ? UIHelper.getThemeColor(oldContext, R.attr.card_checked_bg)
                : UIHelper.getThemeColor(oldContext, R.attr.card_bg);
        int startBorderColor = isChecked
                ? UIHelper.getThemeColor(oldContext, R.attr.card_checked_border)
                : UIHelper.getThemeColor(oldContext, R.attr.card_border);

        int endBorderColor = isChecked
                ? UIHelper.getThemeColor(newContext, R.attr.card_checked_border)
                : UIHelper.getThemeColor(newContext, R.attr.card_border);
        int endBgColor = isChecked
                ? UIHelper.getThemeColor(newContext, R.attr.card_checked_bg)
                : UIHelper.getThemeColor(newContext, R.attr.card_bg);

        animateCardColors(card, startBorderColor, endBorderColor, startBgColor, endBgColor, isChecked);
    }

    private static void animateTextThemeChange(Context oldContext, Context newContext, TextView text, String role) {
        int attr = resolveRoleColorAttr(role);
        int startColor = UIHelper.getThemeColor(oldContext, attr);
        int endColor = UIHelper.getThemeColor(newContext, attr);

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

    private static void animateSecondaryButtonThemeChange(Context oldContext, Context newContext, MaterialButton button, String role) {
        int textAttr = resolveRoleColorAttr(role);
        int startBg = UIHelper.getThemeColor(oldContext, R.attr.card_bg);
        int endBg = UIHelper.getThemeColor(newContext, R.attr.card_bg);
        int startStroke = UIHelper.getThemeColor(oldContext, R.attr.card_border);
        int endStroke = UIHelper.getThemeColor(newContext, R.attr.card_border);
        int startText = UIHelper.getThemeColor(oldContext, textAttr);
        int endText = UIHelper.getThemeColor(newContext, textAttr);

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
                return bgColor == UIHelper.getThemeColor(context, R.attr.input_bg_active)
                        ? UIHelper.getThemeColor(context, R.attr.input_border_active)
                        : UIHelper.getThemeColor(context, R.attr.input_border);
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
                return isFocused
                        ? UIHelper.getThemeColor(context, R.attr.input_border_active)
                        : UIHelper.getThemeColor(context, R.attr.input_border);
            case OK:
                return UIHelper.getThemeColor(context, R.attr.input_border_ok);
            case ERROR:
                return UIHelper.getThemeColor(context, R.attr.input_border_error);
            default:
                return UIHelper.getThemeColor(context, R.attr.input_border);
        }
    }

    private static void animateInputColors(Context context, GradientDrawable drawable, int startBg, int endBg,
                                           int startBorder, int endBorder, float strokeDp) {
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg, drawable::setColor);

        int borderWidth = Math.max(1, Math.round(strokeDp * context.getResources().getDisplayMetrics().density));
        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder,
                color -> drawable.setStroke(borderWidth, color));

        bgAnimator.start();
        borderAnimator.start();
    }

    private static void animateCardColors(MaterialCardView card, int startBorder, int endBorder,
                                          int startBg, int endBg, boolean checked) {
        card.setStrokeColor(startBorder);
        card.setCardBackgroundColor(startBg);
        if (card.isCheckable()) {
            card.setChecked(checked);
            float density = card.getResources().getDisplayMetrics().density;
            int strokePx = Math.max(1, Math.round((checked ? 1.5f : 1.0f) * density));
            card.setStrokeWidth(strokePx);
        }

        ValueAnimator borderAnimator = createColorAnimator(startBorder, endBorder, card::setStrokeColor);
        ValueAnimator bgAnimator = createColorAnimator(startBg, endBg, card::setCardBackgroundColor);

        borderAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    card.setStrokeColor(endBorder);
                }
            }
        });
        bgAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    card.setCardBackgroundColor(endBg);
                }
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

        ValueAnimator textAnimator = createColorAnimator(startText, endText, button::setTextColor);

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
