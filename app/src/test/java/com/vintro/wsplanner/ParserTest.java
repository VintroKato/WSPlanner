package com.vintro.wsplanner;

import org.junit.Test;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import com.vintro.wsplanner.parser.BachelorFullTimeParser;
import com.vintro.wsplanner.parser.excel.ExcelCell;
import com.vintro.wsplanner.parser.excel.ExcelRange;
import com.vintro.wsplanner.parser.excel.ExcelRow;
import com.vintro.wsplanner.parser.excel.ExcelSheet;
import com.vintro.wsplanner.parser.excel.ExcelWorkbook;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.models.Schedule;
import static org.junit.Assert.*;

public class ParserTest {

    @Test
    public void testExcelParser() throws Exception {
        // load test file from resources
        InputStream is = getClass().getClassLoader().getResourceAsStream("hard_Informatyka - studia I stopnia - st III - semestr zimowy (11).xlsx");
        assertNotNull("File not found", is);

        BachelorFullTimeParser parser = new BachelorFullTimeParser();

        // parse schedule with study params
        Schedule schedule = parser.parse(is, "Informatyka", 6, "Lar", "webowe", "2");

        // print parsed result
        System.out.println("====== RESULT ======");
        System.out.println(schedule.toString());
        System.out.println("Found lessond: " + schedule.getLessons().size());

        // verify lessons were parsed
        assertTrue(schedule.getLessons().size() > 0);
    }

    @Test
    public void testFile1() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx");
        assertNotNull(is);
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        // find cell containing target subject
        try (ExcelWorkbook wb = ExcelWorkbook.create(
                getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx")
        )) {
            ExcelSheet sheet = wb.getSheetAt(0);
            for (int r = 0; r <= 30; r++) {
                ExcelRow row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    ExcelCell cell = row.getCell(c);
                    String val = cell != null ? cell.asString() : "";
                    if (val.contains("Projekt zespołowy")) {
                        System.out.println("CELL at r=" + r + " c=" + c + ":\n" + val);
                    }
                }
            }
        }
        Schedule sKowalski = parser.parse(is, "Informatyka", 6, "Kowalski", null, null);
        System.out.println("=== KOWALSKI LESSONS COUNT: " + sKowalski.getLessons().size() + " ===");
        for (com.vintro.wsplanner.models.Lesson l : sKowalski.getLessons()) {
            if (l.getDate() != null && l.getDate().toString().contains("03-06")) {
                System.out.println("06.03: " + l);
            }
            if (l.getSubjectName().contains("Projekt zespołowy")) {
                System.out.println("PZ: " + l);
            }
        }
    }

    @Test
    public void testPrintAllHeaders() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx");
        ExcelWorkbook wb = ExcelWorkbook.create(is);
        ExcelSheet s = wb.getSheetAt(0);
        System.out.println("=== SEARCH SEMINARIUM IN FILE 1 ===");
        for (int r = 0; r <= s.getLastRowNum(); r++) {
            ExcelRow row = s.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                ExcelCell cell = row.getCell(c);
                String val = cell != null ? cell.asString() : "";
                if (val.toLowerCase().contains("seminarium")) {
                    System.out.println("FOUND SEMINARIUM at r=" + r + " c=" + c + ":\n" + val);
                    // check column headers
                    for (int hr = 0; hr <= 4; hr++) {
                        ExcelRow hrow = s.getRow(hr);
                        if (hrow != null) {
                            ExcelCell hcell = hrow.getCell(c);
                            System.out.println("Header at r=" + hr + " c=" + c + ": " + (hcell != null ? hcell.asString() : ""));
                        }
                    }
                    // check merged region
                    for (ExcelRange region : s.getMergedRegions()) {
                        if (region.isInRange(r, c)) {
                            System.out.println("Merged region for r=" + r + " c=" + c + ": " + region.formatAsString());
                        }
                    }
                }
            }
        }
        wb.close();
    }

    @Test
    public void testTargetColumns() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx");
        ExcelWorkbook wb = ExcelWorkbook.create(is);
        ExcelSheet s = wb.getSheetAt(0);

        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        java.lang.reflect.Method m = BachelorFullTimeParser.class.getDeclaredMethod("findTargetColumns", ExcelSheet.class, String.class, String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.List<Integer> colsKowalski = (java.util.List<Integer>) m.invoke(parser, s, "Jan Kowalski", null);
        // kowalski belongs to group 2
        org.junit.Assert.assertTrue(colsKowalski.contains(2));
        org.junit.Assert.assertFalse(colsKowalski.contains(1)); // verify group 1 is not selected

        @SuppressWarnings("unchecked")
        java.util.List<Integer> colsAdamczyk = (java.util.List<Integer>) m.invoke(parser, s, "Adamczyk", null);
        // adamczyk belongs to group 1
        org.junit.Assert.assertTrue(colsAdamczyk.contains(1));
        org.junit.Assert.assertFalse(colsAdamczyk.contains(2));

        wb.close();
    }

    @Test
    public void testLessonsFor0603() throws Exception {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        String sn = "Kowalski";
        InputStream is = getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx");
        Schedule sch = parser.parse(is, "Informatyka", 5, sn, null, null);

        // verify mixed online and offline locations
        boolean foundSala = false;
        boolean foundOnline = false;
        for (Lesson l : sch.getLessons()) {
            if (l.getSubjectName().contains("Projekt zespołowy")) {
                if (l.getDate().equals(java.time.LocalDate.of(2026, 3, 5))) {
                    org.junit.Assert.assertEquals("Sala 205", l.getLocation().getDisplayText());
                    foundSala = true;
                } else if (l.getDate().equals(java.time.LocalDate.of(2026, 3, 12))) {
                    org.junit.Assert.assertTrue(l.getLocation().isOnline());
                    foundOnline = true;
                }
            }
        }
        org.junit.Assert.assertTrue(foundSala);
        org.junit.Assert.assertTrue(foundOnline);

        // verify lesson count without duplicates
        java.time.LocalDate d0603 = java.time.LocalDate.of(2026, 3, 6);
        int count0603 = 0;
        for (Lesson l : sch.getLessons()) {
            if (d0603.equals(l.getDate())) {
                count0603++;
            }
        }
        org.junit.Assert.assertEquals(2, count0603);
    }

    @Test
    public void testSeminarParsing() throws Exception {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        InputStream is1 = getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx");
        
        // verify no seminar added without teacher
        Schedule schNoTeacher = parser.parse(is1, "Informatyka", 6, "Kowalski", null, null, null);
        for (Lesson l : schNoTeacher.getLessons()) {
            org.junit.Assert.assertFalse(l.getSubjectName().toLowerCase().contains("seminarium"));
        }

        // verify seminar parsed for selected teacher
        InputStream is2 = getClass().getClassLoader().getResourceAsStream("Informatyka - studia I stopnia - st III - semestr letni (7).xlsx");
        Schedule schKalisz = parser.parse(is2, "Informatyka", 6, "Kowalski", null, null, "dr Michał Kalisz");
        List<Lesson> seminarLessons = new ArrayList<>();
        for (Lesson l : schKalisz.getLessons()) {
            if (l.getSubjectName().toLowerCase().contains("seminarium")) {
                seminarLessons.add(l);
            }
        }
        // verify seminar lesson count and details
        org.junit.Assert.assertEquals(5, seminarLessons.size());
        org.junit.Assert.assertEquals("dr Michał Kalisz", seminarLessons.get(0).getTeacherName());
        org.junit.Assert.assertTrue(seminarLessons.get(0).getLocation().isOnline());
        // verify lesson type cleaned
        org.junit.Assert.assertNull(seminarLessons.get(0).getLessonType());
    }

    @Test
    public void testCleanLessonType() {
        assertNull(BachelorFullTimeParser.cleanLessonType("15h"));
        assertNull(BachelorFullTimeParser.cleanLessonType(" 15h "));
        assertNull(BachelorFullTimeParser.cleanLessonType("30H"));
        assertNull(BachelorFullTimeParser.cleanLessonType("- 15h"));
        assertNull(BachelorFullTimeParser.cleanLessonType(null));
        assertNull(BachelorFullTimeParser.cleanLessonType(""));

        assertEquals("wykład", BachelorFullTimeParser.cleanLessonType("wykład 15h"));
        assertEquals("laboratorium", BachelorFullTimeParser.cleanLessonType("laboratorium - 30h"));
        assertEquals("ćwiczenia", BachelorFullTimeParser.cleanLessonType("ćwiczenia 15h"));
    }

    @Test
    public void testAdditionalDateParsingCases() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        parser.setAcademicYears(2025, 2026);

        // case 1: added date with custom hours
        String case1 = "Sp.: Projektowanie i konfiguracja sieci komputerowych zorientowana na bezpieczeństwo cz. 2 - laboratorium 40h\n" +
                "mgr Piotr Janiec\n" +
                "daty: 07.04, 14.04, 21.04, 28.04, 05.05, 12.05, 19.05, 26.05, 09.06\n" +
                "w dniach 12.05, 19.05, 26.05, 09.06 zajecia w godz. 17:00-21:15\n" +
                "+17.06 (środa) w godz. 17:00-21:15\n" +
                "sala 207\n" +
                "zajęcia w siedzibie Uczelni";

        List<Lesson> lessons1 = parser.parseLessonBlock(case1, null, LocalTime.of(8, 15), LocalTime.of(9, 45), null, null);
        // 9 base dates plus 1 added date
        assertEquals(10, lessons1.size());

        Lesson l1706 = null;
        for (Lesson l : lessons1) {
            if (l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 6, 17))) {
                l1706 = l;
                break;
            }
        }
        assertNotNull(l1706);
        assertEquals(LocalTime.of(17, 0), l1706.getStartTime());
        assertEquals(LocalTime.of(21, 15), l1706.getEndTime());
        assertEquals("mgr Piotr Janiec", l1706.getTeacherName());
        assertEquals("Sala 207", l1706.getLocation().getDisplayText());
        assertEquals("laboratorium", l1706.getLessonType());

        // case 2: multiple added dates
        String case2 = "Sp.: Projektowanie i konfiguracja sieci komputerowych zorientowana na bezpieczeństwo cz. II - wykład 20h\n" +
                "mgr Piotr Janiec\n" +
                "daty: 05.03, 12.03, 09.04, 16.04, 23.04, 07.05, 14.05, 21.05\n" +
                "+ 02.06 i 16.06 (wtorek)\n" +
                "zajęcia on-line";

        List<Lesson> lessons2 = parser.parseLessonBlock(case2, null, LocalTime.of(17, 50), LocalTime.of(19, 30), null, null);
        // 8 base dates plus 2 added dates
        assertEquals(10, lessons2.size());

        boolean found0206 = false;
        boolean found1606 = false;
        for (Lesson l : lessons2) {
            if (l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 6, 2))) {
                found0206 = true;
                assertEquals(LocalTime.of(17, 50), l.getStartTime());
                assertEquals(LocalTime.of(19, 30), l.getEndTime());
                assertTrue(l.getLocation().isOnline());
            }
            if (l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 6, 16))) {
                found1606 = true;
                assertEquals(LocalTime.of(17, 50), l.getStartTime());
                assertEquals(LocalTime.of(19, 30), l.getEndTime());
                assertTrue(l.getLocation().isOnline());
            }
        }
        assertTrue(found0206);
        assertTrue(found1606);

        // case 3: ignored hour entry
        String case3 = "Bezpieczeństwo systemów informatycznych - wykład 20h\n" +
                "mgr Agnieszka Paradzińska\n" +
                "daty: 06.03, 20.03, 10.04, 17.04, 24.04, 22.05, 29.05, 12.06\n" +
                "+4h w terminie uzgodnionym z prowadzącym\n" +
                "zajęcia on-line";

        List<Lesson> lessons3 = parser.parseLessonBlock(case3, null, LocalTime.of(16, 5), LocalTime.of(17, 45), null, null);
        // only base dates should be parsed
        assertEquals(8, lessons3.size());
        for (Lesson l : lessons3) {
            assertEquals("mgr Agnieszka Paradzińska", l.getTeacherName());
            assertEquals("wykład", l.getLessonType());
            assertTrue(l.getLocation().isOnline());
            assertNotNull(l.getDate());
        }

        // case 4: dot as time separator and ignored hour entry
        String case4 = "Inżynieria oprogramowania - projekt 30h\n" +
                "mgr Małgorzata Wieleba\n" +
                "daty: 02.03, 09.03, 16.03, 23.03, 30.03, 13.04, 20.04, 27.04, 11.05\n" +
                "w dn. 27.04 zajecia w godz. 10.00 - 14:15\n" +
                "+1h w terminie ustalonym z prowadzacą \n" +
                "sala 209\n" +
                "zajęcia w siedzibie Uczelni";

        List<Lesson> lessons4 = parser.parseLessonBlock(case4, null, LocalTime.of(10, 0), LocalTime.of(11, 30), null, null);
        assertEquals(9, lessons4.size());

        Lesson l2704 = null;
        for (Lesson l : lessons4) {
            if (l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 4, 27))) {
                l2704 = l;
                break;
            }
        }
        assertNotNull(l2704);
        assertEquals(LocalTime.of(10, 0), l2704.getStartTime());
        assertEquals(LocalTime.of(14, 15), l2704.getEndTime());
        assertEquals("Sala 209", l2704.getLocation().getDisplayText());
        assertEquals("mgr Małgorzata Wieleba", l2704.getTeacherName());
        assertEquals("projekt", l2704.getLessonType());
    }

    @Test
    public void testPhysicalEducationAddressParsing() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        String peBlock = "Wychowanie fizyczne - \n" +
                "ćwiczenia 30h\n" +
                "w godz. 13:00-15:15\n" +
                "daty: 23.03, 30.03, 13.04, 20.04, 27.04, 04.05, 11.05, 18.05, 25.05, 01.06\n" +
                "7 Fit\n" +
                "ul. Jana Pawła II 17, 20-535 Lublin";

        List<Lesson> lessons = parser.parseLessonBlock(peBlock, null, LocalTime.of(13, 0), LocalTime.of(15, 15), null, null);
        assertEquals(10, lessons.size());

        for (Lesson lesson : lessons) {
            assertEquals("Wychowanie fizyczne", lesson.getSubjectName());
            assertEquals("ćwiczenia", lesson.getLessonType());
            assertFalse(lesson.hasTeacher());
            assertEquals(LocalTime.of(13, 0), lesson.getStartTime());
            assertEquals(LocalTime.of(15, 15), lesson.getEndTime());

            com.vintro.wsplanner.models.Location loc = lesson.getLocation();
            assertNotNull(loc);
            assertTrue(loc.isOffsite());
            assertFalse(loc.getDisplayText().contains("Sala 535"));
            assertTrue(loc.getDisplayText().contains("ul. Jana Pawła II 17, 20-535 Lublin"));
            assertTrue(loc.hasMapLink());
        }
    }

    @Test
    public void testLocationParsing() {
        com.vintro.wsplanner.models.Location loc1 = new com.vintro.wsplanner.models.Location("7 Fit\nul. Jana Pawła II 17, 20-535 Lublin");
        assertTrue(loc1.isOffsite());
        assertFalse(loc1.getDisplayText().contains("Sala 535"));
        assertTrue(loc1.getDisplayText().contains("20-535 Lublin"));
        assertNull(loc1.getRoomNumber());

        com.vintro.wsplanner.models.Location loc2 = new com.vintro.wsplanner.models.Location("ul. Jana Pawła II 17, 20-535 Lublin");
        assertTrue(loc2.isOffsite());
        assertEquals("ul. Jana Pawła II 17, 20-535 Lublin", loc2.getDisplayText());
        assertNull(loc2.getRoomNumber());

        com.vintro.wsplanner.models.Location loc3 = new com.vintro.wsplanner.models.Location("sala 211");
        assertFalse(loc3.isOffsite());
        assertEquals("Sala 211", loc3.getDisplayText());
        assertEquals("211", loc3.getRoomNumber());

        com.vintro.wsplanner.models.Location loc4 = new com.vintro.wsplanner.models.Location("206");
        assertFalse(loc4.isOffsite());
        assertEquals("Sala 206", loc4.getDisplayText());
        assertEquals("206", loc4.getRoomNumber());

        com.vintro.wsplanner.models.Location locDate = new com.vintro.wsplanner.models.Location("18.01");
        assertFalse(locDate.isOffsite());
        assertEquals("WSPA", locDate.getDisplayText());
        assertNull(locDate.getMapQueryUrl());
    }

    @Test
    public void testJavaSubjectAndMultilineTeacher() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        String rawBlock = "Sp.: Programowanie w \n" +
                "języku JAVA - \n" +
                "laboratorium 30h\n" +
                "dr Barbara \n" +
                "Gocłowska, prof. \n" +
                "WSPA\n" +
                "daty: 06.10, 13.10, \n" +
                "20.10, 27.10, 03.11, \n" +
                "10.11, 17.11, 24.11, \n" +
                "01.12, 08.12\n" +
                "sala\n" +
                "zajęcia w siedzibie \n" +
                "Uczelni";

        List<Lesson> lessons = parser.parseLessonBlock(rawBlock, null, LocalTime.of(17, 0), LocalTime.of(19, 30), null, null);
        assertEquals(10, lessons.size());
        Lesson first = lessons.get(0);
        assertEquals("Programowanie w języku JAVA", first.getSubjectName());
        assertEquals("laboratorium", first.getLessonType());
        assertEquals("dr Barbara Gocłowska", first.getTeacherName());
        assertEquals("WSPA", first.getLocation().getDisplayText());
        assertFalse(first.getLocation().hasMapLink());
    }

    @Test
    public void testBareSalaAndDateContinuationNotLocation() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        String rawBlock = "Programowanie - \n" +
                "laboratorium 40h\n" +
                "mgr inż. Pior Wójcicki\n" +
                "daty: 05.10, 12.10, 19.10, 26.10, 02.11, 09.11, 16.11, 23.11, 30.11, 07.12, 14.12, 21.12, 11.01\n" +
                "18.01\n" +
                "w dniu 18.01 zajęcia w godz. 16:05-16:50\n" +
                "sala\n" +
                "zajęcia w siedzibie \n" +
                "Uczelni";

        List<Lesson> lessons = parser.parseLessonBlock(rawBlock, null, LocalTime.of(16, 5), LocalTime.of(18, 35), null, null);
        // 13 dates in first line + 18.01 = 14 dates
        assertEquals(14, lessons.size());
        Lesson first = lessons.get(0);
        assertEquals("Programowanie", first.getSubjectName());
        assertEquals("laboratorium", first.getLessonType());
        assertEquals("mgr inż. Pior Wójcicki", first.getTeacherName());
        assertEquals("WSPA", first.getLocation().getDisplayText());
        assertFalse(first.getLocation().hasMapLink());

        // verify 18.01 lesson
        Lesson jan18 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().getMonthValue() == 1 && l.getDate().getDayOfMonth() == 18).findFirst().orElse(null);
        assertNotNull(jan18);
        assertEquals(LocalTime.of(16, 5), jan18.getStartTime());
        assertEquals(LocalTime.of(16, 50), jan18.getEndTime());
        assertEquals("WSPA", jan18.getLocation().getDisplayText());
    }

    @Test
    public void testRokZerowyMultipleDatyClausesWithDifferentTimes() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        parser.setAcademicYears(2026, 2027);

        // Środa from Rok zerowy screenshot:
        String rawBlock = "Język polski - słownictwo i komunikacja ustna - 50h\n" +
                "dr Agnieszka Mehmegdolu\n" +
                "daty: 14.10 w godz. 11:45-16:00\n" +
                "daty: 04.11, 25.11, 02.12, 09.12, 16.12, 13.01, 20.01, 27.01 w godz. 13:30-16:00\n" +
                "sala 111\n" +
                "zajęcia w siedzibie Uczelni";

        List<Lesson> lessons = parser.parseLessonBlock(rawBlock, null, LocalTime.of(11, 45), LocalTime.of(16, 0), null, null);
        // 1 date on first daty line + 8 dates on second daty line = 9 lessons
        assertEquals(9, lessons.size());

        // Check 14.10 lesson
        Lesson oct14 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 10, 14))).findFirst().orElse(null);
        assertNotNull(oct14);
        assertEquals(LocalTime.of(11, 45), oct14.getStartTime());
        assertEquals(LocalTime.of(16, 0), oct14.getEndTime());
        assertEquals("Sala 111", oct14.getLocation().getDisplayText());
        assertEquals("dr Agnieszka Mehmegdolu", oct14.getTeacherName());
        assertEquals("Język polski - słownictwo i komunikacja ustna", oct14.getSubjectName());

        // Check 04.11 and 27.01 lessons (have 13:30 - 16:00)
        Lesson nov04 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 11, 4))).findFirst().orElse(null);
        assertNotNull(nov04);
        assertEquals(LocalTime.of(13, 30), nov04.getStartTime());
        assertEquals(LocalTime.of(16, 0), nov04.getEndTime());
        assertEquals("Sala 111", nov04.getLocation().getDisplayText());

        Lesson jan27 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2027, 1, 27))).findFirst().orElse(null);
        assertNotNull(jan27);
        assertEquals(LocalTime.of(13, 30), jan27.getStartTime());
        assertEquals(LocalTime.of(16, 0), jan27.getEndTime());
    }

    @Test
    public void testRokZerowyWdnMultiDatesOverride() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        parser.setAcademicYears(2026, 2027);

        // Czwartek from Rok zerowy screenshot:
        String rawBlock = "Język polski - pisanie - 25h\n" +
                "mgr Violetta Wołoch\n" +
                "daty: 12.11, 19.11, 26.11, 03.12, 10.12, 17.12, 14.01, 21.01, 28.01\n" +
                "w dn. 12.11 oraz 19.11 zajęcia w godz. 13:30-15:05\n" +
                "sala\n" +
                "zajęcia w siedzibie Uczelni";

        List<Lesson> lessons = parser.parseLessonBlock(rawBlock, null, LocalTime.of(11, 45), LocalTime.of(13, 20), null, null);
        assertEquals(9, lessons.size());

        // 12.11 and 19.11 must have overridden time 13:30 - 15:05
        Lesson nov12 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 11, 12))).findFirst().orElse(null);
        assertNotNull(nov12);
        assertEquals(LocalTime.of(13, 30), nov12.getStartTime());
        assertEquals(LocalTime.of(15, 5), nov12.getEndTime());
        assertEquals("WSPA", nov12.getLocation().getDisplayText());

        Lesson nov19 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 11, 19))).findFirst().orElse(null);
        assertNotNull(nov19);
        assertEquals(LocalTime.of(13, 30), nov19.getStartTime());
        assertEquals(LocalTime.of(15, 5), nov19.getEndTime());

        // Other dates (e.g. 26.11) keep the cell's default hours (11:45 - 13:20)
        Lesson nov26 = lessons.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 11, 26))).findFirst().orElse(null);
        assertNotNull(nov26);
        assertEquals(LocalTime.of(11, 45), nov26.getStartTime());
        assertEquals(LocalTime.of(13, 20), nov26.getEndTime());
    }

    @Test
    public void testRokZerowyDuplicateUczelniaLocationAndSala111() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        parser.setAcademicYears(2026, 2027);

        // Czwartek from Rok zerowy screenshot with duplicate "zajęcia w siedzibie Uczelni" and sala 111:
        String blockSala111 = "Język polski - słownictwo i komunikacja ustna - 50h\n" +
                "dr Agnieszka Mehmegdolu\n" +
                "daty: 22.10 w godz. 11:45-15:05\n" +
                "daty: 05.11 w godz. 11:45-16:00\n" +
                "sala 111\n" +
                "zajęcia w siedzibie Uczelni\n" +
                "zajęcia w siedzibie Uczelni";

        List<Lesson> lessons111 = parser.parseLessonBlock(blockSala111, null, LocalTime.of(11, 45), LocalTime.of(15, 5), null, null);
        assertEquals(2, lessons111.size());
        for (Lesson l : lessons111) {
            assertEquals("Sala 111", l.getLocation().getDisplayText());
            assertEquals("111", l.getLocation().getRoomNumber());
            assertFalse(l.getLocation().isOffsite());
            assertFalse(l.getLocation().isOnline());
        }

        // Poniedziałek from Rok zerowy screenshot with bare "sala" and duplicate "zajęcia w siedzibie Uczelni":
        String blockBareSala = "Język polski - słownictwo i komunikacja ustna - 50h\n" +
                "dr Agnieszka Mehmegdolu\n" +
                "daty: 19.10 w godz. 11:45-13:20\n" +
                "daty: 18.01, 25.01 w godz. 11:45-14:15\n" +
                "sala\n" +
                "zajęcia w siedzibie Uczelni\n" +
                "zajęcia w siedzibie Uczelni";

        List<Lesson> lessonsBare = parser.parseLessonBlock(blockBareSala, null, LocalTime.of(12, 35), LocalTime.of(14, 15), null, null);
        assertEquals(3, lessonsBare.size());
        for (Lesson l : lessonsBare) {
            assertEquals("WSPA", l.getLocation().getDisplayText());
            assertNull(l.getLocation().getRoomNumber());
        }

        Lesson oct19 = lessonsBare.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2026, 10, 19))).findFirst().orElse(null);
        assertNotNull(oct19);
        assertEquals(LocalTime.of(11, 45), oct19.getStartTime());
        assertEquals(LocalTime.of(13, 20), oct19.getEndTime());

        Lesson jan18 = lessonsBare.stream().filter(l -> l.getDate() != null && l.getDate().equals(LocalDate.of(2027, 1, 18))).findFirst().orElse(null);
        assertNotNull(jan18);
        assertEquals(LocalTime.of(11, 45), jan18.getStartTime());
        assertEquals(LocalTime.of(14, 15), jan18.getEndTime());
    }

    @Test
    public void testRokZerowyFindTargetColumnsWhenNoGroups() throws Exception {
        // Construct an in-memory sheet mimicking Rok zerowy:
        // Row 0: "Rok zerowy I SEMESTR rok akademicki 2026/2027"
        // Row 1: Col 0: "DNI", Col 1: "PONIEDZIAŁEK", Col 2: "WTOREK", Col 3: "ŚRODA", Col 4: "CZWARTEK", Col 5: "PIĄTEK"
        // Row 2: Col 0: "GRUPA" (no groups under any day!)
        // Row 3: Col 0: "GODZ."
        java.util.Map<Integer, java.util.Map<Integer, String>> rowMap = new java.util.HashMap<>();
        
        java.util.Map<Integer, String> r0 = new java.util.HashMap<>();
        r0.put(0, "Rok zerowy I SEMESTR rok akademicki 2026/2027");
        rowMap.put(0, r0);

        java.util.Map<Integer, String> r1 = new java.util.HashMap<>();
        r1.put(0, "DNI");
        r1.put(1, "PONIEDZIAŁEK");
        r1.put(2, "WTOREK");
        r1.put(3, "ŚRODA");
        r1.put(4, "CZWARTEK");
        r1.put(5, "PIĄTEK");
        rowMap.put(1, r1);

        java.util.Map<Integer, String> r2 = new java.util.HashMap<>();
        r2.put(0, "GRUPA");
        rowMap.put(2, r2);

        java.util.Map<Integer, String> r3 = new java.util.HashMap<>();
        r3.put(0, "GODZ.");
        rowMap.put(3, r3);

        ExcelSheet sheet = new ExcelSheet("I semestr", rowMap, java.util.Collections.emptyList());

        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        java.lang.reflect.Method m = BachelorFullTimeParser.class.getDeclaredMethod("findTargetColumns", ExcelSheet.class, String.class, String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.List<Integer> cols = (java.util.List<Integer>) m.invoke(parser, sheet, "Ivanov", null);

        // All 5 days (columns 1, 2, 3, 4, 5) must be included!
        assertEquals(5, cols.size());
        assertTrue(cols.contains(1));
        assertTrue(cols.contains(2));
        assertTrue(cols.contains(3));
        assertTrue(cols.contains(4));
        assertTrue(cols.contains(5));
    }

    @Test
    public void testParserFactoryZeroYear() {
        com.vintro.wsplanner.parser.ScheduleParser parser =
                com.vintro.wsplanner.parser.ParserFactory.getParser(
                        com.vintro.wsplanner.enums.DegreeLevel.ZERO_YEAR,
                        com.vintro.wsplanner.enums.StudyMode.FULL_TIME
                );
        assertNotNull(parser);
        assertTrue(parser instanceof BachelorFullTimeParser);
    }
}