package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Schedule;
import java.io.InputStream;

public class MasterFullTimeParser implements ScheduleParser {
    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup) {
        throw new UnsupportedOperationException("Full-time master parsing is not yet implemented.");
    }
}