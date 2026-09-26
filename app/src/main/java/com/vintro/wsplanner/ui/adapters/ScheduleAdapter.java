package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// adapter for day schedule
public class ScheduleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final Context context;
    private final List<ScheduleItem> items = new ArrayList<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private DaySchedule currentDaySchedule;

    public ScheduleAdapter(Context context) {
        this.context = context;
    }

    // clear items
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

    // load day schedule and insert breaks
    public void submitDaySchedule(DaySchedule daySchedule) {
        this.currentDaySchedule = daySchedule;
        items.clear();
        if (daySchedule == null || daySchedule.isEmpty()) {
            items.add(ScheduleItem.createEmpty());
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
                if (gapMinutes >= 45) {
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

    // view holders

    class LessonViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final LinearLayout bannerNowLayout;
        private final TextView badgeNow;
        private final TextView timeLeftText;
        private final TextView lessonTimeRange;
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
            cardView = (MaterialCardView) itemView.findViewById(R.id.lesson_card);
            bannerNowLayout = itemView.findViewById(R.id.banner_now_layout);
            badgeNow = itemView.findViewById(R.id.badge_now);
            timeLeftText = itemView.findViewById(R.id.time_left_text);
            lessonTimeRange = itemView.findViewById(R.id.lesson_time_range);
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

            // time range
            String start = lesson.getStartTime() != null ? lesson.getStartTime().format(timeFormatter) : "--:--";
            String end = lesson.getEndTime() != null ? lesson.getEndTime().format(timeFormatter) : "--:--";
            lessonTimeRange.setText(start + " – " + end);

            // subject and teacher info
            subjectName.setText(lesson.getSubjectName());
            String rawType = lesson.getLessonType();
            if (rawType != null && !rawType.trim().isEmpty()) {
                if (rowLessonType != null) rowLessonType.setVisibility(View.VISIBLE);
                String formatted = rawType.substring(0, 1).toUpperCase(java.util.Locale.getDefault()) + rawType.substring(1);
                lessonTypeText.setText(formatted);
            } else {
                if (rowLessonType != null) rowLessonType.setVisibility(View.GONE);
            }

            if (lesson.hasTeacher()) {
                if (rowTeacher != null) rowTeacher.setVisibility(View.VISIBLE);
                teacherText.setText(lesson.getTeacherName().trim());
            } else {
                if (rowTeacher != null) rowTeacher.setVisibility(View.GONE);
            }

            // location
            Location loc = lesson.getLocation();
            locationText.setText(loc != null ? loc.getDisplayText() : "—");

            // location badge
            setupLocationBadge(loc != null ? loc.getType() : LocationType.UCZELNIA);

            // open in maps if link available
            if (loc != null && loc.hasMapLink()) {
                offsiteArrowIcon.setVisibility(View.VISIBLE);
                View.OnClickListener mapClick = v -> {
                    String query = loc.getMapQueryUrl();
                    if (query != null) {
                        try {
                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(query));
                            context.startActivity(mapIntent);
                        } catch (Exception ignored) {}
                    }
                };
                locationText.setOnClickListener(mapClick);
                offsiteArrowIcon.setOnClickListener(mapClick);
            } else {
                offsiteArrowIcon.setVisibility(View.GONE);
                locationText.setOnClickListener(null);
                offsiteArrowIcon.setOnClickListener(null);
            }

            // card state: current, next, past, or upcoming
            boolean isToday = lesson.getDate() != null && lesson.getDate().equals(now.toLocalDate());
            boolean isPast = isToday && lesson.isPast(now);
            boolean isCurrent = isToday && lesson.isCurrent(now);

            Lesson activeLesson = (currentDaySchedule != null && isToday) ? currentDaySchedule.getCurrentLesson(now) : null;
            Lesson nextLesson = (currentDaySchedule != null && isToday && activeLesson == null) ? currentDaySchedule.getNextLesson(now) : null;
            boolean isNext = isToday && activeLesson == null && nextLesson != null && lesson.equals(nextLesson);

            float density = context.getResources().getDisplayMetrics().density;

            if (isCurrent) {
                // current lesson
                cardView.setAlpha(1.0f);
                cardView.setStrokeWidth((int) (1.5f * density));
                cardView.setStrokeColor(ContextCompat.getColor(context, R.color.active_lesson_border));
                bannerNowLayout.setVisibility(View.VISIBLE);

                badgeNow.setBackgroundTintList(null);
                badgeNow.setText(context.getString(R.string.schedule_badge_now));
                badgeNow.setBackgroundResource(R.drawable.bg_badge_now);
                badgeNow.setTextColor(ContextCompat.getColor(context, R.color.badge_now_text));

                long minutesLeft = lesson.getMinutesUntilEnd(now);
                String formattedDuration = formatDurationHoursMinutes(context, minutesLeft);
                timeLeftText.setText(context.getString(R.string.schedule_time_left, formattedDuration));
                timeLeftText.setTextColor(ContextCompat.getColor(context, R.color.badge_now_text));
            } else if (isNext) {
                // next lesson
                cardView.setAlpha(1.0f);
                cardView.setStrokeWidth((int) (1.5f * density));
                cardView.setStrokeColor(ContextCompat.getColor(context, R.color.next_lesson_border));
                bannerNowLayout.setVisibility(View.VISIBLE);

                badgeNow.setBackgroundTintList(null);
                badgeNow.setText(context.getString(R.string.schedule_badge_next));
                badgeNow.setBackgroundResource(R.drawable.bg_badge_next);
                badgeNow.setTextColor(ContextCompat.getColor(context, R.color.badge_next_text));

                long minutesUntilStart = lesson.getMinutesUntilStart(now);
                String formattedDuration = formatDurationHoursMinutes(context, minutesUntilStart);
                timeLeftText.setText(context.getString(R.string.schedule_time_until, formattedDuration));
                timeLeftText.setTextColor(ContextCompat.getColor(context, R.color.badge_next_text));
            } else if (isPast) {
                // past lesson today
                cardView.setAlpha(0.5f);
                cardView.setStrokeWidth(0);
                bannerNowLayout.setVisibility(View.GONE);
            } else {
                // default card state
                cardView.setAlpha(1.0f);
                cardView.setStrokeWidth((int) (1.0f * density));
                cardView.setStrokeColor(ContextCompat.getColor(context, R.color.input_border_dark));
                bannerNowLayout.setVisibility(View.GONE);
            }

            // open subject details on click
            cardView.setOnClickListener(v -> {
                Intent intent = new Intent(context, SubjectDetailsActivity.class);
                intent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
                intent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
                intent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_ID, lesson.getId());
                context.startActivity(intent);
            });
        }

        private void setupLocationBadge(LocationType type) {
            badgeLocation.setText(context.getString(type.getStringResId()).toUpperCase(java.util.Locale.getDefault()));
            badgeLocation.setBackgroundTintList(null);
            switch (type) {
                case ONLINE:
                    badgeLocation.setBackgroundResource(R.drawable.bg_badge_puw);
                    badgeLocation.setTextColor(ContextCompat.getColor(context, R.color.badge_puw_text));
                    break;
                case W_TERENIE:
                    badgeLocation.setBackgroundResource(R.drawable.bg_badge_field);
                    badgeLocation.setTextColor(ContextCompat.getColor(context, R.color.badge_field_text));
                    break;
                case UCZELNIA:
                default:
                    badgeLocation.setBackgroundResource(R.drawable.bg_badge_wspa);
                    badgeLocation.setTextColor(ContextCompat.getColor(context, R.color.badge_wspa_text));
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
            long hours = minutes / 60;
            long remMinutes = minutes % 60;
            String timeDurationStr;
            if (hours > 0 && remMinutes > 0) {
                timeDurationStr = context.getString(R.string.hours_and_minutes_short, hours, remMinutes);
            } else if (hours > 0) {
                timeDurationStr = context.getString(R.string.hours_short, hours);
            } else {
                timeDurationStr = context.getString(R.string.minutes_short, remMinutes);
            }
            gapText.setText(context.getString(R.string.schedule_gap_format, timeDurationStr));
        }
    }

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        private final TextView emptyTitle;
        private final TextView emptySubtitle;

        EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
            emptyTitle = itemView.findViewById(R.id.empty_title);
            emptySubtitle = itemView.findViewById(R.id.empty_subtitle);
        }

        void bind(ScheduleItem item) {
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
        }
    }
}
