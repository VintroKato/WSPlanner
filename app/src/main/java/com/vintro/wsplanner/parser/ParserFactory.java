package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;

public class ParserFactory {

    public static ScheduleParser getParser(DegreeLevel degree, StudyMode mode) {
        if (degree == DegreeLevel.BACHELORS) {
            if (mode == StudyMode.FULL_TIME) {
                return new BachelorFullTimeParser();
            } else {
                return new BachelorPartTimeParser(); // Stub
            }
        } else if (degree == DegreeLevel.MASTERS || degree == DegreeLevel.LONG_MASTERS) {
            if (mode == StudyMode.FULL_TIME) {
                return new MasterFullTimeParser(); // Stub
            } else {
                return new MasterPartTimeParser(); // Stub
            }
        }
        throw new IllegalArgumentException("Unsupported degree and mode combination");
    }
}