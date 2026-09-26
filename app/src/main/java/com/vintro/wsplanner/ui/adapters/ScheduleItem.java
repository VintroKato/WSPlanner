package com.vintro.wsplanner.ui.adapters;

import com.vintro.wsplanner.models.Lesson;

// schedule adapter item (lesson, break, or empty)
public class ScheduleItem {
    public static final int TYPE_LESSON = 0;
    public static final int TYPE_BREAK_GAP = 1;
    public static final int TYPE_EMPTY = 2;

    private final int itemType;
    private final Lesson lesson;
    private final long gapMinutes;
    private final String emptyTitle;
    private final String emptySubtitle;
    private final boolean isNoInternet;

    private ScheduleItem(int itemType, Lesson lesson, long gapMinutes, String emptyTitle, String emptySubtitle, boolean isNoInternet) {
        this.itemType = itemType;
        this.lesson = lesson;
        this.gapMinutes = gapMinutes;
        this.emptyTitle = emptyTitle;
        this.emptySubtitle = emptySubtitle;
        this.isNoInternet = isNoInternet;
    }

    public static ScheduleItem createLesson(Lesson lesson) {
        return new ScheduleItem(TYPE_LESSON, lesson, 0, null, null, false);
    }

    public static ScheduleItem createBreakGap(long minutes) {
        return new ScheduleItem(TYPE_BREAK_GAP, null, minutes, null, null, false);
    }

    public static ScheduleItem createEmpty() {
        return new ScheduleItem(TYPE_EMPTY, null, 0, null, null, false);
    }

    public static ScheduleItem createNoInternet(String title, String subtitle) {
        return new ScheduleItem(TYPE_EMPTY, null, 0, title, subtitle, true);
    }

    public int getItemType() {
        return itemType;
    }

    public Lesson getLesson() {
        return lesson;
    }

    public long getGapMinutes() {
        return gapMinutes;
    }

    public String getEmptyTitle() {
        return emptyTitle;
    }

    public String getEmptySubtitle() {
        return emptySubtitle;
    }

    public boolean isNoInternet() {
        return isNoInternet;
    }
}
