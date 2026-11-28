package com.vintro.wsplanner.ui.adapters;

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
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;
import com.vintro.wsplanner.ui.helpers.UIHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// adapter for semester meetings timeline
public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {

    public interface OnTimelineLessonClickListener {
        void onLessonClick(Lesson lesson);
    }

    private final Context context;
    private final List<Lesson> lessons = new ArrayList<>();
    private final int lineCompletedColor;
    private final int lineUpcomingColor;
    private final int textPrimaryColor;
    private final int textSecondaryColor;
    private final int textMutedColor;
    private final int buttonTextColor;
    private final int badgePuwTextColor;
    private final int badgeFieldTextColor;
    private final int badgeWspaTextColor;
    private final int badgeNowTextColor;
    private final int badgeNextTextColor;

    private int nearestIndex = -1;
    private String primaryLocationText = null;
    @Nullable
    private OnTimelineLessonClickListener lessonClickListener;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dayMonthFormatter = DateTimeFormatter.ofPattern("dd.MM");

    public TimelineAdapter(Context context) {
        this.context = context;
        this.lineCompletedColor = UIHelper.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary);
        this.lineUpcomingColor = UIHelper.getThemeColor(context, R.attr.divider_color);
        this.textPrimaryColor = UIHelper.getThemeColor(context, R.attr.app_text);
        this.textSecondaryColor = UIHelper.getThemeColor(context, R.attr.text_secondary);
        this.textMutedColor = UIHelper.getThemeColor(context, R.attr.text_muted);
        this.buttonTextColor = UIHelper.getThemeColor(context, R.attr.button_text);
        this.badgePuwTextColor = UIHelper.getThemeColor(context, R.attr.badge_puw_text);
        this.badgeFieldTextColor = UIHelper.getThemeColor(context, R.attr.badge_field_text);
        this.badgeWspaTextColor = UIHelper.getThemeColor(context, R.attr.badge_wspa_text);
        this.badgeNowTextColor = UIHelper.getThemeColor(context, R.attr.badge_now_text);
        this.badgeNextTextColor = UIHelper.getThemeColor(context, R.attr.badge_next_text);
    }

    public void setOnTimelineLessonClickListener(@Nullable OnTimelineLessonClickListener listener) {
        this.lessonClickListener = listener;
    }

    public void submitLessons(List<Lesson> newLessons) {
        lessons.clear();
        if (newLessons != null) {
            lessons.addAll(newLessons);
        }

        // find most frequent location
        Map<String, Integer> locationCounts = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.getLocation() != null && l.getLocation().getDisplayText() != null) {
                String text = l.getLocation().getDisplayText().trim();
                if (!text.isEmpty()) {
                    locationCounts.put(text, locationCounts.getOrDefault(text, 0) + 1);
                }
            }
        }
        String maxLoc = null;
        int maxCount = -1;
        for (Map.Entry<String, Integer> entry : locationCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxLoc = entry.getKey();
            }
        }
        primaryLocationText = maxLoc;

        // find current or next upcoming lesson
        nearestIndex = -1;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < lessons.size(); i++) {
            Lesson l = lessons.get(i);
            if (l.isCurrent(now) || (l.getDate() != null && l.getStartTime() != null
                    && LocalDateTime.of(l.getDate(), l.getStartTime()).isAfter(now))) {
                nearestIndex = i;
                break;
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_lesson, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        Lesson lesson = lessons.get(position);
        holder.bind(lesson, position, position == nearestIndex, position == 0, position == lessons.size() - 1);
    }

    @Override
    public int getItemCount() {
        return lessons.size();
    }

    class TimelineViewHolder extends RecyclerView.ViewHolder {
        private final View timelineLineTop;
        private final View timelineLineBottom;
        private final TextView timelineDotNumber;

        private final LinearLayout standardContentLayout;
        private final TextView timelineDateTitle;
        private final TextView badgeSpecialLocation;
        private final TextView timelineStatusLabel;
        private final TextView timelineSubtitle;

        private final MaterialCardView nearestCardLayout;
        private final TextView nearestDateTitle;
        private final TextView nearestStatusBadge;
        private final TextView nearestTimeText;
        private final TextView nearestRoomText;

        TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            timelineLineTop = itemView.findViewById(R.id.timeline_line_top);
            timelineLineBottom = itemView.findViewById(R.id.timeline_line_bottom);
            timelineDotNumber = itemView.findViewById(R.id.timeline_dot_number);

            standardContentLayout = itemView.findViewById(R.id.standard_content_layout);
            timelineDateTitle = itemView.findViewById(R.id.timeline_date_title);
            badgeSpecialLocation = itemView.findViewById(R.id.badge_special_location);
            timelineStatusLabel = itemView.findViewById(R.id.timeline_status_label);
            timelineSubtitle = itemView.findViewById(R.id.timeline_subtitle);

            nearestCardLayout = itemView.findViewById(R.id.nearest_card_layout);
            nearestDateTitle = itemView.findViewById(R.id.nearest_date_title);
            nearestStatusBadge = itemView.findViewById(R.id.nearest_status_badge);
            nearestTimeText = itemView.findViewById(R.id.nearest_time_text);
            nearestRoomText = itemView.findViewById(R.id.nearest_room_text);
        }

        void bind(Lesson lesson, int position, boolean isNearest, boolean isFirst, boolean isLast) {
            LocalDateTime now = LocalDateTime.now();
            boolean isPast = lesson.isPast(now);
            boolean isCurrent = lesson.isCurrent(now);
            int itemNumber = position + 1;

            timelineLineTop.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
            timelineLineBottom.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

            timelineLineTop.setBackgroundColor(isPast || isNearest ? lineCompletedColor : lineUpcomingColor);
            timelineLineBottom.setBackgroundColor((isPast && !isNearest) ? lineCompletedColor : lineUpcomingColor);

            timelineDotNumber.setText(String.valueOf(itemNumber));
            if (isNearest || isPast) {
                timelineDotNumber.setBackgroundResource(R.drawable.bg_timeline_dot_completed);
                timelineDotNumber.setTextColor(buttonTextColor);
            } else {
                timelineDotNumber.setBackgroundResource(R.drawable.bg_timeline_dot_upcoming);
                timelineDotNumber.setTextColor(textMutedColor);
            }

            String dayName = "";
            String dayMonth = "";
            if (lesson.getDate() != null) {
                dayName = lesson.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
                if (!dayName.isEmpty()) {
                    dayName = dayName.substring(0, 1).toUpperCase(Locale.getDefault()) + dayName.substring(1);
                }
                dayMonth = lesson.getDate().format(dayMonthFormatter);
            }
            String headerTitle = context.getString(R.string.timeline_item_header_format, dayName, dayMonth);

            String start = lesson.getStartTime() != null ? lesson.getStartTime().format(timeFormatter) : "--:--";
            String end = lesson.getEndTime() != null ? lesson.getEndTime().format(timeFormatter) : "--:--";
            Location loc = lesson.getLocation();
            String room = loc != null ? loc.getDisplayText() : "—";
            String subtitle = context.getString(R.string.timeline_time_room_format, start, end, room);

            View.OnClickListener clickListener = v -> {
                if (lessonClickListener != null) {
                    lessonClickListener.onLessonClick(lesson);
                }
            };

            if (isNearest) {
                standardContentLayout.setVisibility(View.GONE);
                nearestCardLayout.setVisibility(View.VISIBLE);

                nearestDateTitle.setText(headerTitle);
                nearestTimeText.setText(start + " – " + end);
                nearestRoomText.setText(room);

                if (nearestStatusBadge != null) {
                    nearestStatusBadge.setBackgroundTintList(null);
                    if (isCurrent) {
                        nearestStatusBadge.setText(R.string.schedule_badge_now);
                        nearestStatusBadge.setBackgroundResource(R.drawable.bg_badge_now);
                        nearestStatusBadge.setTextColor(badgeNowTextColor);
                    } else {
                        nearestStatusBadge.setText(R.string.subject_status_next);
                        nearestStatusBadge.setBackgroundResource(R.drawable.bg_badge_next);
                        nearestStatusBadge.setTextColor(badgeNextTextColor);
                    }
                }

                nearestCardLayout.setOnClickListener(clickListener);
                standardContentLayout.setOnClickListener(null);
            } else {
                standardContentLayout.setVisibility(View.VISIBLE);
                nearestCardLayout.setVisibility(View.GONE);

                timelineDateTitle.setText(headerTitle);
                timelineSubtitle.setText(subtitle);

                if (isPast) {
                    timelineStatusLabel.setText(context.getString(R.string.subject_status_completed));
                    timelineDateTitle.setTextColor(textSecondaryColor);
                    timelineSubtitle.setTextColor(textMutedColor);
                    timelineStatusLabel.setTextColor(textMutedColor);
                } else {
                    timelineStatusLabel.setText(context.getString(R.string.subject_status_upcoming));
                    timelineDateTitle.setTextColor(textPrimaryColor);
                    timelineSubtitle.setTextColor(textSecondaryColor);
                    timelineStatusLabel.setTextColor(textMutedColor);
                }

                String currentLocText = (loc != null && loc.getDisplayText() != null) ? loc.getDisplayText().trim() : "";
                boolean isDifferentLocation = primaryLocationText != null && !currentLocText.isEmpty()
                        && !currentLocText.equalsIgnoreCase(primaryLocationText);

                if (isDifferentLocation && loc != null) {
                    badgeSpecialLocation.setVisibility(View.VISIBLE);
                    int badgeTextRes = loc.getType().getStringResId();
                    badgeSpecialLocation.setText(context.getString(badgeTextRes).toUpperCase(Locale.getDefault()));
                    badgeSpecialLocation.setBackgroundTintList(null);

                    if (loc.getType() == LocationType.ONLINE) {
                        badgeSpecialLocation.setBackgroundResource(R.drawable.bg_badge_puw);
                        badgeSpecialLocation.setTextColor(badgePuwTextColor);
                    } else if (loc.getType() == LocationType.W_TERENIE) {
                        badgeSpecialLocation.setBackgroundResource(R.drawable.bg_badge_field);
                        badgeSpecialLocation.setTextColor(badgeFieldTextColor);
                    } else {
                        badgeSpecialLocation.setBackgroundResource(R.drawable.bg_badge_wspa);
                        badgeSpecialLocation.setTextColor(badgeWspaTextColor);
                    }
                } else {
                    badgeSpecialLocation.setVisibility(View.GONE);
                }

                standardContentLayout.setOnClickListener(clickListener);
                nearestCardLayout.setOnClickListener(null);
            }
        }
    }
}
