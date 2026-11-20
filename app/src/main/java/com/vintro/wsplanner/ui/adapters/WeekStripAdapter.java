package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.ui.helpers.UIHelper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// adapter for week-snapping horizontal ViewPager2 day strip
public class WeekStripAdapter extends RecyclerView.Adapter<WeekStripAdapter.WeekViewHolder> {

    public interface OnDayClickListener {
        void onDayClick(LocalDate date);
    }

    private final ScheduleRepository scheduleRepository;
    private final OnDayClickListener onDayClickListener;

    private final List<List<LocalDate>> weeks = new ArrayList<>();
    private final Map<LocalDate, Integer> dateToWeekIndex = new HashMap<>();

    private LocalDate today = LocalDate.now();
    private LocalDate selectedDate = LocalDate.now();

    private final DateTimeFormatter dayMonthFormatter = DateTimeFormatter.ofPattern("dd.MM");

    private final int cardBgColor;
    private final int cardBorderColor;
    private final int cardCheckedBgColor;
    private final int buttonBgColor;
    private final int buttonTextColor;
    private final int colorPrimary;
    private final int textPrimaryColor;
    private final int textSecondaryColor;
    private final int textMutedColor;
    private final int strokeDefaultPx;
    private final int strokeTodayPx;

    public WeekStripAdapter(Context context, ScheduleRepository scheduleRepository, OnDayClickListener listener) {
        this.scheduleRepository = scheduleRepository;
        this.onDayClickListener = listener;

        float density = context.getResources().getDisplayMetrics().density;
        this.strokeDefaultPx = Math.max(1, Math.round(1.0f * density));
        this.strokeTodayPx = Math.max(1, Math.round(1.5f * density));

        this.cardBgColor = UIHelper.getThemeColor(context, R.attr.card_bg);
        this.cardBorderColor = UIHelper.getThemeColor(context, R.attr.card_border);
        this.cardCheckedBgColor = UIHelper.getThemeColor(context, R.attr.card_checked_bg);
        this.buttonBgColor = UIHelper.getThemeColor(context, R.attr.button_bg);
        this.buttonTextColor = UIHelper.getThemeColor(context, R.attr.button_text);
        this.colorPrimary = UIHelper.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary);
        this.textPrimaryColor = UIHelper.getThemeColor(context, R.attr.app_text);
        this.textSecondaryColor = UIHelper.getThemeColor(context, R.attr.text_secondary);
        this.textMutedColor = UIHelper.getThemeColor(context, R.attr.text_muted);
    }

    public void submitDates(List<LocalDate> visibleDates, LocalDate todayDate, LocalDate selected) {
        this.today = todayDate != null ? todayDate : LocalDate.now();
        this.selectedDate = selected != null ? selected : this.today;

        weeks.clear();
        dateToWeekIndex.clear();

        if (visibleDates != null && !visibleDates.isEmpty()) {
            WeekFields isoWeeks = WeekFields.ISO;
            int currentWeekYear = Integer.MIN_VALUE;
            int currentWeekNum = Integer.MIN_VALUE;
            List<LocalDate> currentBucket = null;

            for (LocalDate date : visibleDates) {
                int wYear = date.get(isoWeeks.weekBasedYear());
                int wNum = date.get(isoWeeks.weekOfWeekBasedYear());
                if (currentBucket == null || wYear != currentWeekYear || wNum != currentWeekNum) {
                    currentBucket = new ArrayList<>();
                    weeks.add(currentBucket);
                    currentWeekYear = wYear;
                    currentWeekNum = wNum;
                }
                currentBucket.add(date);
                dateToWeekIndex.put(date, weeks.size() - 1);
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedDate(LocalDate newSelected) {
        if (newSelected == null || newSelected.equals(selectedDate)) return;
        LocalDate oldSelected = selectedDate;
        selectedDate = newSelected;

        Integer oldWeek = dateToWeekIndex.get(oldSelected);
        Integer newWeek = dateToWeekIndex.get(newSelected);
        if (oldWeek != null) {
            notifyItemChanged(oldWeek);
        }
        if (newWeek != null && !newWeek.equals(oldWeek)) {
            notifyItemChanged(newWeek);
        }
    }

    public int getWeekPositionForDate(LocalDate date) {
        if (date == null) return RecyclerView.NO_POSITION;
        Integer pos = dateToWeekIndex.get(date);
        return pos != null ? pos : RecyclerView.NO_POSITION;
    }

    @Nullable
    public List<LocalDate> getWeekAt(int position) {
        if (position < 0 || position >= weeks.size()) return null;
        return weeks.get(position);
    }

    @Override
    public int getItemCount() {
        return weeks.size();
    }

    @NonNull
    @Override
    public WeekViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_week_strip_page, parent, false);
        return new WeekViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WeekViewHolder holder, int position) {
        holder.bind(weeks.get(position));
    }

    private String formatShortWeekday(LocalDate date, Locale locale) {
        String name = date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale);
        if (name == null || name.isEmpty()) return "";
        name = name.replace(".", "").trim();
        if (name.isEmpty()) return "";
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1).toLowerCase(locale);
    }

    class WeekViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout rowContainer;

        WeekViewHolder(@NonNull View itemView) {
            super(itemView);
            rowContainer = itemView.findViewById(R.id.week_strip_row);
        }

        void bind(List<LocalDate> weekDates) {
            rowContainer.removeAllViews();
            if (weekDates == null || weekDates.isEmpty()) return;

            LayoutInflater inflater = LayoutInflater.from(rowContainer.getContext());
            Locale locale = Locale.getDefault();

            for (LocalDate date : weekDates) {
                View cardRoot = inflater.inflate(R.layout.item_day_strip_card, rowContainer, false);
                MaterialCardView card = cardRoot.findViewById(R.id.day_strip_card);
                TextView weekdayText = cardRoot.findViewById(R.id.day_strip_weekday);
                TextView numberText = cardRoot.findViewById(R.id.day_strip_number);
                View emptyDash = cardRoot.findViewById(R.id.day_strip_empty_dash);
                View[] dots = new View[]{
                        cardRoot.findViewById(R.id.day_strip_dot_1),
                        cardRoot.findViewById(R.id.day_strip_dot_2),
                        cardRoot.findViewById(R.id.day_strip_dot_3),
                        cardRoot.findViewById(R.id.day_strip_dot_4)
                };

                weekdayText.setText(formatShortWeekday(date, locale));
                numberText.setText(date.format(dayMonthFormatter));

                int lessonCount = scheduleRepository != null ? scheduleRepository.getLessonCountForDateFromMemory(date) : 0;
                int visibleDots = Math.min(lessonCount, dots.length);

                boolean isSelected = date.equals(selectedDate);
                boolean isToday = date.equals(today);

                int dotColor;
                int dashColor;
                float dashAlpha;
                if (isSelected) {
                    card.setCardBackgroundColor(buttonBgColor);
                    card.setStrokeWidth(0);
                    card.setStrokeColor(buttonBgColor);
                    weekdayText.setTextColor(buttonTextColor);
                    numberText.setTextColor(buttonTextColor);
                    dotColor = buttonTextColor;
                    dashColor = buttonTextColor;
                    dashAlpha = 0.48f;
                } else if (isToday) {
                    card.setCardBackgroundColor(cardCheckedBgColor);
                    card.setStrokeWidth(strokeTodayPx);
                    card.setStrokeColor(colorPrimary);
                    weekdayText.setTextColor(colorPrimary);
                    numberText.setTextColor(colorPrimary);
                    dotColor = colorPrimary;
                    dashColor = colorPrimary;
                    dashAlpha = 0.38f;
                } else {
                    // keep uniform high contrast on all non-selected days; dots or dash indicate lessons
                    card.setCardBackgroundColor(cardBgColor);
                    card.setStrokeWidth(strokeDefaultPx);
                    card.setStrokeColor(cardBorderColor);
                    weekdayText.setTextColor(textSecondaryColor);
                    numberText.setTextColor(textPrimaryColor);
                    dotColor = colorPrimary;
                    dashColor = textMutedColor;
                    dashAlpha = 0.38f;
                }

                if (visibleDots == 0) {
                    if (emptyDash != null) {
                        emptyDash.setVisibility(View.VISIBLE);
                        emptyDash.setBackgroundTintList(ColorStateList.valueOf(dashColor));
                        emptyDash.setAlpha(dashAlpha);
                    }
                    for (View dot : dots) {
                        dot.setVisibility(View.GONE);
                    }
                } else {
                    if (emptyDash != null) {
                        emptyDash.setVisibility(View.GONE);
                    }
                    ColorStateList dotTint = ColorStateList.valueOf(dotColor);
                    for (int i = 0; i < dots.length; i++) {
                        if (i < visibleDots) {
                            dots[i].setVisibility(View.VISIBLE);
                            dots[i].setBackgroundTintList(dotTint);
                        } else {
                            dots[i].setVisibility(View.GONE);
                        }
                    }
                }

                card.setOnClickListener(v -> {
                    if (onDayClickListener != null) {
                        onDayClickListener.onDayClick(date);
                    }
                });

                rowContainer.addView(cardRoot);
            }
        }
    }
}
