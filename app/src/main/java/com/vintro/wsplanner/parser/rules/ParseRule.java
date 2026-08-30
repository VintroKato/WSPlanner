package com.vintro.wsplanner.parser.rules;

import com.vintro.wsplanner.models.Lesson;
import java.time.LocalTime;
import java.util.List;

public interface ParseRule {
    // Checks if template (rule) is applicable for given cell
    boolean isApplicable(String rawCellText);

    // Returns a list of lessons
    List<Lesson> parse(String rawCellText, LocalTime startTime, LocalTime endTime);
}