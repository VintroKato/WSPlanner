package com.vintro.wsplanner.parser.rules;

import com.vintro.wsplanner.models.Lesson;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class SeminarRule implements ParseRule {

    @Override
    public boolean isApplicable(String rawCellText) {
        // Срабатывает только если в ячейке есть специфичное слово
        return rawCellText.contains("Seminarium i przygotowanie pracy dyplomowej");
    }

    @Override
    public List<Lesson> parse(String rawCellText, LocalTime startTime, LocalTime endTime) {
        List<Lesson> lessons = new ArrayList<>();
        // Здесь специфичная логика парсинга, где куча групп и преподавателей в одной ячейке
        // ...
        return lessons;
    }
}