package com.vintro.wsplanner.ui.adapters;

import android.animation.ValueAnimator;
import android.content.Context;
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
import com.vintro.wsplanner.ui.helpers.AnimationHelper;
import com.vintro.wsplanner.ui.helpers.AnimationHelper.DayCardVisualState;
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

    private static final Object PAYLOAD_SELECTION = new Object();

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

    private final DayCardVisualState selectedState;
    private final DayCardVisualState todayState;
    private final DayCardVisualState normalState;

    public WeekStripAdapter(Context context, ScheduleRepository scheduleRepository, OnDayClickListener listener) {
        this.scheduleRepository = scheduleRepository;
        this.onDayClickListener = listener;

        float density = context.getResources().getDisplayMetrics().density;
        int strokeDefaultPx = Math.max(1, Math.round(1.0f * density));
        int strokeTodayPx = Math.max(1, Math.round(1.5f * density));

        int cardBgColor = UIHelper.getThemeColor(context, R.attr.card_bg);
        int cardBorderColor = UIHelper.getThemeColor(context, R.attr.card_border);
        int cardCheckedBgColor = UIHelper.getThemeColor(context, R.attr.card_checked_bg);
        int buttonBgColor = UIHelper.getThemeColor(context, R.attr.button_bg);
        int buttonTextColor = UIHelper.getThemeColor(context, R.attr.button_text);
        int colorPrimary = UIHelper.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary);
        int textPrimaryColor = UIHelper.getThemeColor(context, R.attr.app_text);
        int textSecondaryColor = UIHelper.getThemeColor(context, R.attr.text_secondary);
        int textMutedColor = UIHelper.getThemeColor(context, R.attr.text_muted);

        this.selectedState = new DayCardVisualState(
                buttonBgColor, buttonBgColor, 0,
                buttonTextColor, buttonTextColor,
                buttonTextColor, buttonTextColor, 0.48f
        );
        this.todayState = new DayCardVisualState(
                cardCheckedBgColor, colorPrimary, strokeTodayPx,
                colorPrimary, colorPrimary,
                colorPrimary, colorPrimary, 0.38f
        );
        this.normalState = new DayCardVisualState(
                cardBgColor, cardBorderColor, strokeDefaultPx,
                textSecondaryColor, textPrimaryColor,
                colorPrimary, textMutedColor, 0.38f
        );
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
            notifyItemChanged(oldWeek, PAYLOAD_SELECTION);
        }
        if (newWeek != null && !newWeek.equals(oldWeek)) {
            notifyItemChanged(newWeek, PAYLOAD_SELECTION);
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

    @Override
    public void onBindViewHolder(@NonNull WeekViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_SELECTION)) {
            holder.updateSelection(weeks.get(position), true);
            return;
        }
        holder.bind(weeks.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull WeekViewHolder holder) {
        super.onViewRecycled(holder);
        holder.cancelAnimations();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationX(0f);
    }

    private DayCardVisualState resolveStateForDate(LocalDate date) {
        if (date.equals(selectedDate)) {
            return selectedState;
        }
        if (date.equals(today)) {
            return todayState;
        }
        return normalState;
    }

    private String formatShortWeekday(LocalDate date, Locale locale) {
        String name = date.getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale);
        if (name == null || name.isEmpty()) return "";
        name = name.replace(".", "").trim();
        if (name.isEmpty()) return "";
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1).toLowerCase(locale);
    }

    // holds view references and live animation state for a single day card slot
    private static final class DaySlotHolder {
        final LocalDate date;
        final MaterialCardView card;
        final TextView weekdayText;
        final TextView numberText;
        final View emptyDash;
        final View[] dots;
        DayCardVisualState currentState;
        DayCardVisualState targetState;
        ValueAnimator activeAnimator;

        DaySlotHolder(LocalDate date, View cardRoot) {
            this.date = date;
            this.card = cardRoot.findViewById(R.id.day_strip_card);
            this.weekdayText = cardRoot.findViewById(R.id.day_strip_weekday);
            this.numberText = cardRoot.findViewById(R.id.day_strip_number);
            this.emptyDash = cardRoot.findViewById(R.id.day_strip_empty_dash);
            this.dots = new View[]{
                    cardRoot.findViewById(R.id.day_strip_dot_1),
                    cardRoot.findViewById(R.id.day_strip_dot_2),
                    cardRoot.findViewById(R.id.day_strip_dot_3),
                    cardRoot.findViewById(R.id.day_strip_dot_4)
            };
        }

        void cancelAnimation() {
            if (activeAnimator != null) {
                activeAnimator.cancel();
                activeAnimator = null;
            }
            if (card != null) {
                card.animate().cancel();
                card.setScaleX(1f);
                card.setScaleY(1f);
            }
        }

        void applyImmediate(DayCardVisualState state) {
            cancelAnimation();
            currentState = state;
            targetState = state;
            state.applyTo(card, weekdayText, numberText, emptyDash, dots);
        }

        void bindLessonIndicators(int lessonCount) {
            int visibleDots = Math.min(lessonCount, dots.length);
            if (visibleDots == 0) {
                if (emptyDash != null) {
                    emptyDash.setVisibility(View.VISIBLE);
                }
                for (View dot : dots) {
                    dot.setVisibility(View.GONE);
                }
            } else {
                if (emptyDash != null) {
                    emptyDash.setVisibility(View.GONE);
                }
                for (int i = 0; i < dots.length; i++) {
                    dots[i].setVisibility(i < visibleDots ? View.VISIBLE : View.GONE);
                }
            }
        }
    }

    class WeekViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout rowContainer;
        private final List<LocalDate> boundDates = new ArrayList<>();
        private final List<DaySlotHolder> slotHolders = new ArrayList<>();

        WeekViewHolder(@NonNull View itemView) {
            super(itemView);
            rowContainer = itemView.findViewById(R.id.week_strip_row);
        }

        void cancelAnimations() {
            for (DaySlotHolder slot : slotHolders) {
                slot.cancelAnimation();
            }
        }

        void bind(List<LocalDate> weekDates) {
            // clear any stale page transformer alpha or translation from recycled offscreen pages
            itemView.setAlpha(1f);
            itemView.setTranslationX(0f);

            if (weekDates == null || weekDates.isEmpty()) {
                cancelAnimations();
                rowContainer.removeAllViews();
                boundDates.clear();
                slotHolders.clear();
                return;
            }

            // reuse existing day card views when the week structure has not changed
            if (boundDates.equals(weekDates) && slotHolders.size() == weekDates.size()) {
                for (DaySlotHolder slot : slotHolders) {
                    int lessonCount = scheduleRepository != null ? scheduleRepository.getLessonCountForDateFromMemory(slot.date) : 0;
                    slot.bindLessonIndicators(lessonCount);
                    slot.applyImmediate(resolveStateForDate(slot.date));
                }
                return;
            }

            cancelAnimations();
            rowContainer.removeAllViews();
            boundDates.clear();
            slotHolders.clear();

            LayoutInflater inflater = LayoutInflater.from(rowContainer.getContext());
            Locale locale = Locale.getDefault();

            for (LocalDate date : weekDates) {
                View cardRoot = inflater.inflate(R.layout.item_day_strip_card, rowContainer, false);
                DaySlotHolder slot = new DaySlotHolder(date, cardRoot);

                slot.weekdayText.setText(formatShortWeekday(date, locale));
                slot.numberText.setText(date.format(dayMonthFormatter));

                int lessonCount = scheduleRepository != null ? scheduleRepository.getLessonCountForDateFromMemory(date) : 0;
                slot.bindLessonIndicators(lessonCount);
                slot.applyImmediate(resolveStateForDate(date));

                slot.card.setOnClickListener(v -> {
                    if (onDayClickListener != null) {
                        onDayClickListener.onDayClick(date);
                    }
                });

                boundDates.add(date);
                slotHolders.add(slot);
                rowContainer.addView(cardRoot);
            }
        }

        void updateSelection(List<LocalDate> weekDates, boolean animate) {
            if (weekDates == null || !boundDates.equals(weekDates) || slotHolders.size() != weekDates.size()) {
                bind(weekDates);
                return;
            }

            for (DaySlotHolder slot : slotHolders) {
                DayCardVisualState target = resolveStateForDate(slot.date);
                if (slot.targetState != null && slot.targetState.visualEquals(target)) {
                    continue;
                }
                if (!animate || slot.currentState == null) {
                    slot.applyImmediate(target);
                    continue;
                }

                if (slot.activeAnimator != null) {
                    slot.activeAnimator.cancel();
                    slot.activeAnimator = null;
                }
                boolean isNowSelected = slot.date.equals(selectedDate);
                slot.targetState = target;
                slot.activeAnimator = AnimationHelper.animateDayStripCard(
                        slot.card,
                        slot.weekdayText,
                        slot.numberText,
                        slot.emptyDash,
                        slot.dots,
                        slot.currentState,
                        target,
                        isNowSelected,
                        liveState -> slot.currentState = liveState
                );
            }
        }
    }
}
