package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.ui.helpers.UIHelper;
import com.vintro.wsplanner.ui.widgets.WidgetActionHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// adapter for day schedule items
public class ScheduleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnEmptyActionListener {
        void onJumpToNextClassDay(LocalDate nextClassDate);
        void onRetryLoad();
    }

    private final Context context;
    private final List<ScheduleItem> items = new ArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final int defaultStrokeWidthPx;
    private final int defaultCardBgColor;
    private final int nowCardBgColor;
    private final int defaultCardBorderColor;
    private final int stripeActiveColor;
    private final int stripePastColor;
    private final int bannerNowBgColor;
    private final int bannerNextBgColor;
    private final int textPrimaryColor;
    private final int textSecondaryColor;
    private final int textMutedColor;
    private final int badgeNowTextColor;
    private final int badgeNextTextColor;
    private final int badgePuwTextColor;
    private final int badgeFieldTextColor;
    private final int badgeWspaTextColor;

    private DaySchedule currentDaySchedule;
    @Nullable
    private OnEmptyActionListener emptyActionListener;

    public ScheduleAdapter(Context context) {
        this.context = context;
        float density = context.getResources().getDisplayMetrics().density;
        this.defaultStrokeWidthPx = Math.max(1, Math.round(1.0f * density));
        this.defaultCardBgColor = UIHelper.getThemeColor(context, R.attr.card_bg);
        this.nowCardBgColor = UIHelper.getThemeColor(context, R.attr.card_now_bg);
        this.defaultCardBorderColor = UIHelper.getThemeColor(context, R.attr.card_border);
        this.stripeActiveColor = UIHelper.getThemeColor(context, R.attr.stripe_active);
        this.stripePastColor = UIHelper.getThemeColor(context, R.attr.stripe_past);
        this.bannerNowBgColor = UIHelper.getThemeColor(context, R.attr.banner_now_bg);
        this.bannerNextBgColor = UIHelper.getThemeColor(context, R.attr.banner_next_bg);
        this.textPrimaryColor = UIHelper.getThemeColor(context, R.attr.app_text);
        this.textSecondaryColor = UIHelper.getThemeColor(context, R.attr.text_secondary);
        this.textMutedColor = UIHelper.getThemeColor(context, R.attr.text_muted);
        this.badgeNowTextColor = UIHelper.getThemeColor(context, R.attr.badge_now_text);
        this.badgeNextTextColor = UIHelper.getThemeColor(context, R.attr.badge_next_text);
        this.badgePuwTextColor = UIHelper.getThemeColor(context, R.attr.badge_puw_text);
        this.badgeFieldTextColor = UIHelper.getThemeColor(context, R.attr.badge_field_text);
        this.badgeWspaTextColor = UIHelper.getThemeColor(context, R.attr.badge_wspa_text);
    }

    public void setEmptyActionListener(@Nullable OnEmptyActionListener listener) {
        this.emptyActionListener = listener;
    }

    public void clear() {
        items.clear();
        currentDaySchedule = null;
        notifyDataSetChanged();
    }

    public void showNoInternetState() {
        items.clear();
        currentDaySchedule = null;
        items.add(ScheduleItem.createNoInternet(
                context.getString(R.string.no_internet_title),
                context.getString(R.string.no_internet_schedule_desc)
        ));
        notifyDataSetChanged();
    }

    public void submitDaySchedule(DaySchedule daySchedule, @Nullable LocalDate nextClassDate) {
        this.currentDaySchedule = daySchedule;
        items.clear();
        if (daySchedule == null || daySchedule.isEmpty()) {
            items.add(ScheduleItem.createEmpty(nextClassDate));
            notifyDataSetChanged();
            return;
        }

        List<Lesson> lessons = daySchedule.getLessons();
        for (int i = 0; i < lessons.size(); i++) {
            Lesson current = lessons.get(i);
            items.add(ScheduleItem.createLesson(current));

            if (i < lessons.size() - 1) {
                Lesson next = lessons.get(i + 1);
                long gapMinutes = DaySchedule.getGapBetweenMinutes(current, next);
                if (gapMinutes >= 20) {
                    items.add(ScheduleItem.createBreakGap(gapMinutes));
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getItemType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ScheduleItem.TYPE_EMPTY) {
            View v = inflater.inflate(R.layout.item_schedule_empty, parent, false);
            return new EmptyViewHolder(v);
        } else if (viewType == ScheduleItem.TYPE_BREAK_GAP) {
            View v = inflater.inflate(R.layout.item_break_gap, parent, false);
            return new BreakGapViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_lesson_card, parent, false);
            return new LessonViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ScheduleItem item = items.get(position);
        if (holder instanceof LessonViewHolder) {
            ((LessonViewHolder) holder).bind(item.getLesson());
        } else if (holder instanceof BreakGapViewHolder) {
            ((BreakGapViewHolder) holder).bind(item.getGapMinutes());
        } else if (holder instanceof EmptyViewHolder) {
            ((EmptyViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static String formatDurationHoursMinutes(Context context, long totalMinutes) {
        long positiveMinutes = Math.max(1, totalMinutes);
        long hours = positiveMinutes / 60;
        long minutes = positiveMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return context.getString(R.string.hours_and_minutes_short, hours, minutes);
        } else if (hours > 0) {
            return context.getString(R.string.hours_short, hours);
        } else {
            return context.getString(R.string.minutes_short, minutes);
        }
    }

    class LessonViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final View statusStripe;
        private final View bannerStatusContainer;
        private final LinearLayout bannerNowLayout;
        private final TextView badgeNow;
        private final TextView timeLeftText;
        private final TextView lessonTimeRange;
        private final ImageView iconHasNote;
        private final TextView badgeLocation;
        private final TextView subjectName;
        private final View rowLessonType;
        private final TextView lessonTypeText;
        private final View rowTeacher;
        private final TextView teacherText;
        private final TextView locationText;
        private final ImageView offsiteArrowIcon;

        LessonViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.lesson_card);
            statusStripe = itemView.findViewById(R.id.lesson_status_stripe);
            bannerStatusContainer = itemView.findViewById(R.id.banner_status_container);
            bannerNowLayout = itemView.findViewById(R.id.banner_now_layout);
            badgeNow = itemView.findViewById(R.id.badge_now);
            timeLeftText = itemView.findViewById(R.id.time_left_text);
            lessonTimeRange = itemView.findViewById(R.id.lesson_time_range);
            iconHasNote = itemView.findViewById(R.id.icon_has_note);
            badgeLocation = itemView.findViewById(R.id.badge_location);
            subjectName = itemView.findViewById(R.id.subject_name);
            rowLessonType = itemView.findViewById(R.id.row_lesson_type);
            lessonTypeText = itemView.findViewById(R.id.lesson_type_text);
            rowTeacher = itemView.findViewById(R.id.row_teacher);
            teacherText = itemView.findViewById(R.id.teacher_text);
            locationText = itemView.findViewById(R.id.location_text);
            offsiteArrowIcon = itemView.findViewById(R.id.offsite_arrow_icon);
        }

        void bind(Lesson lesson) {
            if (lesson == null) return;
            LocalDateTime now = LocalDateTime.now();

            String start = lesson.getStartTime() != null ? lesson.getStartTime().format(timeFormatter) : "--:--";
            String end = lesson.getEndTime() != null ? lesson.getEndTime().format(timeFormatter) : "--:--";
            lessonTimeRange.setText(start + " – " + end);

            if (iconHasNote != null) {
                boolean hasNote = SubjectDetailsActivity.hasSavedNote(
                        context,
                        lesson.getSubjectName(),
                        lesson.getResolvedLessonType(),
                        lesson.getLessonType()
                );
                iconHasNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
            }

            subjectName.setText(lesson.getSubjectName());
            LessonType resolvedType = lesson.getResolvedLessonType();
            String rawType = lesson.getLessonType();
            String displayType = (resolvedType != null && resolvedType != LessonType.INNE)
                    ? resolvedType.getLocalizedDisplayName(context)
                    : (rawType != null && !rawType.trim().isEmpty() ? rawType.trim() : null);
            if (displayType != null && !displayType.trim().isEmpty()) {
                rowLessonType.setVisibility(View.VISIBLE);
                String formatted = displayType.substring(0, 1).toUpperCase(Locale.getDefault()) + displayType.substring(1);
                lessonTypeText.setText(formatted);
            } else {
                rowLessonType.setVisibility(View.GONE);
            }

            if (lesson.hasTeacher()) {
                rowTeacher.setVisibility(View.VISIBLE);
                teacherText.setText(lesson.getTeacherName().trim());
            } else {
                rowTeacher.setVisibility(View.GONE);
            }

            Location loc = lesson.getLocation();
            locationText.setText(loc != null ? loc.getDisplayText() : "—");
            setupLocationBadge(loc != null ? loc.getType() : LocationType.UCZELNIA);

            if (loc != null && loc.hasMapLink()) {
                offsiteArrowIcon.setVisibility(View.VISIBLE);
                View.OnClickListener mapClick = v -> {
                    String query = loc.getMapQueryUrl();
                    if (query != null) {
                        WidgetActionHelper.openLocationInMaps(context, query);
                    }
                };
                locationText.setOnClickListener(mapClick);
                offsiteArrowIcon.setOnClickListener(mapClick);
            } else {
                offsiteArrowIcon.setVisibility(View.GONE);
                locationText.setOnClickListener(null);
                offsiteArrowIcon.setOnClickListener(null);
            }

            // isPast dimming applies only to completed lessons on today's date
            boolean isToday = lesson.getDate() != null && lesson.getDate().equals(now.toLocalDate());
            boolean isPast = isToday && lesson.isPast(now);
            boolean isCurrent = isToday && lesson.isCurrent(now);

            Lesson activeLesson = (currentDaySchedule != null && isToday) ? currentDaySchedule.getCurrentLesson(now) : null;
            Lesson nextLesson = (currentDaySchedule != null && isToday && activeLesson == null) ? currentDaySchedule.getNextLesson(now) : null;
            boolean isNext = isToday && activeLesson == null && nextLesson != null && lesson.equals(nextLesson);

            // uniform 1dp neutral border avoids visual conflict with left status stripe
            cardView.setStrokeWidth(defaultStrokeWidthPx);
            cardView.setStrokeColor(defaultCardBorderColor);

            if (isCurrent) {
                statusStripe.setBackgroundColor(stripeActiveColor);
                cardView.setCardBackgroundColor(nowCardBgColor);

                bannerStatusContainer.setVisibility(View.VISIBLE);
                bannerNowLayout.setBackgroundColor(bannerNowBgColor);

                badgeNow.setBackgroundTintList(null);
                badgeNow.setText(context.getString(R.string.schedule_badge_now));
                badgeNow.setBackgroundResource(R.drawable.bg_badge_now);
                badgeNow.setTextColor(badgeNowTextColor);

                long minutesLeft = lesson.getMinutesUntilEnd(now);
                String formattedDuration = formatDurationHoursMinutes(context, minutesLeft);
                timeLeftText.setText(context.getString(R.string.schedule_time_left, formattedDuration));
                timeLeftText.setTextColor(badgeNowTextColor);

                applyTextColors(false);
            } else if (isNext) {
                statusStripe.setBackgroundColor(stripeActiveColor);
                cardView.setCardBackgroundColor(defaultCardBgColor);

                bannerStatusContainer.setVisibility(View.VISIBLE);
                bannerNowLayout.setBackgroundColor(bannerNextBgColor);

                badgeNow.setBackgroundTintList(null);
                badgeNow.setText(context.getString(R.string.schedule_badge_next));
                badgeNow.setBackgroundResource(R.drawable.bg_badge_next);
                badgeNow.setTextColor(badgeNextTextColor);

                long minutesUntilStart = lesson.getMinutesUntilStart(now);
                String formattedDuration = formatDurationHoursMinutes(context, minutesUntilStart);
                timeLeftText.setText(context.getString(R.string.schedule_time_until, formattedDuration));
                timeLeftText.setTextColor(badgeNextTextColor);

                applyTextColors(false);
            } else if (isPast) {
                statusStripe.setBackgroundColor(stripePastColor);
                cardView.setCardBackgroundColor(defaultCardBgColor);
                bannerStatusContainer.setVisibility(View.GONE);
                applyTextColors(true);
            } else {
                statusStripe.setBackgroundColor(stripeActiveColor);
                cardView.setCardBackgroundColor(defaultCardBgColor);
                bannerStatusContainer.setVisibility(View.GONE);
                applyTextColors(false);
            }

            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(context, SubjectDetailsActivity.class);
                intent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
                intent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
                intent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_ID, lesson.getId());
                context.startActivity(intent);
            });
        }

        private void applyTextColors(boolean isPastToday) {
            int titleColor = isPastToday ? textSecondaryColor : textPrimaryColor;
            int metaColor = isPastToday ? textMutedColor : textSecondaryColor;
            lessonTimeRange.setTextColor(titleColor);
            subjectName.setTextColor(titleColor);
            lessonTypeText.setTextColor(metaColor);
            teacherText.setTextColor(metaColor);
            locationText.setTextColor(metaColor);
        }

        private void setupLocationBadge(LocationType type) {
            badgeLocation.setText(context.getString(type.getStringResId()).toUpperCase(Locale.getDefault()));
            badgeLocation.setBackgroundTintList(null);
            switch (type) {
                case ONLINE:
                    badgeLocation.setBackgroundResource(R.drawable.bg_badge_puw);
                    badgeLocation.setTextColor(badgePuwTextColor);
                    break;
                case W_TERENIE:
                    badgeLocation.setBackgroundResource(R.drawable.bg_badge_field);
                    badgeLocation.setTextColor(badgeFieldTextColor);
                    break;
                case UCZELNIA:
                default:
                    badgeLocation.setBackgroundResource(R.drawable.bg_badge_wspa);
                    badgeLocation.setTextColor(badgeWspaTextColor);
                    break;
            }
        }
    }

    class BreakGapViewHolder extends RecyclerView.ViewHolder {
        private final TextView gapText;

        BreakGapViewHolder(@NonNull View itemView) {
            super(itemView);
            gapText = itemView.findViewById(R.id.gap_text);
        }

        void bind(long minutes) {
            String durationStr = formatDurationHoursMinutes(context, minutes);
            String label = context.getString(R.string.schedule_break_gap) + " · " + durationStr;
            gapText.setText(label);
        }
    }

    class EmptyViewHolder extends RecyclerView.ViewHolder {
        private final ImageView emptyIcon;
        private final TextView emptyTitle;
        private final TextView emptySubtitle;
        private final MaterialButton actionButton;

        EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
            emptyIcon = itemView.findViewById(R.id.empty_icon);
            emptyTitle = itemView.findViewById(R.id.empty_title);
            emptySubtitle = itemView.findViewById(R.id.empty_subtitle);
            actionButton = itemView.findViewById(R.id.empty_action_button);
        }

        void bind(ScheduleItem item) {
            boolean isNoInternet = item != null && item.isNoInternet();

            if (emptyIcon != null) {
                emptyIcon.setImageResource(isNoInternet ? R.drawable.ic_wifi_off : R.drawable.ic_empty_schedule);
            }

            if (emptyTitle != null) {
                if (item != null && item.getEmptyTitle() != null) {
                    emptyTitle.setText(item.getEmptyTitle());
                } else {
                    emptyTitle.setText(R.string.schedule_empty_title);
                }
            }

            if (emptySubtitle != null) {
                if (item != null && item.getEmptySubtitle() != null) {
                    emptySubtitle.setText(item.getEmptySubtitle());
                } else {
                    emptySubtitle.setText(R.string.schedule_empty_subtitle);
                }
            }

            if (actionButton == null) return;

            if (isNoInternet) {
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.common_retry);
                actionButton.setIconResource(R.drawable.ic_refresh);
                actionButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
                actionButton.setOnClickListener(v -> {
                    if (emptyActionListener != null) {
                        emptyActionListener.onRetryLoad();
                    }
                });
            } else if (item != null && item.getNextClassDate() != null) {
                LocalDate targetDate = item.getNextClassDate();
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText(R.string.schedule_jump_next_day);
                actionButton.setIconResource(R.drawable.ic_arrow_forward);
                actionButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
                actionButton.setOnClickListener(v -> {
                    if (emptyActionListener != null) {
                        emptyActionListener.onJumpToNextClassDay(targetDate);
                    }
                });
            } else {
                actionButton.setVisibility(View.GONE);
                actionButton.setOnClickListener(null);
            }
        }
    }
}
