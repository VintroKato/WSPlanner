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
    }
}