package com.vintro.wsplanner.models;

import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.enums.LocationType;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// semester details and lessons for a subject
public class SubjectDetails implements Serializable {
    private final String subjectName;
    private final String rawLessonType;
    private final LessonType lessonType;
    private final String teacherName;
    private final Location dominantLocation;
    private final List<Lesson> semesterLessons;

    public SubjectDetails(String subjectName, String rawLessonType, LessonType lessonType,
                          String teacherName, List<Lesson> semesterLessons) {
        this.subjectName = subjectName != null ? subjectName.trim() : "";
        String cleanedType = rawLessonType != null ? rawLessonType.replaceAll("(?i)\\b\\d{1,2}h\\b|\\d{1,2}h", "").trim() : "";
        cleanedType = cleanedType.replaceAll("^[-–—\\s,.]+|[-–—\\s,.]+$", "").trim();
        if (cleanedType.equalsIgnoreCase("other") || cleanedType.equalsIgnoreCase("inne")) {
            cleanedType = "";
        }
        this.rawLessonType = cleanedType;
        this.lessonType = lessonType != null ? lessonType : (!this.rawLessonType.isEmpty() ? LessonType.fromString(this.rawLessonType) : LessonType.INNE);
        this.teacherName = teacherName != null ? teacherName.trim() : "";
        this.semesterLessons = semesterLessons != null ? new ArrayList<>(semesterLessons) : new ArrayList<>();

        // sort lessons by date and time
        this.semesterLessons.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            int dateCmp = a.getDate().compareTo(b.getDate());
            if (dateCmp != 0) return dateCmp;
            if (a.getStartTime() == null && b.getStartTime() == null) return 0;
            if (a.getStartTime() == null) return 1;
            if (b.getStartTime() == null) return -1;
            return a.getStartTime().compareTo(b.getStartTime());
        });

        this.dominantLocation = calculateDominantLocation(this.semesterLessons);
    }

    // find most frequent location across all lessons
    private Location calculateDominantLocation(List<Lesson> list) {
        if (list.isEmpty()) {
            return new Location("WSPA");
        }
        Map<LocationType, Integer> counts = new HashMap<>();
        for (Lesson l : list) {
            LocationType type = l.getLocation().getType();
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }

        LocationType dominant = LocationType.UCZELNIA;
        int maxCount = -1;
        for (Map.Entry<LocationType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }

        for (Lesson l : list) {
            if (l.getLocation().getType() == dominant) {
                return l.getLocation();
            }
        }
        return list.get(0).getLocation();
    }

    public int getTotalCount() {
        return semesterLessons.size();
    }

    public int getCompletedCount(LocalDateTime now) {
        int completed = 0;
        for (Lesson l : semesterLessons) {
            if (l.isPast(now)) {
                completed++;
            }
        }
        return completed;
    }

    public int getProgressPercentage(LocalDateTime now) {
        if (getTotalCount() == 0) return 0;
        return (int) Math.round(((double) getCompletedCount(now) / getTotalCount()) * 100);
    }

    public Lesson getNextUpcomingLesson(LocalDateTime now) {
        for (Lesson l : semesterLessons) {
            if (!l.isPast(now)) {
                return l;
            }
        }
        return null;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getRawLessonType() {
        return rawLessonType;
    }

    public LessonType getLessonType() {
        return lessonType;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public Location getDominantLocation() {
        return dominantLocation;
    }

    public List<Lesson> getSemesterLessons() {
        return Collections.unmodifiableList(semesterLessons);
    }
}
