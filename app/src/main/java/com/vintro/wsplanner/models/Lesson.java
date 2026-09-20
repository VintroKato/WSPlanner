package com.vintro.wsplanner.models;

import com.vintro.wsplanner.enums.LessonType;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

// single scheduled lesson
public class Lesson implements Serializable {
    private final String id;
    private final String subjectName;
    private final String lessonType; // raw type name
    private final LessonType resolvedLessonType;
    private final String teacherName;
    private final Location location;
    private LocalDate date;
    private Integer sessionNumber; // weekend session number
    private LocalTime startTime;
    private LocalTime endTime;

    // parser constructor
    public Lesson(String subjectName, String lessonType, String teacherName, String room,
                  LocalDate date, Integer sessionNumber, LocalTime startTime, LocalTime endTime) {
        this(
            generateId(date, startTime, subjectName, lessonType),
            subjectName,
            lessonType,
            teacherName,
            new Location(room),
            date,
            sessionNumber,
            startTime,
            endTime
        );
    }

    // main constructor
    public Lesson(String id, String subjectName, String lessonType, String teacherName,
                  Location location, LocalDate date, Integer sessionNumber,
                  LocalTime startTime, LocalTime endTime) {
        this.subjectName = subjectName != null ? subjectName.trim() : "";
        
        String cleanedType = null;
        if (lessonType != null) {
            cleanedType = lessonType.replaceAll("(?i)\\b\\d{1,2}h\\b|\\d{1,2}h", "").trim();
            cleanedType = cleanedType.replaceAll("^[-–—\\s,.]+|[-–—\\s,.]+$", "").trim();
            if (cleanedType.isEmpty() || cleanedType.equalsIgnoreCase("other") || cleanedType.equalsIgnoreCase("inne")) {
                cleanedType = null;
            }
        }
        this.lessonType = cleanedType;
        this.resolvedLessonType = this.lessonType != null 
                ? LessonType.fromString(this.lessonType) 
                : (this.subjectName != null ? LessonType.fromString(this.subjectName) : LessonType.INNE);
        this.teacherName = teacherName != null ? teacherName.trim() : "";
        this.location = location != null ? location : new Location("");
        this.date = date;
        this.sessionNumber = sessionNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.id = (id != null && !id.isEmpty()) ? id : generateId(this.date, this.startTime, this.subjectName, this.lessonType);
    }

    // unique id based on date, time, subject, and type
    public static String generateId(LocalDate date, LocalTime startTime, String subjectName, String lessonType) {
        String dateStr = date != null ? date.toString() : "nodate";
        String timeStr = startTime != null ? startTime.toString() : "notime";
        String subStr = subjectName != null ? subjectName.trim().replaceAll("\\s+", "_") : "nosub";
        String typeStr = lessonType != null ? lessonType.trim().replaceAll("\\s+", "_") : "notype";
        return dateStr + "_" + timeStr + "_" + subStr + "_" + typeStr;
    }

    // note key shared by all sessions of this course
    public String getSubjectNoteKey() {
        String sub = subjectName != null ? subjectName.trim().toLowerCase() : "";
        String type = resolvedLessonType != null ? resolvedLessonType.name().toLowerCase() : (lessonType != null ? lessonType.trim().toLowerCase() : "");
        return sub + "_" + type;
    }

    // status checks
    public boolean isPast(LocalDateTime now) {
        if (date == null || endTime == null) return false;
        return now.toLocalDate().isAfter(date) ||
                (now.toLocalDate().isEqual(date) && now.toLocalTime().isAfter(endTime));
    }

    public boolean isCurrent(LocalDateTime now) {
        if (date == null || startTime == null || endTime == null) return false;
        return now.toLocalDate().isEqual(date) &&
                !now.toLocalTime().isBefore(startTime) &&
                !now.toLocalTime().isAfter(endTime);
    }

    // minutes until lesson ends
    public long getMinutesUntilEnd(LocalDateTime now) {
        if (!isCurrent(now) || endTime == null) return 0;
        return Math.max(0, Duration.between(now.toLocalTime(), endTime).toMinutes());
    }

    // minutes until lesson starts
    public long getMinutesUntilStart(LocalDateTime now) {
        if (isPast(now) || date == null || startTime == null) return 0;
        LocalDateTime startDateTime = LocalDateTime.of(date, startTime);
        return Math.max(0, Duration.between(now, startDateTime).toMinutes());
    }

    // getters and setters
    public String getId() {
        return id;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getLessonType() {
        return lessonType;
    }

    public LessonType getResolvedLessonType() {
        return resolvedLessonType;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public Location getLocation() {
        return location;
    }

    public String getRoom() {
        return location != null ? location.getDisplayText() : "";
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getSessionNumber() {
        return sessionNumber;
    }

    public void setSessionNumber(Integer sessionNumber) {
        this.sessionNumber = sessionNumber;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lesson)) return false;
        Lesson lesson = (Lesson) o;
        return Objects.equals(id, lesson.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s. %s | %s | %s | %s | %s - %s",
                date != null ? date.toString() : "Brak daty",
                subjectName, lessonType, teacherName, location, startTime, endTime);
    }
}
