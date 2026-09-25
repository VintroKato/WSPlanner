package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Schedule;
import java.io.InputStream;

// common interface for schedule excel parsers
public interface ScheduleParser {
    // parse schedule stream with student specialization and language group
    Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup);
    
    // parse schedule stream with optional seminar teacher
    default Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup, String seminarTeacher) {
        return parse(excelStream, fieldOfStudy, semester, studentSurname, studentSpecialization, targetLangGroup);
    }
}