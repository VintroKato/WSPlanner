package com.vintro.wsplanner.models;

import com.vintro.wsplanner.enums.DegreeLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// scraped academic course metadata from puw student zone
public class ParsedCourse {
    public String fieldOfStudy;
    public DegreeLevel degreeLevel;
    public String courseUrl;

    // mode ("st", "nst", "nst puw") to study years
    public Map<String, List<Integer>> availableModesAndYears = new HashMap<>();

    @Override
    public String toString() {
        return fieldOfStudy + " (" + degreeLevel + ") -> " + availableModesAndYears.toString();
    }
}