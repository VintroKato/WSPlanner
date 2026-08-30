package com.vintro.wsplanner.parser.rules;

import com.vintro.wsplanner.models.Lesson;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class StandardRule implements ParseRule {

    @Override
    public boolean isApplicable(String rawCellText) {
        // Если это обычное занятие, в тексте обычно есть стандартные слова-маркеры
        return rawCellText.contains("- laboratorium") || rawCellText.contains("- wykład");
    }

    @Override
    public List<Lesson> parse(String rawCellText, LocalTime startTime, LocalTime endTime) {
        List<Lesson> lessons = new ArrayList<>();
        // Здесь ваша логика разбиения строки по \n и Regex для обычных пар
        // ...
        return lessons;
    }
}