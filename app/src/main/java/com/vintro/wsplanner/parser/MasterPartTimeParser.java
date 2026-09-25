package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Schedule;
import com.vintro.wsplanner.utils.Logger;
import java.io.InputStream;

// schedule parser stub for part-time master degree
public class MasterPartTimeParser implements ScheduleParser {
    // part-time master parsing is pending timetable format specification
    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup) {
        Logger.w("MasterPartTimeParser.parse", "Part-time master parsing requested but not implemented for field: " + fieldOfStudy);
        throw new UnsupportedOperationException("Part-time master parsing is not yet implemented.");
    }
}