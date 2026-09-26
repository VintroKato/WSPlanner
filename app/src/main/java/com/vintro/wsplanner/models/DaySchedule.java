package com.vintro.wsplanner.models;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// schedule for a single day
public class DaySchedule implements Serializable {
    private final LocalDate date;
    private final List<Lesson> lessons;
    private final LocalDateTime lastSyncTimestamp;
    private boolean isFromCacheFallback = false;

    public DaySchedule(LocalDate date, List<Lesson> lessons, LocalDateTime lastSyncTimestamp) {
        this.date = date;
        this.lessons = lessons != null ? new ArrayList<>(lessons) : new ArrayList<>();
        this.lastSyncTimestamp = lastSyncTimestamp;

        // sort by start time
        this.lessons.sort((a, b) -> {
            if (a.getStartTime() == null && b.getStartTime() == null) return 0;
            if (a.getStartTime() == null) return 1;
            if (b.getStartTime() == null) return -1;
            return a.getStartTime().compareTo(b.getStartTime());
        });
    }

    // get ongoing lesson
    public Lesson getCurrentLesson(LocalDateTime now) {
        for (Lesson lesson : lessons) {
            if (lesson.isCurrent(now)) {
                return lesson;
            }
        }
        return null;
    }

    // get next upcoming lesson
    public Lesson getNextLesson(LocalDateTime now) {
        for (Lesson lesson : lessons) {
            if (!lesson.isPast(now) && !lesson.isCurrent(now)) {
                return lesson;
            }
        }
        return null;
    }

    // gap in minutes between two lessons
    public static long getGapBetweenMinutes(Lesson first, Lesson second) {
        if (first == null || second == null || first.getEndTime() == null || second.getStartTime() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(first.getEndTime(), second.getStartTime()).toMinutes());
    }

    public boolean isEmpty() {
        return lessons.isEmpty();
    }

    public int getLessonCount() {
        return lessons.size();
    }

    public List<Lesson> getLessons() {
        return Collections.unmodifiableList(lessons);
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalDateTime getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    public boolean isFromCacheFallback() {
        return isFromCacheFallback;
    }

    public void setFromCacheFallback(boolean fromCacheFallback) {
        this.isFromCacheFallback = fromCacheFallback;
    }
}
