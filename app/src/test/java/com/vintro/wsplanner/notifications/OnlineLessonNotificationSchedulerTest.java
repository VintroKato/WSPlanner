package com.vintro.wsplanner.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vintro.wsplanner.models.Lesson;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// unit tests for online class reminder eligibility, multi-offset scheduling, due windows, and deduplication
public class OnlineLessonNotificationSchedulerTest {

    private static Lesson makeLesson(String subject, String room, LocalDate date, LocalTime start, LocalTime end) {
        return new Lesson(subject, "Wykład", "dr Jan Kowalski", room, date, null, start, end);
    }

    @Test
    public void onlyOnlineLessonsAreEligible() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson online = makeLesson("Algorytmy", "Online, PUW", date, LocalTime.of(10, 0), LocalTime.of(11, 30));
        Lesson campus = makeLesson("Fizyka", "Sala 204", date, LocalTime.of(10, 0), LocalTime.of(11, 30));
        Lesson offsite = makeLesson("WF", "ul. Lipowa 10, Lublin", date, LocalTime.of(10, 0), LocalTime.of(11, 30));

        assertTrue(OnlineLessonNotificationScheduler.isLessonEligible(online));
        assertFalse(OnlineLessonNotificationScheduler.isLessonEligible(campus));
        assertFalse(OnlineLessonNotificationScheduler.isLessonEligible(offsite));
        assertFalse(OnlineLessonNotificationScheduler.isLessonEligible(null));
    }

    @Test
    public void normalizeLeadOffsetsClampsDeduplicatesAndDefaultsToZero() {
        assertEquals(Collections.singletonList(0), OnlineLessonNotificationScheduler.normalizeLeadOffsets(null));
        assertEquals(Collections.singletonList(0), OnlineLessonNotificationScheduler.normalizeLeadOffsets(Collections.emptyList()));
        assertEquals(
                Arrays.asList(0, 5, 20, 120),
                OnlineLessonNotificationScheduler.normalizeLeadOffsets(Arrays.asList(20, 5, 5, -3, 250))
        );
    }

    @Test
    public void findNextScheduledEventSelectsEarliestAcrossMultipleOffsetsAndIgnoresCampus() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson campusEarlier = makeLesson("Matematyka", "Sala 101", date, LocalTime.of(8, 0), LocalTime.of(9, 30));
        Lesson onlineFirst = makeLesson("Bazy danych", "Online, PUW", date, LocalTime.of(10, 0), LocalTime.of(11, 30));
        Lesson onlineSecond = makeLesson("Sieci", "PUW", date, LocalTime.of(12, 0), LocalTime.of(13, 30));

        List<Lesson> lessons = Arrays.asList(campusEarlier, onlineFirst, onlineSecond);
        List<Integer> offsets = Arrays.asList(0, 5, 15);
        LocalDateTime now = LocalDateTime.of(date, LocalTime.of(9, 40));

        OnlineLessonNotificationScheduler.ReminderEvent next =
                OnlineLessonNotificationScheduler.findNextScheduledEvent(lessons, offsets, Collections.emptySet(), now);

        assertNotNull(next);
        assertEquals("Bazy danych", next.lesson.getSubjectName());
        assertEquals(15, next.leadMinutes);
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 45)), next.triggerTime);

        // after 15-min reminder is delivered, next should be 5-min reminder for same lesson
        Set<String> delivered = new HashSet<>();
        delivered.add(next.deliveryKey);
        OnlineLessonNotificationScheduler.ReminderEvent second =
                OnlineLessonNotificationScheduler.findNextScheduledEvent(
                        lessons,
                        offsets,
                        delivered,
                        LocalDateTime.of(date, LocalTime.of(9, 45))
                );

        assertNotNull(second);
        assertEquals("Bazy danych", second.lesson.getSubjectName());
        assertEquals(5, second.leadMinutes);
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 55)), second.triggerTime);

        // after 5-min reminder is delivered, next should be 0-min reminder at class start
        delivered.add(second.deliveryKey);
        OnlineLessonNotificationScheduler.ReminderEvent third =
                OnlineLessonNotificationScheduler.findNextScheduledEvent(
                        lessons,
                        offsets,
                        delivered,
                        LocalDateTime.of(date, LocalTime.of(9, 55))
                );

        assertNotNull(third);
        assertEquals("Bazy danych", third.lesson.getSubjectName());
        assertEquals(0, third.leadMinutes);
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), third.triggerTime);
    }

    @Test
    public void findDueEventsRespectsWindowDeduplicationAndParallelLessons() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson parallelA = makeLesson("AI", "Online", date, LocalTime.of(10, 0), LocalTime.of(11, 30));
        Lesson parallelB = makeLesson("Cloud", "PUW", date, LocalTime.of(10, 0), LocalTime.of(11, 30));
        Lesson later = makeLesson("Security", "Online", date, LocalTime.of(12, 0), LocalTime.of(13, 30));

        List<Lesson> lessons = Arrays.asList(parallelA, parallelB, later);
        List<Integer> offsets = Arrays.asList(0, 5);

        // at 09:55:00 both 5-min reminders are due, 0-min reminders are not due yet
        List<OnlineLessonNotificationScheduler.ReminderEvent> dueAt955 =
                OnlineLessonNotificationScheduler.findDueEvents(
                        lessons,
                        offsets,
                        Collections.emptySet(),
                        LocalDateTime.of(date, LocalTime.of(9, 55, 0))
                );
        assertEquals(2, dueAt955.size());
        assertEquals(5, dueAt955.get(0).leadMinutes);
        assertEquals(5, dueAt955.get(1).leadMinutes);

        // mark 5-min reminders delivered; at 10:00:05 only 0-min reminders fire
        Set<String> delivered = new HashSet<>();
        for (OnlineLessonNotificationScheduler.ReminderEvent e : dueAt955) {
            delivered.add(e.deliveryKey);
        }
        List<OnlineLessonNotificationScheduler.ReminderEvent> dueAt1000 =
                OnlineLessonNotificationScheduler.findDueEvents(
                        lessons,
                        offsets,
                        delivered,
                        LocalDateTime.of(date, LocalTime.of(10, 0, 5))
                );
        assertEquals(2, dueAt1000.size());
        assertEquals(0, dueAt1000.get(0).leadMinutes);
        assertEquals(0, dueAt1000.get(1).leadMinutes);

        // stale time past 5-minute grace period after start should not fire
        List<OnlineLessonNotificationScheduler.ReminderEvent> stale =
                OnlineLessonNotificationScheduler.findDueEvents(
                        lessons,
                        offsets,
                        Collections.emptySet(),
                        LocalDateTime.of(date, LocalTime.of(10, 6, 0))
                );
        assertTrue(stale.isEmpty());
    }

    @Test
    public void computeRemainingMinutesRoundsCleanly() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson lesson = makeLesson("Java", "Online", date, LocalTime.of(10, 0), LocalTime.of(11, 30));

        assertEquals(5, OnlineLessonNotificationScheduler.computeRemainingMinutesUntilStart(
                lesson, LocalDateTime.of(date, LocalTime.of(9, 55, 10))));
        assertEquals(0, OnlineLessonNotificationScheduler.computeRemainingMinutesUntilStart(
                lesson, LocalDateTime.of(date, LocalTime.of(9, 59, 45))));
        assertEquals(0, OnlineLessonNotificationScheduler.computeRemainingMinutesUntilStart(
                lesson, LocalDateTime.of(date, LocalTime.of(10, 1, 0))));
    }

    @Test
    public void pruneDeliveredKeysRemovesOnlyOlderDates() {
        Set<String> keys = new HashSet<>();
        keys.add("2026-11-20|lesson1|0");
        keys.add("2026-11-27|lesson2|5");
        keys.add("2026-11-28|lesson3|0");
        keys.add("invalid_key");

        Set<String> pruned = OnlineLessonNotificationScheduler.pruneDeliveredKeys(keys, LocalDate.of(2026, 11, 26));
        assertEquals(2, pruned.size());
        assertTrue(pruned.contains("2026-11-27|lesson2|5"));
        assertTrue(pruned.contains("2026-11-28|lesson3|0"));
        assertFalse(pruned.contains("2026-11-20|lesson1|0"));
    }

    @Test
    public void evaluateDueBatchCollapsesMultipleOverdueOffsetsForSameLessonIntoOneNotification() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson online = makeLesson("AI", "Online, PUW", date, LocalTime.of(10, 0), LocalTime.of(11, 30));

        // both 15-min (09:45) and 5-min (09:55) offsets are overdue at 09:55 (e.g. clock jump or phone reboot)
        OnlineLessonNotificationScheduler.DueBatch batch = OnlineLessonNotificationScheduler.evaluateDueBatch(
                Collections.singletonList(online),
                Arrays.asList(0, 5, 15),
                Collections.emptySet(),
                LocalDateTime.of(date, LocalTime.of(9, 55, 0))
        );

        assertEquals(1, batch.eventsToNotify.size());
        assertEquals(5, batch.eventsToNotify.get(0).leadMinutes);

        List<String> consumed = batch.deliveryKeysByLessonId.get(online.getId());
        assertNotNull(consumed);
        assertEquals(2, consumed.size());
        assertTrue(consumed.contains(OnlineLessonNotificationScheduler.buildDeliveryKey(online, 15)));
        assertTrue(consumed.contains(OnlineLessonNotificationScheduler.buildDeliveryKey(online, 5)));

        // after marking both 15 and 5 consumed, only the 0-min reminder remains for 10:00
        Set<String> delivered = new HashSet<>(consumed);
        OnlineLessonNotificationScheduler.ReminderEvent next = OnlineLessonNotificationScheduler.findNextScheduledEvent(
                Collections.singletonList(online),
                Arrays.asList(0, 5, 15),
                delivered,
                LocalDateTime.of(date, LocalTime.of(9, 55, 1))
        );
        assertNotNull(next);
        assertEquals(0, next.leadMinutes);
    }

    @Test
    public void adjacentOneMinAndZeroMinOffsetsDoNotCollide() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson online = makeLesson("AI", "Online, PUW", date, LocalTime.of(10, 0), LocalTime.of(11, 30));

        // at 09:59:20 only the 1-min offset should fire, leaving 0-min intact for 10:00
        OnlineLessonNotificationScheduler.DueBatch batchAt95920 = OnlineLessonNotificationScheduler.evaluateDueBatch(
                Collections.singletonList(online),
                Arrays.asList(0, 1),
                Collections.emptySet(),
                LocalDateTime.of(date, LocalTime.of(9, 59, 20))
        );
        assertEquals(1, batchAt95920.eventsToNotify.size());
        assertEquals(1, batchAt95920.eventsToNotify.get(0).leadMinutes);

        Set<String> delivered = new HashSet<>(batchAt95920.deliveryKeysByLessonId.get(online.getId()));
        assertEquals(1, delivered.size());

        OnlineLessonNotificationScheduler.ReminderEvent next = OnlineLessonNotificationScheduler.findNextScheduledEvent(
                Collections.singletonList(online),
                Arrays.asList(0, 1),
                delivered,
                LocalDateTime.of(date, LocalTime.of(9, 59, 20))
        );
        assertNotNull(next);
        assertEquals(0, next.leadMinutes);
        assertEquals(LocalDateTime.of(date, LocalTime.of(10, 0)), next.triggerTime);
    }

    @Test
    public void findNextScheduledEventReturnsNullWhenAllOnlineClassesPassed() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        Lesson pastOnline = makeLesson("AI", "Online", date, LocalTime.of(8, 0), LocalTime.of(9, 30));
        OnlineLessonNotificationScheduler.ReminderEvent next =
                OnlineLessonNotificationScheduler.findNextScheduledEvent(
                        Collections.singletonList(pastOnline),
                        Arrays.asList(0, 10),
                        Collections.emptySet(),
                        LocalDateTime.of(date, LocalTime.of(9, 0))
                );
        assertNull(next);
    }
}
