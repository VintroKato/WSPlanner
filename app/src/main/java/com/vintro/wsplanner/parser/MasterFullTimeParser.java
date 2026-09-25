package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Schedule;
import com.vintro.wsplanner.utils.Logger;
import java.io.InputStream;

// schedule parser stub for full-time master degree
public class MasterFullTimeParser implements ScheduleParser {
    // full-time master parsing is pending timetable format specification
    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup) {
        Logger.w("MasterFullTimeParser.parse", "Full-time master parsing requested but not implemented for field: " + fieldOfStudy);
        throw new UnsupportedOperationException("Full-time master parsing is not yet implemented.");
    }
}