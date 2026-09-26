package com.vintro.wsplanner.parser;

import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Schedule;
import com.vintro.wsplanner.parser.excel.ExcelCell;
import com.vintro.wsplanner.parser.excel.ExcelRange;
import com.vintro.wsplanner.parser.excel.ExcelRow;
import com.vintro.wsplanner.parser.excel.ExcelSheet;
import com.vintro.wsplanner.parser.excel.ExcelWorkbook;
import com.vintro.wsplanner.utils.Logger;

import java.io.InputStream;
import java.text.Collator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BachelorFullTimeParser implements ScheduleParser {

    private String formatCellValue(ExcelCell cell) {
        return cell != null ? cell.asString() : "";
    }

    // daty: 02.03, 09.03
    private final Pattern datesPattern = Pattern.compile("daty:\\s*(.+)");

    // single date: 02.03
    private final Pattern singleDatePattern = Pattern.compile("(\\d{2}\\.\\d{2})");

    // default time in first col: 8:15-9:45 or 815-945 or 8.15-9.45
    private final Pattern defaultRowTimePattern = Pattern.compile("(\\d{1,2})[:.]?(\\d{2})\\s*[-–]\\s*(\\d{1,2})[:.]?(\\d{2})");

    // specific time range override: w godz. 8:15 - 9:45 or 10.00 - 14:15
    private final Pattern specificTimePattern = Pattern.compile("(?:godz)?\\.?\\s*(\\d{1,2}[:.]\\d{2})\\s*[-–]\\s*(\\d{1,2}[:.]\\d{2})");

    // specific start time override: od godz. 8:15 or od 8.15
    private final Pattern overrideStartTimePattern = Pattern.compile("od\\s*(?:godz)?\\.?\\s*(\\d{1,2}[:.]\\d{2})");

    // lesson duration in hours: 15h or 30h
    private final Pattern subjectTypeFilter = Pattern.compile("\\s*[-–—]?\\s*\\d{1,2}h\\b", Pattern.CASE_INSENSITIVE);

    // additional dates: +17.06 (środa) w godz. 17:00-21:15 or + 02.06 i 16.06 (wtorek)
    private final Pattern plusDatePattern = Pattern.compile("^\\+\\s*(\\d{2}\\.\\d{2})");

    public static String cleanLessonType(String raw) {
        if (raw == null) return null;
        // remove all occurrences of \d{1,2}h (e.g. 15h, 30h)
        String cleaned = raw.replaceAll("(?i)\\b\\d{1,2}h\\b|\\d{1,2}h", "").trim();
        // strip any remaining leading or trailing punctuation/dashes
        cleaned = cleaned.replaceAll("^[-–—\\s,.]+|[-–—\\s,.]+$", "").trim();
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("other") || cleaned.equalsIgnoreCase("inne")) {
            return null;
        }
        return cleaned;
    }

    private boolean isSeminarTeacherMatch(String line, String seminarTeacher) {
        if (seminarTeacher == null || seminarTeacher.trim().isEmpty() || line == null) {
            return false;
        }
        String lowerLine = line.toLowerCase().trim();
        String lowerTeacher = seminarTeacher.toLowerCase().trim();

        // match full teacher name
        if (lowerLine.contains(lowerTeacher)) {
            return true;
        }

        // extract name tokens without academic titles
        String cleaned = lowerTeacher.replaceAll("[.,]", " ");
        String[] tokens = cleaned.split("\\s+");
        List<String> nameTokens = new ArrayList<>();
        for (String t : tokens) {
            if (!t.isEmpty() && !t.equals("mgr") && !t.equals("dr") && !t.equals("inz") &&
                !t.equals("inż") && !t.equals("prof") && !t.equals("hab") &&
                !t.equals("doc") && !t.equals("lic") && t.length() > 2) {
                nameTokens.add(t);
            }
        }

        // Match if the last name (surname) is present in the line
        if (!nameTokens.isEmpty()) {
            String surname = nameTokens.get(nameTokens.size() - 1);
            if (lowerLine.contains(surname)) {
                return true;
            }
        }

        return false;
    }

    // room with "sala" prefix: sala 205, w sali A018, w salach 205
    private final Pattern roomSalaPattern = Pattern.compile("(?:w\\s+)?sal(?:a|i|ach)\\s+([a-zA-Z0-9]+)");

    // room with "s." prefix: w s. 215
    private final Pattern roomSPattern = Pattern.compile("s\\.\\s+([a-zA-Z0-9]+)");

    // academic year in header: rok akademicki 2025/2026
    private final Pattern academicYearPattern = Pattern.compile("rok akademicki\\s*(\\d{4})/(\\d{4})", Pattern.CASE_INSENSITIVE);

    // surname range in header: wg nazwisk: A-Ha or A-J or K-La
    private final Pattern surnameRangePattern = Pattern.compile("(?:nazwisk\\s*[:]?\\s*|\\b)([a-ząćęłńóśźż]+)\\s*-\\s*([a-ząćęłńóśźż]+)", Pattern.CASE_INSENSITIVE);

    int academicYearStart = LocalDate.now().getYear();
    int academicYearEnd = academicYearStart + 1;

    public void setAcademicYears(int start, int end) {
        this.academicYearStart = start;
        this.academicYearEnd = end;
    }

    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup) {
        return parse(excelStream, fieldOfStudy, semester, studentSurname, studentSpecialization, targetLangGroup, null);
    }

    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup, String seminarTeacher) {
        Logger.d("BachelorFullTimeParser.parse", "Starting schedule parsing. Field: '" + fieldOfStudy + "', Semester: " + semester + ", StudentSurname: '" + studentSurname + "', Specialization: '" + studentSpecialization + "', LangGroup: '" + targetLangGroup + "', SeminarTeacher: '" + seminarTeacher + "'");
        Schedule schedule = new Schedule(fieldOfStudy, semester, studentSurname);

        try (ExcelWorkbook workbook = ExcelWorkbook.create(excelStream)) {
            ExcelSheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                Logger.w("BachelorFullTimeParser.parse", "Sheet 0 is null");
                return schedule;
            }
            extractAcademicYear(sheet);

            // find which columns belong to the student based on surname and specialization
            List<Integer> targetColumns = findTargetColumns(sheet, studentSurname, studentSpecialization);
            if (targetColumns.isEmpty()) {
                Logger.w("BachelorFullTimeParser.findTargetColumns", "Target group columns not found for student: '" + studentSurname + "'");
                return schedule;
            }
            Logger.d("BachelorFullTimeParser.findTargetColumns", "Matched " + targetColumns.size() + " target column(s): " + targetColumns);

            // processed merged cells to avoid duplicating lessons
            Set<String> processedMergedRegions = new HashSet<>();

            // iterate rows starting from index 4 where schedule data begins
            for (int rowIndex = 4; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                ExcelRow row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // get default lesson time from the first column
                String rowTimeRaw = formatCellValue(row.getCell(0));
                LocalTime[] defaultTimes = parseRowTime(rowTimeRaw);
                if (defaultTimes == null) continue;

                for (int targetColumnIndex : targetColumns) {
                    CellTarget cellTarget = getMergedCellValue(sheet, rowIndex, targetColumnIndex);
                    if (cellTarget.value.trim().isEmpty()) continue;

                    // if cell is part of a merged region, process it once per column
                    if (cellTarget.mergedRegionId != null) {
                        String verticalId = cellTarget.mergedRegionId + "_col" + targetColumnIndex;
                        if (processedMergedRegions.contains(verticalId)) {
                            continue;
                        }
                        processedMergedRegions.add(verticalId);
                    }

                    Logger.d("BachelorFullTimeParser.parse", "Cell at [row=" + rowIndex + ", col=" + targetColumnIndex + "] (mergedRegion=" + cellTarget.mergedRegionId + ", defaultTime=" + defaultTimes[0] + "-" + defaultTimes[1] + "). Raw cell content:\n" + cellTarget.value);

                    // calc end time if cell takes multiple rows
                    LocalTime overrideEnd = defaultTimes[1];
                    if (cellTarget.lastRowIdx > rowIndex) {
                        ExcelRow lastRow = sheet.getRow(cellTarget.lastRowIdx);
                        if (lastRow != null) {
                            String lastRowTimeRaw = formatCellValue(lastRow.getCell(0));
                            LocalTime[] lastTimes = parseRowTime(lastRowTimeRaw);
                            if (lastTimes != null) overrideEnd = lastTimes[1];
                        }
                    }

                    // split cell to lesson blocks
                    List<String> lessonBlocks = splitIntoBlocks(cellTarget.value);

                    for (String block : lessonBlocks) {
                        List<Lesson> parsedLessons = parseLessonBlock(block, studentSurname, defaultTimes[0], overrideEnd, targetLangGroup, seminarTeacher);
                        schedule.addLessons(parsedLessons);
                    }
                }
            }
            Logger.i("BachelorFullTimeParser.parse", "Finished schedule parsing successfully. Total lessons extracted: " + schedule.getLessons().size());
        } catch (Exception e) {
            Logger.e("BachelorFullTimeParser.parse", "Error parsing Excel schedule: " + e.getMessage());
        }

        return schedule;
    }

    private boolean isTeacherDegreePrefix(String lower) {
        return lower.startsWith("mgr") || lower.startsWith("dr ") || lower.startsWith("dr.") ||
                lower.startsWith("prof") || lower.startsWith("ks.") || lower.startsWith("inż") ||
                lower.startsWith("inz");
    }

    public List<Lesson> parseLessonBlock(String block, String targetGroup, LocalTime defaultStart, LocalTime defaultEnd, String targetLangGroup, String seminarTeacher) {
        Logger.d("BachelorFullTimeParser.parseLessonBlock", "Parsing raw lesson block:\n" + block);
        List<Lesson> lessons = new ArrayList<>();
        String[] rawLines = block.split("\\n");
        if (rawLines.length == 0) return lessons;

        // Preprocess lines: merge continuation lines (e.g. "... zajęcia w formie" + "on-line")
        List<String> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            if (!lines.isEmpty()) {
                String last = lines.get(lines.size() - 1);
                String lastLower = last.toLowerCase().trim();
                String curLower = trimmed.toLowerCase();

                boolean isHangingEnd = lastLower.endsWith("w formie") || lastLower.endsWith("formie") ||
                                       lastLower.endsWith("w") || lastLower.endsWith("godz.") ||
                                       lastLower.endsWith("godz") || lastLower.endsWith("od") ||
                                       lastLower.endsWith("sali") || lastLower.endsWith("s.") ||
                                       lastLower.endsWith(",");

                boolean isContinuationStart = curLower.startsWith("on-line") || curLower.startsWith("online") ||
                                              curLower.startsWith("w godz") || curLower.startsWith("godz.") ||
                                              curLower.startsWith("od ") || curLower.startsWith("od godz") ||
                                              curLower.matches("\\d{1,2}:\\d{2}\\s*[-–]\\s*\\d{1,2}:\\d{2}.*");

                if (isHangingEnd || (lastLower.contains("w dn") && isContinuationStart)) {
                    lines.set(lines.size() - 1, last + " " + trimmed);
                    continue;
                }
            }
            lines.add(trimmed);
        }
        if (lines.isEmpty()) return lessons;

        String subjectName = "Unknown Subject";
        String lessonType = null;
        String globalTeacher = "Unknown Teacher";
        String globalRoom = "Unknown Room";

        // 1. Gather all subject header lines (multi-line subjects wrapped across lines)
        List<String> subjectHeaderLines = new ArrayList<>();
        int lineIdx = 0;
        while (lineIdx < lines.size()) {
            String curLine = lines.get(lineIdx);
            String curLower = curLine.toLowerCase().trim();
            if (curLower.startsWith("daty:") || curLower.startsWith("terminy:") ||
                    curLower.startsWith("sala") || curLower.contains("on-line") || curLower.contains("online") ||
                    curLower.contains("w siedzibie") || curLower.startsWith("gr.") || curLower.startsWith("+") ||
                    curLower.contains("godz") || curLower.matches(".*\\b\\d{1,2}:\\d{2}\\b.*") ||
                    curLower.matches(".*\\b\\d{1,2}\\.\\d{2}\\b.*") ||
                    isTeacherDegreePrefix(curLower)) {
                break;
            }
            subjectHeaderLines.add(curLine.trim());
            lineIdx++;
        }

        String fullSubjectHeader = String.join(" ", subjectHeaderLines).replaceAll("\\s+", " ").trim();
        if (fullSubjectHeader.isEmpty() && !lines.isEmpty()) {
            fullSubjectHeader = lines.get(0).trim();
            lineIdx = 1;
        }

        // extract subject name and lesson type from fullSubjectHeader
        if (fullSubjectHeader.contains(" - ") || fullSubjectHeader.contains(" – ")) {
            String delimiter = fullSubjectHeader.contains(" - ") ? " - " : " – ";
            int dashIndex = fullSubjectHeader.lastIndexOf(delimiter);
            subjectName = fullSubjectHeader.substring(0, dashIndex).trim();
            String lessonTypeRaw = fullSubjectHeader.substring(dashIndex + delimiter.length()).trim();
            lessonType = cleanLessonType(lessonTypeRaw);
        } else {
            subjectName = fullSubjectHeader;
            lessonType = null;
        }

        // Clean subjectName from Sp: prefix, duration hours, and whitespace
        subjectName = ParserUtils.cleanSubjectName(subjectName);
        Logger.d("BachelorFullTimeParser.parseLessonBlock", "Extracted Subject: '" + subjectName + "', LessonType: '" + lessonType + "'");

        LocalTime globalStart = defaultStart;
        LocalTime globalEnd = defaultEnd;

        // overrides for specific dates
        Map<String, LocalTime[]> dateTimeOverrides = new HashMap<>();
        Map<String, String> dateRoomOverrides = new HashMap<>();
        List<String> rawDates = new ArrayList<>();

        // state machine flag for english lektorat and seminar
        boolean readingMyGroup = true;
        boolean isSeminarSubject = subjectName.toLowerCase().contains("seminarium") || subjectName.toLowerCase().contains("dyplom");
        boolean seminarFound = false;

        while (lineIdx < lines.size()) {
            String line = lines.get(lineIdx++);
            String lowerLine = line.toLowerCase().trim();
            if (lowerLine.isEmpty()) continue;

            // lektoraty or seminar groups
            if (lowerLine.startsWith("gr.")) {
                if (isSeminarSubject) {
                    if (isSeminarTeacherMatch(lowerLine, seminarTeacher)) {
                        readingMyGroup = true;
                        seminarFound = true;
                        Logger.d("BachelorFullTimeParser.parseLessonBlock", "Matched seminar line: '" + line + "' with teacher target: '" + seminarTeacher + "'");
                        String[] parts = line.split("[-–]");
                        if (parts.length >= 2) globalTeacher = parts[1].trim();
                        if (parts.length >= 3) {
                            String room = extractRoomInfo(parts[2]);
                            if (room != null) globalRoom = room;
                        }
                    } else {
                        readingMyGroup = false;
                        Logger.d("BachelorFullTimeParser.parseLessonBlock", "Skipped non-matching seminar line: '" + line + "' (target: '" + seminarTeacher + "')");
                    }
                    continue;
                }

                String normalizedLine = lowerLine.replace(" ", "");
                String targetGr = targetLangGroup != null ? targetLangGroup.replace(" ", "").toLowerCase() : "";

                if (!targetGr.isEmpty() && (normalizedLine.startsWith("gr." + targetGr + "-") ||
                        normalizedLine.startsWith("gr." + targetGr + "–") ||
                        normalizedLine.contains("gr." + targetGr))) {
                    readingMyGroup = true; //  our group
                    Logger.d("BachelorFullTimeParser.parseLessonBlock", "Matched target lang group: '" + line + "' (target: '" + targetLangGroup + "')");
                    String[] parts = line.split("[-–]");
                    if (parts.length >= 2) globalTeacher = parts[1].trim();
                    if (parts.length >= 3) {
                        String room = extractRoomInfo(parts[2]);
                        if (room != null) globalRoom = room;
                    }
                } else {
                    readingMyGroup = false; // foreign group
                    Logger.d("BachelorFullTimeParser.parseLessonBlock", "Skipped foreign lang group line: '" + line + "' (target: '" + targetLangGroup + "')");
                }
                continue;
            }

            // dates or global info
            if (isSeminarSubject && seminarFound) {
                readingMyGroup = true;
            }

            // dates are parsed only if we are in our group section or global section
            if (!readingMyGroup) continue;

            if (lowerLine.startsWith("daty:") || lowerLine.startsWith("terminy:")) {
                Matcher dateMatcher = singleDatePattern.matcher(lowerLine);
                while (dateMatcher.find()) rawDates.add(dateMatcher.group(1));

                // Continue reading subsequent date lines (e.g. "18.01" or "01.12, 08.12")
                while (lineIdx < lines.size()) {
                    String next = lines.get(lineIdx);
                    String nextLower = next.toLowerCase().trim();
                    if (nextLower.startsWith("+") || nextLower.contains("w dn") || nextLower.contains("w dniu") ||
                            nextLower.contains("godz") || nextLower.startsWith("sala") || nextLower.contains("zajęcia") ||
                            nextLower.contains("online") || nextLower.contains("on-line") || nextLower.startsWith("gr.") ||
                            isTeacherDegreePrefix(nextLower)) {
                        break;
                    }
                    Matcher m = singleDatePattern.matcher(nextLower);
                    boolean found = false;
                    while (m.find()) {
                        rawDates.add(m.group(1));
                        found = true;
                    }
                    if (!found) break;
                    lineIdx++;
                }
                continue;
            }

            // additional dates (e.g. "+17.06 (środa) w godz. 17:00-21:15" or "+ 02.06 i 16.06 (wtorek)")
            // Ignore non-specific additions like "+4h w terminie uzgodnionym z prowadzącym"
            if (lowerLine.startsWith("+") && plusDatePattern.matcher(lowerLine).find()) {
                List<String> additionalDates = new ArrayList<>();
                Matcher m = singleDatePattern.matcher(lowerLine);
                while (m.find()) {
                    String d = m.group(1);
                    rawDates.add(d);
                    additionalDates.add(d);
                }

                // override start and end time if specified on this line
                Matcher tm = specificTimePattern.matcher(lowerLine);
                if (tm.find()) {
                    LocalTime s = parseTime(tm.group(1));
                    LocalTime e = parseTime(tm.group(2));
                    if (s != null && e != null) {
                        for (String d : additionalDates) dateTimeOverrides.put(d, new LocalTime[]{s, e});
                    }
                } else if (lowerLine.contains("od")) {
                    Matcher odTm = overrideStartTimePattern.matcher(lowerLine);
                    if (odTm.find()) {
                        LocalTime s = parseTime(odTm.group(1));
                        if (s != null) {
                            for (String d : additionalDates) dateTimeOverrides.put(d, new LocalTime[]{s, defaultEnd});
                        }
                    }
                }

                String roomOverride = extractRoomInfo(line);
                if (roomOverride != null) {
                    for (String d : additionalDates) dateRoomOverrides.put(d, roomOverride);
                }
                continue;
            }

            // overrides to specific dates (w dn. / w dniu / na zj.)
            if (lowerLine.contains("w dn") || lowerLine.contains("w dniu") || lowerLine.contains("na zj")) {
                List<String> specificDates = new ArrayList<>();
                Matcher m = singleDatePattern.matcher(lowerLine);
                while (m.find()) specificDates.add(m.group(1));

                // override start time
                Matcher tm = specificTimePattern.matcher(lowerLine);
                if (tm.find()) {
                    LocalTime s = parseTime(tm.group(1));
                    LocalTime e = parseTime(tm.group(2));
                    if (s != null && e != null) {
                        for (String d : specificDates) dateTimeOverrides.put(d, new LocalTime[]{s, e});
                    }
                } else if (lowerLine.contains("od")) {
                    Matcher odTm = overrideStartTimePattern.matcher(lowerLine);
                    if (odTm.find()) {
                        LocalTime s = parseTime(odTm.group(1));
                        if (s != null) {
                            for (String d : specificDates) dateTimeOverrides.put(d, new LocalTime[]{s, defaultEnd});
                        }
                    }
                }

                String roomOverride = extractRoomInfo(line);
                if (roomOverride != null) {
                    for (String d : specificDates) dateRoomOverrides.put(d, roomOverride);
                }
                continue;
            }

            boolean hasTime = specificTimePattern.matcher(lowerLine).find();
            boolean hasSingleTime = overrideStartTimePattern.matcher(lowerLine).find();

            // global time overrides
            if (!lowerLine.contains("w dn") && !lowerLine.contains("w dniu") && (lowerLine.contains("godz") || hasTime || hasSingleTime)) {
                Matcher tm = specificTimePattern.matcher(lowerLine);
                Matcher odTm = overrideStartTimePattern.matcher(lowerLine);
                if (tm.find()) {
                    globalStart = parseTime(tm.group(1));
                    globalEnd = parseTime(tm.group(2));
                } else if (odTm.find()) {
                    globalStart = parseTime(odTm.group(1));
                    globalEnd = defaultEnd; // end time from bottom of merged cell
                }
            }
            // global teacher and room
            else {
                if (isTeacherDegreePrefix(lowerLine)) {
                    List<String> tLines = new ArrayList<>();
                    tLines.add(line.trim());
                    while (lineIdx < lines.size()) {
                        String next = lines.get(lineIdx);
                        String nextLower = next.toLowerCase().trim();
                        if (nextLower.startsWith("daty:") || nextLower.startsWith("terminy:") ||
                                nextLower.startsWith("sala") || nextLower.contains("on-line") || nextLower.contains("online") ||
                                nextLower.contains("w siedzibie") || nextLower.startsWith("gr.") || nextLower.startsWith("+") ||
                                nextLower.contains("w dn") || nextLower.contains("w dniu") || isTeacherDegreePrefix(nextLower)) {
                            break;
                        }
                        tLines.add(next.trim());
                        lineIdx++;
                    }
                    globalTeacher = String.join(" ", tLines).replaceAll("\\s+", " ").trim();
                    globalTeacher = globalTeacher.replaceAll("(?i)\\s*,?\\s*prof\\.?\\s*wspa\\b.*", "").trim();
                    if (globalTeacher.isEmpty()) globalTeacher = "Unknown Teacher";
                } else if (lowerLine.startsWith("sala") || lowerLine.contains("on-line") || lowerLine.contains("online")
                        || lowerLine.contains("ul.") || lowerLine.contains("al.") || lowerLine.contains("lublin")
                        || lowerLine.contains("fit") || lowerLine.contains("siłownia") || lowerLine.contains("basen")
                        || lowerLine.matches(".*\\b\\d{2}-\\d{3}\\b.*") || lowerLine.contains("w siedzibie")) {
                    String potentialRoom = extractRoomInfo(line);
                    if (potentialRoom != null) {
                        if (globalRoom.equals("Unknown Room") || globalRoom.equals("WSPA")) {
                            globalRoom = potentialRoom;
                        } else if (globalRoom.equals("online") && potentialRoom.startsWith("sala")) {
                            // If global room was tentatively online, a concrete physical room is the base room
                            globalRoom = potentialRoom;
                        } else if (!potentialRoom.equals("online") && !globalRoom.contains(potentialRoom)) {
                            globalRoom += ", " + potentialRoom;
                        }
                    }
                } else if (!lowerLine.startsWith("+") && !lowerLine.contains("zajęcia") &&
                        !singleDatePattern.matcher(lowerLine).find() && !specificTimePattern.matcher(lowerLine).find() &&
                        globalRoom.equals("Unknown Room")) {
                    // capture custom location from line only if it's not a date/time/subject
                    if (!line.contains(subjectName)) {
                        globalRoom = line.trim();
                    }
                }
            }
        }

        // skip seminar if teacher not matched
        if (isSeminarSubject && !seminarFound) {
            Logger.d("BachelorFullTimeParser.parseLessonBlock", "Skipping seminar subject '" + subjectName + "': teacher not matched (target: " + seminarTeacher + ")");
            return lessons;
        }

        // create lesson objects
        if (!rawDates.isEmpty()) {
            for (String dateStr : rawDates) {
                LocalDate lessonDate = parseDateStr(dateStr);
                LocalTime finalStart = dateTimeOverrides.containsKey(dateStr) ? dateTimeOverrides.get(dateStr)[0] : globalStart;
                LocalTime finalEnd = dateTimeOverrides.containsKey(dateStr) ? dateTimeOverrides.get(dateStr)[1] : globalEnd;
                String finalRoom = dateRoomOverrides.getOrDefault(dateStr, globalRoom);

                lessons.add(new Lesson(subjectName, lessonType, globalTeacher, finalRoom, lessonDate, null, finalStart, finalEnd));
            }
        } else {
            lessons.add(new Lesson(subjectName, lessonType, globalTeacher, globalRoom, null, null, globalStart, globalEnd));
        }

        if (lessons.isEmpty()) {
            Logger.d("BachelorFullTimeParser.parseLessonBlock", "Block produced 0 lessons. Raw block:\n" + block);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Block parsed into ").append(lessons.size()).append(" lesson(s):");
            for (Lesson l : lessons) {
                sb.append("\n  -> [")
                  .append(l.getDate() != null ? l.getDate() : "No date").append(" ")
                  .append(l.getStartTime()).append("-").append(l.getEndTime())
                  .append("] '").append(l.getSubjectName())
                  .append("' (type=").append(l.getLessonType())
                  .append(", teacher=").append(l.getTeacherName())
                  .append(", room=").append(l.getRoom()).append(")");
            }
            Logger.d("BachelorFullTimeParser.parseLessonBlock", sb.toString());
        }

        return lessons;
    }

    private List<String> splitIntoBlocks(String rawCellText) {
        String[] potentialBlocks = rawCellText.split("\\n\\s*\\n");
        List<String> realBlocks = new ArrayList<>();
        StringBuilder currentBlock = new StringBuilder();

        for (String pBlock : potentialBlocks) {
            String firstLineLower = pBlock.trim().split("\\n")[0].toLowerCase();

            // check if this block is actually a continuation of the previous lesson
            boolean isContinuation = firstLineLower.startsWith("mgr") ||
                    firstLineLower.startsWith("dr") ||
                    firstLineLower.startsWith("prof") ||
                    firstLineLower.startsWith("ks.") ||
                    firstLineLower.startsWith("inż") ||
                    firstLineLower.startsWith("inz") ||
                    firstLineLower.startsWith("sala") ||
                    firstLineLower.startsWith("daty:") ||
                    firstLineLower.startsWith("terminy:") ||
                    firstLineLower.startsWith("w dn") ||
                    firstLineLower.startsWith("w dniu") ||
                    firstLineLower.startsWith("zj.") ||
                    firstLineLower.startsWith("od ") ||
                    firstLineLower.startsWith("na ") ||
                    firstLineLower.startsWith("gr.") ||
                    firstLineLower.startsWith("+") ||
                    firstLineLower.matches(".*\\b\\d{1,2}\\.\\d{2}\\b.*") ||
                    firstLineLower.contains("godz") ||
                    firstLineLower.contains("ul.") ||
                    firstLineLower.contains("lublin") ||
                    firstLineLower.contains("fit") ||
                    firstLineLower.startsWith("zajęcia");

            if (currentBlock.length() > 0 && isContinuation) {
                currentBlock.append("\n").append(pBlock.trim());
            } else {
                if (currentBlock.length() > 0) realBlocks.add(currentBlock.toString());
                currentBlock = new StringBuilder(pBlock.trim());
            }
        }
        if (currentBlock.length() > 0) realBlocks.add(currentBlock.toString());
        Logger.d("BachelorFullTimeParser.splitIntoBlocks", "Split raw cell into " + realBlocks.size() + " block(s)");
        return realBlocks;
    }

    private String extractRoomInfo(String line) {
        String lower = line.toLowerCase().trim();
        if (lower.contains("on-line") || lower.contains("online")) return "online";

        if (lower.equals("sala") || lower.equals("sala:") || lower.contains("w siedzibie uczelni") || lower.contains("w siedzibie")) {
            return "WSPA";
        }

        // format addresses and offsite venues by removing unrelated text
        if (lower.contains("ul.") || lower.contains("al.") || lower.contains("instytut")
                || lower.contains("lublin") || lower.contains("fit") || lower.contains("siłownia")
                || lower.contains("basen") || lower.matches(".*\\b\\d{2}-\\d{3}\\b.*")) {
            return line.replaceAll("(?i)w dn(?:[a-zA-Z]*\\.)?.*?zaj[eę]cia\\s+(?:w\\s+)?", "").trim();
        }

        Matcher mSala = roomSalaPattern.matcher(lower);
        if (mSala.find()) return "sala " + mSala.group(1).toUpperCase();

        Matcher mS = roomSPattern.matcher(lower);
        if (mS.find()) return "sala " + mS.group(1).toUpperCase();

        if (lower.startsWith("sala ")) return line.trim();

        return null;
    }

    private LocalDate parseDateStr(String dateStr) {
        try {
            String[] parts = dateStr.split("\\.");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = (month < 9) ? academicYearEnd : academicYearStart;
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private void extractAcademicYear(ExcelSheet sheet) {
        for (int r = 0; r <= 3; r++) {
            ExcelRow row = sheet.getRow(r);
            if (row != null) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    ExcelCell cell = row.getCell(c);
                    if (cell != null) {
                        String header = formatCellValue(cell);
                        Matcher yMatcher = academicYearPattern.matcher(header);
                        if (yMatcher.find()) {
                            academicYearStart = Integer.parseInt(yMatcher.group(1));
                            academicYearEnd = Integer.parseInt(yMatcher.group(2));
                            Logger.d("BachelorFullTimeParser.extractAcademicYear", "Academic years determined: " + academicYearStart + "/" + academicYearEnd + " from header: '" + header + "'");
                            return;
                        }
                    }
                }
            }
        }
    }

    private LocalTime parseTime(String timeRaw) {
        if (timeRaw == null) return LocalTime.of(0, 0);
        try {
            String normalized = timeRaw.trim().replace('.', ':');
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return LocalTime.of(0, 0);
        }
    }

    private List<Integer> findTargetColumns(ExcelSheet sheet, String studentSurname, String studentSpecialization) {
        List<Integer> cols = new ArrayList<>();
        
        int headerRowIdx = 3;
        for (int r = 1; r <= 4; r++) {
            ExcelRow row = sheet.getRow(r);
            if (row != null) {
                for (int c = 1; c < row.getLastCellNum(); c++) {
                    ExcelCell cell = row.getCell(c);
                    if (cell != null) {
                        String text = formatCellValue(cell).toLowerCase();
                        if (text.contains("gr.") || text.contains("grupa") || text.contains("nazwisk") || text.contains("sp.")) {
                            headerRowIdx = r;
                            break;
                        }
                    }
                }
            }
        }

        ExcelRow groupRow = sheet.getRow(headerRowIdx);
        boolean hasAnyGroupsDefined = false;

        if (groupRow != null) {
            int lastCellNum = groupRow.getLastCellNum();
            for (int i = 1; i < lastCellNum; i++) {
                CellTarget ct = getMergedCellValue(sheet, headerRowIdx, i);
                String rawHeader = ct.value != null ? ct.value.trim() : "";
                if (rawHeader.isEmpty()) {
                    for (int r = 1; r <= 3; r++) {
                        CellTarget altCt = getMergedCellValue(sheet, r, i);
                        if (altCt.value != null && !altCt.value.trim().isEmpty()) {
                            rawHeader = altCt.value.trim();
                            break;
                        }
                    }
                }
                if (rawHeader.isEmpty()) continue;
                hasAnyGroupsDefined = true;

                String headerNoNewline = rawHeader.toLowerCase().replace("\n", " ");
                boolean isTargetColumn = true;

                boolean isCommonGroup = headerNoNewline.startsWith("grupa ") || headerNoNewline.startsWith("gr.") || headerNoNewline.startsWith("gr ");
                boolean isSpecializationColumn = false;

                if (headerNoNewline.contains("sp.") || headerNoNewline.contains("specjalność") || headerNoNewline.contains("spec.")) {
                    isSpecializationColumn = true;
                } else if (!isCommonGroup) {
                    String firstLine = rawHeader.split("\n")[0].toLowerCase().trim();
                    if (!firstLine.startsWith("gr") && firstLine.length() > 8) {
                        isSpecializationColumn = true;
                    }
                }

                boolean hasStudentSpec = studentSpecialization != null && !studentSpecialization.trim().isEmpty();

                if (isSpecializationColumn) {
                    if (hasStudentSpec && headerNoNewline.contains(studentSpecialization.toLowerCase().trim())) {
                        isTargetColumn = true;
                    } else {
                        isTargetColumn = false;
                    }
                } else {
                    isTargetColumn = true;
                }

                if (isTargetColumn && (headerNoNewline.contains("nazwisk") || headerNoNewline.matches(".*\\b[a-ząćęłńóśźż]+-[a-ząćęłńóśźż]+\\b.*"))) {
                    if (studentSurname != null && !studentSurname.trim().isEmpty()) {
                        isTargetColumn = isSurnameInHeaderRange(studentSurname, headerNoNewline);
                    } else {
                        // default to group 1 if no surname
                        isTargetColumn = headerNoNewline.contains("gr.1") || headerNoNewline.contains("gr 1") || headerNoNewline.contains("grupa 1") || headerNoNewline.contains("a-");
                    }
                }

                if (isTargetColumn) {
                    cols.add(i);
                }
            }
        }

        if (cols.isEmpty() && groupRow != null) {
            for (int i = 1; i < groupRow.getLastCellNum(); i++) {
                CellTarget ct = getMergedCellValue(sheet, headerRowIdx, i);
                String raw = ct.value != null ? ct.value.toLowerCase() : "";
                if (raw.contains("gr.1") || raw.contains("gr 1") || raw.contains("grupa 1") || raw.contains("a-")) {
                    cols.add(i);
                }
            }
        }

        if (cols.isEmpty() && groupRow != null) {
            for (int i = 1; i <= Math.min(20, (int) groupRow.getLastCellNum()); i++) {
                cols.add(i);
            }
        }

        Logger.d("BachelorFullTimeParser.findTargetColumns", "Target columns selected: " + cols + " for surname='" + studentSurname + "', spec='" + studentSpecialization + "'");
        return cols;
    }

    private boolean isSurnameInHeaderRange(String surnameOrFullName, String headerText) {
        if (surnameOrFullName == null || surnameOrFullName.trim().isEmpty()) return false;

        Matcher matcher = surnameRangePattern.matcher(headerText.toLowerCase());

        if (matcher.find()) {
            String startBound = matcher.group(1);
            String endBound = matcher.group(2);

            String candidate = extractCandidateSurname(surnameOrFullName);
            if (!candidate.isEmpty()) {
                return isAlphabeticallyBetween(candidate, startBound, endBound);
            }
            return false;
        }

        return true;
    }

    public static String extractCandidateSurname(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.matches("(?i)s?\\d+")) return "";

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 1) {
            return tokens[0].replaceAll("[^a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ-]", "");
        }

        // uppercase token indicates surname
        for (String t : tokens) {
            String clean = t.replaceAll("[^a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ-]", "");
            if (clean.length() > 1 && clean.equals(clean.toUpperCase(new Locale("pl", "PL")))) {
                return clean;
            }
        }

        // fallback to last word as surname
        return tokens[tokens.length - 1].replaceAll("[^a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ-]", "");
    }

    private boolean isAlphabeticallyBetween(String surname, String startBound, String endBound) {
        if (surname == null || startBound == null || endBound == null) return false;
        surname = surname.toLowerCase().trim();
        startBound = startBound.toLowerCase().trim();
        endBound = endBound.toLowerCase().trim();
        if (surname.isEmpty() || startBound.isEmpty() || endBound.isEmpty()) return false;

        if (surname.startsWith(startBound) || surname.startsWith(endBound)) {
            return true;
        }

        Collator plCollator = Collator.getInstance(new Locale("pl", "PL"));
        plCollator.setStrength(Collator.PRIMARY);

        int compareStart = plCollator.compare(surname, startBound);

        if (endBound.equals("z") || endBound.equals("ż") || endBound.equals("ź")) {
            return compareStart >= 0;
        }

        String paddedEndBound = endBound + "\uFFFF";
        int compareEnd = plCollator.compare(surname, paddedEndBound);

        return compareStart >= 0 && compareEnd <= 0;
    }

    private LocalTime[] parseRowTime(String rawTime) {
        Matcher m = defaultRowTimePattern.matcher(rawTime.replaceAll("\\s+", ""));
        if (m.find()) {
            try {
                LocalTime start = LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                LocalTime end = LocalTime.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
                return new LocalTime[]{start, end};
            } catch (Exception ignored) {}
        }
        return null;
    }

    private CellTarget getMergedCellValue(ExcelSheet sheet, int rowIdx, int colIdx) {
        ExcelRow row = sheet.getRow(rowIdx);
        ExcelCell cell = (row != null) ? row.getCell(colIdx) : null;

        for (ExcelRange mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.isInRange(rowIdx, colIdx)) {
                ExcelRow firstRow = sheet.getRow(mergedRegion.getFirstRow());
                ExcelCell firstCell = (firstRow != null) ? firstRow.getCell(mergedRegion.getFirstColumn()) : null;
                String value = formatCellValue(firstCell);
                return new CellTarget(value, mergedRegion.formatAsString(), mergedRegion.getLastRow());
            }
        }
        return new CellTarget(formatCellValue(cell), null, rowIdx);
    }

    private static class CellTarget {
        String value;
        String mergedRegionId;
        int lastRowIdx;

        CellTarget(String value, String mergedRegionId, int lastRowIdx) {
            this.value = value;
            this.mergedRegionId = mergedRegionId;
            this.lastRowIdx = lastRowIdx;
        }
    }
}