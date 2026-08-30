package com.vintro.wsplanner.models;

import com.vintro.wsplanner.enums.DegreeLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsedCourse {
    public String fieldOfStudy;
    public DegreeLevel degreeLevel; // Из DegreeLevel.java
    public String courseUrl;

    // Ключ: тип обучения ("st", "nst", "nst puw")
    // Значение: список доступных годов обучения (1, 2, 3...)
    public Map<String, List<Integer>> availableModesAndYears = new HashMap<>();

    @Override
    public String toString() {
        return fieldOfStudy + " (" + degreeLevel + ") -> " + availableModesAndYears.toString();
    }
}