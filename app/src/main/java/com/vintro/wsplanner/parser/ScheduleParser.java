package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Schedule;
import java.io.InputStream;

public interface ScheduleParser {
    Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup);
    
    default Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup, String seminarTeacher) {
        return parse(excelStream, fieldOfStudy, semester, studentSurname, studentSpecialization, targetLangGroup);
    }
}