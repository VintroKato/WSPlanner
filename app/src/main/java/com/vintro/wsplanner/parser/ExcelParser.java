package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Schedule;
import com.vintro.wsplanner.parser.rules.ParseRule;
import com.vintro.wsplanner.parser.rules.SeminarRule;
import com.vintro.wsplanner.parser.rules.StandardRule;

import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ExcelParser {

    private final List<ParseRule> rules;

    public ExcelParser() {
        rules = new ArrayList<>();
        // Регистрируем наши правила.
        // Порядок важен! Сначала узкие исключения, потом стандартные правила.
        rules.add(new SeminarRule());
        rules.add(new StandardRule());
        // В будущем можете добавить ProjectRule, ExamRule и т.д.
    }

    public Schedule parseSchedule(InputStream excelFileStream, String targetGroup) {
        Schedule schedule = new Schedule("Informatyka", 6, targetGroup);

        // Псевдокод работы с Apache POI:
        // 1. Находим нужную колонку для targetGroup
        // 2. Идем по строкам со временем (напр. startTime = 08:15, endTime = 09:00)
        // 3. Достаем текст из ячейки: String cellText = getCellTextWithMerges(sheet, row, col);

        String cellText = "текст из ячейки excel";
        LocalTime startTime = LocalTime.of(8, 15);
        LocalTime endTime = LocalTime.of(9, 0);

        if (cellText != null && !cellText.trim().isEmpty()) {
            boolean parsed = false;

            // Прогоняем текст через систему правил
            for (ParseRule rule : rules) {
                if (rule.isApplicable(cellText)) {
                    List<Lesson> extractedLessons = rule.parse(cellText, startTime, endTime);
                    schedule.addLessons(extractedLessons);
                    parsed = true;
                    break; // Правило найдено, идем к следующей ячейке
                }
            }

            if (!parsed) {
                // Логируем, если попалась ячейка, для которой нет шаблона
                // Это поможет вам быстро добавить новое правило в будущем
            }
        }

        return schedule;
    }
}