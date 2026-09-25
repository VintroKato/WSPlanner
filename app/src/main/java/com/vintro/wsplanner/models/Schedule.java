package com.vintro.wsplanner.models;

import java.util.ArrayList;
import java.util.List;

// schedule model representing full timetable for a field of study and semester
public class Schedule {
    private final String fieldOfStudy;
    private final int semester;
    private final String groupName;
    private final List<Lesson> lessons;

    public Schedule(String fieldOfStudy, int semester, String groupName) {
        this.fieldOfStudy = fieldOfStudy;
        this.semester = semester;
        this.groupName = groupName;
        this.lessons = new ArrayList<>();
    }

    // append single lesson to schedule
    public void addLesson(Lesson lesson) {
        this.lessons.add(lesson);
    }

    // append collection of lessons to schedule
    public void addLessons(List<Lesson> newLessons) {
        this.lessons.addAll(newLessons);
    }


    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public int getSemester() {
        return semester;
    }

    public String getGroupName() {
        return groupName;
    }

    public List<Lesson> getLessons() {
        return lessons;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Kierunek: ").append(fieldOfStudy).append(", Grupa: ").append(groupName).append("\n");
        for (Lesson l : lessons) {
            sb.append(l.toString()).append("\n");
        }
        return sb.toString();
    }
}