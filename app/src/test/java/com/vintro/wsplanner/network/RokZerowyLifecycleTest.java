package com.vintro.wsplanner.network;

import com.vintro.wsplanner.enums.DegreeLevel;
import com.vintro.wsplanner.enums.StudyMode;
import com.vintro.wsplanner.models.ParsedCourse;
import com.vintro.wsplanner.parser.BachelorFullTimeParser;
import com.vintro.wsplanner.parser.ParserFactory;
import com.vintro.wsplanner.parser.ScheduleParser;
import com.vintro.wsplanner.parser.excel.ExcelSheet;
import com.vintro.wsplanner.services.CourseSetupService;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// end-to-end tests verifying all lifecycle stages of rok zerowy preparatory course support
public class RokZerowyLifecycleTest {

    // stage 1: puw html parsing and student zone prioritization

    @Test
    public void testPuwParsing_WhenBothRegularAndRokZerowy_PrioritizesRegularStudentZone() {
        String htmlBoth = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=808\" " +
                "   title=\"Strefa uczestnika kursu &quot;ROK ZEROWY&quot;\">" +
                "   Strefa uczestnika kursu \"ROK ZEROWY\"</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=303\" " +
                "   title=\"Strefa studenta - kierunek Informatyka - studia I stopnia\">" +
                "   Strefa studenta - kierunek Informatyka - studia I stopnia</a>" +
                "</body></html>";

        List<ParsedCourse> courses = StudyPlanScraper.parseAvailableCoursesFromHtml(htmlBoth);

        assertNotNull(courses);
        assertEquals("Must return only the active degree course when regular student zone exists", 1, courses.size());
        ParsedCourse activeCourse = courses.get(0);
        assertEquals("Informatyka", activeCourse.fieldOfStudy);
        assertEquals(DegreeLevel.BACHELORS, activeCourse.degreeLevel);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=303", activeCourse.courseUrl);
    }

    @Test
    public void testPuwParsing_WhenOnlyRokZerowy_CorrectlyBuildsZeroYearCourse() {
        String htmlOnlyZero = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=808\" " +
                "   title=\"Strefa uczestnika kursu &quot;ROK ZEROWY&quot;\">" +
                "   Strefa uczestnika kursu \"ROK ZEROWY\"</a>" +
                "</body></html>";

        List<ParsedCourse> courses = StudyPlanScraper.parseAvailableCoursesFromHtml(htmlOnlyZero);

        assertNotNull(courses);
        assertEquals(1, courses.size());
        ParsedCourse zeroCourse = courses.get(0);
        assertEquals("Rok zerowy", zeroCourse.fieldOfStudy);
        assertEquals(DegreeLevel.ZERO_YEAR, zeroCourse.degreeLevel);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=808", zeroCourse.courseUrl);

        // Verify available modes and years defaults: only full-time (st) and year 1
        assertNotNull(zeroCourse.availableModesAndYears);
        assertTrue("Must contain full-time mode 'st'", zeroCourse.availableModesAndYears.containsKey("st"));
        assertEquals(Collections.singletonList(1), zeroCourse.availableModesAndYears.get("st"));
        assertFalse("Must NOT contain part-time 'nst'", zeroCourse.availableModesAndYears.containsKey("nst"));
        assertFalse("Must NOT contain online 'nst puw'", zeroCourse.availableModesAndYears.containsKey("nst puw"));
    }

    @Test
    public void testPuwParsing_WithCaseAndTextVariations_DetectsZeroYear() {
        String htmlVariation = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=999\">" +
                "   Strefa uczestnika kursu Rok zerowy" +
                "</a>" +
                "</body></html>";

        List<ParsedCourse> courses = StudyPlanScraper.parseAvailableCoursesFromHtml(htmlVariation);

        assertEquals(1, courses.size());
        assertEquals("Rok zerowy", courses.get(0).fieldOfStudy);
        assertEquals(DegreeLevel.ZERO_YEAR, courses.get(0).degreeLevel);
    }

    // stage 2: student zone url resolution

    @Test
    public void testExtractStudentZoneUrl_PriorityAndMatching() {
        String htmlMixed = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=808\">Strefa uczestnika kursu \"ROK ZEROWY\"</a>" +
                "<a href=\"https://e-learning.wspa.pl/course/view.php?id=303\">Strefa studenta - kierunek Informatyka - studia I stopnia</a>" +
                "</body></html>";

        // active bachelor student must never get rok zerowy zone
        String urlRegular = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlMixed, "Informatyka", DegreeLevel.BACHELORS);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=303", urlRegular);

        // rok zerowy student gets participant zone
        String urlZero = StudyPlanScraper.extractStudentZoneUrlFromHtml(htmlMixed, "Rok zerowy", DegreeLevel.ZERO_YEAR);
        assertEquals("https://e-learning.wspa.pl/course/view.php?id=808", urlZero);
    }

    // stage 3: schedule excel file resolution

    @Test
    public void testExtractScheduleFileFromFolderDoc_RokZerowyFiles() {
        // Variation 1: "Rok zerowy - semestr zimowy.xlsx"
        String htmlWinter = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/mod/folder/view.php?id=10\">Plany zajęć</a>" +
                "<a href=\"https://e-learning.wspa.pl/pluginfile.php/123/mod_folder/content/0/Rok%20zerowy%20-%20semestr%20zimowy.xlsx\">" +
                "<span class=\"fp-filename\">Rok zerowy - semestr zimowy.xlsx</span></a>" +
                "</body></html>";
        Document docWinter = Jsoup.parse(htmlWinter);
        String urlWinter = StudyPlanScraper.extractScheduleFileFromFolderDoc(docWinter, true, StudyMode.FULL_TIME, 1, "plany zajęć");
        assertNotNull(urlWinter);
        assertTrue(urlWinter.contains("Rok%20zerowy%20-%20semestr%20zimowy.xlsx"));
        assertTrue(urlWinter.contains("forcedownload=1"));

        // Variation 2: "semestr letni.xlsx"
        String htmlSummer = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/pluginfile.php/456/mod_folder/content/0/semestr%20letni.xlsx\">" +
                "<span class=\"fp-filename\">semestr letni.xlsx</span></a>" +
                "</body></html>";
        Document docSummer = Jsoup.parse(htmlSummer);
        String urlSummer = StudyPlanScraper.extractScheduleFileFromFolderDoc(docSummer, true, StudyMode.FULL_TIME, 1, "plany zajęć");
        assertNotNull(urlSummer);
        assertTrue(urlSummer.contains("semestr%20letni.xlsx"));
        assertTrue(urlSummer.contains("forcedownload=1"));

        // Variation 3: Simple "Rok zerowy.xlsx" (with other irrelevant files present)
        String htmlSimple = "<html><body>" +
                "<a href=\"https://e-learning.wspa.pl/pluginfile.php/789/mod_folder/content/0/Regulamin.pdf\">" +
                "<span class=\"fp-filename\">Regulamin.pdf</span></a>" +
                "<a href=\"https://e-learning.wspa.pl/pluginfile.php/999/mod_folder/content/0/Rok%20zerowy.xlsx\">" +
                "<span class=\"fp-filename\">Rok zerowy.xlsx</span></a>" +
                "</body></html>";
        Document docSimple = Jsoup.parse(htmlSimple);
        String urlSimple = StudyPlanScraper.extractScheduleFileFromFolderDoc(docSimple, true, StudyMode.FULL_TIME, 1, "plany zajęć");
        assertNotNull(urlSimple);
        assertTrue(urlSimple.contains("Rok%20zerowy.xlsx"));
        assertTrue(urlSimple.contains("forcedownload=1"));
    }

    @Test
    public void testIsRokZerowyScheduleFile() {
        assertTrue("Must match 'Rok zerowy.xlsx'", StudyPlanScraper.isRokZerowyScheduleFile("Rok zerowy.xlsx"));
        assertTrue("Must match 'rok zerowy.xlsx'", StudyPlanScraper.isRokZerowyScheduleFile("rok zerowy.xlsx"));
        assertTrue("Must match 'ROK ZEROWY.XLSX'", StudyPlanScraper.isRokZerowyScheduleFile("ROK ZEROWY.XLSX"));
        assertTrue("Must match 'Rok zerowy - semestr zimowy.xlsx'", StudyPlanScraper.isRokZerowyScheduleFile("Rok zerowy - semestr zimowy.xlsx"));
        assertTrue("Must match 'Rok zerowy - semestr letni.xlsx'", StudyPlanScraper.isRokZerowyScheduleFile("Rok zerowy - semestr letni.xlsx"));
        assertTrue("Must match 'semestr zimowy.xlsx'", StudyPlanScraper.isRokZerowyScheduleFile("semestr zimowy.xlsx"));
        assertTrue("Must match 'semestr letni.xlsx'", StudyPlanScraper.isRokZerowyScheduleFile("semestr letni.xlsx"));

        assertFalse("Must not match non-excel files", StudyPlanScraper.isRokZerowyScheduleFile("Rok zerowy.pdf"));
        assertFalse("Must not match null", StudyPlanScraper.isRokZerowyScheduleFile(null));
        assertFalse("Must not match empty string", StudyPlanScraper.isRokZerowyScheduleFile(""));
    }

    // stage 4: course setup service rules for rok zerowy

    @Test
    public void testCourseSetupService_RulesAndConstraints() {
        CourseSetupService service = CourseSetupService.getInstance();

        ParsedCourse zeroCourse = new ParsedCourse();
        zeroCourse.fieldOfStudy = "Rok zerowy";
        zeroCourse.degreeLevel = DegreeLevel.ZERO_YEAR;
        zeroCourse.courseUrl = "https://e-learning.wspa.pl/course/view.php?id=808";
        zeroCourse.availableModesAndYears.put("st", new ArrayList<>(List.of(1)));

        List<ParsedCourse> listOnlyZero = Collections.singletonList(zeroCourse);

        // primary course determination
        ParsedCourse primary = service.determinePrimaryCourse(listOnlyZero);
        assertNotNull(primary);
        assertEquals("Rok zerowy", primary.fieldOfStudy);

        // degree levels: only ZERO_YEAR
        List<DegreeLevel> degrees = service.getAvailableDegreeLevels(listOnlyZero, "Rok zerowy");
        assertEquals(1, degrees.size());
        assertEquals(DegreeLevel.ZERO_YEAR, degrees.get(0));

        // study modes: only FULL_TIME
        List<StudyMode> modes = service.getAvailableStudyModes(zeroCourse);
        assertEquals(1, modes.size());
        assertEquals(StudyMode.FULL_TIME, modes.get(0));

        // study years: only year 1
        List<Integer> years = service.getAvailableYears(zeroCourse, StudyMode.FULL_TIME);
        assertEquals(Collections.singletonList(1), years);

        // findCourse matching
        ParsedCourse found = service.findCourse(listOnlyZero, "Rok zerowy", DegreeLevel.ZERO_YEAR);
        assertNotNull(found);
        assertEquals(zeroCourse, found);

        // findCourse when looking for BACHELORS should return null
        ParsedCourse notFound = service.findCourse(listOnlyZero, "Rok zerowy", DegreeLevel.BACHELORS);
        assertNull(notFound);
    }

    // stage 5: parser factory and parser implementation

    @Test
    public void testParserFactory_MapsZeroYearToBachelorFullTimeParser() {
        ScheduleParser parser = ParserFactory.getParser(DegreeLevel.ZERO_YEAR, StudyMode.FULL_TIME);
        assertNotNull(parser);
        assertTrue("Parser for ZERO_YEAR must be BachelorFullTimeParser", parser instanceof BachelorFullTimeParser);
    }

    @Test
    public void testBachelorFullTimeParser_HeaderAndColumnResolutionForRokZerowy() throws Exception {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();

        // 1. verify header detection treats "rok zerowy" as non-lesson meta header
        Method isDayOrMeta = BachelorFullTimeParser.class.getDeclaredMethod("isDayOrMetaHeader", String.class);
        isDayOrMeta.setAccessible(true);
        boolean isHeader = (boolean) isDayOrMeta.invoke(parser, "Rok zerowy I SEMESTR rok akademicki 2026/2027");
        assertTrue("Must detect 'Rok zerowy' as day or meta header", isHeader);

        // 2. verify findTargetColumns selects all weekday columns when no student groups exist
        Map<Integer, Map<Integer, String>> rowMap = new HashMap<>();

        Map<Integer, String> r0 = new HashMap<>();
        r0.put(0, "Rok zerowy I SEMESTR");
        rowMap.put(0, r0);

        Map<Integer, String> r1 = new HashMap<>();
        r1.put(0, "DNI");
        r1.put(1, "PONIEDZIAŁEK");
        r1.put(2, "WTOREK");
        r1.put(3, "ŚRODA");
        r1.put(4, "CZWARTEK");
        r1.put(5, "PIĄTEK");
        rowMap.put(1, r1);

        Map<Integer, String> r2 = new HashMap<>();
        r2.put(0, "GRUPA"); // no groups listed under any day
        rowMap.put(2, r2);

        Map<Integer, String> r3 = new HashMap<>();
        r3.put(0, "GODZ.");
        rowMap.put(3, r3);

        ExcelSheet sheet = new ExcelSheet("I semestr", rowMap, Collections.emptyList());

        Method findTargetCols = BachelorFullTimeParser.class.getDeclaredMethod("findTargetColumns", ExcelSheet.class, String.class, String.class);
        findTargetCols.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Integer> cols = (List<Integer>) findTargetCols.invoke(parser, sheet, "SurnameOnly", null);

        assertNotNull(cols);
        assertEquals("Must select all 5 weekday columns when no groups are present in timetable", 5, cols.size());
        assertTrue(cols.contains(1));
        assertTrue(cols.contains(2));
        assertTrue(cols.contains(3));
        assertTrue(cols.contains(4));
        assertTrue(cols.contains(5));
    }

    // stage 6: degree level enum and string naming standards

    @Test
    public void testDegreeLevelEnum_NamingAndConversions() {
        // enforce English enum constant name
        assertEquals("ZERO_YEAR", DegreeLevel.ZERO_YEAR.name());

        // test scraper string conversions
        assertEquals("rok zerowy", StudyPlanScraper.getDegreeLevelString(DegreeLevel.ZERO_YEAR));

        // test scraper parsing from strings
        assertEquals(DegreeLevel.ZERO_YEAR, StudyPlanScraper.parseDegreeLevel("rok zerowy"));
        assertEquals(DegreeLevel.ZERO_YEAR, StudyPlanScraper.parseDegreeLevel("ROK ZEROWY"));
        assertEquals(DegreeLevel.ZERO_YEAR, StudyPlanScraper.parseDegreeLevel("uczestnika kursu"));
    }
}
