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
import com.vintro.wsplanner.utils.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// remote views service providing list items for daily schedule widget
public class DailyScheduleRemoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new DailyScheduleRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    // factory generating RemoteViews for each lesson in the daily list
    private static class DailyScheduleRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private final List<Lesson> lessonList = new ArrayList<>();
        private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

        public DailyScheduleRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        // populate lesson list from schedule repository
        private void loadData() {
            lessonList.clear();
            if (!PreferencesManager.isOnboardingCompleted(context)
                    || !ScheduleRepository.getInstance(context).isConfigurationComplete()) {
                Logger.d("DailyScheduleRemoteViewsFactory.loadData", "Student configuration not completed, skipping list load");
                return;
            }
            LocalDate today = LocalDate.now();
            try {
                DaySchedule schedule = ScheduleRepository.getInstance(context).getScheduleForDateSync(today);
                if (schedule != null && !schedule.isEmpty()) {
                    lessonList.addAll(schedule.getLessons());
                }
                Logger.d("DailyScheduleRemoteViewsFactory.loadData", "Loaded " + lessonList.size() + " items for daily schedule widget");
            } catch (Exception e) {
                Logger.e("DailyScheduleRemoteViewsFactory.loadData", "Error loading data: " + e.getMessage());
            }
        }

        @Override
        public void onDestroy() {
            lessonList.clear();
        }

        @Override
        public int getCount() {
            return lessonList.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= lessonList.size()) return null;

            Lesson lesson = lessonList.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_daily_lesson);

            String theme = PreferencesManager.getWidgetTheme(context);
            if (PreferencesManager.WIDGET_THEME_LIGHT.equals(theme)) {
                views.setInt(R.id.item_daily_root, "setBackgroundResource", R.drawable.bg_widget_item_light);
                views.setTextColor(R.id.item_daily_time, ContextCompat.getColor(context, R.color.widget_text_light_primary));
                views.setTextColor(R.id.item_daily_subject, ContextCompat.getColor(context, R.color.widget_text_light_primary));
                views.setTextColor(R.id.item_daily_teacher, ContextCompat.getColor(context, R.color.widget_text_light_secondary));
            } else {
                views.setInt(R.id.item_daily_root, "setBackgroundResource", R.drawable.bg_widget_item_dark);
                views.setTextColor(R.id.item_daily_time, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
                views.setTextColor(R.id.item_daily_subject, ContextCompat.getColor(context, R.color.widget_text_dark_primary));
                views.setTextColor(R.id.item_daily_teacher, ContextCompat.getColor(context, R.color.widget_text_dark_secondary));
            }

            // Time
            String timeStr = "";
            if (lesson.getStartTime() != null && lesson.getEndTime() != null) {
                timeStr = lesson.getStartTime().format(TIME_FORMATTER) + " - " + lesson.getEndTime().format(TIME_FORMATTER);
            }
            views.setTextViewText(R.id.item_daily_time, timeStr);

            // Active NOW indicator
            LocalDateTime now = LocalDateTime.now();
            boolean isCurrent = lesson.isCurrent(now);
            views.setViewVisibility(R.id.item_daily_status_indicator, isCurrent ? View.VISIBLE : View.GONE);

            // Subject
            views.setTextViewText(R.id.item_daily_subject, lesson.getSubjectName());

            // Type
            LessonType type = lesson.getResolvedLessonType();
            String typeStr = type != null ? type.getDisplayName() : (lesson.getLessonType() != null ? lesson.getLessonType() : "");
            if (typeStr != null && !typeStr.isEmpty()) {
                views.setViewVisibility(R.id.item_daily_type, View.VISIBLE);
                views.setTextViewText(R.id.item_daily_type, typeStr.toUpperCase());
            } else {
                views.setViewVisibility(R.id.item_daily_type, View.GONE);
            }

            // Room / Location
            String loc = lesson.getLocation() != null ? lesson.getLocation().getDisplayText() : "WSPA";
            views.setTextViewText(R.id.item_daily_room, loc);

            // Teacher
            String teacher = lesson.getTeacherName();
            if (teacher != null && !teacher.isEmpty()) {
                views.setTextViewText(R.id.item_daily_teacher, "· " + teacher);
                views.setViewVisibility(R.id.item_daily_teacher, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.item_daily_teacher, View.GONE);
            }

            // Fill-in Intent for item click (opens subject details or main activity)
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(com.vintro.wsplanner.ui.activities.SubjectDetailsActivity.EXTRA_SUBJECT_NAME, lesson.getSubjectName());
            fillInIntent.putExtra(com.vintro.wsplanner.ui.activities.SubjectDetailsActivity.EXTRA_LESSON_TYPE, lesson.getLessonType());
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
