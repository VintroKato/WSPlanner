package com.vintro.wsplanner.ui.widgets;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import androidx.core.content.ContextCompat;

import com.vintro.wsplanner.R;
import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.data.preferences.PreferencesManager;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.models.DaySchedule;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// remote views service providing list items for daily schedule widget
public class DailyScheduleRemoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new DailyScheduleRemoteViewsFactory(this.getApplicationContext());
    }

    // thread-safe factory generating remote views for each lesson in the daily list
    private static class DailyScheduleRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private volatile List<Lesson> lessonSnapshot = Collections.emptyList();
        private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

        public DailyScheduleRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            // data is populated immediately by onDataSetChanged
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        // populate immutable lesson snapshot from schedule repository
        private void loadData() {
            try {
                if (!PreferencesManager.isOnboardingCompleted(context)
                        || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
                    lessonSnapshot = Collections.emptyList();
                    Logger.d("DailyScheduleRemoteViewsFactory.loadData", "Student configuration not completed, skipping list load");
                    return;
                }
                LocalDate today = LocalDate.now();
                DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
                if (schedule != null && !schedule.isEmpty()) {
                    lessonSnapshot = Collections.unmodifiableList(new ArrayList<>(schedule.getLessons()));
                } else {
                    lessonSnapshot = Collections.emptyList();
                }
                Logger.d("DailyScheduleRemoteViewsFactory.loadData", "Loaded " + lessonSnapshot.size() + " items for daily schedule widget");
            } catch (Exception e) {
                lessonSnapshot = Collections.emptyList();
                Logger.e("DailyScheduleRemoteViewsFactory.loadData", "Error loading data: " + Logger.formatException(e), e);
            }
        }

        @Override
        public void onDestroy() {
            lessonSnapshot = Collections.emptyList();
        }

        @Override
        public int getCount() {
            return lessonSnapshot.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            List<Lesson> currentList = lessonSnapshot;
            if (position < 0 || position >= currentList.size()) return null;

            Lesson lesson = currentList.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_daily_lesson);

            boolean isLight = WidgetActionHelper.isWidgetLightTheme(context);
            if (isLight) {
                views.setInt(R.id.item_daily_root, "setBackgroundResource", R.drawable.bg_widget_item_light);
                views.setTextColor(R.id.item_daily_time, ContextCompat.getColor(context, R.color.widget_text_light_primary));
                views.setTextColor(R.id.item_daily_subject, ContextCompat.getColor(context, R.color.widget_text_light_primary));
                views.setTextColor(R.id.item_daily_teacher, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
                views.setInt(R.id.item_daily_type, "setBackgroundResource", R.drawable.bg_badge_rect_light);
                views.setTextColor(R.id.item_daily_type, ContextCompat.getColor(context, R.color.widget_badge_type_text_light));
                views.setTextColor(R.id.item_daily_room, ContextCompat.getColor(context, R.color.widget_room_text_light));
            } else {
                views.setInt(R.id.item_daily_root, "setBackgroundResource", R.drawable.bg_widget_item_dark);
                views.setTextColor(R.id.item_daily_time, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
                views.setTextColor(R.id.item_daily_subject, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
                views.setTextColor(R.id.item_daily_teacher, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
                views.setInt(R.id.item_daily_type, "setBackgroundResource", R.drawable.bg_badge_rect);
                views.setTextColor(R.id.item_daily_type, ContextCompat.getColor(context, R.color.badge_now_text));
                views.setTextColor(R.id.item_daily_room, ContextCompat.getColor(context, R.color.badge_field_text));
            }

            String timeStr = "";
            if (lesson.getStartTime() != null && lesson.getEndTime() != null) {
                timeStr = lesson.getStartTime().format(TIME_FORMATTER) + " - " + lesson.getEndTime().format(TIME_FORMATTER);
            }
            views.setTextViewText(R.id.item_daily_time, timeStr);

            LocalDateTime now = LocalDateTime.now();
            boolean isCurrent = lesson.isCurrent(now);
            views.setViewVisibility(R.id.item_daily_status_indicator, isCurrent ? View.VISIBLE : View.GONE);

            views.setTextViewText(R.id.item_daily_subject, lesson.getSubjectName());

            LessonType type = lesson.getResolvedLessonType();
            if (type != null && type != LessonType.INNE) {
                views.setViewVisibility(R.id.item_daily_type, View.VISIBLE);
                views.setTextViewText(R.id.item_daily_type, type.getLocalizedDisplayName(context).toUpperCase(Locale.getDefault()));
            } else {
                views.setViewVisibility(R.id.item_daily_type, View.GONE);
            }

            String loc = lesson.getLocation() != null ? lesson.getLocation().getDisplayText() : "WSPA";
            views.setTextViewText(R.id.item_daily_room, loc);
            views.setViewVisibility(R.id.item_daily_teacher, View.GONE);

            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
            fillInIntent.putExtra(SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
            fillInIntent.putExtra("subject_name", lesson.getSubjectName());
            fillInIntent.putExtra("lesson_type", lesson.getLessonType());
            views.setOnClickFillInIntent(R.id.item_daily_root, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
