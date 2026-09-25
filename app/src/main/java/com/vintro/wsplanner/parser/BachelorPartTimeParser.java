package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Schedule;
import com.vintro.wsplanner.utils.Logger;
import java.io.InputStream;

// schedule parser stub for part-time bachelor degree
public class BachelorPartTimeParser implements ScheduleParser {
    // part-time bachelor parsing is pending timetable format specification
    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup) {
        Logger.w("BachelorPartTimeParser.parse", "Part-time bachelor parsing requested but not implemented for field: " + fieldOfStudy);
        throw new UnsupportedOperationException("Part-time bachelor parsing is not yet implemented.");
    }
}