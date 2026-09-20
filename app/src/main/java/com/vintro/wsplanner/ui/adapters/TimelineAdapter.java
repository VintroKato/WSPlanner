package com.vintro.wsplanner.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.vintro.wsplanner.R;
import com.vintro.wsplanner.enums.LocationType;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Location;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// adapter for semester timeline
public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {
    private final Context context;
    private final List<Lesson> lessons = new ArrayList<>();
    private int nearestIndex = -1;
    private String primaryLocationText = null;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dayMonthFormatter = DateTimeFormatter.ofPattern("dd.MM");

    public TimelineAdapter(Context context) {
        this.context = context;
    }

    public void submitLessons(List<Lesson> newLessons) {
        lessons.clear();
        if (newLessons != null) {
            lessons.addAll(newLessons);
        }

        // find most frequent location
        java.util.Map<String, Integer> locationCounts = new java.util.HashMap<>();
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
        for (java.util.Map.Entry<String, Integer> entry : locationCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                maxLoc = entry.getKey();
            }
        }
        primaryLocationText = maxLoc;

        // find next upcoming lesson
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
        private final FrameLayout dotCompleted;
        private final TextView dotUpcomingNumber;
        private final TextView dotCurrentNumber;

        private final LinearLayout standardContentLayout;
        private final TextView timelineDateTitle;
        private final TextView badgeSpecialLocation;
        private final TextView timelineStatusLabel;
        private final TextView timelineSubtitle;

        private final MaterialCardView nearestCardLayout;
        private final TextView nearestDateTitle;
        private final TextView nearestTimeText;
        private final TextView nearestRoomText;

        TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            timelineLineTop = itemView.findViewById(R.id.timeline_line_top);
            timelineLineBottom = itemView.findViewById(R.id.timeline_line_bottom);
            dotCompleted = itemView.findViewById(R.id.dot_completed);
            dotUpcomingNumber = itemView.findViewById(R.id.dot_upcoming_number);
            dotCurrentNumber = itemView.findViewById(R.id.dot_current_number);

            standardContentLayout = itemView.findViewById(R.id.standard_content_layout);
            timelineDateTitle = itemView.findViewById(R.id.timeline_date_title);
            badgeSpecialLocation = itemView.findViewById(R.id.badge_special_location);
            timelineStatusLabel = itemView.findViewById(R.id.timeline_status_label);
            timelineSubtitle = itemView.findViewById(R.id.timeline_subtitle);

            nearestCardLayout = itemView.findViewById(R.id.nearest_card_layout);
            nearestDateTitle = itemView.findViewById(R.id.nearest_date_title);
            nearestTimeText = itemView.findViewById(R.id.nearest_time_text);
            nearestRoomText = itemView.findViewById(R.id.nearest_room_text);
        }

        void bind(Lesson lesson, int position, boolean isNearest, boolean isFirst, boolean isLast) {
            LocalDateTime now = LocalDateTime.now();
            boolean isPast = lesson.isPast(now);
            int itemNumber = position + 1;

            // connecting lines
            timelineLineTop.setVisibility(isFirst ? View.INVISIBLE : View.VISIBLE);
            timelineLineBottom.setVisibility(isLast ? View.INVISIBLE : View.VISIBLE);

            int blueColor = ContextCompat.getColor(context, R.color.timeline_line_completed);
            int grayColor = ContextCompat.getColor(context, R.color.timeline_line_upcoming);

            // past lines are blue
            timelineLineTop.setBackgroundColor(isPast ? blueColor : grayColor);
            timelineLineBottom.setBackgroundColor((isPast && !isNearest) ? blueColor : grayColor);

            // node dot state
            if (isNearest) {
                dotCompleted.setVisibility(View.GONE);
                dotUpcomingNumber.setVisibility(View.GONE);
                dotCurrentNumber.setVisibility(View.VISIBLE);
                dotCurrentNumber.setText(String.valueOf(itemNumber));
            } else if (isPast) {
                dotCompleted.setVisibility(View.VISIBLE);
                dotUpcomingNumber.setVisibility(View.GONE);
                dotCurrentNumber.setVisibility(View.GONE);
            } else {
                dotCompleted.setVisibility(View.GONE);
                dotUpcomingNumber.setVisibility(View.VISIBLE);
                dotCurrentNumber.setVisibility(View.GONE);
                dotUpcomingNumber.setText(String.valueOf(itemNumber));
            }

            // format date
            String dayName = "";
            String dayMonth = "";
            if (lesson.getDate() != null) {
                dayName = lesson.getDate().getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault());
                if (!dayName.isEmpty()) {
                    dayName = dayName.substring(0, 1).toUpperCase(Locale.getDefault()) + dayName.substring(1);
                }
                dayMonth = lesson.getDate().format(dayMonthFormatter);
            }
            String headerTitle = context.getString(R.string.timeline_item_header_format, itemNumber, dayName, dayMonth);

            // format time and room
            String start = lesson.getStartTime() != null ? lesson.getStartTime().format(timeFormatter) : "--:--";
            String end = lesson.getEndTime() != null ? lesson.getEndTime().format(timeFormatter) : "--:--";
            Location loc = lesson.getLocation();
            String room = loc != null ? loc.getDisplayText() : "—";
            String subtitle = context.getString(R.string.timeline_time_room_format, start, end, room);

            // card for next lesson, row for others
            if (isNearest) {
                standardContentLayout.setVisibility(View.GONE);
                nearestCardLayout.setVisibility(View.VISIBLE);

                nearestDateTitle.setText(headerTitle);
                nearestTimeText.setText(start + " – " + end);

                String lessonTypeName = lesson.getLessonType();
                if (lessonTypeName != null && !lessonTypeName.trim().isEmpty()) {
                    lessonTypeName = lessonTypeName.substring(0, 1).toUpperCase(Locale.getDefault()) + lessonTypeName.substring(1);
                    nearestRoomText.setText(room);
                } else {
                    nearestRoomText.setText(room);
                }
            } else {
                standardContentLayout.setVisibility(View.VISIBLE);
                nearestCardLayout.setVisibility(View.GONE);

                timelineDateTitle.setText(headerTitle);
                timelineSubtitle.setText(subtitle);

                if (isPast) {
                    timelineStatusLabel.setText(context.getString(R.string.subject_status_completed));
                    timelineStatusLabel.setAlpha(0.5f);
                    timelineDateTitle.setAlpha(0.6f);
                    timelineSubtitle.setAlpha(0.5f);
                } else {
                    timelineStatusLabel.setText(context.getString(R.string.subject_status_upcoming));
                    timelineStatusLabel.setAlpha(0.7f);
                    timelineDateTitle.setAlpha(0.9f);
                    timelineSubtitle.setAlpha(0.7f);
                }

                // badge if location differs from primary
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
                        badgeSpecialLocation.setTextColor(ContextCompat.getColor(context, R.color.badge_puw_text));
                    } else if (loc.getType() == LocationType.W_TERENIE) {
                        badgeSpecialLocation.setBackgroundResource(R.drawable.bg_badge_field);
                        badgeSpecialLocation.setTextColor(ContextCompat.getColor(context, R.color.badge_field_text));
                    } else {
                        badgeSpecialLocation.setBackgroundResource(R.drawable.bg_badge_wspa);
                        badgeSpecialLocation.setTextColor(ContextCompat.getColor(context, R.color.badge_wspa_text));
                    }
                } else {
                    badgeSpecialLocation.setVisibility(View.GONE);
                }
            }
        }
    }
}
