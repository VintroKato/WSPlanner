package com.vintro.wsplanner;

import com.vintro.wsplanner.data.ScheduleRepository;
import com.vintro.wsplanner.enums.LessonType;
import com.vintro.wsplanner.models.Lesson;
import com.vintro.wsplanner.parser.BachelorFullTimeParser;
import com.vintro.wsplanner.ui.activities.SubjectDetailsActivity;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// regression unit tests for critical parser, model, cache, and note key bugfixes
public class BugfixRegressionTest {

    @Test
    public void polishPrepositionWInSubjectTitleIsNotMisclassifiedAsLecture() {
        assertEquals(LessonType.INNE, LessonType.fromSubjectName("Programowanie w języku Java"));
        assertEquals(LessonType.INNE, LessonType.fromSubjectName("Wprowadzenie w sieci komputerowe"));
        assertEquals(LessonType.WYKLAD, LessonType.fromSubjectName("Algorytmy - wykład"));
        assertEquals(LessonType.LABORATORIUM, LessonType.fromSubjectName("Bazy danych (lab)"));

        // explicit raw type abbreviations remain recognized
        assertEquals(LessonType.WYKLAD, LessonType.fromString("W"));
        assertEquals(LessonType.WYKLAD, LessonType.fromString("w."));
        assertEquals(LessonType.WYKLAD, LessonType.fromString("wyk"));

        Lesson lesson = new Lesson(
                "Programowanie w języku Java",
                null,
                "dr Jan Nowak",
                "Sala 204",
                LocalDate.of(2026, 5, 12),
                null,
                LocalTime.of(10, 0),
                LocalTime.of(11, 30)
        );
        assertEquals(LessonType.INNE, lesson.getResolvedLessonType());
    }

    @Test
    public void lessonBoundaryUsesHalfOpenIntervalWithoutOverlapAtEndTime() {
        LocalDate date = LocalDate.of(2026, 5, 12);
        Lesson lesson = new Lesson(
                "Bazy danych",
                "wykład",
                "dr Jan Nowak",
                "Sala 204",
                date,
                null,
                LocalTime.of(10, 0),
                LocalTime.of(11, 30)
        );

        LocalDateTime beforeStart = LocalDateTime.of(date, LocalTime.of(9, 59, 59));
        assertFalse(lesson.isCurrent(beforeStart));
        assertFalse(lesson.isPast(beforeStart));

        LocalDateTime exactStart = LocalDateTime.of(date, LocalTime.of(10, 0, 0));
        assertTrue(lesson.isCurrent(exactStart));
        assertFalse(lesson.isPast(exactStart));

        LocalDateTime beforeEnd = LocalDateTime.of(date, LocalTime.of(11, 29, 59));
        assertTrue(lesson.isCurrent(beforeEnd));
        assertFalse(lesson.isPast(beforeEnd));

        // exact end time must be past and never current simultaneously
        LocalDateTime exactEnd = LocalDateTime.of(date, LocalTime.of(11, 30, 0));
        assertFalse(lesson.isCurrent(exactEnd));
        assertTrue(lesson.isPast(exactEnd));
    }

    @Test
    public void languageGroupOneDoesNotMatchGroupTen() {
        BachelorFullTimeParser parser = new BachelorFullTimeParser();
        parser.setAcademicYears(2025, 2026);

        String block = "Język angielski - lektorat 30h\n"
                + "gr. 10 - mgr Anna Zielińska - sala 105\n"
                + "daty: 12.05\n"
                + "gr. 1 - mgr Jan Kowalski - sala 201\n"
                + "daty: 19.05\n"
                + "zajęcia w siedzibie Uczelni";

        List<Lesson> group1Lessons = parser.parseLessonBlock(
                block, null, LocalTime.of(8, 15), LocalTime.of(9, 45), "1", null
        );
        assertEquals(1, group1Lessons.size());
        assertEquals(LocalDate.of(2026, 5, 19), group1Lessons.get(0).getDate());
        assertEquals("mgr Jan Kowalski", group1Lessons.get(0).getTeacherName());
        assertEquals("Sala 201", group1Lessons.get(0).getLocation().getDisplayText());

        List<Lesson> group10Lessons = parser.parseLessonBlock(
                block, null, LocalTime.of(8, 15), LocalTime.of(9, 45), "10", null
        );
        assertEquals(1, group10Lessons.size());
        assertEquals(LocalDate.of(2026, 5, 12), group10Lessons.get(0).getDate());
        assertEquals("mgr Anna Zielińska", group10Lessons.get(0).getTeacherName());
        assertEquals("Sala 105", group10Lessons.get(0).getLocation().getDisplayText());

        // block containing only gr.10 must return no lessons for group 1
        String group10Only = "Język angielski - lektorat 30h\n"
                + "gr. 10 - mgr Anna Zielińska - sala 105\n"
                + "daty: 12.05\n"
                + "zajęcia w siedzibie Uczelni";
        List<Lesson> noMatch = parser.parseLessonBlock(
                group10Only, null, LocalTime.of(8, 15), LocalTime.of(9, 45), "1", null
        );
        assertTrue(noMatch.isEmpty());
    }

    @Test
    public void zipMagicValidationAcceptsXlsxHeaderAndRejectsHtmlLoginPage() throws Exception {
        File validZip = File.createTempFile("wsplanner_valid_", ".xlsx");
        File htmlFile = File.createTempFile("wsplanner_html_", ".xlsx");
        File emptyFile = File.createTempFile("wsplanner_empty_", ".xlsx");
        try {
            try (FileOutputStream out = new FileOutputStream(validZip)) {
                out.write(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00});
            }
            try (FileOutputStream out = new FileOutputStream(htmlFile)) {
                out.write("<!DOCTYPE html><html><body>Login</body></html>".getBytes(StandardCharsets.UTF_8));
            }

            assertTrue(ScheduleRepository.isValidZipFile(validZip));
            assertFalse(ScheduleRepository.isValidZipFile(htmlFile));
            assertFalse(ScheduleRepository.isValidZipFile(emptyFile));
            assertFalse(ScheduleRepository.isValidZipFile(new File(validZip.getParentFile(), "does_not_exist.xlsx")));
        } finally {
            validZip.delete();
            htmlFile.delete();
            emptyFile.delete();
        }
    }

    @Test
    public void canonicalNoteKeyNormalizesRawAndResolvedLessonTypes() {
        String keyFromEnum = SubjectDetailsActivity.buildCanonicalNoteKey("Bazy danych", LessonType.WYKLAD, "Wykład");
        String keyFromShortRaw = SubjectDetailsActivity.buildCanonicalNoteKey("  bazy danych ", null, "W");
        String keyFromPolishRaw = SubjectDetailsActivity.buildCanonicalNoteKey("Bazy danych", null, "wykład");

        assertEquals("note_bazy danych_wyklad", keyFromEnum);
        assertEquals(keyFromEnum, keyFromShortRaw);
        assertEquals(keyFromEnum, keyFromPolishRaw);
    }
}
