package com.vintro.wsplanner.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Schedule;
//import com.vintro.wsplanner.utils.Logger;

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

public class BachelorFullTimeParser implements ScheduleParser {

    private final DataFormatter dataFormatter = new DataFormatter();

    // daty: 02.03, 09.03
    private final Pattern datesPattern = Pattern.compile("daty:\\s*(.+)");

    // single date: 02.03
    private final Pattern singleDatePattern = Pattern.compile("(\\d{2}\\.\\d{2})");

    // default time in first col: 8:15-9:45 or 815-945
    private final Pattern defaultRowTimePattern = Pattern.compile("(\\d{1,2}):?(\\d{2})\\s*[-–]\\s*(\\d{1,2}):?(\\d{2})");

    // specific time range override: w godz. 8:15 - 9:45
    private final Pattern specificTimePattern = Pattern.compile("(?:godz)?\\.?\\s*(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})");

    // specific start time override: od godz. 8:15
    private final Pattern overrideStartTimePattern = Pattern.compile("od\\s*(?:godz)?\\.?\\s*(\\d{1,2}:\\d{2})");

    // lesson duration in hours: 15h or 30h
    private final Pattern subjectTypeFilter = Pattern.compile("\\s+\\d{1,2}h");

    // room with "sali" prefix: w sali A018
    private final Pattern roomSalaPattern = Pattern.compile("sali\\s+([a-zA-Z0-9]+)");

    // room with "s." prefix: w s. 215
    private final Pattern roomSPattern = Pattern.compile("s\\.\\s+([a-zA-Z0-9]+)");

    // academic year in header: rok akademicki 2025/2026
    private final Pattern academicYearPattern = Pattern.compile("rok akademicki (\\d{4})/(\\d{4})");

    // surname range in header: nazwisk: A-Ha
    private final Pattern surnameRangePattern = Pattern.compile("nazwisk\\s*[:]?\\s*([a-ząćęłńóśźż]+)\\s*-\\s*([a-ząćęłńóśźż]+)");

    private int academicYearStart = LocalDate.now().getYear();
    private int academicYearEnd = academicYearStart + 1;

    @Override
    public Schedule parse(InputStream excelStream, String fieldOfStudy, int semester, String studentSurname, String studentSpecialization, String targetLangGroup) {
        Schedule schedule = new Schedule(fieldOfStudy, semester, studentSurname);

        try (Workbook workbook = WorkbookFactory.create(excelStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            extractAcademicYear(sheet);

            // find which columns belong to the student based on surname and specialization
            List<Integer> targetColumns = findTargetColumns(sheet, studentSurname, studentSpecialization);
            if (targetColumns.isEmpty()) {
                throw new IllegalArgumentException("Target group columns not found: " + studentSurname);
            }

            // processed merged cells to avoid duplicating lessons
            Set<String> processedMergedRegions = new HashSet<>();

            // iterate rows starting from index 4 where schedule data begins
            for (int rowIndex = 4; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                // get default lesson time from the first column
                String rowTimeRaw = dataFormatter.formatCellValue(row.getCell(0));
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

                    // calc end time if cell takes multiple rows
                    LocalTime overrideEnd = defaultTimes[1];
                    if (cellTarget.lastRowIdx > rowIndex) {
                        Row lastRow = sheet.getRow(cellTarget.lastRowIdx);
                        if (lastRow != null) {
                            String lastRowTimeRaw = dataFormatter.formatCellValue(lastRow.getCell(0));
                            LocalTime[] lastTimes = parseRowTime(lastRowTimeRaw);
                            if (lastTimes != null) overrideEnd = lastTimes[1];
                        }
                    }

                    // split cell to lesson blocks
                    List<String> lessonBlocks = splitIntoBlocks(cellTarget.value);

                    for (String block : lessonBlocks) {
                        List<Lesson> parsedLessons = parseLessonBlock(block, studentSurname, defaultTimes[0], overrideEnd, targetLangGroup);
                        schedule.addLessons(parsedLessons);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error parsing excel: " + e.getMessage());
        }

        return schedule;
    }

    private List<Lesson> parseLessonBlock(String block, String targetGroup, LocalTime defaultStart, LocalTime defaultEnd, String targetLangGroup) {
        List<Lesson> lessons = new ArrayList<>();
        String[] lines = block.split("\\n");
        if (lines.length == 0) return lessons;

        String subjectName = "Unknown Subject";
        String lessonType = "Other";
        String globalTeacher = "Unknown Teacher";
        String globalRoom = "Unknown Room";

        // merge first two lines
        String firstLine = lines[0].trim();
        if (firstLine.endsWith("-") && lines.length > 1) {
            firstLine = firstLine + " " + lines[1].trim();
            lines[1] = ""; // clear the second line so it is not parsed again
        }

        // extract subject name and lesson type from first line
        if (firstLine.contains(" - ") || firstLine.contains(" – ")) {
            String delimiter = firstLine.contains(" - ") ? " - " : " – ";
            int dashIndex = firstLine.lastIndexOf(delimiter);
            subjectName = firstLine.substring(0, dashIndex).trim();
            String lessonTypeRaw = firstLine.substring(dashIndex + delimiter.length()).trim();
            lessonType = lessonTypeRaw.replaceAll(subjectTypeFilter.pattern(), "").trim();
        } else {
            subjectName = firstLine;
        }

        LocalTime globalStart = defaultStart;
        LocalTime globalEnd = defaultEnd;

        // overrides for specific dates
        Map<String, LocalTime[]> dateTimeOverrides = new HashMap<>();
        Map<String, String> dateRoomOverrides = new HashMap<>();
        List<String> rawDates = new ArrayList<>();

        // state machine flag for english lekrorat
        boolean readingMyGroup = true;

        for (String line : lines) {
            String lowerLine = line.toLowerCase().trim();
            if (lowerLine.isEmpty()) continue;

            // lektoraty
            if (lowerLine.startsWith("gr.")) {
                String normalizedLine = lowerLine.replace(" ", "");
                String targetGr = targetLangGroup != null ? targetLangGroup.replace(" ", "").toLowerCase() : "";

                if (!targetGr.isEmpty() && (normalizedLine.startsWith("gr." + targetGr + "-") ||
                        normalizedLine.startsWith("gr." + targetGr + "–") ||
                        normalizedLine.contains("gr." + targetGr))) {
                    readingMyGroup = true; //  our group
                    String[] parts = line.split("[-–]");
                    if (parts.length >= 2) globalTeacher = parts[1].trim();
                    if (parts.length >= 3) {
                        String room = extractRoomInfo(parts[2]);
                        if (room != null) globalRoom = room;
                    }
                } else {
                    readingMyGroup = false; // foreign group
                }
                continue;
            }

            // dates are parsed only if we are in our group section or global section
            if (!readingMyGroup) continue;

            if (lowerLine.startsWith("daty:")) {
                Matcher dateMatcher = singleDatePattern.matcher(lowerLine);
                while (dateMatcher.find()) rawDates.add(dateMatcher.group(1));
                continue;
            }

            // overrides to specific dates (w dn.)
            if (lowerLine.contains("w dn")) {
                List<String> specificDates = new ArrayList<>();
                Matcher m = singleDatePattern.matcher(lowerLine);
                while (m.find()) specificDates.add(m.group(1));

                // override start time
                Matcher tm = specificTimePattern.matcher(lowerLine);
                if (tm.find()) {
                    LocalTime s = parseTime(tm.group(1));
                    LocalTime e = parseTime(tm.group(2));
                    for (String d : specificDates) dateTimeOverrides.put(d, new LocalTime[]{s, e});
                } else if (lowerLine.contains("od")) {
                    Matcher odTm = overrideStartTimePattern.matcher(lowerLine);
                    if (odTm.find()) {
                        LocalTime s = parseTime(odTm.group(1));
                        for (String d : specificDates) dateTimeOverrides.put(d, new LocalTime[]{s, defaultEnd});
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
            if (!lowerLine.contains("w dn") && (lowerLine.contains("godz") || hasTime || hasSingleTime)) {
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
                if (lowerLine.startsWith("mgr") || lowerLine.startsWith("dr") || lowerLine.startsWith("prof") || lowerLine.startsWith("ks.")) {
                    globalTeacher = line.trim();
                } else if (lowerLine.startsWith("sala") || lowerLine.contains("on-line") || lowerLine.contains("online") || lowerLine.contains("ul.") || lowerLine.contains("lublin")) {
                    String potentialRoom = extractRoomInfo(line);
                    if (potentialRoom != null) {
                        if (globalRoom.equals("Unknown Room")) globalRoom = potentialRoom;
                        else if (!globalRoom.contains(potentialRoom)) globalRoom += ", " + potentialRoom;
                    }
                } else if (!lowerLine.startsWith("+") && !lowerLine.contains("zajęcia") && globalRoom.equals("Unknown Room")) {
                    // fallback to capture custom locations like fitness clubs and clear it from the subject name
                    if (!line.contains(subjectName)) {
                        globalRoom = line.trim();
                    }
                }
            }
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
                    firstLineLower.startsWith("sala") ||
                    firstLineLower.startsWith("daty:") ||
                    firstLineLower.startsWith("w dn") ||
                    firstLineLower.startsWith("zj.") ||
                    firstLineLower.startsWith("od ") ||
                    firstLineLower.startsWith("na ") ||
                    firstLineLower.startsWith("gr.") ||
                    firstLineLower.startsWith("+") ||
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
        return realBlocks;
    }

    private String extractRoomInfo(String line) {
        String lower = line.toLowerCase().trim();
        if (lower.contains("on-line") || lower.contains("online")) return "online";

        Matcher mSala = roomSalaPattern.matcher(lower);
        if (mSala.find()) return "sala " + mSala.group(1).toUpperCase();

        Matcher mS = roomSPattern.matcher(lower);
        if (mS.find()) return "sala " + mS.group(1).toUpperCase();

        if (lower.startsWith("sala ")) return line.trim();

        // format addresses by removing unrelated text
        if (lower.contains("ul.") || lower.contains("instytut") || lower.contains("lublin")) {
            return line.replaceAll("(?i)w dn(?:[a-zA-Z]*\\.)?.*?zaj[eę]cia\\s+(?:w\\s+)?", "").trim();
        }
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

    private void extractAcademicYear(Sheet sheet) {
        Row row = sheet.getRow(0);
        if (row != null) {
            String header = dataFormatter.formatCellValue(row.getCell(0));
            Matcher yMatcher = academicYearPattern.matcher(header);
            if (yMatcher.find()) {
                academicYearStart = Integer.parseInt(yMatcher.group(1));
                academicYearEnd = Integer.parseInt(yMatcher.group(2));
            }
        }
    }

    private LocalTime parseTime(String timeRaw) {
        try {
            return LocalTime.parse(timeRaw, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeParseException e) {
            return LocalTime.of(0, 0);
        }
    }

    private List<Integer> findTargetColumns(Sheet sheet, String studentSurname, String studentSpecialization) {
        List<Integer> cols = new ArrayList<>();
        Row groupRow = sheet.getRow(3);

        if (groupRow != null) {
            for (Cell cell : groupRow) {
                // skip column A
                if (cell.getColumnIndex() == 0) {
                    continue;
                }
                String header = dataFormatter.formatCellValue(cell).toLowerCase().replace("\n", " ");
                if (header.trim().isEmpty()) continue;

                boolean isTargetColumn = true;

                // check if the column matches the student specialization
                boolean hasStudentSpec = studentSpecialization != null && !studentSpecialization.isEmpty()
                        && header.contains(studentSpecialization.toLowerCase());

                // keywords to identify if a column belongs to any specialization
                boolean isSomeSpecialization = header.contains("sp.") || header.contains("specjalność") ||
                        header.contains("webowe") || header.contains("mobilne") ||
                        header.contains("grafika") || header.contains("cyberbezpieczeństwo") ||
                        header.contains("sztuczna inteligencja") || header.contains("data");

                if (hasStudentSpec) {
                    //  student specialization
                    isTargetColumn = true;
                } else if (isSomeSpecialization) {
                    // specialization but not student's
                    isTargetColumn = false;
                } else {
                    // general subject without specialization
                    isTargetColumn = true;
                }

                // check the surname range
                if (isTargetColumn && header.contains("nazwisk")) {
                    isTargetColumn = isSurnameInHeaderRange(studentSurname, header);
                }

                // add column index if all checks passed
                if (isTargetColumn) {
                    cols.add(cell.getColumnIndex());
                }
            }
        }
        return cols;
    }

    private boolean isSurnameInHeaderRange(String surname, String headerText) {
        if (surname == null || surname.trim().isEmpty()) return false;

        Matcher matcher = surnameRangePattern.matcher(headerText.toLowerCase());

        if (matcher.find()) {
            String startBound = matcher.group(1);
            String endBound = matcher.group(2);
            return isAlphabeticallyBetween(surname, startBound, endBound);
        }

        // fallback to true if range cannot be parsed to avoid leaving the student without a schedule
        System.out.println("Warning: Unable to parse surname range from header: " + headerText);
        return true;
    }

    private boolean isAlphabeticallyBetween(String surname, String startBound, String endBound) {
        surname = surname.toLowerCase().trim();
        startBound = startBound.toLowerCase().trim();
        endBound = endBound.toLowerCase().trim();

        // use polish collator to ensure correct alphabetical sorting of special characters
        Collator plCollator = Collator.getInstance(new Locale("pl", "PL"));
        // ignore case differences
        plCollator.setStrength(Collator.PRIMARY);

        // quick check for exact match with boundaries
        if (surname.startsWith(startBound) || surname.startsWith(endBound)) {
            return true;
        }

        int compareStart = plCollator.compare(surname, startBound);

        //  append maximum unicode value to include names starting with the bound letter
        // if bounds are A-L, and surname is 'La...'
        // by default 'La' > 'L', but 'La\uFFFF' < 'L', and it will fit
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

    private CellTarget getMergedCellValue(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        Cell cell = (row != null) ? row.getCell(colIdx) : null;

        for (CellRangeAddress mergedRegion : sheet.getMergedRegions()) {
            if (mergedRegion.isInRange(rowIdx, colIdx)) {
                Row firstRow = sheet.getRow(mergedRegion.getFirstRow());
                Cell firstCell = firstRow.getCell(mergedRegion.getFirstColumn());
                String value = dataFormatter.formatCellValue(firstCell);
                return new CellTarget(value, mergedRegion.formatAsString(), mergedRegion.getLastRow());
            }
        }
        return new CellTarget(dataFormatter.formatCellValue(cell), null, rowIdx);
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