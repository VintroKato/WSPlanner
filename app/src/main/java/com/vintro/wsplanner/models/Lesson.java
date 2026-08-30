package com.vintro.wsplanner.models;

import java.time.LocalDate;
import java.time.LocalTime;

public class Lesson {
    private final String subjectName;
    private final String lessonType;
    private final String teacherName;
    private String room;

    // For FULL_TIME (stacjonarne)
    private LocalDate date;

    // For PART_TIME (niestacjonarne) -  'zjazd'
    private Integer sessionNumber;

    private LocalTime startTime;
    private LocalTime endTime;

    public Lesson(String subjectName, String lessonType, String teacherName, String room,
                  LocalDate date, Integer sessionNumber, LocalTime startTime, LocalTime endTime) {
        this.subjectName = subjectName;
        this.lessonType = lessonType;
        this.teacherName = teacherName;
        this.room = room;
        this.date = date;
        this.sessionNumber = sessionNumber;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getLessonType() {
        return lessonType;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
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
    public String toString() {
        return String.format("%s. %s | %s | %s | %s | %s - %s",
                date != null ? date.toString() : "Brak daty",
                subjectName, lessonType, teacherName, room, startTime, endTime);
    }
}