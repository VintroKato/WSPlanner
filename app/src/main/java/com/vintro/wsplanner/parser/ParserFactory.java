package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.utils.Logger;

// factory to resolve schedule parser based on degree level and study mode
public class ParserFactory {

    // return matching parser implementation for study program
    public static ScheduleParser getParser(DegreeLevel degree, StudyMode mode) {
        Logger.d("ParserFactory.getParser", "Resolving parser for degree: " + degree + ", mode: " + mode);
        if (degree == DegreeLevel.BACHELORS) {
            if (mode == StudyMode.FULL_TIME) {
                return new BachelorFullTimeParser();
            } else {
                return new BachelorPartTimeParser(); // stub
            }
        } else if (degree == DegreeLevel.MASTERS || degree == DegreeLevel.LONG_MASTERS) {
            if (mode == StudyMode.FULL_TIME) {
                return new MasterFullTimeParser(); // stub
            } else {
                return new MasterPartTimeParser(); // stub
            }
        }
        Logger.e("ParserFactory.getParser", "Unsupported degree and mode combination: degree=" + degree + ", mode=" + mode);
        throw new IllegalArgumentException("Unsupported degree and mode combination");
    }
}